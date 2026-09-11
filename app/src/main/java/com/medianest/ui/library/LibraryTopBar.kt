package com.medianest.ui.library

import androidx.compose.animation.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.components.GlassDropdownMenu
import com.medianest.ui.components.GlassSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    isDashboardTab: Boolean,
    isImagesTab: Boolean,
    isVideosTab: Boolean,
    isAudioTab: Boolean,
    onOpenSettings: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val tabIcon = remember(isDashboardTab, isImagesTab, isVideosTab, isAudioTab) {
        when {
            isDashboardTab -> Icons.Default.Dashboard
            isImagesTab -> Icons.Default.PhotoLibrary
            isVideosTab -> Icons.Default.VideoLibrary
            isAudioTab -> Icons.Default.LibraryMusic
            else -> Icons.Default.Dashboard
        }
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        title = {
            if (isTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = tabIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = when {
                                isDashboardTab -> "MediaNest"
                                isImagesTab -> "Image Gallery"
                                isVideosTab -> "Video Library"
                                isAudioTab -> "Music Library"
                                else -> "MediaNest"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isDashboardTab) 24.sp else 20.sp,
                            color = Color.White
                        )
                        if (isDashboardTab) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Expanded Search Bar for Tablets
                        Surface(
                            modifier = Modifier
                                .width(260.dp)
                                .height(40.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x221C1F2B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2EFFFFFF))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF8E95A5),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color(0xFFC0C5D0)),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 13.5.sp
                                    ),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = when {
                                                        isAudioTab -> "Search folders & tracks..."
                                                        isVideosTab -> "Search videos..."
                                                        isImagesTab -> "Search photos..."
                                                        else -> "Search..."
                                                    },
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF8E95A5)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF8E95A5),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onSearchQueryChange("") }
                                    )
                                }
                            }
                        }

                        // Settings Gear Icon
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenSettings() },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x221C1F2B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2EFFFFFF))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                androidx.compose.animation.AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(280)) +
                                androidx.compose.animation.slideInHorizontally { width -> width / 4 })
                            .togetherWith(
                                androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) +
                                        androidx.compose.animation.slideOutHorizontally { width -> -width / 4 }
                            )
                    },
                    label = "searchBarAnimation"
                ) { active ->
                    if (active) {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = Color(0x221C1F2B),
                            borderColor = Color(0x2EFFFFFF)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF8E95A5),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color(0xFFC0C5D0)),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 13.5.sp
                                    ),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search filename...",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF8E95A5)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF8E95A5),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onSearchQueryChange("") }
                                    )
                                }
                            }
                        }
                    } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = tabIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = when {
                                isDashboardTab -> "MediaNest"
                                isImagesTab -> "MediaNest Gallery"
                                isVideosTab -> "Video Library"
                                isAudioTab -> "Audio Player"
                                else -> "MediaNest"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isDashboardTab) 24.sp else 20.sp
                        )
                        if (isDashboardTab) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    }
                }
            }
        },
        actions = {
            if (!isTablet) {
                IconButton(onClick = {
                    onSearchActiveChange(!isSearchActive)
                    if (isSearchActive) onSearchQueryChange("")
                }) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
            }
            
            /*
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort Options",
                        tint = Color.White
                    )
                }
                GlassDropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    useImageBackground = false
                ) {
                    DropdownMenuItem(
                        text = { Text("Date (Newest First)") },
                        onClick = { showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Oldest First)") },
                        onClick = { showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Name (A to Z)") },
                        onClick = { showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Name (Z to A)") },
                        onClick = { showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Size (Largest First)") },
                        onClick = { showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Size (Smallest First)") },
                        onClick = { showSortMenu = false }
                    )
                }
            }
            */

            if (!isTablet) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
    )
}
