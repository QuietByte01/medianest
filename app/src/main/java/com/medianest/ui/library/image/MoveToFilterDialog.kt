package com.medianest.ui.library.image

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.data.model.MediaItem
import com.medianest.ui.components.GlassSurface
import com.medianest.ui.components.LocalBackdropState
import com.medianest.ui.components.backdropReceiver
import com.medianest.util.ImageExclusionManager
import com.medianest.util.ImageTagManager

internal data class TargetFilterSpec(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveToFilterDialog(
    item: MediaItem,
    currentFilterTab: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600
    val maxDialogWidth = when {
        isLandscape -> 580.dp
        isTablet -> 520.dp
        else -> 340.dp
    }

    val availableTargets = remember(currentFilterTab) {
        listOf(
            TargetFilterSpec("COOKING", "Cooking & Food", Icons.Default.Restaurant),
            TargetFilterSpec("TRAVEL", "Travel & Places", Icons.Default.Flight),
            TargetFilterSpec("NOTES", "Notes & Studies", Icons.Default.Note),
            TargetFilterSpec("AI_GENERATED", "AI Generated", Icons.Default.AutoAwesome),
            TargetFilterSpec("GARDENING", "Gardening & Nature", Icons.Default.Park),
            TargetFilterSpec("ANIME", "Anime & Art", Icons.Default.Brush),
            TargetFilterSpec("WALLPAPERS", "Wallpapers", Icons.Default.Wallpaper),
            TargetFilterSpec("SCREENSHOTS", "Screenshots", Icons.Default.Screenshot),
            TargetFilterSpec("PETS", "Pets & Animals", Icons.Default.Pets),
            TargetFilterSpec("FAMILY", "Family & People", Icons.Default.People),
            TargetFilterSpec("DOCUMENTS", "Receipts & Docs", Icons.Default.Description),
            TargetFilterSpec("MEMES", "Memes & Funny", Icons.Default.EmojiEmotions),
            TargetFilterSpec("SOCIAL", "Social Media", Icons.Default.Share),
            TargetFilterSpec("CAMERA", "Camera Shots", Icons.Default.PhotoCamera),
            TargetFilterSpec("EDITED", "Edited", Icons.Default.Edit)
        ).filter { it.id != currentFilterTab }
    }

    val backdropState = LocalBackdropState.current

    // Full screen dim backdrop with blur and black tint
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.50f))
            .then(
                if (backdropState != null) {
                    Modifier.backdropReceiver(backdropState, blurRadius = 28.dp)
                } else Modifier
            )
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = maxDialogWidth)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .clickable(enabled = false) {}, // Intercept click inside card
            shape = RoundedCornerShape(22.dp),
            backgroundColor = Color(0xDC0F1015), // Black tint with blur
            borderColor = Color(0x38FFFFFF),
            enableBlur = true,
            blurRadius = 28.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header with Title and Red Close X Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "MOVE IMAGE TO FILTER",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Text(
                    text = "Select a category filter to assign this item:",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableTargets.forEach { spec ->
                            GlassSurface(
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = Color(0x22FFFFFF),
                                borderColor = Color(0x33FFFFFF),
                                modifier = Modifier
                                    .clickable {
                                        val uriStr = item.uri.toString()
                                        // 1. Exclude from current filter
                                        if (currentFilterTab !in listOf("ALL", "FOLDERS", "HIDDEN", "EXCLUDED")) {
                                            ImageExclusionManager.excludeFromFilter(context, uriStr, currentFilterTab)
                                        }
                                        // 2. Assign to target filter
                                        ImageTagManager.toggleTag(context, uriStr, spec.id)
                                        ImageExclusionManager.restoreToFilter(context, uriStr, spec.id)

                                        Toast.makeText(context, "Moved to '${spec.label}'", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = spec.icon,
                                        contentDescription = spec.label,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = spec.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchMoveToFilterDialog(
    selectedUris: Set<String>,
    currentFilterTab: String = "ALL",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600
    val maxDialogWidth = when {
        isLandscape -> 580.dp
        isTablet -> 520.dp
        else -> 340.dp
    }

    val availableTargets = remember(currentFilterTab) {
        listOf(
            TargetFilterSpec("COOKING", "Cooking & Food", Icons.Default.Restaurant),
            TargetFilterSpec("TRAVEL", "Travel & Places", Icons.Default.Flight),
            TargetFilterSpec("NOTES", "Notes & Studies", Icons.Default.Note),
            TargetFilterSpec("AI_GENERATED", "AI Generated", Icons.Default.AutoAwesome),
            TargetFilterSpec("GARDENING", "Gardening & Nature", Icons.Default.Park),
            TargetFilterSpec("ANIME", "Anime & Art", Icons.Default.Brush),
            TargetFilterSpec("WALLPAPERS", "Wallpapers", Icons.Default.Wallpaper),
            TargetFilterSpec("SCREENSHOTS", "Screenshots", Icons.Default.Screenshot),
            TargetFilterSpec("PETS", "Pets & Animals", Icons.Default.Pets),
            TargetFilterSpec("FAMILY", "Family & People", Icons.Default.People),
            TargetFilterSpec("DOCUMENTS", "Receipts & Docs", Icons.Default.Description),
            TargetFilterSpec("MEMES", "Memes & Funny", Icons.Default.EmojiEmotions),
            TargetFilterSpec("SOCIAL", "Social Media", Icons.Default.Share),
            TargetFilterSpec("CAMERA", "Camera Shots", Icons.Default.PhotoCamera),
            TargetFilterSpec("EDITED", "Edited", Icons.Default.Edit)
        ).filter { it.id != currentFilterTab }
    }

    val backdropState = LocalBackdropState.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.50f))
            .then(
                if (backdropState != null) {
                    Modifier.backdropReceiver(backdropState, blurRadius = 28.dp)
                } else Modifier
            )
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = maxDialogWidth)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(22.dp),
            backgroundColor = Color(0xDC0F1015),
            borderColor = Color(0x38FFFFFF),
            enableBlur = true,
            blurRadius = 28.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header with Title and Red Close X Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "MOVE ${selectedUris.size} IMAGES TO FILTER",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                Text(
                    text = "Select a category filter to assign the ${selectedUris.size} selected items:",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableTargets.forEach { spec ->
                            GlassSurface(
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = Color(0x22FFFFFF),
                                borderColor = Color(0x33FFFFFF),
                                modifier = Modifier
                                    .clickable {
                                        selectedUris.forEach { uriStr ->
                                            if (currentFilterTab !in listOf("ALL", "FOLDERS", "HIDDEN", "EXCLUDED")) {
                                                ImageExclusionManager.excludeFromFilter(context, uriStr, currentFilterTab)
                                            }
                                            ImageTagManager.toggleTag(context, uriStr, spec.id)
                                            ImageExclusionManager.restoreToFilter(context, uriStr, spec.id)
                                        }

                                        Toast.makeText(context, "Moved ${selectedUris.size} items to '${spec.label}'", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = spec.icon,
                                        contentDescription = spec.label,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = spec.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun getFilterLabel(tabId: String): String {
    return when (tabId) {
        "CAMERA" -> "Camera"
        "FAVORITES" -> "Favorites"
        "NOTES" -> "Notes & Studies"
        "SCREENSHOTS" -> "Screenshots"
        "GIFS" -> "GIFs"
        "SOCIAL" -> "Social Media"
        "PNG_SVG" -> "PNG & SVG"
        "EDITED" -> "Edited"
        "AI_GENERATED" -> "AI Generated"
        "ANIME" -> "Anime & Art"
        "COOKING" -> "Cooking & Food"
        "TRAVEL" -> "Travel & Places"
        "GARDENING" -> "Gardening & Nature"
        "PETS" -> "Pets & Animals"
        "FAMILY" -> "Family & People"
        "DOCUMENTS" -> "Receipts & Docs"
        "MEMES" -> "Memes & Funny"
        "WALLPAPERS" -> "Wallpapers"
        else -> tabId.lowercase().replaceFirstChar { it.uppercase() }
    }
}
