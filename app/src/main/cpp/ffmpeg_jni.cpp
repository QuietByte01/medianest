#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <vector>
#include <queue>
#include <chrono>
#include <unistd.h>
#include <fcntl.h>
#include <oboe/Oboe.h>

#define TAG "FFmpegNativePlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/avutil.h>
#include <libavutil/imgutils.h>
#include <libavutil/time.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersink.h>
#include <libavfilter/buffersrc.h>
}

static JavaVM *g_jvm = nullptr;

// Thread-safe buffer for audio samples
struct AudioBuffer {
    std::mutex mutex;
    std::vector<int16_t> samples;
    size_t read_ptr = 0;

    void push(const int16_t* data, size_t count) {
        std::lock_guard<std::mutex> lock(mutex);
        samples.insert(samples.end(), data, data + count);
        // Limit buffer size to 500ms to prevent extreme latency
        if (samples.size() > read_ptr + 48000 * 2) {
             // Drop old data if buffer is too large
        }
    }

    size_t pull(int16_t* out, size_t count) {
        std::lock_guard<std::mutex> lock(mutex);
        size_t available = samples.size() - read_ptr;
        size_t to_read = std::min(count, available);
        if (to_read > 0) {
            std::copy(samples.begin() + read_ptr, samples.begin() + read_ptr + to_read, out);
            read_ptr += to_read;
            if (read_ptr > 100000) { // Cleanup periodically
                samples.erase(samples.begin(), samples.begin() + read_ptr);
                read_ptr = 0;
            }
        }
        return to_read;
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mutex);
        samples.clear();
        read_ptr = 0;
    }
};

class PlayerContext;

class OboeAudioCallback : public oboe::AudioStreamDataCallback {
public:
    PlayerContext* ctx;
    explicit OboeAudioCallback(PlayerContext* c) : ctx(c) {}
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override;
};

struct PlayerContext {
    jobject java_ref = nullptr;
    jmethodID on_stats_mid = nullptr;

    std::mutex mutex;
    AVFormatContext *fmt_ctx = nullptr;
    int video_stream_idx = -1;
    int audio_stream_idx = -1;
    AVCodecContext *v_codec_ctx = nullptr;
    AVCodecContext *a_codec_ctx = nullptr;
    ANativeWindow *native_window = nullptr;

    // Filter Graph for DSP
    AVFilterGraph *filter_graph = nullptr;
    AVFilterContext *buffersrc_ctx = nullptr;
    AVFilterContext *buffersink_ctx = nullptr;
    std::string current_filter_desc = "anull"; // Default no-op filter

    // Oboe Audio
    std::shared_ptr<oboe::AudioStream> audio_stream;
    std::unique_ptr<OboeAudioCallback> oboe_callback;
    AudioBuffer audio_buffer;

    int source_fd = -1;
    std::atomic<bool> is_playing{false};
    std::atomic<bool> is_released{false};
    int64_t duration = 0;
    std::atomic<int64_t> current_position{0};

    std::atomic<int> dropped_frames{0};
    std::atomic<int> audio_errors{0};
    std::atomic<int> ts_recoveries{0};

    std::atomic<int64_t> video_frame_count{0};
    std::atomic<int64_t> start_time{0};

    std::thread playback_thread;

    void cleanup(JNIEnv *env) {
        LOGI("cleanup: Releasing all resources");
        is_released = true;
        is_playing = false;
        if (playback_thread.joinable()) playback_thread.join();

        if (audio_stream) {
            audio_stream->stop();
            audio_stream->close();
        }

        std::lock_guard<std::mutex> lock(mutex);
        if (filter_graph) avfilter_graph_free(&filter_graph);
        if (v_codec_ctx) avcodec_free_context(&v_codec_ctx);
        if (a_codec_ctx) avcodec_free_context(&a_codec_ctx);
        if (fmt_ctx) avformat_close_input(&fmt_ctx);
        if (source_fd != -1) {
            close(source_fd);
            source_fd = -1;
        }
        if (native_window) {
            ANativeWindow_release(native_window);
            native_window = nullptr;
        }
        if (java_ref) {
            env->DeleteGlobalRef(java_ref);
            java_ref = nullptr;
        }
    }

