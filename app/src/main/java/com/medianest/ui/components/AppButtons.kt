package com.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    cornerRadius: Dp = 14.dp
) {
    val bgColor = if (isSelected) {
        accentColor.copy(alpha = if (style == AppButtonStyle.Solid) 0.9f else 0.25f)
    } else {
        Color(0x14FFFFFF)
    }
    
    val borderColor = if (isSelected) {
        accentColor
    } else {
        Color(0x22FFFFFF)
    }
    
    val textColor = if (isSelected) {
        if (style == AppButtonStyle.Solid) Color.Black else accentColor
    } else {
        Color.White.copy(alpha = 0.85f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.0.dp else 0.5.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}
