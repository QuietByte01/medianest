package com.medianest.ui.components.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class FilterCarouselStyle {
    STUDIO_PREVIEW,
    PLAYER_CHIP
}

@Composable
fun MediaFilterCarousel(
    filters: List<MediaEffect>,
    activeFilter: MediaEffect,
    onFilterChange: (MediaEffect) -> Unit,
    style: FilterCarouselStyle = FilterCarouselStyle.STUDIO_PREVIEW,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (style == FilterCarouselStyle.STUDIO_PREVIEW) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(filters) { filter ->
            val isSelected = activeFilter == filter
            when (style) {
                FilterCarouselStyle.STUDIO_PREVIEW -> {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onFilterChange(filter) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(filter.previewColor)
                                .border(
                                    width = if (isSelected) 1.0.dp else 0.dp,
                                    color = if (isSelected) Color(0xFFFFD54F) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = filter.label,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFFFFD54F) else Color.White
                        )
                    }
                }
                FilterCarouselStyle.PLAYER_CHIP -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Color(0x3338BDF8)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onFilterChange(filter) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            color = if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.85f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
