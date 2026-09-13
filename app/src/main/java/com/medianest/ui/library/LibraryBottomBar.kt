package com.medianest.ui.library

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.player.ExoPlayerManager
import com.medianest.player.PlayerState
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.MiniPlayerBar
import com.medianest.ui.theme.LocalDarkTheme

@Composable
fun LibraryBottomBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    enableAnalyticsTab: Boolean,
    isAudioTab: Boolean,
    playerState: PlayerState,
    exoPlayerManager: ExoPlayerManager,
    onOpenAudioPlayer: (Int) -> Unit
) {
    val isDark = LocalDarkTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Persistent Mini Player above bottom dock only in Audio tab
        if (isAudioTab && playerState.currentItem?.type == com.medianest.data.db.MediaType.AUDIO) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                MiniPlayerBar(
                    playerState = playerState,
                    onPlayPauseToggle = { exoPlayerManager.togglePlayPause() },
                    onNext = { exoPlayerManager.next() },
                    onClickExpand = { onOpenAudioPlayer(currentTab) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // iPad-style transparent glossy floating dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.15f),
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.10f)
                    )
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color(0x3DFFFFFF), // Glossy specular top sheen
                                    Color(0x22FFFFFF),
                                    Color(0x14FFFFFF)  // Translucent body
                                )
                            } else {
                                listOf(
                                    Color(0x99FFFFFF), // Bright glass top highlight
                                    Color(0x80FFFFFF),
                                    Color(0x66FFFFFF)  // Frosted light glass
                                )
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = if (isDark) {
                                listOf(
                                    Color(0x5EFFFFFF), // Crisp top rim highlight
                                    Color(0x1AFFFFFF)  // Soft bottom rim
                                )
                            } else {
                                listOf(
                                    Color(0x80FFFFFF),
                                    Color(0x33000000)
                                )
                            }
                        ),
                        shape = CircleShape
                    )
            ) {
                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (enableAnalyticsTab) {
                        FloatingDockTabItem(
                            selected = currentTab == 0,
                            icon = Icons.Default.BarChart,
                            label = "Dashboard",
//                            accentColor = Color(0xFF6366F1),
                            accentColor = Color.White,
                            onClick = { onTabSelected(0) }
                        )
                    }

                    FloatingDockTabItem(
                        selected = if (enableAnalyticsTab) currentTab == 1 else currentTab == 0,
                        icon = Icons.Default.Image,
                        label = "Images",
                        accentColor = Color(0xFF06A77D),
                        onClick = { onTabSelected(if (enableAnalyticsTab) 1 else 0) }
                    )

                    FloatingDockTabItem(
                        selected = if (enableAnalyticsTab) currentTab == 2 else currentTab == 1,
                        icon = Icons.Default.Movie,
                        label = "Videos",
                        accentColor = Color(0xFF3B82F6),
                        onClick = { onTabSelected(if (enableAnalyticsTab) 2 else 1) }
                    )

                    FloatingDockTabItem(
                        selected = if (enableAnalyticsTab) currentTab == 3 else currentTab == 2,
                        icon = Icons.Default.Audiotrack,
                        label = "Audio",
                        accentColor = Color(0xFFFF006E),
                        onClick = { onTabSelected(if (enableAnalyticsTab) 3 else 2) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingDockTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    accentColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val effectiveAccent = if (accentColor != Color.Unspecified) accentColor else (if (isDark) Color.White else Color(0xFF0F172A))

    // Icon + Text colored with accent when selected
    val contentColor by animateColorAsState(
        targetValue = when {
            selected -> effectiveAccent
            else -> if (isDark) Color(0xB3FFFFFF) else Color(0x990F172A)
        },
        animationSpec = tween(200),
        label = "dockContentColor"
    )

    // Neutral translucent glass background (NOT colored with accent)
    val itemBgColor by animateColorAsState(
        targetValue = when {
            selected -> if (isDark) Color(0x33FFFFFF) else Color(0x50FFFFFF)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "dockBgColor"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.10f else 1.0f,
        animationSpec = spring<Float>(dampingRatio = 0.70f, stiffness = 400f),
        label = "dockIconScale"
    )

    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(CircleShape)
            .background(itemBgColor)
            .border(
                width = if (selected) 0.8.dp else 0.dp,
                brush = if (selected) {
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(Color(0x66FFFFFF), Color(0x1AFFFFFF))
                        } else {
                            listOf(Color(0x99FFFFFF), Color(0x33000000))
                        }
                    )
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                },
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .animateContentSize(
                animationSpec = spring<IntSize>(
                    dampingRatio = 0.80f,
                    stiffness = 450f
                )
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(19.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(160)) + expandHorizontally(
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 450f),
                    expandFrom = Alignment.Start
                ),
                exit = fadeOut(tween(120)) + shrinkHorizontally(
                    animationSpec = spring(dampingRatio = 0.80f, stiffness = 450f),
                    shrinkTowards = Alignment.Start
                )
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
