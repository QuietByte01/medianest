package com.medianest.util

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object BlurUtils {
    /**
     * Highly optimized backdrop blur for AndroidViews and complex layouts.
     * Uses RenderEffect on API 31+ for native-level performance.
     */
    @Composable
    fun Modifier.videoBlur(visible: Boolean, radius: Float = 40f): Modifier {
        val blurAmount by animateFloatAsState(
            targetValue = if (visible) radius else 0f,
            animationSpec = tween(500),
            label = "VideoBlurAlpha"
        )
        
        return if (blurAmount > 0f) {
            this.graphicsLayer {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = BlurEffect(blurAmount, blurAmount)
                } else {
                    // Fallback for older versions (limited support for AndroidView blur)
                    alpha = (1f - (blurAmount / radius) * 0.3f).coerceIn(0.7f, 1f)
                }
                clip = true
            }
        } else {
            this
        }
    }
    
    /**
     * Legacy backdropBlur for backward compatibility or simple boxes.
     */
    @Composable
    fun Modifier.backdropBlur(visible: Boolean, radius: Dp = 16.dp): Modifier {
        return this.videoBlur(visible, radius.value * 2f)
    }
}
