package com.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppButtonStyle {
    Solid,
    Glossy
}

/**
 * Reusable critical task button (Clear Cache, Delete, Remove) modeled after the
 * App Settings "Clear All Cache & History" button. Glossy-only layout.
 */
@Composable
fun AppCriticalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accentColor: Color = Color(0xFFEF4444),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor.copy(alpha = 0.20f),
            contentColor = accentColor,
            disabledContainerColor = accentColor.copy(alpha = 0.08f),
            disabledContentColor = accentColor.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.40f)),
        contentPadding = contentPadding
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                fontSize = 12.5.sp,
                color = accentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Reusable action/navigation button modeled after the App Settings "Open Trash" button.
 * Supports both Solid and Glossy visual modes with customizable accent colors.
 */
@Composable
fun AppActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    style: AppButtonStyle = AppButtonStyle.Glossy,
    accentColor: Color = Color(0xFF38BDF8),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
) {
    if (style == AppButtonStyle.Solid) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.Black,
                disabledContainerColor = accentColor.copy(alpha = 0.3f),
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            contentPadding = contentPadding
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = text,
                    fontSize = 12.5.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        // Glossy mode
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor.copy(alpha = 0.20f),
                contentColor = accentColor,
                disabledContainerColor = accentColor.copy(alpha = 0.08f),
                disabledContentColor = accentColor.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
            contentPadding = contentPadding
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = text,
                    fontSize = 12.5.sp,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Reusable pill selection button modeled after FFmpeg Media Studio -> Convert -> Library/Browse
 * [selected / unselected] pill buttons.
 */
@Composable
fun AppPillButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    style: AppButtonStyle = AppButtonStyle.Glossy,
    accentColor: Color = Color(0xFF38BDF8),
    cornerRadius: Dp = 14.dp,
    forceTransparentBg: Boolean = false,
    contentPadding: PaddingValues? = null
) {
    val bgAlpha = if (isSelected) {
        if (style == AppButtonStyle.Solid) 0.9f else 0.35f
    } else {
        if (forceTransparentBg) 0.01f else 0.05f
    }
    val finalBgBrush = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = bgAlpha),
            accentColor.copy(alpha = bgAlpha * 0.4f)
        )
    )

    val finalBorderBrush = Brush.verticalGradient(
        colors = listOf(
            (if (isSelected) accentColor else Color.White).copy(alpha = if (isSelected) 0.5f else 0.25f),
            (if (isSelected) accentColor else Color.White).copy(alpha = if (isSelected) 0.2f else 0.10f)
        )
    )
    
    val textColor = if (isSelected) {
        if (style == AppButtonStyle.Solid) Color.Black else Color.White
    } else {
        Color.White.copy(alpha = 0.75f)
    }

    val resolvedPadding = contentPadding ?: PaddingValues(horizontal = 14.dp, vertical = 7.dp)

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(cornerRadius),
            color = Color.Transparent,
            modifier = modifier.clip(RoundedCornerShape(cornerRadius))
                .background(finalBgBrush)
                .border(
                    width = if (isSelected) 1.dp else 0.5.dp,
                    brush = finalBorderBrush,
                    shape = RoundedCornerShape(cornerRadius)
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(resolvedPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Text(
                    text = text,
                    fontSize = 11.sp, // Slightly smaller text for compact pills
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}
