package com.medianest.ui.components.mediainfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.PaletteTagChip

@Composable
internal fun QualityBadgePill(text: String) {
    PaletteTagChip(
        label = text,
        paletteColor = Color(0xFFCBD5E1),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
internal fun InfoSectionCard(
    icon: ImageVector? = null,
    title: String,
    leadingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x1A1E2438),
        borderColor = Color(0x333F4A6A)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (leadingContent != null) {
                    leadingContent()
                } else if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color.White.copy(alpha = 0.90f), modifier = Modifier.size(18.dp))
                }
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * Media stat card styled identically to Media Diagnostics stat cards.
 */
@Composable
internal fun StatBox(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color(0x1A1E2438),
        borderColor = Color(0x333F4A6A)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x336366F1)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFFA5B4FC), modifier = Modifier.size(16.dp))
            }
            Column {
                Text(label, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 0.5.sp)
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun LabelValueBlock(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF7A7F90)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}
