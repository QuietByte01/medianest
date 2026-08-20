package com.medianest.ui.videoeditor.model

import android.graphics.Bitmap

data class VideoThumbnail(
    val timestampMs: Long,
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = false
)
