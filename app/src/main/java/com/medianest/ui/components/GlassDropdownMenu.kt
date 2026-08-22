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

/**
 * Premium Ambient Glass Dropdown Menu component matching the exact background of the Info Sheet.
 * Features an obsidian background, warm top-left and cool bottom-right ambient radial glow orbs,
 * a vertical glass sheen, and a sleek gradient border with zero JPG image dependency.
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
    useImageBackground: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
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
        AmbientGlassSurface(
            shape = shape,
            borderWidth = 0.5.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}
