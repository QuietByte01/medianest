package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.db.MediaType

@Composable
fun MediaLoadingAnimation(
    mediaType: MediaType,
    modifier: Modifier = Modifier,
    iconSize: Dp = 42.dp,
    showLabel: Boolean = true,
    customMessage: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "media_loading_anim")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_anim"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_anim"
    )

    val (icon, defaultLabel) = when (mediaType) {
        MediaType.IMAGE -> Icons.Default.Image to "Loading images..."
        MediaType.AUDIO -> Icons.Default.MusicNote to "Loading music..."
        MediaType.VIDEO -> Icons.Default.Movie to "Loading movies..."
    }

    val labelText = customMessage ?: defaultLabel

    Column(
        modifier = modifier.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = labelText,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            )
        }

        if (showLabel) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

