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
import com.medianest.ui.theme.LocalDarkTheme

/**
 * Premium Ambient Glass Dropdown Menu component.
 * Wraps content with [BackdropGlassSurface].
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
    shadowElevation: Dp = 0.dp,
    useImageBackground: Boolean = false,
    backgroundImage: Any? = null,
    hue: Float? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current
    val menuBg = if (containerColor != Color.Transparent) {
        containerColor
    } else {
        if (isDark) Color(0xEE08090E) else Color(0xEEFFFFFF)
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
        BackdropGlassSurface(
            shape = shape,
            backgroundColor = menuBg,
            borderColor = if (isDark) Color(0x38FFFFFF) else Color(0x28000000),
            borderWidth = 0.5.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}
