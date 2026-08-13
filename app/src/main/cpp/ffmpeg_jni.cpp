#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <vector>
#include <chrono>
#include <unistd.h>
#include <fcntl.h>

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
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>
}

static JavaVM *g_jvm = nullptr;

struct PlayerContext {
    jobject java_ref = nullptr;
    jmethodID on_audio_data_mid = nullptr;
    jmethodID on_stats_mid = nullptr;

    std::mutex mutex;
    AVFormatContext *fmt_ctx = nullptr;
    int video_stream_idx = -1;
    int audio_stream_idx = -1;
    AVCodecContext *v_codec_ctx = nullptr;
    AVCodecContext *a_codec_ctx = nullptr;
    ANativeWindow *native_window = nullptr;

    int source_fd = -1;

    std::atomic<bool> is_playing{false};
    std::atomic<bool> is_released{false};
    std::atomic<bool> is_seeking{false};
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
        std::lock_guard<std::mutex> lock(mutex);
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
};

extern "C" jint JNI_OnLoad(JavaVM *vm, void *reserved) {
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
        ctx->on_audio_data_mid = env->GetMethodID(clazz, "onAudioData", "([BII)V");
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

    // Set fast probe options
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
    JNIEnv *env = nullptr;
    if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;

    AVPacket *packet = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    SwrContext *swr_ctx = nullptr;
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
                std::this_thread::sleep_for(std::chrono::milliseconds(2));
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

                    // Resilience: Large jumps or backward movement
                    int64_t now_ms = (av_gettime() - ctx->start_time.load()) / 1000;
                    if (abs(pts_ms - now_ms) > 1000 || pts_ms < ctx->current_position) {
                        ctx->start_time = av_gettime() - (pts_ms * 1000);
                        ctx->ts_recoveries++;
                        now_ms = pts_ms;
                    }

                    if (pts_ms > now_ms + 2) {
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
                    if (!swr_ctx) {
                        swr_alloc_set_opts2(&swr_ctx,
                            &ctx->a_codec_ctx->ch_layout, AV_SAMPLE_FMT_S16, ctx->a_codec_ctx->sample_rate,
                            &ctx->a_codec_ctx->ch_layout, ctx->a_codec_ctx->sample_fmt, ctx->a_codec_ctx->sample_rate,
                            0, nullptr);
                        swr_init(swr_ctx);
                    }
                    int out_samples = swr_get_out_samples(swr_ctx, frame->nb_samples);
                    uint8_t *out_data = nullptr;
                    av_samples_alloc(&out_data, nullptr, ctx->a_codec_ctx->ch_layout.nb_channels, out_samples, AV_SAMPLE_FMT_S16, 0);
                    swr_convert(swr_ctx, &out_data, out_samples, (const uint8_t **)frame->data, frame->nb_samples);
                    int out_size = out_samples * ctx->a_codec_ctx->ch_layout.nb_channels * 2;
                    jbyteArray j_data = env->NewByteArray(out_size);
                    env->SetByteArrayRegion(j_data, 0, out_size, (jbyte *)out_data);
                    env->CallVoidMethod(ctx->java_ref, ctx->on_audio_data_mid, j_data, ctx->a_codec_ctx->sample_rate, ctx->a_codec_ctx->ch_layout.nb_channels);
                    env->DeleteLocalRef(j_data);
                    av_freep(&out_data);
                }
            } else {
                ctx->audio_errors++;
            }
        }
        av_packet_unref(packet);
        if (++stats_counter % 20 == 0) {
            env->CallVoidMethod(ctx->java_ref, ctx->on_stats_mid, (int)ctx->dropped_frames, (int)ctx->audio_errors, (int)ctx->ts_recoveries);
        }
    }

    av_packet_free(&packet);
    av_frame_free(&frame);
    if (swr_ctx) swr_free(&swr_ctx);
    if (sws_ctx) sws_freeContext(sws_ctx);
    g_jvm->DetachCurrentThread();
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

    AVDictionary *options = nullptr;
    av_dict_set(&options, "fflags", "+discardcorrupt", 0);
    av_dict_set(&options, "err_detect", "ignore_err", 0);
    av_dict_set(&options, "threads", "auto", 0);

    if (avformat_open_input(&ctx->fmt_ctx, path, nullptr, &options) != 0) {
        av_dict_free(&options);
        close(ctx->source_fd);
        ctx->source_fd = -1;
        return JNI_FALSE;
    }
    av_dict_free(&options);

    ctx->fmt_ctx->max_analyze_duration = 5000000;
    ctx->fmt_ctx->probesize = 5000000;
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
        (*c)->thread_count = 0; // auto
        avcodec_open2(*c, codec, nullptr);
    };
    setup(ctx->video_stream_idx, &ctx->v_codec_ctx);
    setup(ctx->audio_stream_idx, &ctx->a_codec_ctx);

    ctx->duration = (ctx->fmt_ctx->duration != AV_NOPTS_VALUE) ? (ctx->fmt_ctx->duration / AV_TIME_BASE) * 1000 : 0;
    ctx->set_surface(env, surface);
    ctx->is_released = false;
    ctx->playback_thread = std::thread(playback_loop, ctx);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativePlay(JNIEnv *env, jobject thiz, jlong ptr) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) ctx->is_playing = true;
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativePause(JNIEnv *env, jobject thiz, jlong ptr) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) ctx->is_playing = false;
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativeSeek(JNIEnv *env, jobject thiz, jlong ptr, jlong pos) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        std::lock_guard<std::mutex> lock(ctx->mutex);
        av_seek_frame(ctx->fmt_ctx, -1, pos * AV_TIME_BASE / 1000, AVSEEK_FLAG_BACKWARD);
        ctx->current_position = pos;
        ctx->start_time = 0;
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
        ctx->is_released = true;
        ctx->is_playing = false;
        if (ctx->playback_thread.joinable()) ctx->playback_thread.join();
        ctx->cleanup(env);
        delete ctx;
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_example_player_FFmpegPlaybackEngine_nativeUpdateSurface(JNIEnv *env, jobject thiz, jlong ptr, jobject surface) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) ctx->set_surface(env, surface);
}
