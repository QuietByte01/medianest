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
 * Reusable switch component supporting both Solid and Glossy visual styles.
 * Glossy style renders a translucent switch with 10% track opacity when unchecked and 30% track opacity when selected, with no track border.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppSwitchStyle = AppSwitchStyle.Glossy,
    accentColor: Color = Color.Unspecified
) {
    if (style == AppSwitchStyle.Glossy) {
        GlossySwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
    } else {
        val checkedTrack = if (accentColor != Color.Unspecified) accentColor else Color(0xFF6366F1)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = checkedTrack,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFF717D96),
                uncheckedTrackColor = Color(0x3D2D3748),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/**
 * A translucent switch with 10% track opacity when unchecked and 30% white track opacity when selected (no track border).
 */
@Composable
fun GlossySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color.White.copy(alpha = 0.30f),
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White.copy(alpha = 0.70f),
            uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color.White.copy(alpha = 0.40f),
            disabledCheckedTrackColor = Color.White.copy(alpha = 0.15f),
            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.30f),
            disabledUncheckedTrackColor = Color.White.copy(alpha = 0.05f)
        )
    )
}
