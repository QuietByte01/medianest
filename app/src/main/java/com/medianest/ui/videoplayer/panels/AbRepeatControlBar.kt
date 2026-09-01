package com.medianest.ui.videoplayer.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.player.AbRepeatState
import com.medianest.ui.components.BackdropBlurState
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.backdropReceiver
import com.medianest.ui.components.formatDuration

/**
 * Clean, floating Glassmorphic A-B Repeat Control Panel.
 * Designed with balanced, equal-sized button columns and no overlapping.
 */
@Composable
fun AbRepeatControlBar(
    abRepeatState: AbRepeatState,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onAdjustPointA: (Long) -> Unit,
    onAdjustPointB: (Long) -> Unit,
    onSeekToA: () -> Unit,
    onSeekToB: () -> Unit,
    onToggleActive: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    backdropState: BackdropBlurState? = null,
    modifier: Modifier = Modifier
) {
    val isDark = com.medianest.ui.theme.LocalDarkTheme.current
    val cardBg = if (isDark) Color(0x6608090E) else Color(0x80FFFFFF)
    val shape = RoundedCornerShape(20.dp)

    val pointA = abRepeatState.pointA
    val pointB = abRepeatState.pointB
    val isActive = abRepeatState.isActive && pointA != null && pointB != null

    val cardModifier = modifier
        .fillMaxWidth(0.96f)
        .clip(shape)
        .then(
            if (backdropState != null) {
                Modifier.backdropReceiver(
                    state = backdropState,
                    blurRadius = 24.dp,
                    tint = cardBg,
                    baseColor = Color.Transparent,
                    showTopBorder = false
                )
            } else Modifier
        )
        .clickable(enabled = false) {}

    GlassSurface(
        modifier = cardModifier,
        shape = shape,
        backgroundColor = if (backdropState != null) Color.Transparent else cardBg,
        borderColor = if (isDark) Color(0x33FFFFFF) else Color(0x33000000),
        borderWidth = 0.5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: Title + Status Badge + Timestamps + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RepeatOne,
                        contentDescription = null,
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "A-B Repeat",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isActive -> Color(0xFF00E676).copy(alpha = 0.2f)
                            pointA != null && pointB != null -> Color(0xFFFFD54F).copy(alpha = 0.2f)
                            pointA != null -> Color(0xFF4FC3F7).copy(alpha = 0.2f)
                            else -> Color.White.copy(alpha = 0.12f)
                        },
                        border = BorderStroke(
                            0.5.dp,
                            when {
                                isActive -> Color(0xFF00E676).copy(alpha = 0.6f)
                                pointA != null && pointB != null -> Color(0xFFFFD54F).copy(alpha = 0.6f)
                                pointA != null -> Color(0xFF4FC3F7).copy(alpha = 0.6f)
                                else -> Color.White.copy(alpha = 0.25f)
                            }
                        )
                    ) {
                        Text(
                            text = when {
                                isActive -> "LOOPING"
                                pointA != null && pointB != null -> "PAUSED"
                                pointA != null -> "SET [B]"
                                else -> "SET [A]"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isActive -> Color(0xFF00E676)
                                pointA != null && pointB != null -> Color(0xFFFFD54F)
                                pointA != null -> Color(0xFF4FC3F7)
                                else -> Color.White.copy(alpha = 0.8f)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Points summary & Close
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Clickable Point A Pill
                    Surface(
                        onClick = { if (pointA != null) onSeekToA() },
                        shape = RoundedCornerShape(8.dp),
                        color = if (pointA != null) Color(0xFF4FC3F7).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            0.5.dp,
                            if (pointA != null) Color(0xFF4FC3F7).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Text(
                            text = "A: ${if (pointA != null) formatDuration(pointA) else "--:--"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pointA != null) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Text("→", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))

                    // Clickable Point B Pill
                    Surface(
                        onClick = { if (pointB != null) onSeekToB() },
                        shape = RoundedCornerShape(8.dp),
                        color = if (pointB != null) Color(0xFF4FC3F7).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            0.5.dp,
                            if (pointB != null) Color(0xFF4FC3F7).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Text(
                            text = "B: ${if (pointB != null) formatDuration(pointB) else "--:--"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (pointB != null) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Controls section: Two equal 50/50 columns for Point A and Point B
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Column 1: Point A Controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onSetPointA,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pointA != null) Color(0x334FC3F7) else Color(0x244FC3F7),
                            contentColor = if (pointA != null) Color(0xFF4FC3F7) else Color.White
                        ),
                        border = BorderStroke(
                            0.75.dp,
                            if (pointA != null) Color(0xFF4FC3F7).copy(alpha = 0.7f) else Color(0xFF4FC3F7).copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Text(
                            text = if (pointA != null) "Point A (${formatDuration(pointA)})" else "Set Point [A]",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pointA != null) Color(0xFF4FC3F7) else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (pointA != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { onAdjustPointA(-1000L) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            ) {
                                Text("-1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onAdjustPointA(1000L) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            ) {
                                Text("+1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Column 2: Point B Controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onSetPointB,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pointB != null) Color(0x334FC3F7) else Color(0x244FC3F7),
                            contentColor = if (pointB != null) Color(0xFF4FC3F7) else Color.White
                        ),
                        border = BorderStroke(
                            0.75.dp,
                            if (pointB != null) Color(0xFF4FC3F7).copy(alpha = 0.7f) else Color(0xFF4FC3F7).copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Text(
                            text = if (pointB != null) "Point B (${formatDuration(pointB)})" else "Set Point [B]",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pointB != null) Color(0xFF4FC3F7) else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (pointB != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { onAdjustPointB(-1000L) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            ) {
                                Text("-1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onAdjustPointB(1000L) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                            ) {
                                Text("+1s", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Bottom Action Row: Loop Toggle & Clear
            if (pointA != null || pointB != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pointA != null && pointB != null) {
                        Button(
                            onClick = onToggleActive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) Color(0xFF00E676).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                                contentColor = if (isActive) Color(0xFF00E676) else Color.White
                            ),
                            border = BorderStroke(
                                0.5.dp,
                                if (isActive) Color(0xFF00E676).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isActive) Icons.Default.AllInclusive else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isActive) "Looping Active" else "Loop Paused",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onClear,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .then(if (pointA != null && pointB != null) Modifier.wrapContentWidth() else Modifier.fillMaxWidth())
                            .height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset Points",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

