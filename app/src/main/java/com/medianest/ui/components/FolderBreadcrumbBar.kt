package com.medianest.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FolderBreadcrumbBar(
    rootTab: String, // "FOLDERS", "HIDDEN", "EXCLUDED"
    selectedFolder: String?,
    onNavigateToRoot: () -> Unit,
    onNavigateToSegment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootIcon: ImageVector = when (rootTab) {
        "HIDDEN" -> Icons.Default.VisibilityOff
        "EXCLUDED" -> Icons.Default.FolderOff
        else -> Icons.Default.Folder
    }

    val rootLabel = when (rootTab) {
        "HIDDEN" -> "Hidden"
        "EXCLUDED" -> "Excluded"
        else -> "Folders"
    }

    val segments = remember(selectedFolder) {
        if (selectedFolder.isNullOrBlank()) emptyList()
        else {
            val rawParts = selectedFolder.trim('/').split('/').filter { it.isNotBlank() }
            var accumulated = ""
            rawParts.map { part ->
                accumulated = if (accumulated.isEmpty()) part else "$accumulated/$part"
                Pair(part, accumulated)
            }
        }
    }

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Root element (clean text + icon, no bulky chip border)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onNavigateToRoot() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = rootIcon,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = rootLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )
        }

        // Segments
        segments.forEachIndexed { index, (segmentName, fullSubPath) ->
            val isLast = index == segments.size - 1

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(14.dp)
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isLast) { onNavigateToSegment(fullSubPath) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (segmentName.startsWith(".")) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color(0xFFA78BFA),
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = segmentName,
                    fontSize = 13.sp,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLast) Color.White else Color(0xFF94A3B8)
                )
            }
        }
    }
}
