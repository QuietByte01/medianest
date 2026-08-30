package com.medianest.ui.videoplayer

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.player.PlayerState
import com.medianest.ui.components.ControlButtonStyle
import com.medianest.ui.components.CustomRoundedNextButton
import com.medianest.ui.components.CustomRoundedPlayPauseButton
import com.medianest.ui.components.CustomRoundedPreviousButton
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.formatDuration

@Composable
fun ZoomPercentagePill(
    scale: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (scale > 1.05f) {
        Box(modifier = modifier) {
            GlassSurface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onReset() },
                shape = RoundedCornerShape(12.dp),
                backgroundColor = Color(0x33000000),
                borderColor = Color(0x1AFFFFFF)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Zoom: ${(scale * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(modifier = Modifier.size(1.dp, 10.dp).background(Color.White.copy(alpha = 0.2f)))
                    Text(
                        text = "Reset",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SubtitleTextOverlay(
    text: String?,
    isVisible: Boolean,
    fontSizeSp: Float,
    textColor: Color,
    bgColor: Color,
    hasShadow: Boolean,
    modifier: Modifier = Modifier
) {
    if (!text.isNullOrBlank()) {
        Box(
            modifier = modifier
                .padding(bottom = if (isVisible) 120.dp else 45.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = if (hasShadow) {
                    androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            offset = Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    )
                } else androidx.compose.ui.text.TextStyle.Default
            )
        }
    }
}

@Composable
fun CenterTransportControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomRoundedPreviousButton(
            onClick = onPrevious,
            buttonSize = 46.dp,
            iconSize = 26.dp,
            style = ControlButtonStyle.TRANSPARENT_MINIMAL,
            tint = Color.White,
            useDoubleIcon = false
        )

        CustomRoundedPlayPauseButton(
            isPlaying = isPlaying,
            onClick = onTogglePlayPause,
            buttonSize = 64.dp,
            iconSize = 36.dp,
            style = ControlButtonStyle.TRANSPARENT_MINIMAL,
            tint = Color.White
        )

        CustomRoundedNextButton(
            onClick = onNext,
            buttonSize = 46.dp,
            iconSize = 26.dp,
            style = ControlButtonStyle.TRANSPARENT_MINIMAL,
            tint = Color.White,
            useDoubleIcon = false
        )
    }
}

@Composable
fun VerticalGestureHUD(
    value: Float,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        backgroundColor = Color.Transparent,
        borderColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .width(44.dp)
                .height(200.dp)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))

            val isBrightness = icon == Icons.Default.WbSunny
            val displayValue = if (isBrightness) value.coerceAtMost(1.0f) else value
            val isBoost = !isBrightness && value > 1.0f
            val hudColor = if (isBoost) Color(0xFFEF4444) else color
            val fillFactor = if (isBrightness) displayValue else (value / 2.0f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillFactor)
                        .clip(RoundedCornerShape(3.dp))
                        .background(hudColor)
                )
            }

            Text(
                text = "${(displayValue * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isBoost) Color(0xFFEF4444) else Color.White
            )
        }
    }
}

@Composable
fun SeekHUD(
    deltaMs: Long,
    targetPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = Color(0xB3000000),
        borderColor = Color(0x33FFFFFF)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (deltaMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${formatDuration(targetPositionMs)} / ${formatDuration(durationMs)}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (deltaMs >= 0) "+${formatDuration(deltaMs)}" else "-${formatDuration(-deltaMs)}",
                    color = if (deltaMs >= 0) Color(0xFF4ADE80) else Color(0xFFF87171),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun GestureFeedbackHUD(
    text: String,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        backgroundColor = Color(0x33000000),
        borderColor = Color(0x33FFFFFF)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val icon = when {
                text.contains("Volume") -> Icons.Default.VolumeUp
                text.contains("Brightness") -> Icons.Default.Brightness6
                text.contains("-") || text.contains("<<") -> Icons.Default.FastRewind
                else -> Icons.Default.FastForward
            }
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