    void set_surface(JNIEnv *env, jobject surface) {
        std::lock_guard<std::mutex> lock(mutex);
        if (native_window) {
            ANativeWindow_release(native_window);
            native_window = nullptr;
        }
        if (surface) {
            native_window = ANativeWindow_fromSurface(env, surface);
            if (native_window && v_codec_ctx) {
                ANativeWindow_setBuffersGeometry(native_window, v_codec_ctx->width, v_codec_ctx->height, WINDOW_FORMAT_RGBA_8888);
            }
        }
    }

    int init_filter_graph(const std::string& filters_desc) {
        std::lock_guard<std::mutex> lock(mutex);
        if (filter_graph) avfilter_graph_free(&filter_graph);
        filter_graph = avfilter_graph_alloc();

        const AVFilter *abuffersrc = avfilter_get_by_name("abuffer");
        const AVFilter *abuffersink = avfilter_get_by_name("abuffersink");
        AVFilterInOut *outputs = avfilter_inout_alloc();
        AVFilterInOut *inputs = avfilter_inout_alloc();
        int ret = 0;

        char args[512];
        AVChannelLayout ch_layout = a_codec_ctx->ch_layout;
        snprintf(args, sizeof(args),
                 "sample_rate=%d:sample_fmt=%s:time_base=%d/%d:channel_layout=%s",
                 a_codec_ctx->sample_rate, av_get_sample_fmt_name(a_codec_ctx->sample_fmt),
                 a_codec_ctx->time_base.num, a_codec_ctx->time_base.den, "stereo"); // Force stereo for mobile

        ret = avfilter_graph_create_filter(&buffersrc_ctx, abuffersrc, "in", args, nullptr, filter_graph);
        if (ret < 0) goto end;

        ret = avfilter_graph_create_filter(&buffersink_ctx, abuffersink, "out", nullptr, nullptr, filter_graph);
        if (ret < 0) goto end;

        static const enum AVSampleFormat out_sample_fmts[] = { AV_SAMPLE_FMT_S16, AV_SAMPLE_FMT_NONE };
        ret = av_opt_set_int_list(buffersink_ctx, "sample_fmts", out_sample_fmts, -1, AV_OPT_SEARCH_CHILDREN);
        if (ret < 0) goto end;

        outputs->name = av_strdup("in");
        outputs->filter_ctx = buffersrc_ctx;
        outputs->pad_idx = 0;
        outputs->next = nullptr;

        inputs->name = av_strdup("out");
        inputs->filter_ctx = buffersink_ctx;
        inputs->pad_idx = 0;
        inputs->next = nullptr;

        if ((ret = avfilter_graph_parse_ptr(filter_graph, filters_desc.c_str(), &inputs, &outputs, nullptr)) < 0) goto end;
        if ((ret = avfilter_graph_config(filter_graph, nullptr)) < 0) goto end;

    end:
        avfilter_inout_free(&inputs);
        avfilter_inout_free(&outputs);
        return ret;
    }
};

