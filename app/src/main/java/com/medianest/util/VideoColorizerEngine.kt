package com.medianest.util

/**
 * VideoColorizerEngine — Delegated to Media Studio Add-On App (`com.medianest.studio`).
 */
object VideoColorizerEngine {
    enum class ColorizeMode(val label: String) {
        HISTOGRAM_BALANCED("Histogram Match"),
        NEURAL_LUMA("Luma Transfer"),
        VIBRANT_POP("Vivid Pop")
    }

    enum class ColorizeCodec(val containerExt: String) {
        MP4_H264("mp4"),
        MP4_HEVC("mp4"),
        WEBM_VP9("webm")
    }

    enum class ColorizePixFmt {
        YUV420P,
        YUV420P10LE
    }

    data class ColorizerConfig(
        val mode: ColorizeMode = ColorizeMode.HISTOGRAM_BALANCED,
        val codec: ColorizeCodec = ColorizeCodec.MP4_H264,
        val pixFmt: ColorizePixFmt = ColorizePixFmt.YUV420P
    )
}
