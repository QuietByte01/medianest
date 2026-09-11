package com.medianest.studio.ui

import android.net.Uri
import android.widget.EditText
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import com.medianest.ui.components.AppSlider
import com.medianest.ui.components.GlassSurface
import kotlin.math.roundToInt

@Composable
internal fun KeyboardStickerReceiverDialog(
    onReceiveContent: (uri: Uri, isGif: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF0111625),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Insert from Keyboard",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Open your keyboard (Gboard/SwiftKey) and tap any GIF or Sticker icon below to insert it directly onto the video.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(0.75f)
                )

                AndroidView(
                    factory = { ctx ->
                        EditText(ctx).apply {
                            hint = "Tap here & pick GIF/Sticker from keyboard"
                            setHintTextColor(android.graphics.Color.GRAY)
                            setTextColor(android.graphics.Color.WHITE)
                            setBackgroundColor(android.graphics.Color.parseColor("#22FFFFFF"))
                            setPadding(32, 28, 32, 28)

                            val mimeTypes = arrayOf("image/gif", "image/png", "image/webp", "image/jpeg", "image/*")
                            ViewCompat.setOnReceiveContentListener(
                                this,
                                mimeTypes,
                                object : OnReceiveContentListener {
                                    override fun onReceiveContent(view: android.view.View, payload: ContentInfoCompat): ContentInfoCompat? {
                                        val clipData = payload.clip
                                        for (i in 0 until clipData.itemCount) {
                                            val uri = clipData.getItemAt(i).uri
                                            if (uri != null) {
                                                val isGif = ctx.contentResolver.getType(uri)?.contains("gif") == true || uri.toString().lowercase().contains("gif")
                                                onReceiveContent(uri, isGif)
                                                return null
                                            }
                                        }
                                        return payload
                                    }
                                }
                            )
                            requestFocus()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSpeedSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(18.dp),
            backgroundColor = Color(0xF0141824),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Video Playback Speed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(speeds) { sp ->
                        val isSel = kotlin.math.abs(currentSpeed - sp) < 0.05f
                        FilterChip(
                            selected = isSel,
                            onClick = { onSpeedSelect(sp) },
                            label = { Text("${sp}x", fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddRichTextDialog(
    onAdd: (text: String, fontStyle: StudioFontFamily, color: Color, bgStyle: TextBackgroundStyle, fontSize: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedFont by remember { mutableStateOf(StudioFontFamily.SANS_SERIF) }
    var selectedColor by remember { mutableStateOf(Color.White) }
    var selectedBgStyle by remember { mutableStateOf(TextBackgroundStyle.TRANSLUCENT) }
    var fontSize by remember { mutableFloatStateOf(20f) }

    val colors = listOf(Color.White, Color.Yellow, Color(0xFF00E5FF), Color(0xFFFF4081), Color(0xFF69F0AE), Color(0xFFFF9100), Color(0xFFE040FB))

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF0111625),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Add Text & Font Styles", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type your caption...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Font Family", fontSize = 12.sp, color = Color(0xFFFFD54F))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioFontFamily.entries.forEach { f ->
                        FilterChip(
                            selected = selectedFont == f,
                            onClick = { selectedFont = f },
                            label = { Text(f.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Text("Font Color", fontSize = 12.sp, color = Color(0xFFFFD54F))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    colors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (selectedColor == c) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable { selectedColor = c }
                        )
                    }
                }

                Text("Background Badge", fontSize = 12.sp, color = Color(0xFFFFD54F))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextBackgroundStyle.entries.forEach { bg ->
                        FilterChip(
                            selected = selectedBgStyle == bg,
                            onClick = { selectedBgStyle = bg },
                            label = { Text(bg.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Text("Font Size: ${fontSize.toInt()}sp", fontSize = 12.sp, color = Color(0xFFFFD54F))
                AppSlider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 14f..42f,
                    accentColor = Color(0xFFFFD54F)
                )

                Button(
                    onClick = { if (text.isNotBlank()) onAdd(text, selectedFont, selectedColor, selectedBgStyle, fontSize) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Text to Video", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryStickerPickerDialog(
    onSelectSticker: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = listOf("Emojis", "Badges & Tags", "Arrows & Shapes")

    val emojiList = listOf("🔥", "❤️", "✨", "😂", "🎬", "🍿", "🚀", "⚡", "💯", "🕶️", "👏", "🎉", "😍", "🌟", "👑", "🥳", "💡", "💎", "🏆", "💥")
    val badgeList = listOf("🔴 REC", "PRO", "4K", "HD", "LIVE", "SALE", "BEST", "NEW", "VLOG", "TOP 10", "MEME", "VIP", "HOT 🔥")
    val shapeList = listOf("➔", "➜", "⬆", "⬇", "⚡", "✦", "★", "💖", "💬", "💥", "🎯", "🚀", "💯", "👑")

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF0111625),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Pick Stickers & Badges", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEachIndexed { index, cat ->
                        FilterChip(
                            selected = selectedCategory == index,
                            onClick = { selectedCategory = index },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                val currentItems = when (selectedCategory) {
                    0 -> emojiList
                    1 -> badgeList
                    else -> shapeList
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    currentItems.forEach { sticker ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33FFFFFF))
                                .clickable { onSelectSticker(sticker) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sticker,
                                fontSize = if (selectedCategory == 1) 14.sp else 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportStudioDialog(
    clipDurationMs: Long,
    onExport: (format: String, gifFps: Int, gifWidth: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf("MP4") }
    var gifFps by remember { mutableIntStateOf(15) }
    var gifWidth by remember { mutableIntStateOf(480) }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xF0111625),
            borderColor = Color(0x33FFFFFF)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Export Media Studio", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = "Clip Duration: ${formatTimeShort(clipDurationMs)} • Destination: MediaNest Studio",
                    fontSize = 11.sp,
                    color = Color.White.copy(0.7f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("MP4", "GIF", "WEBP").forEach { fmt ->
                        val isSel = selectedFormat == fmt
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedFormat = fmt },
                            label = { Text(if (fmt == "MP4") "Video (MP4)" else if (fmt == "GIF") "Animated GIF" else "Animated WebP", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFD54F),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0x22FFFFFF),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                if (selectedFormat == "GIF" || selectedFormat == "WEBP") {
                    Text("Framerate: ${gifFps}fps • Width: ${gifWidth}px", fontSize = 11.sp, color = Color(0xFFFFD54F))
                    AppSlider(
                        value = gifFps.toFloat(),
                        onValueChange = { gifFps = it.roundToInt() },
                        valueRange = 10f..30f,
                        steps = 3,
                        accentColor = Color(0xFFFFD54F)
                    )
                }

                Button(
                    onClick = { onExport(selectedFormat, gifFps, gifWidth) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Export", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
