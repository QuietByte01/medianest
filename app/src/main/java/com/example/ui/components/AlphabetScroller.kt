package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AlphabetScroller(
    songs: List<MediaItem>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    if (songs.size < 5) return

    val alphabetList = remember {
        listOf("#") + ('A'..'Z').map { it.toString() }
    }

    var activeLetter by remember { mutableStateOf<String?>(null) }
    var columnHeightPx by remember { mutableFloatStateOf(1f) }

    fun scrollToLetter(letter: String) {
        val targetIndex = if (letter == "#") {
            songs.indexOfFirst { song ->
                val firstChar = song.title.trim().firstOrNull()?.uppercaseChar() ?: ' '
                !firstChar.isLetter()
            }.takeIf { it >= 0 } ?: 0
        } else {
            songs.indexOfFirst { song ->
                song.title.trim().startsWith(letter, ignoreCase = true)
            }.takeIf { it >= 0 } ?: songs.indexOfFirst { song ->
                song.title.trim().lowercase() > letter.lowercase()
            }.takeIf { it >= 0 } ?: 0
        }

        activeLetter = letter
        CoroutineScope(Dispatchers.Main).launch {
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 2.dp, top = 8.dp, bottom = 92.dp), // Respect space for mini-player
        contentAlignment = Alignment.CenterEnd
    ) {
        // Floating Letter Preview Pill
        AnimatedVisibility(
            visible = activeLetter != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            activeLetter?.let { letter ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xEE1E2235))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Vertical Alphabet Strip
        Column(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight() // Fill available height in the padded Box
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x1A000000))
                .onGloballyPositioned { layoutCoordinates ->
                    columnHeightPx = layoutCoordinates.size.height.toFloat().coerceAtLeast(1f)
                }
                .pointerInput(songs, alphabetList) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val fraction = (offset.y / columnHeightPx).coerceIn(0f, 0.99f)
                            val index = (fraction * alphabetList.size).toInt().coerceIn(0, alphabetList.lastIndex)
                            scrollToLetter(alphabetList[index])
                        },
                        onDragEnd = { activeLetter = null },
                        onDragCancel = { activeLetter = null },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.y / columnHeightPx).coerceIn(0f, 0.99f)
                            val index = (fraction * alphabetList.size).toInt().coerceIn(0, alphabetList.lastIndex)
                            scrollToLetter(alphabetList[index])
                        }
                    )
                },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabetList.forEach { charStr ->
                Text(
                    text = charStr,
                    color = Color(0xCCFFFFFF),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
