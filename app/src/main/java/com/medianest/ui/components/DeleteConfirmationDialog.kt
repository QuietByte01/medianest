package com.medianest.ui.components

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Universal Glassmorphic Transparent Delete Confirmation Dialog.
 * Used consistently across Audio, Video, Image libraries and players.
 */
@Composable
fun DeleteConfirmationDialog(
    title: String = "Delete File",
    message: String? = null,
    itemTitle: String? = null,
    confirmButtonText: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    backdropState: BackdropBlurState? = LocalBackdropState.current
) {
    val isDark = LocalDarkTheme.current
    val cardBg = if (isDark) Color(0x2E2A324B) else Color(0xBFFFFFFF)
    val shape = RoundedCornerShape(24.dp)

    val effectiveMessage = message ?: if (itemTitle != null) {
        "Are you sure you want to delete '$itemTitle'? This will permanently remove the file from your device storage."
    } else {
        "Are you sure you want to delete this item? This will permanently remove the file from your device storage."
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.let { w ->
                w.setDimAmount(0.12f) // Light transparent background dim
                w.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
            onDispose {}
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            val cardModifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = false
                ) {}
                .clip(shape)
                .then(
                    if (backdropState != null) {
                        Modifier.backdropReceiver(
                            state = backdropState,
                            blurRadius = 24.dp,
                            tint = if (isDark) Color(0x1F2A324B) else Color(0x80FFFFFF),
                            baseColor = Color.Transparent,
                            showTopBorder = false
                        )
                    } else Modifier
                )

            GlassSurface(
                shape = shape,
                backgroundColor = if (backdropState != null) Color.Transparent else cardBg,
                borderColor = if (isDark) Color(0x28FFFFFF) else Color(0x28000000),
                modifier = cardModifier
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Warning / Trash Icon with soft translucent glowing container
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0x25EF4444), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Title
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = if (isDark) Color.White else Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )

                    // Message description
                    Text(
                        text = effectiveMessage,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Button
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        }

                        // Delete Confirm Button (70% opacity translucent red)
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444).copy(alpha = 0.70f),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = confirmButtonText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
