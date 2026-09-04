package com.medianest.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class AppSwitchStyle {
    Solid,
    Glossy,
    Glass
}

/**
 * Reusable switch component.
 * Supports [AppSwitchStyle.Glass] (frosted glass switch matching CSS glass design),
 * [AppSwitchStyle.Glossy], and [AppSwitchStyle.Solid].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppSwitchStyle = AppSwitchStyle.Glass,
    accentColor: Color = Color.Unspecified,
) {
    when (style) {
        AppSwitchStyle.Glass -> {
            GlassToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = modifier,
                enabled = enabled
            )
        }
        AppSwitchStyle.Solid -> {
            if (accentColor != Color.Unspecified) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = modifier,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White.copy(alpha = 0.90f),
                        checkedTrackColor = accentColor.copy(alpha = 0.30f),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.10f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.05f),
                        uncheckedBorderColor = Color.Transparent,
                    )
                )
            } else {
                GlossySwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = modifier,
                    enabled = enabled,
                )
            }
        }
        AppSwitchStyle.Glossy -> {
            GlossySwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = modifier,
                enabled = enabled,
            )
        }
    }
}

/**
 * Transparent fluid toggle switch with 30% opacity checked white thumb and 10% opacity unchecked white thumb.
 */
@Composable
fun GlossySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White.copy(alpha = 0.30f),
            checkedTrackColor = Color.White.copy(alpha = 0.20f),
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White.copy(alpha = 0.10f),
            uncheckedTrackColor = Color.White.copy(alpha = 0.05f),
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color.White.copy(alpha = 0.10f),
            disabledCheckedTrackColor = Color.White.copy(alpha = 0.08f),
            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.05f),
            disabledUncheckedTrackColor = Color.White.copy(alpha = 0.02f),
        )
    )
}
