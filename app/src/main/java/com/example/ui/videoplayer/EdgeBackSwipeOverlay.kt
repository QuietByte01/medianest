package com.example.ui.videoplayer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EdgeBackSwipeOverlay(
    activeEdge: SwipeEdge,
    swipeProgress: Float,
    modifier: Modifier = Modifier
) {
    if (activeEdge == SwipeEdge.NONE || swipeProgress <= 0f) return

    val animatedScale = animateFloatAsState(
        targetValue = (0.6f + (swipeProgress * 0.4f)).coerceIn(0.6f, 1.0f),
        animationSpec = tween(durationMillis = 100),
        label = "edge_swipe_scale"
    ).value

    val animatedAlpha = animateFloatAsState(
        targetValue = (swipeProgress * 1.5f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100),
        label = "edge_swipe_alpha"
    ).value

    val isLeft = activeEdge == SwipeEdge.LEFT

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .scale(animatedScale)
                .alpha(animatedAlpha)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xDD0F1015))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isLeft) {
                    Text(
                        text = "Next Video",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { swipeProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = if (swipeProgress >= 1.0f) Color(0xFFA855F7) else Color.White,
                        trackColor = Color(0x33FFFFFF),
                        strokeWidth = 3.dp
                    )

                    Icon(
                        imageVector = if (isLeft) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Edge Gesture",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (isLeft) {
                    Text(
                        text = if (swipeProgress >= 1.0f) "Release to Exit" else "Swipe to Exit",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}