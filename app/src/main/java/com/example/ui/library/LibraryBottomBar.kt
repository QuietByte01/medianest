package com.example.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.player.ExoPlayerManager
import com.example.player.PlayerState
import com.example.ui.components.GlassSurface
import com.example.ui.components.MiniPlayerBar

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
    Column {
        // Persistent Mini Player above bottom bar only in Audio tab
        if (isAudioTab && playerState.currentItem != null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                MiniPlayerBar(
                    playerState = playerState,
                    onPlayPauseToggle = { exoPlayerManager.togglePlayPause() },
                    onNext = { exoPlayerManager.next() },
                    onClickExpand = { onOpenAudioPlayer(currentTab) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        val navItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = Color.Transparent,
            selectedIconColor = Color(0xFFF1F5F9),
            selectedTextColor = Color(0xFFF1F5F9),
            unselectedIconColor = Color(0xFF9EA3B0),
            unselectedTextColor = Color(0xFF9EA3B0)
        )

        GlassSurface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            backgroundColor = Color(0x6612151F),
            borderColor = Color(0x28FFFFFF),
            modifier = Modifier.fillMaxWidth()
        ) {
            NavigationBar(
                containerColor = Color.Transparent
            ) {
                if (enableAnalyticsTab) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { onTabSelected(0) },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        colors = navItemColors
                    )
                }
                NavigationBarItem(
                    selected = if (enableAnalyticsTab) currentTab == 1 else currentTab == 0,
                    onClick = { onTabSelected(if (enableAnalyticsTab) 1 else 0) },
                    icon = { Icon(Icons.Default.Image, contentDescription = "Images") },
                    label = { Text("Images") },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = if (enableAnalyticsTab) currentTab == 2 else currentTab == 1,
                    onClick = { onTabSelected(if (enableAnalyticsTab) 2 else 1) },
                    icon = { Icon(Icons.Default.Movie, contentDescription = "Videos") },
                    label = { Text("Videos") },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = if (enableAnalyticsTab) currentTab == 3 else currentTab == 2,
                    onClick = { onTabSelected(if (enableAnalyticsTab) 3 else 2) },
                    icon = { Icon(Icons.Default.Audiotrack, contentDescription = "Audio") },
                    label = { Text("Audio") },
                    colors = navItemColors
                )
            }
        }
    }
}
