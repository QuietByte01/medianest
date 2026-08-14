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
    GlassSurface(
        modifier = glassSurfaceModifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cardShapeRadius),
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
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Synced Lyrics",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF5F5F5),
                    fontSize = titleFontSize.sp
                )
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
            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingLyrics) {
                CircularProgressIndicator(color = Color(0xFFF5F5F5))
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onEditLyrics) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Lyrics")
                    }
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