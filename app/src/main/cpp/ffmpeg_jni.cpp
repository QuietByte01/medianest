#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <vector>
#include <queue>
#include <array>
#include <chrono>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <oboe/Oboe.h>

#define TAG "FFmpegNativePlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
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

// Lock-free atomic ring buffer for audio samples (zero allocations, zero mutexes in high-priority audio callback)
struct AudioBuffer {
    static constexpr size_t RING_SIZE = 1536000; // 8 seconds of 96kHz stereo Float32 PCM for high-res stability
    std::array<float, RING_SIZE> ring_buffer{};
    std::atomic<size_t> write_pos{0};
    std::atomic<size_t> read_pos{0};

    void push(const float* data, size_t count) {
        if (!data || count == 0) return;

        size_t w = write_pos.load(std::memory_order_relaxed);
        for (size_t i = 0; i < count; ++i) {
            ring_buffer[w] = data[i];
            w = (w + 1) % RING_SIZE;
        }
        write_pos.store(w, std::memory_order_release);
    }

    size_t pull(float* out, size_t count) {
        if (!out || count == 0) return 0;
        size_t w = write_pos.load(std::memory_order_acquire);
        size_t r = read_pos.load(std::memory_order_relaxed);

        size_t available = (w >= r) ? (w - r) : (RING_SIZE - (r - w));
        size_t to_read = std::min(count, available);

        for (size_t i = 0; i < to_read; ++i) {
            out[i] = ring_buffer[r];
            r = (r + 1) % RING_SIZE;
        }
        read_pos.store(r, std::memory_order_release);
        return to_read;
    }

    size_t get_available() const {
        size_t w = write_pos.load(std::memory_order_acquire);
        size_t r = read_pos.load(std::memory_order_acquire);
        return (w >= r) ? (w - r) : (RING_SIZE - (r - w));
    }

    size_t get_free() const {
        return RING_SIZE - get_available() - 1;
    }

