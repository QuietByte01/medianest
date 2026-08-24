package com.medianest.ui.videoplayer.panels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.player.AbRepeatState
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.formatDuration

/**
 * Clean, floating Glassmorphic A-B Repeat Control Panel.
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
    modifier: Modifier = Modifier
) {
    val pointA = abRepeatState.pointA
    val pointB = abRepeatState.pointB
    val isActive = abRepeatState.isActive && pointA != null && pointB != null

    GlassSurface(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .clickable(enabled = false) {},
        shape = RoundedCornerShape(20.dp),
        borderWidth = 0.5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row: Title + Status Badge + Points preview + Close button
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
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "A-B Repeat",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isActive) Color(0xFF00E676).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(
                            0.5.dp,
                            if (isActive) Color(0xFF00E676).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f)
                        )
                    ) {
                        Text(
                            text = if (isActive) "LOOPING" else if (pointA != null) "SET POINT B" else "SET POINT A",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color(0xFF00E676) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Points summary & Close
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Point A Pill
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

                    Text("→", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))

                    // Point B Pill
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

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Controls row: [Set A] [-1s / +1s] | [Set B] [-1s / +1s] | [Toggle] | [Reset]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Set A Button
                Button(
                    onClick = onSetPointA,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pointA != null) Color(0xFF4FC3F7).copy(alpha = 0.25f) else Color(0xFF4FC3F7),
                        contentColor = if (pointA != null) Color.White else Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Text(
                        text = if (pointA != null) "Re-Set A" else "[A] Start",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (pointA != null) {
                    IconButton(
                        onClick = { onAdjustPointA(-1000L) },
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Text("-1s", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { onAdjustPointA(1000L) },
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Text("+1s", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Set B Button
                Button(
                    onClick = onSetPointB,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pointB != null) Color(0xFF4FC3F7).copy(alpha = 0.25f) else Color(0xFF4FC3F7),
                        contentColor = if (pointB != null) Color.White else Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Text(
                        text = if (pointB != null) "Re-Set B" else "[B] End",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (pointB != null) {
                    IconButton(
                        onClick = { onAdjustPointB(-1000L) },
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Text("-1s", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { onAdjustPointB(1000L) },
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Text("+1s", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Loop Toggle
                if (pointA != null && pointB != null) {
                    IconButton(
                        onClick = onToggleActive,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) Color(0xFF00E676).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.AllInclusive else Icons.Default.Pause,
                            contentDescription = "Toggle Loop",
                            tint = if (isActive) Color(0xFF00E676) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Reset / Clear
                if (pointA != null || pointB != null) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear AB Repeat",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
