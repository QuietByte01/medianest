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
 * Reusable switch/toggle component modeled after the App Settings toggles.
 * Supports both Solid and Glossy visual modes with customizable colors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppSwitchStyle = AppSwitchStyle.Solid,
    accentColor: Color = Color(0xFF6366F1),
    checkedThumbColor: Color = Color.White,
    checkedTrackColor: Color = if (style == AppSwitchStyle.Glossy) accentColor.copy(alpha = 0.5f) else accentColor,
    uncheckedThumbColor: Color = Color(0xFF717D96),
    uncheckedTrackColor: Color = if (style == AppSwitchStyle.Glossy) Color(0x1AFFFFFF) else Color(0x3D2D3748),
    checkedBorderColor: Color = if (style == AppSwitchStyle.Glossy) accentColor else Color.Transparent,
    uncheckedBorderColor: Color = if (style == AppSwitchStyle.Glossy) Color(0x33FFFFFF) else Color.Transparent
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = checkedThumbColor,
            checkedTrackColor = checkedTrackColor,
            checkedBorderColor = checkedBorderColor,
            uncheckedThumbColor = uncheckedThumbColor,
            uncheckedTrackColor = uncheckedTrackColor,
            uncheckedBorderColor = uncheckedBorderColor,
            disabledCheckedThumbColor = checkedThumbColor.copy(alpha = 0.4f),
            disabledCheckedTrackColor = checkedTrackColor.copy(alpha = 0.2f),
            disabledUncheckedThumbColor = uncheckedThumbColor.copy(alpha = 0.4f),
            disabledUncheckedTrackColor = uncheckedTrackColor.copy(alpha = 0.2f)
        )
    )
}