    void clear() {
        write_pos.store(0, std::memory_order_relaxed);
        read_pos.store(0, std::memory_order_relaxed);
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
    jmethodID on_ended_mid = nullptr;
    jmethodID on_hdr_mid = nullptr; // New callback for HDR
    std::atomic<int> repeat_mode{0};

    std::recursive_mutex mutex;
    AVFormatContext *fmt_ctx = nullptr;
    int video_stream_idx = -1;
    int audio_stream_idx = -1;
    AVCodecContext *v_codec_ctx = nullptr;
    AVCodecContext *a_codec_ctx = nullptr;
    ANativeWindow *native_window = nullptr;

    // Audio Filter Graph for DSP
    AVFilterGraph *a_filter_graph = nullptr;
    AVFilterContext *a_buffersrc_ctx = nullptr;
    AVFilterContext *a_buffersink_ctx = nullptr;
    std::string current_a_filter_desc = "anull";

    // Video Filter Graph for Tonemapping/FX
    AVFilterGraph *v_filter_graph = nullptr;
    AVFilterContext *v_buffersrc_ctx = nullptr;
    AVFilterContext *v_buffersink_ctx = nullptr;
    std::string current_v_filter_desc = "null";

    // Oboe Audio
    std::shared_ptr<oboe::AudioStream> audio_stream;
    std::unique_ptr<OboeAudioCallback> oboe_callback;
    AudioBuffer audio_buffer;
    std::atomic<bool> audio_initialized{false};

    int source_fd = -1;
    std::atomic<bool> is_playing{false};
    std::atomic<bool> is_released{false};
    std::atomic<bool> is_stopping{false};
    int64_t duration = 0;
    std::atomic<int64_t> current_position{0};

    std::atomic<int> dropped_frames{0};
    std::atomic<int> audio_errors{0};
    std::atomic<int> ts_recoveries{0};

    std::atomic<int64_t> video_frame_count{0};
    std::atomic<int64_t> start_time{0};

    std::thread playback_thread;

    ~PlayerContext() {
        reset_playback();
        if (native_window) {
            ANativeWindow_release(native_window);
            native_window = nullptr;
        }
    }

    void reset_playback() {
        LOGI("reset_playback: Initiating reset. is_playing=%d", is_playing.load());
        is_stopping = true;
        is_released = true;
        is_playing = false;

        // Wait for thread to finish using resources
        if (playback_thread.joinable()) {
            LOGD("reset_playback: Waiting for playback thread to join...");
            playback_thread.join();
            LOGD("reset_playback: Playback thread joined.");
        }

        if (audio_stream) {
            audio_stream->stop();
            audio_stream->close();
            audio_stream.reset();
        }
        audio_initialized = false;
        is_stopping = false;
        is_released = false;

        std::lock_guard<std::recursive_mutex> lock(mutex);
        if (a_filter_graph) {
            avfilter_graph_free(&a_filter_graph);
            a_filter_graph = nullptr;
            a_buffersrc_ctx = nullptr;
            a_buffersink_ctx = nullptr;
        }
        if (v_filter_graph) {
            avfilter_graph_free(&v_filter_graph);
            v_filter_graph = nullptr;
            v_buffersrc_ctx = nullptr;
            v_buffersink_ctx = nullptr;
        }
        if (v_codec_ctx) {
            avcodec_free_context(&v_codec_ctx);
            v_codec_ctx = nullptr;
        }
        if (a_codec_ctx) {
            avcodec_free_context(&a_codec_ctx);
            a_codec_ctx = nullptr;
        }
        if (fmt_ctx) {
            avformat_close_input(&fmt_ctx);
            fmt_ctx = nullptr;
        }
        if (source_fd != -1) {
            close(source_fd);
            source_fd = -1;
        }
        video_stream_idx = -1;
        audio_stream_idx = -1;
        duration = 0;
        current_position = 0;
        video_frame_count = 0;
        start_time = 0;
        dropped_frames = 0;
        audio_errors = 0;
        ts_recoveries = 0;
        audio_buffer.clear();
        // Keep is_released=true until we are ready for a new session in nativePrepare
    }

    void cleanup(JNIEnv *env) {
        LOGI("cleanup: Releasing all resources");
        reset_playback();

        std::lock_guard<std::recursive_mutex> lock(mutex);
        if (native_window) {
            ANativeWindow_release(native_window);
            native_window = nullptr;
        }
        if (java_ref && env) {
            env->DeleteGlobalRef(java_ref);
            java_ref = nullptr;
        }
    }

    void set_surface(JNIEnv *env, jobject surface) {
        std::lock_guard<std::recursive_mutex> lock(mutex);
        if (native_window) {
            ANativeWindow_release(native_window);
            native_window = nullptr;
        }
        if (surface && env) {
            native_window = ANativeWindow_fromSurface(env, surface);
            if (native_window && v_codec_ctx && v_codec_ctx->width > 0 && v_codec_ctx->height > 0) {
                ANativeWindow_setBuffersGeometry(native_window, v_codec_ctx->width, v_codec_ctx->height, WINDOW_FORMAT_RGBA_8888);
            }
        }
    }

    int init_audio_filter_graph(const std::string& filters_desc) {
        std::lock_guard<std::recursive_mutex> lock(mutex);
        if (a_filter_graph) {
            avfilter_graph_free(&a_filter_graph);
            a_filter_graph = nullptr;
            a_buffersrc_ctx = nullptr;
            a_buffersink_ctx = nullptr;
        }
        if (!a_codec_ctx) return -1;

        a_filter_graph = avfilter_graph_alloc();
        if (!a_filter_graph) return -1;

        const AVFilter *abuffersrc = avfilter_get_by_name("abuffer");
        const AVFilter *abuffersink = avfilter_get_by_name("abuffersink");
        if (!abuffersrc || !abuffersink) return -1;

        AVFilterInOut *outputs = avfilter_inout_alloc();
        AVFilterInOut *inputs = avfilter_inout_alloc();
        int ret = 0;

        char args[512];
        AVRational tb = {1, 44100};
        if (fmt_ctx && audio_stream_idx >= 0 && fmt_ctx->streams[audio_stream_idx]) {
            tb = fmt_ctx->streams[audio_stream_idx]->time_base;
        }
        if (tb.num <= 0 || tb.den <= 0) {
            tb = {1, (a_codec_ctx->sample_rate > 0) ? a_codec_ctx->sample_rate : 44100};
        }
        int sample_rate = (a_codec_ctx->sample_rate > 0) ? a_codec_ctx->sample_rate : 44100;
        const char* fmt_name = (a_codec_ctx->sample_fmt != AV_SAMPLE_FMT_NONE) ? av_get_sample_fmt_name(a_codec_ctx->sample_fmt) : "fltp";
        if (!fmt_name) fmt_name = "fltp";

        char in_layout_str[128] = "stereo";
        if (a_codec_ctx->ch_layout.nb_channels > 0) {
            av_channel_layout_describe(&a_codec_ctx->ch_layout, in_layout_str, sizeof(in_layout_str));
        }

        snprintf(args, sizeof(args),
                 "sample_rate=%d:sample_fmt=%s:time_base=%d/%d:channel_layout=%s",
                 sample_rate, fmt_name,
                 tb.num, tb.den, in_layout_str);

        ret = avfilter_graph_create_filter(&a_buffersrc_ctx, abuffersrc, "in", args, nullptr, a_filter_graph);
        if (ret < 0) goto end;

        ret = avfilter_graph_create_filter(&a_buffersink_ctx, abuffersink, "out", nullptr, nullptr, a_filter_graph);
        if (ret < 0) goto end;

        static const enum AVSampleFormat out_sample_fmts[] = { AV_SAMPLE_FMT_FLT, AV_SAMPLE_FMT_NONE };
        ret = av_opt_set_int_list(a_buffersink_ctx, "sample_fmts", out_sample_fmts, -1, AV_OPT_SEARCH_CHILDREN);
        if (ret < 0) goto end;

        outputs->name = av_strdup("in");
        outputs->filter_ctx = a_buffersrc_ctx;
        outputs->pad_idx = 0;
        outputs->next = nullptr;

        inputs->name = av_strdup("out");
        inputs->filter_ctx = a_buffersink_ctx;
        inputs->pad_idx = 0;
        inputs->next = nullptr;

        {
            std::string full_filters = filters_desc.empty() ? "anull" : filters_desc;
            full_filters += ",aformat=sample_fmts=flt:channel_layouts=stereo";

            if ((ret = avfilter_graph_parse_ptr(a_filter_graph, full_filters.c_str(), &inputs, &outputs, nullptr)) < 0) goto end;
            if ((ret = avfilter_graph_config(a_filter_graph, nullptr)) < 0) goto end;
        }

    end:
        avfilter_inout_free(&inputs);
        avfilter_inout_free(&outputs);
        return ret;
    }

    int init_video_filter_graph(const std::string& filters_desc) {
        std::lock_guard<std::recursive_mutex> lock(mutex);
        if (v_filter_graph) {
            avfilter_graph_free(&v_filter_graph);
            v_filter_graph = nullptr;
            v_buffersrc_ctx = nullptr;
            v_buffersink_ctx = nullptr;
        }
        if (!v_codec_ctx) return -1;

        v_filter_graph = avfilter_graph_alloc();
        if (!v_filter_graph) return -1;

        const AVFilter *buffersrc = avfilter_get_by_name("buffer");
        const AVFilter *buffersink = avfilter_get_by_name("buffersink");
        if (!buffersrc || !buffersink) return -1;

        AVFilterInOut *outputs = avfilter_inout_alloc();
        AVFilterInOut *inputs = avfilter_inout_alloc();
        int ret = 0;

        char args[512];
        AVRational tb = {1, 30};
        if (fmt_ctx && video_stream_idx >= 0 && fmt_ctx->streams[video_stream_idx]) {
            tb = fmt_ctx->streams[video_stream_idx]->time_base;
        }

        snprintf(args, sizeof(args),
                 "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d",
                 v_codec_ctx->width, v_codec_ctx->height, v_codec_ctx->pix_fmt,
                 tb.num, tb.den,
                 v_codec_ctx->sample_aspect_ratio.num, v_codec_ctx->sample_aspect_ratio.den);

        ret = avfilter_graph_create_filter(&v_buffersrc_ctx, buffersrc, "in", args, nullptr, v_filter_graph);
        if (ret < 0) goto end;

        ret = avfilter_graph_create_filter(&v_buffersink_ctx, buffersink, "out", nullptr, nullptr, v_filter_graph);
        if (ret < 0) goto end;

        outputs->name = av_strdup("in");
        outputs->filter_ctx = v_buffersrc_ctx;
        outputs->pad_idx = 0;
        outputs->next = nullptr;

        inputs->name = av_strdup("out");
        inputs->filter_ctx = v_buffersink_ctx;
        inputs->pad_idx = 0;
        inputs->next = nullptr;

        if ((ret = avfilter_graph_parse_ptr(v_filter_graph, filters_desc.c_str(), &inputs, &outputs, nullptr)) < 0) goto end;
        if ((ret = avfilter_graph_config(v_filter_graph, nullptr)) < 0) goto end;

    end:
        avfilter_inout_free(&inputs);
        avfilter_inout_free(&outputs);
        return ret;
    }
};

oboe::DataCallbackResult OboeAudioCallback::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
    if (!audioStream || !audioData || !ctx || ctx->is_stopping.load()) return oboe::DataCallbackResult::Stop;
    auto *outputData = static_cast<float *>(audioData);
    size_t samplesNeeded = numFrames * audioStream->getChannelCount();
    size_t pulled = ctx->audio_buffer.pull(outputData, samplesNeeded);

