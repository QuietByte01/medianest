package com.medianest.ui.components.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.medianest.ui.components.PlaybackSpeedChip
import com.medianest.ui.components.WireframePreviewChip

enum class AspectRatioSelectorStyle {
    STUDIO_CHIP,
    PLAYER_LIST
}

@Composable
fun MediaAspectRatioSelector(
    options: List<MediaAspectRatio>,
    selected: MediaAspectRatio,
    onSelect: (MediaAspectRatio) -> Unit,
    style: AspectRatioSelectorStyle = AspectRatioSelectorStyle.STUDIO_CHIP,
    backdropState: com.medianest.ui.components.BackdropBlurState? = com.medianest.ui.components.LocalBackdropState.current,
    modifier: Modifier = Modifier
) {
    when (style) {
        AspectRatioSelectorStyle.STUDIO_CHIP -> {
            LazyRow(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(options) { ratio ->
                    val isSelected = ratio == selected
                    WireframePreviewChip(
                        label = ratio.label,
                        ratio = ratio.ratio,
                        isSelected = isSelected,
                        onClick = { onSelect(ratio) }
                    )
                }
            }
        }
        AspectRatioSelectorStyle.PLAYER_LIST -> {
            LazyRow(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(options) { ratio ->
                    val isSelected = ratio == selected
                    PlaybackSpeedChip(
                        label = ratio.label,
                        isSelected = isSelected,
                        onClick = { onSelect(ratio) },
                        fontSize = 14.sp,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        backdropState = backdropState
                    )
                }
            }
        }
    }
}
