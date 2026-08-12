#include <jni.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <mutex>
#include <vector>

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

/**
 * Robust Player Context with memory management and synchronization
 */
struct PlayerContext {
    std::mutex mutex;

    AVFormatContext *fmt_ctx = nullptr;
    int video_stream_idx = -1;
    int audio_stream_idx = -1;

    AVCodecContext *v_codec_ctx = nullptr;
    AVCodecContext *a_codec_ctx = nullptr;

    ANativeWindow *native_window = nullptr;

    std::atomic<bool> is_playing{false};
    std::atomic<bool> is_released{false};
    std::atomic<bool> is_seeking{false};

    int64_t duration = 0;
    std::atomic<int64_t> current_position{0};

    std::thread playback_thread;

    // Resource cleanup
    void cleanup() {
        std::lock_guard<std::mutex> lock(mutex);
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
        if (native_window) {
            ANativeWindow_release(native_window);
            native_window = nullptr;
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
        }
    }

    ~PlayerContext() {
        is_released = true;
        if (playback_thread.joinable()) {
            playback_thread.join();
        }
        cleanup();
    }
};

static void throw_java_exception(JNIEnv *env, const char *msg) {
    jclass exClass = env->FindClass("java/lang/RuntimeException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, msg);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativeInit(JNIEnv *env, jobject thiz) {
    LOGI("Initializing new FFmpeg player instance.");
    try {
        PlayerContext *ctx = new PlayerContext();
        return reinterpret_cast<jlong>(ctx);
    } catch (const std::exception &e) {
        LOGE("Failed to allocate PlayerContext: %s", e.what());
        throw_java_exception(env, "Failed to allocate native player context");
        return 0;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativePrepare(JNIEnv *env, jobject thiz, jlong ptr, jstring uri_str, jobject surface) {
    PlayerContext *ctx = reinterpret_cast<PlayerContext *>(ptr);
    if (!ctx) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(ctx->mutex);

    const char *url = env->GetStringUTFChars(uri_str, nullptr);
    if (!url) return JNI_FALSE;

    LOGI("Preparing FFmpeg instance %p for URI: %s", ctx, url);

    // Fault-tolerant options
    AVDictionary *options = nullptr;
    av_dict_set(&options, "fflags", "+discardcorrupt", 0);
    av_dict_set(&options, "err_detect", "ignore_err", 0);
    av_dict_set(&options, "rtsp_transport", "tcp", 0); // For network resilience

    int ret = avformat_open_input(&ctx->fmt_ctx, url, nullptr, &options);
    av_dict_free(&options); // Free the options dictionary after use

    if (ret < 0) {
        char err_buf[AV_ERROR_MAX_STRING_SIZE];
        av_strerror(ret, err_buf, sizeof(err_buf));
        LOGE("Failed to open input %s: %s", url, err_buf);
        env->ReleaseStringUTFChars(uri_str, url);
        return JNI_FALSE;
    }

    if (avformat_find_stream_info(ctx->fmt_ctx, nullptr) < 0) {
        LOGW("Could not find stream info, attempting to continue anyway.");
    }

    // Identify streams
    for (unsigned int i = 0; i < ctx->fmt_ctx->nb_streams; i++) {
        AVCodecParameters *codecpar = ctx->fmt_ctx->streams[i]->codecpar;
        if (codecpar->codec_type == AVMEDIA_TYPE_VIDEO && ctx->video_stream_idx < 0) {
            ctx->video_stream_idx = i;
        } else if (codecpar->codec_type == AVMEDIA_TYPE_AUDIO && ctx->audio_stream_idx < 0) {
            ctx->audio_stream_idx = i;
        }
    }

    if (ctx->fmt_ctx->duration != AV_NOPTS_VALUE) {
        ctx->duration = (ctx->fmt_ctx->duration / AV_TIME_BASE) * 1000;
    }

    // Decoder setup with error handling
    auto setup_decoder = [&](int stream_idx, AVCodecContext **codec_ctx) {
        if (stream_idx < 0) return false;
        const AVCodec *codec = avcodec_find_decoder(ctx->fmt_ctx->streams[stream_idx]->codecpar->codec_id);
        if (!codec) return false;
        *codec_ctx = avcodec_alloc_context3(codec);
        if (!*codec_ctx) return false;
        avcodec_parameters_to_context(*codec_ctx, ctx->fmt_ctx->streams[stream_idx]->codecpar);
        if (avcodec_open2(*codec_ctx, codec, nullptr) < 0) {
            avcodec_free_context(codec_ctx);
            return false;
        }
        return true;
    };

    setup_decoder(ctx->video_stream_idx, &ctx->v_codec_ctx);
    setup_decoder(ctx->audio_stream_idx, &ctx->a_codec_ctx);

    ctx->set_surface(env, surface);

    env->ReleaseStringUTFChars(uri_str, url);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativeUpdateSurface(JNIEnv *env, jobject thiz, jlong ptr, jobject surface) {
    PlayerContext *ctx = reinterpret_cast<PlayerContext *>(ptr);
    if (ctx) {
        ctx->set_surface(env, surface);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativePlay(JNIEnv *env, jobject thiz, jlong ptr) {
    PlayerContext *ctx = reinterpret_cast<PlayerContext *>(ptr);
    if (ctx) ctx->is_playing = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativePause(JNIEnv *env, jobject thiz, jlong ptr) {
    PlayerContext *ctx = reinterpret_cast<PlayerContext *>(ptr);
    if (ctx) ctx->is_playing = false;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativeSeek(JNIEnv *env, jobject thiz, jlong ptr, jlong pos) {
    PlayerContext *ctx = reinterpret_cast<PlayerContext *>(ptr);
    if (ctx && ctx->fmt_ctx) {
        std::lock_guard<std::mutex> lock(ctx->mutex);
        int ret = av_seek_frame(ctx->fmt_ctx, -1, pos * AV_TIME_BASE / 1000, AVSEEK_FLAG_BACKWARD);
        if (ret >= 0) {
            ctx->current_position = pos;
            if (ctx->v_codec_ctx) avcodec_flush_buffers(ctx->v_codec_ctx);
            if (ctx->a_codec_ctx) avcodec_flush_buffers(ctx->a_codec_ctx);
        }
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativeGetPosition(JNIEnv *env, jobject thiz, jlong ptr) {
    PlayerContext *ctx = reinterpret_cast<PlayerContext *>(ptr);
    return ctx ? ctx->current_position.load() : 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativeGetDuration(JNIEnv *env, jobject thiz, jlong ptr) {
    PlayerContext *ctx = reinterpret_cast<PlayerContext *>(ptr);
    return ctx ? ctx->duration : 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativeIsPlaying(JNIEnv *env, jobject thiz, jlong ptr) {
    PlayerContext *ctx = reinterpret_cast<PlayerContext *>(ptr);
    return ctx ? ctx->is_playing.load() : false;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_player_FFmpegPlaybackEngine_nativeRelease(JNIEnv *env, jobject thiz, jlong ptr) {
    PlayerContext *ctx = reinterpret_cast<PlayerContext *>(ptr);
    if (ctx) {
        LOGI("Releasing native player context %p", ctx);
        delete ctx;
    }
}