    if (pulled < samplesNeeded) {
        std::fill(outputData + pulled, outputData + samplesNeeded, 0.0f);
    }
    return oboe::DataCallbackResult::Continue;
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_jvm = vm;
    av_log_set_level(AV_LOG_ERROR);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_medianest_player_FFmpegPlaybackEngine_nativeInit(JNIEnv *env, jobject thiz) {
    try {
        auto ctx = new PlayerContext();
        ctx->java_ref = env->NewGlobalRef(thiz);
        jclass clazz = env->GetObjectClass(thiz);
        ctx->on_stats_mid = env->GetMethodID(clazz, "onNativeStatsUpdate", "(III)V");
        ctx->on_ended_mid = env->GetMethodID(clazz, "onNativePlaybackEnded", "()V");
        ctx->on_hdr_mid = env->GetMethodID(clazz, "onNativeHdrUpdate", "(ZLjava/lang/String;Ljava/lang/String;)V");
        return reinterpret_cast<jlong>(ctx);
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_medianest_player_FFmpegPlaybackEngine_nativeProbe(JNIEnv *env, jobject thiz, jlong ptr, jint fd) {
    AVFormatContext *probe_fmt_ctx = avformat_alloc_context();
    char path[64];
    int dup_fd = dup(fd);
    lseek(dup_fd, 0, SEEK_SET);
    sprintf(path, "/proc/self/fd/%d", dup_fd);

    probe_fmt_ctx->probesize = 10000000;
    probe_fmt_ctx->max_analyze_duration = 10000000;

    if (avformat_open_input(&probe_fmt_ctx, path, nullptr, nullptr) != 0) {
        avformat_free_context(probe_fmt_ctx);
        close(dup_fd);
        return nullptr;
    }

    avformat_find_stream_info(probe_fmt_ctx, nullptr);

    std::string container = probe_fmt_ctx->iformat ? probe_fmt_ctx->iformat->name : "unknown";
    std::string container_long = probe_fmt_ctx->iformat && probe_fmt_ctx->iformat->long_name ? probe_fmt_ctx->iformat->long_name : container;

    double duration_sec = probe_fmt_ctx->duration > 0 ? (double)probe_fmt_ctx->duration / (double)AV_TIME_BASE : 0.0;
    int64_t bitrate = probe_fmt_ctx->bit_rate > 0 ? probe_fmt_ctx->bit_rate : 0;

    int v_width = 0, v_height = 0, a_channels = 0, a_sample_rate = 0;
    std::string vcodec = "none", acodec = "none";
    std::string v_fps = "30/1";

    int64_t v_bitrate = 0;
    int64_t a_bitrate = 0;

    int v_stream_idx = -1;
    int a_stream_idx = -1;
    for (unsigned int i = 0; i < probe_fmt_ctx->nb_streams; i++) {
        auto cp = probe_fmt_ctx->streams[i]->codecpar;
        if (cp->codec_type == AVMEDIA_TYPE_VIDEO && vcodec == "none") {
            vcodec = avcodec_get_name(cp->codec_id);
            v_width = cp->width;
            v_height = cp->height;
            v_stream_idx = i;
            v_bitrate = cp->bit_rate;
            if (probe_fmt_ctx->streams[i]->avg_frame_rate.den > 0) {
                double fps = (double)probe_fmt_ctx->streams[i]->avg_frame_rate.num / (double)probe_fmt_ctx->streams[i]->avg_frame_rate.den;
                char fps_buf[32];
                snprintf(fps_buf, sizeof(fps_buf), "%.2f", fps);
                v_fps = fps_buf;
            }
        } else if (cp->codec_type == AVMEDIA_TYPE_AUDIO && acodec == "none") {
            acodec = avcodec_get_name(cp->codec_id);
            a_channels = cp->ch_layout.nb_channels > 0 ? cp->ch_layout.nb_channels : cp->channels;
            a_sample_rate = cp->sample_rate;
            a_bitrate = cp->bit_rate;
            a_stream_idx = i;
        }
    }

    // Fallback: Compute overall bitrate from file size and duration if probe_fmt_ctx->bit_rate is 0
    if (bitrate <= 0 && duration_sec > 0.0) {
        struct stat st;
        if (fstat(dup_fd, &st) == 0 && st.st_size > 0) {
            bitrate = (int64_t)((st.st_size * 8.0) / duration_sec);
        }
    }
    if (v_bitrate <= 0 && bitrate > 0) {
        v_bitrate = (a_bitrate > 0 && bitrate > a_bitrate) ? (bitrate - a_bitrate) : (int64_t)(bitrate * 0.85);
    }

    // Initialize decoders for stream corruption & bitstream integrity validation
    AVCodecContext *v_dec_ctx = nullptr;
    AVCodecContext *a_dec_ctx = nullptr;

    if (v_stream_idx >= 0) {
        const AVCodec *codec = avcodec_find_decoder(probe_fmt_ctx->streams[v_stream_idx]->codecpar->codec_id);
        if (codec) {
            v_dec_ctx = avcodec_alloc_context3(codec);
            if (v_dec_ctx) {
                avcodec_parameters_to_context(v_dec_ctx, probe_fmt_ctx->streams[v_stream_idx]->codecpar);
                // Flag errors explicitly
                v_dec_ctx->err_recognition = AV_EF_EXPLODE | AV_EF_CRCCHECK;
                if (avcodec_open2(v_dec_ctx, codec, nullptr) < 0) {
                    avcodec_free_context(&v_dec_ctx);
                    v_dec_ctx = nullptr;
                }
            }
        }
    }

    if (a_stream_idx >= 0) {
        const AVCodec *codec = avcodec_find_decoder(probe_fmt_ctx->streams[a_stream_idx]->codecpar->codec_id);
        if (codec) {
            a_dec_ctx = avcodec_alloc_context3(codec);
            if (a_dec_ctx) {
                avcodec_parameters_to_context(a_dec_ctx, probe_fmt_ctx->streams[a_stream_idx]->codecpar);
                a_dec_ctx->err_recognition = AV_EF_CRCCHECK;
                if (avcodec_open2(a_dec_ctx, codec, nullptr) < 0) {
                    avcodec_free_context(&a_dec_ctx);
                    a_dec_ctx = nullptr;
                }
            }
        }
    }

    int v_corrupt = 0, a_corrupt = 0, ts_discontinuity = 0;
    int64_t v_frames_count = 0;
    AVPacket *pkt = av_packet_alloc();
    AVFrame *dec_frame = av_frame_alloc();
    int64_t prev_v_pts = AV_NOPTS_VALUE, prev_a_pts = AV_NOPTS_VALUE;

    // Full-file scan across all packets with decoder validation
    while (av_read_frame(probe_fmt_ctx, pkt) == 0) {
        bool pkt_corrupted = (pkt->flags & AV_PKT_FLAG_CORRUPT) != 0;

        if (pkt->stream_index == v_stream_idx) {
            v_frames_count++;
            if (pkt_corrupted) {
                v_corrupt++;
            } else if (v_dec_ctx) {
                int send_ret = avcodec_send_packet(v_dec_ctx, pkt);
                if (send_ret < 0 && send_ret != AVERROR(EAGAIN) && send_ret != AVERROR_EOF) {
                    v_corrupt++;
                } else {
                    while (true) {
                        int rec_ret = avcodec_receive_frame(v_dec_ctx, dec_frame);
                        if (rec_ret < 0) break;
                        if ((dec_frame->flags & AV_FRAME_FLAG_CORRUPT) || dec_frame->decode_error_flags != 0) {
                            v_corrupt++;
                        }
                        av_frame_unref(dec_frame);
                    }
                }
            }

            if (prev_v_pts != AV_NOPTS_VALUE && pkt->pts != AV_NOPTS_VALUE && pkt->pts < prev_v_pts) {
                ts_discontinuity++;
            }
            if (pkt->pts != AV_NOPTS_VALUE) prev_v_pts = pkt->pts;
        } else if (pkt->stream_index == a_stream_idx) {
            if (pkt_corrupted) {
                a_corrupt++;
            } else if (a_dec_ctx) {
                int send_ret = avcodec_send_packet(a_dec_ctx, pkt);
                if (send_ret < 0 && send_ret != AVERROR(EAGAIN) && send_ret != AVERROR_EOF) {
                    a_corrupt++;
                } else {
                    while (true) {
                        int rec_ret = avcodec_receive_frame(a_dec_ctx, dec_frame);
                        if (rec_ret < 0) break;
                        if ((dec_frame->flags & AV_FRAME_FLAG_CORRUPT) || dec_frame->decode_error_flags != 0) {
                            a_corrupt++;
                        }
                        av_frame_unref(dec_frame);
                    }
                }
            }

            if (prev_a_pts != AV_NOPTS_VALUE && pkt->pts != AV_NOPTS_VALUE && pkt->pts < prev_a_pts) {
                ts_discontinuity++;
            }
            if (pkt->pts != AV_NOPTS_VALUE) prev_a_pts = pkt->pts;
        }
        av_packet_unref(pkt);
    }
    av_frame_free(&dec_frame);
    av_packet_free(&pkt);

    if (v_dec_ctx) avcodec_free_context(&v_dec_ctx);
    if (a_dec_ctx) avcodec_free_context(&a_dec_ctx);

    avformat_close_input(&probe_fmt_ctx);
    close(dup_fd);

    // Fallback stream frame count from stream header if available
    if (v_stream_idx >= 0 && probe_fmt_ctx && probe_fmt_ctx->streams[v_stream_idx]->nb_frames > 0) {
        if (v_frames_count <= 0) v_frames_count = probe_fmt_ctx->streams[v_stream_idx]->nb_frames;
    }

    // Construct valid FFprobe JSON response
    std::string json = "{";
    json += "\"format\":{";
    json += "\"format_name\":\"" + container + "\",";
    json += "\"format_long_name\":\"" + container_long + "\",";
    json += "\"duration\":\"" + std::to_string(duration_sec) + "\",";
    json += "\"bit_rate\":\"" + std::to_string(bitrate) + "\",";
    json += "\"v_corrupt\":" + std::to_string(v_corrupt) + ",";
    json += "\"a_corrupt\":" + std::to_string(a_corrupt) + ",";
    json += "\"ts_discontinuity\":" + std::to_string(ts_discontinuity) + ",";
    json += "\"probe_score\":100";
    json += "},";

    json += "\"streams\":[";
    bool has_prev = false;
    if (vcodec != "none") {
        json += "{";
        json += "\"index\":0,";
        json += "\"codec_type\":\"video\",";
        json += "\"codec_name\":\"" + vcodec + "\",";
        json += "\"codec_long_name\":\"FFmpeg " + vcodec + " Decoder\",";
        json += "\"width\":" + std::to_string(v_width) + ",";
        json += "\"height\":" + std::to_string(v_height) + ",";
        json += "\"r_frame_rate\":\"" + v_fps + "\",";
        json += "\"nb_frames\":\"" + std::to_string(v_frames_count) + "\",";
        json += "\"duration\":\"" + std::to_string(duration_sec) + "\",";
        json += "\"bit_rate\":\"" + std::to_string(v_bitrate) + "\"";
        json += "}";
        has_prev = true;
    }
    if (acodec != "none") {
        if (has_prev) json += ",";
        json += "{";
        json += "\"index\":1,";
        json += "\"codec_type\":\"audio\",";
        json += "\"codec_name\":\"" + acodec + "\",";
        json += "\"codec_long_name\":\"FFmpeg " + acodec + " Decoder\",";
        json += "\"channels\":" + std::to_string(a_channels) + ",";
        json += "\"sample_rate\":\"" + std::to_string(a_sample_rate) + "\",";
        json += "\"duration\":\"" + std::to_string(duration_sec) + "\",";
        json += "\"bit_rate\":\"" + std::to_string(a_bitrate) + "\"";
        json += "}";
    }
    json += "]";
    json += "}";

    return env->NewStringUTF(json.c_str());
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
            for (int i = 0; i < 3 && !ctx->is_released; ++i) {
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
            }
            continue;
        }

        if (ctx->start_time == 0) {
            ctx->start_time = av_gettime() - (ctx->current_position * 1000);
        }

        int ret;
        {
            std::lock_guard<std::recursive_mutex> lock(ctx->mutex);
            if (!ctx->fmt_ctx) {
                std::this_thread::sleep_for(std::chrono::milliseconds(30));
                continue;
            }
            ret = av_read_frame(ctx->fmt_ctx, packet);
        }
        if (ret < 0) {
            if (ret == AVERROR_EOF) {
                LOGI("playback_loop: EOF Reached");
                if (ctx->repeat_mode.load() == 1) { // Player.REPEAT_MODE_ONE
                    std::lock_guard<std::recursive_mutex> lock(ctx->mutex);
                    if (ctx->fmt_ctx) {
                        av_seek_frame(ctx->fmt_ctx, -1, 0, AVSEEK_FLAG_BACKWARD);
                    }
                    ctx->current_position = 0;
                    ctx->start_time = 0;
                    ctx->audio_buffer.clear();
                    if (ctx->v_codec_ctx) avcodec_flush_buffers(ctx->v_codec_ctx);
                    if (ctx->a_codec_ctx) avcodec_flush_buffers(ctx->a_codec_ctx);
                    continue;
                } else {
                    ctx->is_playing = false;
                    JNIEnv *env = nullptr;
                    if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                        if (ctx->java_ref && ctx->on_ended_mid) {
                            env->CallVoidMethod(ctx->java_ref, ctx->on_ended_mid);
                        }
                        g_jvm->DetachCurrentThread();
                    }
                }
            } else {
                ctx->ts_recoveries++;
                std::this_thread::sleep_for(std::chrono::milliseconds(5));
            }
            continue;
        }

        if (packet->stream_index == ctx->video_stream_idx && ctx->v_codec_ctx) {
            int send_res = avcodec_send_packet(ctx->v_codec_ctx, packet);
            if (send_res >= 0) {
                while (avcodec_receive_frame(ctx->v_codec_ctx, frame) >= 0 && !ctx->is_released) {
                    ctx->video_frame_count++;
                    // ... rest of video handling ...

                    int64_t pts = frame->best_effort_timestamp;
                    if (pts == AV_NOPTS_VALUE) pts = frame->pts;
                    if (pts == AV_NOPTS_VALUE) pts = ctx->video_frame_count;

                    double time_base = 1.0 / 24.0;
                    if (ctx->fmt_ctx && ctx->video_stream_idx >= 0 && ctx->fmt_ctx->streams[ctx->video_stream_idx]) {
                        AVRational vtb = ctx->fmt_ctx->streams[ctx->video_stream_idx]->time_base;
                        if (vtb.num > 0 && vtb.den > 0) {
                            time_base = av_q2d(vtb);
                        }
                    }
                    int64_t pts_ms = (int64_t)(pts * time_base * 1000);

                    int64_t now_ms = (av_gettime() - ctx->start_time.load()) / 1000;
                    if (abs(pts_ms - now_ms) > 1000) {
                        ctx->start_time = av_gettime() - (pts_ms * 1000);
                        ctx->ts_recoveries++;
                        now_ms = pts_ms;
                    }

                    if (pts_ms > now_ms + 5) {
                        int64_t wait = pts_ms - now_ms;
                        if (wait > 100) wait = 100;
                        for (int k = 0; k < wait / 10; k++) {
                            if (ctx->is_released || !ctx->is_playing) break;
                            std::this_thread::sleep_for(std::chrono::milliseconds(10));
                        }
                    }

                    bool skip_render = (now_ms > pts_ms + 120);

                    AVFrame *out_frame = frame;
                    {
                        std::lock_guard<std::recursive_mutex> lock(ctx->mutex);
                        if (ctx->v_filter_graph && ctx->v_buffersrc_ctx && ctx->v_buffersink_ctx) {
                            if (av_buffersrc_add_frame_flags(ctx->v_buffersrc_ctx, frame, AV_BUFFERSRC_FLAG_KEEP_REF) >= 0) {
                                if (av_buffersink_get_frame(ctx->v_buffersink_ctx, filter_frame) >= 0) {
                                    out_frame = filter_frame;
                                }
                            }
                        }
                    }

                    if (!skip_render) {
                        ANativeWindow* win = nullptr;
                        {
                            std::lock_guard<std::recursive_mutex> lock(ctx->mutex);
                            if (ctx->native_window) {
                                win = ctx->native_window;
                                ANativeWindow_acquire(win);
                            }
                        }

                        if (win && out_frame->width > 0 && out_frame->height > 0) {
                            ANativeWindow_setBuffersGeometry(win, out_frame->width, out_frame->height, WINDOW_FORMAT_RGBA_8888);
                            ANativeWindow_Buffer buffer;
                            if (ANativeWindow_lock(win, &buffer, nullptr) == 0) {
                                if (buffer.bits != nullptr && buffer.width > 0 && buffer.height > 0) {
                                    sws_ctx = sws_getCachedContext(sws_ctx,
                                        out_frame->width, out_frame->height, (AVPixelFormat)out_frame->format,
                                        buffer.width, buffer.height, AV_PIX_FMT_RGBA,
                                        SWS_FAST_BILINEAR, nullptr, nullptr, nullptr);
                                    if (sws_ctx) {
                                        uint8_t *dest[4] = {(uint8_t *)buffer.bits, nullptr, nullptr, nullptr};
                                        int dest_linesize[4] = {buffer.stride * 4, 0, 0, 0};
                                        sws_scale(sws_ctx, out_frame->data, out_frame->linesize, 0, out_frame->height, dest, dest_linesize);
                                    }
                                }
                                ANativeWindow_unlockAndPost(win);
                            }
                            ANativeWindow_release(win);
                        }
                    } else {
                        ctx->dropped_frames++;
                    }

                    if (out_frame == filter_frame) av_frame_unref(filter_frame);
                    ctx->current_position = pts_ms;
                }
            } else {
                ctx->dropped_frames++;
            }
        } else if (packet->stream_index == ctx->audio_stream_idx && ctx->a_codec_ctx) {
            // Throttle audio decoding if buffer is getting full to prevent spin-loop
            while (ctx->audio_buffer.get_free() < 4096 && !ctx->is_released && ctx->is_playing) {
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
            }
            if (ctx->is_released) {
                av_packet_unref(packet);
                break;
            }

            int send_res = avcodec_send_packet(ctx->a_codec_ctx, packet);
            if (send_res >= 0) {
                while (avcodec_receive_frame(ctx->a_codec_ctx, frame) >= 0 && !ctx->is_released) {
                    if (!ctx->audio_initialized.load() && ctx->a_codec_ctx->sample_rate > 0 && ctx->a_codec_ctx->sample_fmt != AV_SAMPLE_FMT_NONE) {
                        LOGI("playback_loop: Initializing AAudio stream for stream %d, rate=%d", ctx->audio_stream_idx, ctx->a_codec_ctx->sample_rate);
                        bool filter_ok = false;
                        {
                            std::lock_guard<std::recursive_mutex> lock(ctx->mutex);
                            filter_ok = (ctx->init_audio_filter_graph(ctx->current_a_filter_desc) >= 0);
                        }
                        if (filter_ok) {
                            oboe::AudioStreamBuilder builder;
                            builder.setAudioApi(oboe::AudioApi::AAudio);
                            builder.setDirection(oboe::Direction::Output);
                            builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
                            builder.setSharingMode(oboe::SharingMode::Exclusive);
                            builder.setFormat(oboe::AudioFormat::Float);
                            builder.setChannelCount(oboe::ChannelCount::Stereo);
                            builder.setSampleRate(ctx->a_codec_ctx->sample_rate);
                            ctx->oboe_callback = std::make_unique<OboeAudioCallback>(ctx);
                            builder.setDataCallback(ctx->oboe_callback.get());

                            if (builder.openStream(ctx->audio_stream) == oboe::Result::OK) {
                                if (ctx->is_playing.load()) {
                                    ctx->audio_stream->requestStart();
                                }
                                ctx->audio_initialized = true;
                                LOGI("playback_loop: Oboe AAudio Float32 stream opened successfully");
                            } else {
                                LOGE("playback_loop: Failed to open Oboe AAudio Float32 stream");
                            }
                        }
                    }

                    {
                        std::lock_guard<std::recursive_mutex> lock(ctx->mutex);
                        if (ctx->a_filter_graph && ctx->a_buffersrc_ctx && ctx->a_buffersink_ctx) {
                            int filter_res = av_buffersrc_add_frame_flags(ctx->a_buffersrc_ctx, frame, AV_BUFFERSRC_FLAG_KEEP_REF);
                            if (filter_res >= 0) {
                                while (av_buffersink_get_frame(ctx->a_buffersink_ctx, filter_frame) >= 0) {
                                    ctx->audio_buffer.push(reinterpret_cast<float*>(filter_frame->data[0]), filter_frame->nb_samples * 2);
                                    av_frame_unref(filter_frame);
                                }
                            } else {
                                LOGW("playback_loop: Audio buffer source add error: %d", filter_res);
                            }
                        }
                    }
                    // For audio-only, update position from audio frames
                    if (ctx->video_stream_idx == -1 && ctx->fmt_ctx && ctx->audio_stream_idx >= 0 && ctx->fmt_ctx->streams[ctx->audio_stream_idx]) {
                         AVRational atb = ctx->fmt_ctx->streams[ctx->audio_stream_idx]->time_base;
                         if (atb.num > 0 && atb.den > 0) {
                             double time_base = av_q2d(atb);
                             int64_t f_pts = (frame->best_effort_timestamp != AV_NOPTS_VALUE) ? frame->best_effort_timestamp : frame->pts;
                             if (f_pts != AV_NOPTS_VALUE) {
                                 ctx->current_position = (int64_t)(f_pts * time_base * 1000);
                             }
                         }
                    }
                }
            } else {
                ctx->audio_errors++;
                LOGW("playback_loop: Audio codec send error: %d", send_res);
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
Java_com_medianest_player_FFmpegPlaybackEngine_nativePrepare(JNIEnv *env, jobject thiz, jlong ptr, jint fd, jobject surface) {
    auto ctx = reinterpret_cast<PlayerContext *>(ptr);
    if (!ctx) return JNI_FALSE;

    ctx->reset_playback();

    char path[64];
    ctx->source_fd = dup(fd);
    lseek(ctx->source_fd, 0, SEEK_SET);
    sprintf(path, "/proc/self/fd/%d", ctx->source_fd);

    jboolean is_prepared = JNI_FALSE;
    if (avformat_open_input(&ctx->fmt_ctx, path, nullptr, nullptr) != 0) {
        LOGE("nativePrepare: Could not open input for FD %d", fd);
        close(ctx->source_fd);
        ctx->source_fd = -1;
        return JNI_FALSE;
    }

    if (avformat_find_stream_info(ctx->fmt_ctx, nullptr) < 0) {
        LOGE("nativePrepare: Could not find stream info");
        ctx->reset_playback();
        return JNI_FALSE;
    }

    LOGI("nativePrepare: Opened %s, duration %lld ms, %d streams", ctx->fmt_ctx->iformat->name, (long long)ctx->duration, ctx->fmt_ctx->nb_streams);
    for (unsigned int i = 0; i < ctx->fmt_ctx->nb_streams; i++) {
        auto cp = ctx->fmt_ctx->streams[i]->codecpar;
        if (cp->codec_type == AVMEDIA_TYPE_VIDEO && ctx->video_stream_idx == -1) ctx->video_stream_idx = i;
        else if (cp->codec_type == AVMEDIA_TYPE_AUDIO && ctx->audio_stream_idx == -1) ctx->audio_stream_idx = i;
    }

    auto setup = [&](int idx, AVCodecContext **c) {
        if (idx < 0 || !ctx->fmt_ctx || !ctx->fmt_ctx->streams[idx]) return;
        auto cp = ctx->fmt_ctx->streams[idx]->codecpar;
        auto codec = avcodec_find_decoder(cp->codec_id);
        if (!codec) return;
        *c = avcodec_alloc_context3(codec);
        if (!*c) return;
        avcodec_parameters_to_context(*c, cp);

        // Multi-threaded decoding for software fallback performance
        if (cp->codec_type == AVMEDIA_TYPE_VIDEO) {
            (*c)->thread_count = 4;
            (*c)->thread_type = FF_THREAD_SLICE;
        } else if (cp->codec_type == AVMEDIA_TYPE_AUDIO) {
            // AS REQUESTED: Enhanced resilience for AVI with corrupted audio frames
            (*c)->err_recognition = AV_EF_IGNORE_ERR;
            (*c)->request_sample_fmt = AV_SAMPLE_FMT_S16;
        }

        if (avcodec_open2(*c, codec, nullptr) < 0) {
            avcodec_free_context(c);
            *c = nullptr;
        }
    };
    setup(ctx->video_stream_idx, &ctx->v_codec_ctx);
    if (!ctx->v_codec_ctx) ctx->video_stream_idx = -1;
    
    setup(ctx->audio_stream_idx, &ctx->a_codec_ctx);
    if (!ctx->a_codec_ctx) ctx->audio_stream_idx = -1;

    // Detect HDR and notify Java
    if (ctx->v_codec_ctx) {
        bool is_hdr = (ctx->v_codec_ctx->color_trc == AVCOL_TRC_SMPTE2084 || ctx->v_codec_ctx->color_trc == AVCOL_TRC_ARIB_STD_B67);
        const char* hdr_type = "SDR";
        if (is_hdr) {
            if (ctx->v_codec_ctx->color_trc == AVCOL_TRC_ARIB_STD_B67) hdr_type = "HLG";
            else if (ctx->v_codec_ctx->color_trc == AVCOL_TRC_SMPTE2084) hdr_type = "HDR10";
        }

        // Check for HDR10+ or Dolby Vision in side data
        if (ctx->fmt_ctx && ctx->video_stream_idx >= 0) {
            AVStream *st = ctx->fmt_ctx->streams[ctx->video_stream_idx];
            if (st && st->codecpar) {
                if (av_packet_side_data_get(st->codecpar->coded_side_data, st->codecpar->nb_coded_side_data, AV_PKT_DATA_DYNAMIC_HDR10_PLUS)) {
                    hdr_type = "HDR10+";
                }
                if (av_packet_side_data_get(st->codecpar->coded_side_data, st->codecpar->nb_coded_side_data, AV_PKT_DATA_DOVI_CONF)) {
                    hdr_type = "Dolby Vision";
                }
            }
        }

        const char* cs_name = "SDR";
        if (ctx->v_codec_ctx->colorspace == AVCOL_SPC_BT2020_NCL || ctx->v_codec_ctx->colorspace == AVCOL_SPC_BT2020_CL) {
            cs_name = "BT.2020";
        } else if (ctx->v_codec_ctx->colorspace == AVCOL_SPC_BT709) {
            cs_name = "BT.709";
        }

        if (ctx->on_hdr_mid) {
            jstring jhdr = env->NewStringUTF(hdr_type);
            jstring jcs = env->NewStringUTF(cs_name);
            env->CallVoidMethod(ctx->java_ref, ctx->on_hdr_mid, (jboolean)is_hdr, jhdr, jcs);
            env->DeleteLocalRef(jhdr);
            env->DeleteLocalRef(jcs);
        }
    }

    if (ctx->video_stream_idx == -1 && ctx->audio_stream_idx == -1) {
        ctx->reset_playback();
        return JNI_FALSE;
    }

    if (ctx->audio_stream_idx >= 0 && ctx->a_codec_ctx && ctx->a_codec_ctx->sample_rate > 0 && ctx->a_codec_ctx->sample_fmt != AV_SAMPLE_FMT_NONE) {
        if (ctx->init_audio_filter_graph(ctx->current_a_filter_desc) >= 0) {
            oboe::AudioStreamBuilder builder;
            builder.setAudioApi(oboe::AudioApi::AAudio);
            builder.setDirection(oboe::Direction::Output);
            builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
            builder.setSharingMode(oboe::SharingMode::Exclusive);
            builder.setFormat(oboe::AudioFormat::Float);
            builder.setChannelCount(oboe::ChannelCount::Stereo);
            builder.setSampleRate(ctx->a_codec_ctx->sample_rate);
            ctx->oboe_callback = std::make_unique<OboeAudioCallback>(ctx);
            builder.setDataCallback(ctx->oboe_callback.get());

            if (builder.openStream(ctx->audio_stream) == oboe::Result::OK) {
                ctx->audio_stream->requestStart();
                ctx->audio_initialized = true;
            }
        }
    }

    if (ctx->v_codec_ctx && ctx->current_v_filter_desc != "null") {
        ctx->init_video_filter_graph(ctx->current_v_filter_desc);
    }

    ctx->duration = (ctx->fmt_ctx->duration != AV_NOPTS_VALUE) ? (ctx->fmt_ctx->duration / AV_TIME_BASE) * 1000 : 0;
    ctx->set_surface(env, surface);
    ctx->is_released = false;
    ctx->playback_thread = std::thread(playback_loop, ctx);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativePlay(JNIEnv *env, jobject thiz, jlong ptr) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        ctx->is_playing = true;
        if (ctx->audio_stream) ctx->audio_stream->requestStart();
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativePause(JNIEnv *env, jobject thiz, jlong ptr) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        ctx->is_playing = false;
        if (ctx->audio_stream) ctx->audio_stream->requestPause();
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativeSeek(JNIEnv *env, jobject thiz, jlong ptr, jlong pos) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        std::lock_guard<std::recursive_mutex> lock(ctx->mutex);
        if (ctx->fmt_ctx) {
            av_seek_frame(ctx->fmt_ctx, -1, pos * AV_TIME_BASE / 1000, AVSEEK_FLAG_BACKWARD);
        }
        ctx->current_position = pos;
        ctx->start_time = av_gettime() - (pos * 1000);
        ctx->audio_buffer.clear();
        if (ctx->audio_stream) {
            ctx->audio_stream->flush();
        }
        if (ctx->v_codec_ctx) avcodec_flush_buffers(ctx->v_codec_ctx);
        if (ctx->a_codec_ctx) avcodec_flush_buffers(ctx->a_codec_ctx);
        LOGI("nativeSeek: seeked to %lld ms, start_time synced", (long long)pos);
    }
}

extern "C" JNIEXPORT jlong JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativeGetPosition(JNIEnv *env, jobject thiz, jlong ptr) {
    auto ctx = reinterpret_cast<PlayerContext *>(ptr);
    return ctx ? ctx->current_position.load() : 0;
}

extern "C" JNIEXPORT jlong JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativeGetDuration(JNIEnv *env, jobject thiz, jlong ptr) {
    auto ctx = reinterpret_cast<PlayerContext *>(ptr);
    return ctx ? ctx->duration : 0;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativeIsPlaying(JNIEnv *env, jobject thiz, jlong ptr) {
    auto ctx = reinterpret_cast<PlayerContext *>(ptr);
    return ctx ? ctx->is_playing.load() : false;
}

extern "C" JNIEXPORT void JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativeRelease(JNIEnv *env, jobject thiz, jlong ptr) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        ctx->cleanup(env);
        delete ctx;
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativeUpdateSurface(JNIEnv *env, jobject thiz, jlong ptr, jobject surface) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) ctx->set_surface(env, surface);
}

extern "C" JNIEXPORT void JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativeSetRepeatMode(JNIEnv *env, jobject thiz, jlong ptr, jint repeat_mode) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        ctx->repeat_mode = repeat_mode;
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativeSetAudioFilters(JNIEnv *env, jobject thiz, jlong ptr, jstring filters) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        const char* f = env->GetStringUTFChars(filters, nullptr);
        if (f) {
            ctx->current_a_filter_desc = f;
            if (ctx->fmt_ctx && ctx->audio_initialized.load()) {
                ctx->init_audio_filter_graph(f);
            }
            env->ReleaseStringUTFChars(filters, f);
        }
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_medianest_player_FFmpegPlaybackEngine_nativeSetVideoFilters(JNIEnv *env, jobject thiz, jlong ptr, jstring filters) {
    if (auto ctx = reinterpret_cast<PlayerContext *>(ptr)) {
        const char* f = env->GetStringUTFChars(filters, nullptr);
        if (f) {
            ctx->current_v_filter_desc = f;
            if (ctx->v_codec_ctx) {
                ctx->init_video_filter_graph(f);
            }
            env->ReleaseStringUTFChars(filters, f);
        }
    }
}