oboe::DataCallbackResult OboeAudioCallback::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
    auto *outputData = static_cast<int16_t *>(audioData);
    size_t samplesNeeded = numFrames * audioStream->getChannelCount();
    size_t pulled = ctx->audio_buffer.pull(outputData, samplesNeeded);

    if (pulled < samplesNeeded) {
        std::fill(outputData + pulled, outputData + samplesNeeded, 0);
    }
    return oboe::DataCallbackResult::Continue;
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    av_log_set_level(AV_LOG_ERROR);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativeInit(JNIEnv *env, jobject thiz) {
    try {
        auto ctx = new PlayerContext();
        ctx->java_ref = env->NewGlobalRef(thiz);
        jclass clazz = env->GetObjectClass(thiz);
        ctx->on_stats_mid = env->GetMethodID(clazz, "onNativeStatsUpdate", "(III)V");
        return reinterpret_cast<jlong>(ctx);
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativeProbe(JNIEnv *env, jobject thiz, jlong ptr, jint fd) {
    AVFormatContext *probe_fmt_ctx = avformat_alloc_context();
    char path[64];
    int dup_fd = dup(fd);
    lseek(dup_fd, 0, SEEK_SET);
    sprintf(path, "/proc/self/fd/%d", dup_fd);

    probe_fmt_ctx->probesize = 1000000;
    probe_fmt_ctx->max_analyze_duration = 1000000;

    if (avformat_open_input(&probe_fmt_ctx, path, nullptr, nullptr) != 0) {
        avformat_free_context(probe_fmt_ctx);
        close(dup_fd);
        return nullptr;
    }

    avformat_find_stream_info(probe_fmt_ctx, nullptr);
    std::string container = probe_fmt_ctx->iformat->name;
    std::string vcodec = "none", acodec = "none";
    for (unsigned int i = 0; i < probe_fmt_ctx->nb_streams; i++) {
        auto cp = probe_fmt_ctx->streams[i]->codecpar;
        if (cp->codec_type == AVMEDIA_TYPE_VIDEO && vcodec == "none") vcodec = avcodec_get_name(cp->codec_id);
        else if (cp->codec_type == AVMEDIA_TYPE_AUDIO && acodec == "none") acodec = avcodec_get_name(cp->codec_id);
    }
    avformat_close_input(&probe_fmt_ctx);
    close(dup_fd);
    return env->NewStringUTF((container + "|" + vcodec + "|" + acodec).c_str());
}

void playback_loop(PlayerContext *ctx) {
    LOGI("playback_loop: Thread started");
    AVPacket *packet = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    AVFrame *filter_frame = av_frame_alloc();
    struct SwsContext *sws_ctx = nullptr;

    int stats_counter = 0;

    while (!ctx->is_released) {
        if (!ctx->is_playing) {
            ctx->start_time = 0;
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            continue;
        }

        if (ctx->start_time == 0) {
            ctx->start_time = av_gettime() - (ctx->current_position * 1000);
        }

        int ret = av_read_frame(ctx->fmt_ctx, packet);
        if (ret < 0) {
            if (ret == AVERROR_EOF) {
                LOGI("playback_loop: EOFReached");
                ctx->is_playing = false;
            } else {
                ctx->ts_recoveries++;
                std::this_thread::sleep_for(std::chrono::milliseconds(5));
            }
            continue;
        }

        if (packet->stream_index == ctx->video_stream_idx) {
            if (avcodec_send_packet(ctx->v_codec_ctx, packet) >= 0) {
                while (avcodec_receive_frame(ctx->v_codec_ctx, frame) >= 0) {
                    ctx->video_frame_count++;
                    int64_t pts = frame->best_effort_timestamp;
                    if (pts == AV_NOPTS_VALUE) pts = ctx->video_frame_count;

                    double time_base = av_q2d(ctx->fmt_ctx->streams[ctx->video_stream_idx]->time_base);
                    int64_t pts_ms = (int64_t)(pts * time_base * 1000);

                    int64_t now_ms = (av_gettime() - ctx->start_time.load()) / 1000;
                    if (abs(pts_ms - now_ms) > 1000 || pts_ms < ctx->current_position) {
                        ctx->start_time = av_gettime() - (pts_ms * 1000);
                        ctx->ts_recoveries++;
                        now_ms = pts_ms;
                    }

                    if (pts_ms > now_ms + 5) {
                        int64_t wait = pts_ms - now_ms;
                        if (wait > 100) wait = 100;
                        std::this_thread::sleep_for(std::chrono::milliseconds(wait));
                    }

                    std::lock_guard<std::mutex> lock(ctx->mutex);
                    if (ctx->native_window) {
                        ANativeWindow_Buffer buffer;
                        if (ANativeWindow_lock(ctx->native_window, &buffer, nullptr) == 0) {
                            sws_ctx = sws_getCachedContext(sws_ctx,
                                frame->width, frame->height, (AVPixelFormat)frame->format,
                                buffer.width, buffer.height, AV_PIX_FMT_RGBA,
                                SWS_FAST_BILINEAR, nullptr, nullptr, nullptr);
                            uint8_t *dest[4] = {(uint8_t *)buffer.bits, nullptr, nullptr, nullptr};
                            int dest_linesize[4] = {buffer.stride * 4, 0, 0, 0};
                            sws_scale(sws_ctx, frame->data, frame->linesize, 0, frame->height, dest, dest_linesize);
                            ANativeWindow_unlockAndPost(ctx->native_window);
                        }
                    }
                    ctx->current_position = pts_ms;
                }
            } else {
                ctx->dropped_frames++;
            }
        } else if (packet->stream_index == ctx->audio_stream_idx) {
            if (avcodec_send_packet(ctx->a_codec_ctx, packet) >= 0) {
                while (avcodec_receive_frame(ctx->a_codec_ctx, frame) >= 0) {
                    std::lock_guard<std::mutex> lock(ctx->mutex);
                    if (ctx->filter_graph) {
                        if (av_buffersrc_add_frame_flags(ctx->buffersrc_ctx, frame, AV_BUFFERSRC_FLAG_KEEP_REF) >= 0) {
                            while (av_buffersink_get_frame(ctx->buffersink_ctx, filter_frame) >= 0) {
                                ctx->audio_buffer.push(reinterpret_cast<int16_t*>(filter_frame->data[0]), filter_frame->nb_samples * 2);
                                av_frame_unref(filter_frame);
                            }
                        }
                    }
                    // For audio-only, update position from audio frames
                    if (ctx->video_stream_idx == -1) {
                         double time_base = av_q2d(ctx->fmt_ctx->streams[ctx->audio_stream_idx]->time_base);
                         ctx->current_position = (int64_t)(frame->pts * time_base * 1000);
                    }
                }
            } else {
                ctx->audio_errors++;
            }
        }
        av_packet_unref(packet);
        if (++stats_counter % 50 == 0) {
            JNIEnv *env = nullptr;
            if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                env->CallVoidMethod(ctx->java_ref, ctx->on_stats_mid, (int)ctx->dropped_frames, (int)ctx->audio_errors, (int)ctx->ts_recoveries);
                g_jvm->DetachCurrentThread();
            }
        }
    }

    av_packet_free(&packet);
    av_frame_free(&frame);
    av_frame_free(&filter_frame);
    if (sws_ctx) sws_freeContext(sws_ctx);
    LOGI("playback_loop: Thread exiting");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativePrepare(JNIEnv *env, jobject thiz, jlong ptr, jint fd, jobject surface) {
    auto ctx = reinterpret_cast<PlayerContext *>(ptr);
    if (!ctx) return JNI_FALSE;

    char path[64];
    ctx->source_fd = dup(fd);
    lseek(ctx->source_fd, 0, SEEK_SET);
    sprintf(path, "/proc/self/fd/%d", ctx->source_fd);

    if (avformat_open_input(&ctx->fmt_ctx, path, nullptr, nullptr) != 0) {
        close(ctx->source_fd);
        ctx->source_fd = -1;
        return JNI_FALSE;
    }

    avformat_find_stream_info(ctx->fmt_ctx, nullptr);
    for (unsigned int i = 0; i < ctx->fmt_ctx->nb_streams; i++) {
        auto cp = ctx->fmt_ctx->streams[i]->codecpar;
        if (cp->codec_type == AVMEDIA_TYPE_VIDEO) ctx->video_stream_idx = i;
        else if (cp->codec_type == AVMEDIA_TYPE_AUDIO) ctx->audio_stream_idx = i;
    }

    auto setup = [&](int idx, AVCodecContext **c) {
        if (idx < 0) return;
        auto codec = avcodec_find_decoder(ctx->fmt_ctx->streams[idx]->codecpar->codec_id);
        if (!codec) return;
        *c = avcodec_alloc_context3(codec);
        avcodec_parameters_to_context(*c, ctx->fmt_ctx->streams[idx]->codecpar);
        avcodec_open2(*c, codec, nullptr);
    };
    setup(ctx->video_stream_idx, &ctx->v_codec_ctx);
    setup(ctx->audio_stream_idx, &ctx->a_codec_ctx);

    if (ctx->audio_stream_idx >= 0) {
        ctx->init_filter_graph(ctx->current_filter_desc);

        // Init Oboe
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output);
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
        builder.setSharingMode(oboe::SharingMode::Exclusive);
        builder.setFormat(oboe::AudioFormat::I16);
        builder.setChannelCount(oboe::ChannelCount::Stereo);
        builder.setSampleRate(ctx->a_codec_ctx->sample_rate);
        ctx->oboe_callback = std::make_unique<OboeAudioCallback>(ctx);
        builder.setDataCallback(ctx->oboe_callback.get());

        auto result = builder.openStream(ctx->audio_stream);
        if (result == oboe::Result::OK) {
            ctx->audio_stream->requestStart();
        }
    }

    ctx->duration = (ctx->fmt_ctx->duration != AV_NOPTS_VALUE) ? (ctx->fmt_ctx->duration / AV_TIME_BASE) * 1000 : 0;
    ctx->set_surface(env, surface);
    ctx->is_released = false;
    ctx->playback_thread = std::thread(playback_loop, ctx);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativePlay(JNIEnv *env, jobject thiz, jlong ptr) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        ctx->is_playing = true;
        if (ctx->audio_stream) ctx->audio_stream->requestStart();
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativePause(JNIEnv *env, jobject thiz, jlong ptr) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        ctx->is_playing = false;
        if (ctx->audio_stream) ctx->audio_stream->requestPause();
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativeSeek(JNIEnv *env, jobject thiz, jlong ptr, jlong pos) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        std::lock_guard<std::mutex> lock(ctx->mutex);
        av_seek_frame(ctx->fmt_ctx, -1, pos * AV_TIME_BASE / 1000, AVSEEK_FLAG_BACKWARD);
        ctx->current_position = pos;
        ctx->start_time = 0;
        ctx->audio_buffer.clear();
        if (ctx->v_codec_ctx) avcodec_flush_buffers(ctx->v_codec_ctx);
        if (ctx->a_codec_ctx) avcodec_flush_buffers(ctx->a_codec_ctx);
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativeGetPosition(JNIEnv *env, jobject thiz, jlong ptr) {
    auto ctx = reinterpret_cast<PlayerContext *>(ptr);
    return ctx ? ctx->current_position.load() : 0;
}

extern "C" JNIEXPORT jlong JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativeGetDuration(JNIEnv *env, jobject thiz, jlong ptr) {
    auto ctx = reinterpret_cast<PlayerContext *>(ptr);
    return ctx ? ctx->duration : 0;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativeIsPlaying(JNIEnv *env, jobject thiz, jlong ptr) {
    auto ctx = reinterpret_cast<PlayerContext *>(ptr);
    return ctx ? ctx->is_playing.load() : false;
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativeRelease(JNIEnv *env, jobject thiz, jlong ptr) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        ctx->cleanup(env);
        delete ctx;
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativeUpdateSurface(JNIEnv *env, jobject thiz, jlong ptr, jobject surface) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) ctx->set_surface(env, surface);
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativeSetAudioFilters(JNIEnv *env, jobject thiz, jlong ptr, jstring filters) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        const char* f = env->GetStringUTFChars(filters, nullptr);
        ctx->current_filter_desc = f;
        if (ctx->fmt_ctx) ctx->init_filter_graph(f);
        env->ReleaseStringUTFChars(filters, f);
    }
}
