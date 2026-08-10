package com.example.ui.audioplayer

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LyricLine
import com.example.data.model.MediaItem
import com.example.data.repository.NetworkRepository

@Composable
fun ManualLyricsDialog(
    currentItem: MediaItem?,
    rawLyricsText: String?,
    initialInput: String,
    networkRepository: NetworkRepository,
    context: Context,
    onLyricsUpdated: (String?, List<LyricLine>) -> Unit,
    onDismiss: () -> Unit
) {
    var manualLyricsInput = initialInput

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xEB101114),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Manual Lyrics Entry", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
            }
        },
        confirmButton = {
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
        },
        dismissButton = {
            Row {
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
            }
        }
    )
}
