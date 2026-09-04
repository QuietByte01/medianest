package com.medianest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Full-screen overlay host. Place this ONCE near the root of your screen, inside the same
 * composition as your backdropSource content, so backdropState's GraphicsLayer is valid
 * for anything drawn here.
 *
 * Usage:
 *   Box(Modifier.backdropSource(state)) { ScreenContent() }
 *   if (showDeleteDialog) {
 *       DeleteConfirmationDialog(
 *           backdropState = state,
 *           onDismiss = { showDeleteDialog = false },
 *           onConfirm = { ... }
 *       )
 *   }
 */
@Composable
fun DeleteConfirmationDialog(
    title: String = "Delete File",
    itemTitle: String? = null,
    message: String? = null,
    confirmButtonText: String = "Delete",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    backdropState: BackdropBlurState? = LocalBackdropState.current
) {
    val isDark = LocalDarkTheme.current
    val shape = RoundedCornerShape(24.dp)

    val resolvedMessage = message ?: if (!itemTitle.isNullOrBlank()) {
        "Are you sure you want to delete '$itemTitle'? This will permanently remove the file from your device storage."
    } else {
        "Are you sure you want to delete this item? This will permanently remove the file from your device storage."
    }

    // FIX: no Dialog(...) window. This is a plain in-tree overlay, drawn above the rest of
    // the screen's content via zIndex, inside the SAME window as backdropSource. That's what
    // makes backdropReceiver's drawLayer(sourceLayer) valid — it's sampling a GraphicsLayer
    // recorded in this same window's render tree, not a foreign one.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            // Scrim + back-press/tap-outside-to-dismiss, replacing what Dialog gave us for free.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        BackHandler(onBack = onDismiss)

        BackdropGlassSurface(
            shape = shape,
            enableBlur = true,
            blurRadius = 24.dp,
            tint = Color.Black.copy(alpha = 0.30f),
            baseColor = Color.Transparent,
            borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x18000000),
            borderWidth = 1.dp,
            backdropState = backdropState,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
                // Stop taps on the card itself from bubbling to the scrim's dismiss handler.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { /* consume */ })
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0x22EF4444)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Warning",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = resolvedMessage,
                    fontSize = 13.sp,
                    color = Color(0xFFC0C5D0),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Text(text = "Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.70f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = confirmButtonText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}