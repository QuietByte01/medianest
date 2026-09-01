package com.medianest.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class AppSwitchStyle {
    Solid,
    Glossy
}

/**
 * Reusable switch component.
 * Features a warm Clay / Terracotta color palette for checked and unchecked states with no track border.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppSwitchStyle = AppSwitchStyle.Glossy,
    accentColor: Color = Color.Unspecified,
) {
    val clayColor = if (accentColor != Color.Unspecified) accentColor else Color(0xFFD97757)
    if (style == AppSwitchStyle.Solid) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = clayColor,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFF717D96),
                uncheckedTrackColor = Color(0x3D2D3748),
                uncheckedBorderColor = Color.Transparent
            )
        )
    } else {
        GlossySwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            clayColor = clayColor,
        )
    }
}

/**
 * A switch styled with a warm Clay / Terracotta color palette (no track border).
 */
@Composable
fun GlossySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    clayColor: Color = Color(0xFFD97757),
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color(0xFFFFF8F5),
            checkedTrackColor = clayColor,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color(0xFFB8ADA8),
            uncheckedTrackColor = Color(0xFF382F2E),
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color(0xFFFFF8F5).copy(alpha = 0.40f),
            disabledCheckedTrackColor = clayColor.copy(alpha = 0.30f),
            disabledUncheckedThumbColor = Color(0xFFB8ADA8).copy(alpha = 0.40f),
            disabledUncheckedTrackColor = Color(0xFF382F2E).copy(alpha = 0.30f)
        )
    )
}
