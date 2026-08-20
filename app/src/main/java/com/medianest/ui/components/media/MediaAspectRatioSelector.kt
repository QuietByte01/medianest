package com.medianest.ui.components.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AspectRatioSelectorStyle {
    STUDIO_CHIP,
    PLAYER_LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAspectRatioSelector(
    options: List<MediaAspectRatio>,
    selected: MediaAspectRatio,
    onSelect: (MediaAspectRatio) -> Unit,
    style: AspectRatioSelectorStyle = AspectRatioSelectorStyle.STUDIO_CHIP,
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
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(ratio) },
                        label = { Text(ratio.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFD54F),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0x22FFFFFF),
                            labelColor = Color.White
                        )
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
                    Box(
                        modifier = Modifier
                            .clickable { onSelect(ratio) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ratio.label,
                            color = if (isSelected) Color.White else Color(0x80FFFFFF),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
