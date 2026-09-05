package com.medianest.ui.audioplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.model.LyricLine
import com.medianest.ui.components.GlassSurface

@Composable
fun LyricsView(
    songTitle: String,
    songArtist: String,
    isLoadingLyrics: Boolean,
    lyricsLines: List<LyricLine>,
    rawLyricsText: String?,
    activeLyricIndex: Int,
    listState: LazyListState,
    onSeekTo: (Long) -> Unit,
    onEditLyrics: () -> Unit,
    onHideLyrics: () -> Unit,
    titleFontSize: Int = 14,
    activeLyricFontSize: Int = 20,
    inactiveLyricFontSize: Int = 15,
    glassSurfaceModifier: Modifier = Modifier,
    cardShapeRadius: Dp = 24.dp
) {
    val showEditButton = lyricsLines.isEmpty() && rawLyricsText == null

    GlassSurface(
        modifier = glassSurfaceModifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cardShapeRadius),
        enableBlur = true,
        backgroundColor = Color(0x1F24293A),
        borderColor = Color(0x2EFFFFFF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = songTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = titleFontSize.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = songArtist,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = (titleFontSize - 1).sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (showEditButton) {
                    IconButton(
                        onClick = onEditLyrics,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Lyrics",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingLyrics) {
                com.medianest.ui.components.MediaLoadingAnimation(
                    mediaType = com.medianest.data.db.MediaType.AUDIO,
                    iconSize = 36.dp
                )
            } else if (lyricsLines.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(lyricsLines) { index, line ->
                        val isActive = index == activeLyricIndex
                        Text(
                            text = line.text,
                            fontSize = if (isActive) activeLyricFontSize.sp else inactiveLyricFontSize.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) Color(0xFFF5F5F5) else Color.White.copy(alpha = 0.45f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onSeekTo(line.timeMs) }
                        )
                    }
                }
            } else if (rawLyricsText != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = rawLyricsText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No lyrics available",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { onHideLyrics() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    IconButton(onClick = onEditLyrics) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Lyrics", tint = Color.White)
                    }
                }
            }
        }
    }
}