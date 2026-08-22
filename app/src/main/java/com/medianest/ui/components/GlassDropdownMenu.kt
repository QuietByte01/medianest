package com.medianest.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.medianest.R
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Premium Blurred Glass Dropdown Menu component.
 * Uses 70% opacity blurred backdrop texture (64dp blur) with unblurred crisp contents on top.
 * When [useImageBackground] is false (e.g., in sort dropdowns), uses a transparent frosted glass background with blur.
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = Color.Transparent,
    shadowElevation: Dp = 12.dp,
    useImageBackground: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current
    val bgRes = if (useImageBackground) R.drawable.bg_596 else null
    val effectiveBgColor = if (useImageBackground) {
        if (containerColor != Color.Transparent && containerColor != Color.Unspecified) {
            containerColor
        } else if (isDark) {
            Color(0x33000000) // subtle dark tint to blend naturally
        } else {
            Color(0x1AFFFFFF) // subtle light sheen
        }
    } else {
        if (containerColor != Color.Transparent && containerColor != Color.Unspecified) {
            containerColor
        } else if (isDark) {
            Color(0x730F1015)
        } else {
            Color(0x73F0F4F8)
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation
    ) {
        GlassSurface(
            shape = shape,
            backgroundColor = effectiveBgColor,
            borderColor = if (isDark) Color(0x33FFFFFF) else Color(0x22000000),
            borderWidth = 1.dp,
            backgroundImage = bgRes,
            backgroundImageAlpha = if (useImageBackground) 1.0f else 0f,
            enableBlur = !useImageBackground,
            blurRadius = 16.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}
