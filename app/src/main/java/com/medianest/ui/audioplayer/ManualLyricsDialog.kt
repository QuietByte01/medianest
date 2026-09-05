package com.medianest.ui.audioplayer

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.medianest.data.model.LyricLine
import com.medianest.data.model.MediaItem
import com.medianest.data.repository.NetworkRepository
import com.medianest.ui.components.BackdropGlassSurface
import com.medianest.ui.components.BackdropBlurState
import com.medianest.ui.components.LocalBackdropState

@Composable
fun ManualLyricsDialog(
    currentItem: MediaItem?,
    rawLyricsText: String?,
    initialInput: String,
    networkRepository: NetworkRepository,
    context: Context,
    onLyricsUpdated: (String?, List<LyricLine>) -> Unit,
    onDismiss: () -> Unit,
    backdropState: BackdropBlurState? = LocalBackdropState.current
) {
    var manualLyricsInput by remember { mutableStateOf(initialInput) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        BackHandler(onBack = onDismiss)

        BackdropGlassSurface(
            shape = RoundedCornerShape(24.dp),
            enableBlur = true,
            blurRadius = 24.dp,
            tint = Color.Black.copy(alpha = 0.30f),
            baseColor = Color.Transparent,
            borderColor = Color(0x38FFFFFF),
            borderWidth = 1.dp,
            backdropState = backdropState,
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.90f)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { /* consume */ })
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Manual Lyrics Entry",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Paste or type lyrics for '${currentItem?.title}'. You can enter plain text or synced LRC format (e.g. [00:12.30] Lyrics).",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = manualLyricsInput,
                    onValueChange = { manualLyricsInput = it },
                    label = { Text("Lyrics", color = Color.White.copy(alpha = 0.8f)) },
                    placeholder = { Text("Type or paste lyrics here...", color = Color.White.copy(alpha = 0.4f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 280.dp),
                    maxLines = 15
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rawLyricsText != null) {
                        TextButton(
                            onClick = {
                                val item = currentItem
                                if (item != null) {
                                    val prefs = context.getSharedPreferences("manual_lyrics", Context.MODE_PRIVATE)
                                    prefs.edit().remove(item.uri.toString()).apply()
                                    onLyricsUpdated(null, emptyList())
                                    Toast.makeText(context, "Lyrics removed", Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            }
                        ) {
                            Text("Clear", color = Color(0xFFFF6B6B))
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        onClick = {
                            val item = currentItem
                            if (item != null) {
                                val textToSave = manualLyricsInput.trim()
                                val prefs = context.getSharedPreferences("manual_lyrics", Context.MODE_PRIVATE)
                                if (textToSave.isNotBlank()) {
                                    prefs.edit().putString(item.uri.toString(), textToSave).apply()
                                    val lines = networkRepository.parseLrcLyrics(textToSave)
                                    onLyricsUpdated(textToSave, lines)
                                    Toast.makeText(context, "Lyrics saved", Toast.LENGTH_SHORT).show()
                                } else {
                                    prefs.edit().remove(item.uri.toString()).apply()
                                    onLyricsUpdated(null, emptyList())
                                    Toast.makeText(context, "Lyrics cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                            onDismiss()
                        }
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
