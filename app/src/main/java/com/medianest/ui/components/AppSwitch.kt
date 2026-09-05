package com.medianest.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class AppSwitchStyle {
    Solid,
    Glossy,
    Glass
}

/**
 * Universal Glass Switch Component used everywhere across MediaNest.
 * Uses [GlassToggle] for exact pill-shaped capsule clipping, custom translucent track colors,
 * inner highlights, and real-time hardware backdrop blur without square blur box artifacts.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppSwitchStyle = AppSwitchStyle.Glass,
    accentColor: Color = Color.Unspecified,
) {
    GlassToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled
    )
}

/**
 * Universal Switch delegating to [GlassToggle].
 */
@Composable
fun GlossySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdropState: BackdropBlurState? = LocalBackdropState.current
) {
    GlassToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        backdropState = backdropState
    )
}
