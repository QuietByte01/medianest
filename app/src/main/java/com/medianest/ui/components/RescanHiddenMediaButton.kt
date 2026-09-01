package com.medianest.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable animated refresh/rescan button for hidden and excluded media discovery.
 * Displays a continuous smooth rotation while scanning is active.
 */
@Composable
fun RescanHiddenMediaButton(
    isScanning: Boolean,
    onRescanClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    buttonSize: Dp = 32.dp,
    tint: Color = Color(0xFFC0C5D0)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rescan_rotation_transition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing)
        ),
        label = "rescan_rotation"
    )

    IconButton(
        onClick = onRescanClick,
        enabled = !isScanning,
        modifier = modifier.size(buttonSize)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Rescan Hidden & Excluded Folders",
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .then(if (isScanning) Modifier.graphicsLayer { rotationZ = rotation } else Modifier)
        )
    }
}
