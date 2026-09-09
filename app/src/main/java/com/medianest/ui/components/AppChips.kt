package com.medianest.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medianest.ui.components.media.MediaAspectRatio
import com.medianest.ui.dashboard.AnalyticsColors

/**
 * Universal color configuration for all App Chip design types.
 */
@Immutable
data class AppChipColors(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
    val selectedBorderColor: Color,
    val indicatorDotColor: Color
)

// Legacy alias
typealias FormatChipColors = AppChipColors

/**
 * Default values, paddings, shapes, and color builders for App Chips.
 */
object AppChipDefaults {
    val DefaultShape: Shape = RoundedCornerShape(8.dp)
    val PillShape: Shape = RoundedCornerShape(14.dp)
    val RoundedShape: Shape = RoundedCornerShape(10.dp)

    val DefaultContentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    val CompactContentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
    val BadgeContentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 3.dp)

    // Vibrant Palette Accents
    val AccentGold = Color(0xFFFFD54F)
    val AccentCyan = Color(0xFF38BDF8)
    val AccentGreen = Color(0xFF06A77D)
    val AccentPink = Color(0xFFFF006E)
    val AccentPurple = Color(0xFFA855F7)
    val AccentOrange = Color(0xFFFF9100)
    val AccentBlue = Color(0xFF60A5FA)

    /**
     * Creates [AppChipColors] based on a custom accent color, or automatically
     * resolves palette colors from [AnalyticsColors] when a format/category is provided.
     */
    @Composable
    fun colors(
        format: String? = null,
        category: String? = null,
        accentColor: Color? = null,
        containerAlpha: Float = 0.12f,
        selectedContainerAlpha: Float = 0.35f,
        borderAlpha: Float = 0.45f,
        selectedBorderAlpha: Float = 1.0f
    ): AppChipColors {
        val baseColor = accentColor ?: remember(format, category) {
            if (format != null) AnalyticsColors.getFormatColor(format, category)
            else AccentGold
        }

        return AppChipColors(
            containerColor = baseColor.copy(alpha = containerAlpha),
            contentColor = baseColor.copy(alpha = 0.90f),
            borderColor = baseColor.copy(alpha = borderAlpha),
            selectedContainerColor = baseColor.copy(alpha = selectedContainerAlpha),
            selectedContentColor = baseColor,
            selectedBorderColor = baseColor.copy(alpha = selectedBorderAlpha),
            indicatorDotColor = baseColor
        )
    }

    /**
     * Creates [AppChipColors] with explicit control over every single color state.
     */
    fun customColors(
        containerColor: Color,
        contentColor: Color,
        borderColor: Color,
        selectedContainerColor: Color = containerColor,
        selectedContentColor: Color = contentColor,
        selectedBorderColor: Color = borderColor,
        indicatorDotColor: Color = contentColor
    ): AppChipColors {
        return AppChipColors(
            containerColor = containerColor,
            contentColor = contentColor,
            borderColor = borderColor,
            selectedContainerColor = selectedContainerColor,
            selectedContentColor = selectedContentColor,
            selectedBorderColor = selectedBorderColor,
            indicatorDotColor = indicatorDotColor
        )
    }
}

// Legacy alias
typealias FormatChipDefaults = AppChipDefaults

// ============================================================================
// 1. BASE GENERIC CHIP: AppChip / GlossyChip
// ============================================================================

/**
 * [GlossyChip] / [AppChip]
 * Universal foundation chip featuring glassmorphic translucency, glowing border accents,
 * customizable leading/trailing slots, and selection feedback.
 */
@Composable
fun AppChip(
    label: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    colors: AppChipColors = AppChipDefaults.colors(accentColor = accentColor),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = false,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily? = null,
    contentPadding: PaddingValues = if (onClick != null) AppChipDefaults.DefaultContentPadding else AppChipDefaults.BadgeContentPadding,
    enabled: Boolean = true
) {
    val currentContainerColor = if (isSelected) colors.selectedContainerColor else colors.containerColor
    val currentContentColor = if (isSelected) colors.selectedContentColor else colors.contentColor
    val currentBorderColor = if (isSelected) colors.selectedBorderColor else colors.borderColor
    val borderWidth: Dp = if (isSelected) 1.5.dp else 1.dp

    val chipBody: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingContent != null) {
                leadingContent()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = currentContentColor,
                    modifier = Modifier.size(14.dp)
                )
            } else if (isSelected && showIndicatorDot) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(colors.indicatorDotColor)
                )
            }

            Text(
                text = label,
                fontSize = fontSize,
                fontWeight = if (isSelected) FontWeight.Bold else fontWeight,
                fontFamily = fontFamily,
                color = currentContentColor
            )

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = currentContainerColor,
            border = BorderStroke(borderWidth, currentBorderColor),
            modifier = modifier
        ) {
            chipBody()
        }
    } else {
        Surface(
            shape = shape,
            color = currentContainerColor,
            border = BorderStroke(borderWidth, currentBorderColor),
            modifier = modifier
        ) {
            chipBody()
        }
    }
}

// Design Alias
@Composable
fun GlossyChip(
    label: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    colors: AppChipColors = AppChipDefaults.colors(accentColor = accentColor),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = false,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily? = null,
    contentPadding: PaddingValues = if (onClick != null) AppChipDefaults.DefaultContentPadding else AppChipDefaults.BadgeContentPadding,
    enabled: Boolean = true
) = AppChip(
    label = label,
    modifier = modifier,
    isSelected = isSelected,
    onClick = onClick,
    icon = icon,
    leadingContent = leadingContent,
    trailingContent = trailingContent,
    accentColor = accentColor,
    colors = colors,
    shape = shape,
    showIndicatorDot = showIndicatorDot,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    contentPadding = contentPadding,
    enabled = enabled
)

/**
 * [PlaybackSpeedChip]
 * A sleek pill chip styled with the video player's playback speed chip design.
 * Features a glossy vertical white gradient background and dark text when selected,
 * and a translucent glass surface with crisp white text and subtle border when unselected.
 *
 * Designed for: Playback speed selections, media action pills (Play, Rename, Share), mode switches.
 */
@Composable
fun PlaybackSpeedChip(
    label: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    backdropState: BackdropBlurState? = LocalBackdropState.current,
    enabled: Boolean = true
) {
    val frostedWhiteBrush = Brush.verticalGradient(
        colors = listOf(Color(0xE6FFFFFF), Color(0xCCFFFFFF))
    )
    val contentColor = if (isSelected) Color(0xFF0F172A) else Color.White

    val baseModifier = modifier
        .clip(shape)
        .then(
            if (isSelected) {
                Modifier
                    .background(frostedWhiteBrush)
                    .border(0.75.dp, Color.White, shape)
            } else {
                val isSurfaceViewMode = LocalIsSurfaceViewMode.current
                if (isSurfaceViewMode) {
                    Modifier
                        .background(Color(0xCC0D0D12))
                        .border(0.5.dp, Color(0x38FFFFFF), shape)
                } else if (backdropState != null) {
                    Modifier
                        .backdropReceiver(
                            state = backdropState,
                            blurRadius = 16.dp,
                            tint = Color(0x550A0C10),
                            baseColor = Color.Transparent
                        )
                        .border(0.5.dp, Color(0x38FFFFFF), shape)
                } else {
                    Modifier
                        .background(Color(0x33FFFFFF))
                        .border(0.5.dp, Color(0x22FFFFFF), shape)
                }
            }
        )
        .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
        .padding(contentPadding)

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingContent != null) {
                leadingContent()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size((fontSize.value + 2).dp)
                )
            }
            Text(
                text = label,
                color = contentColor,
                fontWeight = fontWeight,
                fontSize = fontSize
            )
        }
    }
}

@Composable
fun SpeedChip(
    label: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    enabled: Boolean = true
) = PlaybackSpeedChip(
    label = label,
    modifier = modifier,
    isSelected = isSelected,
    onClick = onClick,
    icon = icon,
    leadingContent = leadingContent,
    shape = shape,
    fontSize = fontSize,
    fontWeight = fontWeight,
    contentPadding = contentPadding,
    enabled = enabled
)

// ============================================================================
// 2. DESIGN TYPE: PaletteTagChip (Tinted Pill Badge Design)
// ============================================================================

/**
 * [PaletteTagChip]
 * A tinted translucent pill badge chip with dynamic palette/accent colors,
 * bold uppercase tag typography, and glowing indicator dot.
 *
 * Designed for: Formats, Codecs, Statuses, Metrics, Extension Tags, Category Pills.
 */
@Composable
fun PaletteTagChip(
    label: String,
    modifier: Modifier = Modifier,
    paletteColor: Color? = null,
    formatKey: String? = null,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    colors: AppChipColors = AppChipDefaults.colors(
        format = formatKey ?: label,
        accentColor = paletteColor
    ),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = true,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    contentPadding: PaddingValues = if (onClick != null) AppChipDefaults.DefaultContentPadding else AppChipDefaults.BadgeContentPadding,
    enabled: Boolean = true
) {
    AppChip(
        label = label.uppercase(),
        modifier = modifier,
        isSelected = isSelected,
        onClick = onClick,
        colors = colors,
        shape = shape,
        showIndicatorDot = showIndicatorDot,
        fontSize = fontSize,
        fontWeight = fontWeight,
        contentPadding = contentPadding,
        enabled = enabled
    )
}

// ============================================================================
// 3. DESIGN TYPE: WireframePreviewChip (Geometric Proportion Frame Design)
// ============================================================================

/**
 * [WireframePreviewChip]
 * A chip featuring a live mini geometric proportional wireframe box preview
 * alongside its proportion/dimension label.
 *
 * Designed for: Aspect Ratios (16:9, 9:16, 1:1, 4:3, 21:9), Canvas Scales, Resolutions, Frames.
 */
@Composable
fun WireframePreviewChip(
    label: String,
    ratio: Float?,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    showWireframePreview: Boolean = true,
    accentColor: Color = AppChipDefaults.AccentGold,
    bgOpacity: Float? = null,
    selectedBgOpacity: Float? = null,
    colors: AppChipColors = AppChipDefaults.colors(
        accentColor = accentColor,
        containerAlpha = bgOpacity ?: 0.12f,
        selectedContainerAlpha = selectedBgOpacity ?: (bgOpacity?.let { (it * 2.5f).coerceIn(0f, 1f) } ?: 0.35f)
    ),
    shape: Shape = AppChipDefaults.DefaultShape,
    enabled: Boolean = true
) {
    AppChip(
        label = label,
        modifier = modifier,
        isSelected = isSelected,
        onClick = onClick,
        colors = colors,
        shape = shape,
        showIndicatorDot = false,
        fontSize = 11.5.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.5.dp),
        leadingContent = if (showWireframePreview) {
            {
                GeometricWireframeIcon(
                    ratio = ratio,
                    color = if (isSelected) colors.selectedContentColor else colors.contentColor
                )
            }
        } else null,
        enabled = enabled
    )
}

/**
 * Geometric proportional wireframe rectangle canvas renderer.
 */
@Composable
fun GeometricWireframeIcon(
    ratio: Float?,
    color: Color,
    modifier: Modifier = Modifier.size(15.dp, 12.dp)
) {
    Canvas(modifier = modifier) {
        val totalW = size.width
        val totalH = size.height

        val (boxW, boxH) = when {
            ratio == null -> {
                totalW * 0.85f to totalH * 0.85f
            }
            ratio >= 1.7f -> {
                // Wide horizontal (16:9, 21:9, 16:10)
                totalW to (totalW / (if (ratio > 2.0f) 2.3f else 1.77f)).coerceIn(4f, totalH)
            }
            ratio <= 0.65f -> {
                // Tall vertical (9:16, 3:4, 4:5)
                (totalH * ratio).coerceIn(4f, totalW) to totalH
            }
            ratio in 0.9f..1.1f -> {
                // Square (1:1)
                val s = minOf(totalW, totalH) * 0.85f
                s to s
            }
            else -> {
                // Standard (4:3, etc.)
                val h = totalH * 0.9f
                (h * ratio).coerceIn(4f, totalW) to h
            }
        }

        val topLeft = Offset(
            x = (totalW - boxW) / 2f,
            y = (totalH - boxH) / 2f
        )

        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

// ============================================================================
// 4. DESIGN TYPE: IconActionChip (Tool & Action Design)
// ============================================================================

/**
 * [IconActionChip]
 * A tool/action chip with a leading vector icon, compact typography, and active glow border.
 *
 * Designed for: Toolbar Actions, Crop Modes, Transforms (Rotate, Flip), Filters, Toggles.
 */
@Composable
fun IconActionChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.Crop,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    accentColor: Color = AppChipDefaults.AccentGold,
    colors: AppChipColors = AppChipDefaults.colors(accentColor = accentColor),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = false,
    fontSize: TextUnit = 11.5.sp,
    enabled: Boolean = true
) {
    AppChip(
        label = label,
        modifier = modifier,
        icon = icon,
        isSelected = isSelected,
        onClick = onClick,
        colors = colors,
        shape = shape,
        showIndicatorDot = showIndicatorDot,
        fontSize = fontSize,
        contentPadding = AppChipDefaults.CompactContentPadding,
        enabled = enabled
    )
}

// ============================================================================
// 5. DESIGN TYPE: TypographyTagChip (Typography & Emoji Tag Design)
// ============================================================================

/**
 * [TypographyTagChip]
 * A chip capable of rendering text in custom font families (live typography preview)
 * or displaying leading emoji badges.
 *
 * Designed for: Font Selectors, Typography Pickers, Emoji Badges, Category Tags.
 */
@Composable
fun TypographyTagChip(
    label: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: ImageVector? = null,
    fontFamily: FontFamily? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    accentColor: Color = AppChipDefaults.AccentGold,
    colors: AppChipColors = AppChipDefaults.colors(accentColor = accentColor),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = isSelected && emoji == null && icon == null,
    fontSize: TextUnit = 11.5.sp,
    enabled: Boolean = true
) {
    AppChip(
        label = label,
        modifier = modifier,
        icon = icon,
        fontFamily = fontFamily,
        isSelected = isSelected,
        onClick = onClick,
        colors = colors,
        shape = shape,
        showIndicatorDot = showIndicatorDot,
        fontSize = fontSize,
        contentPadding = AppChipDefaults.CompactContentPadding,
        leadingContent = if (emoji != null) {
            {
                Text(text = emoji, fontSize = (fontSize.value + 1).sp)
            }
        } else null,
        enabled = enabled
    )
}

// ============================================================================
// CONVENIENCE & DOMAIN WRAPPERS (Full Backwards Compatibility)
// ============================================================================

/**
 * Format & Codec chip wrapper delegating to [PaletteTagChip].
 */
@Composable
fun AppFormatChip(
    format: String,
    modifier: Modifier = Modifier,
    label: String = format.uppercase(),
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    customColor: Color? = null,
    colors: AppChipColors = AppChipDefaults.colors(
        format = format,
        accentColor = customColor
    ),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = true,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    contentPadding: PaddingValues = if (onClick != null) AppChipDefaults.DefaultContentPadding else AppChipDefaults.BadgeContentPadding,
    enabled: Boolean = true
) = PaletteTagChip(
    label = label,
    modifier = modifier,
    formatKey = format,
    paletteColor = customColor,
    isSelected = isSelected,
    onClick = onClick,
    colors = colors,
    shape = shape,
    showIndicatorDot = showIndicatorDot,
    fontSize = fontSize,
    fontWeight = fontWeight,
    contentPadding = contentPadding,
    enabled = enabled
)

@Composable
fun MediaFormatChip(
    format: String,
    modifier: Modifier = Modifier,
    label: String = format.uppercase(),
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    customColor: Color? = null,
    colors: AppChipColors = AppChipDefaults.colors(
        format = format,
        accentColor = customColor
    ),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = true,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    contentPadding: PaddingValues = if (onClick != null) AppChipDefaults.DefaultContentPadding else AppChipDefaults.BadgeContentPadding,
    enabled: Boolean = true
) = AppFormatChip(
    format = format,
    modifier = modifier,
    label = label,
    isSelected = isSelected,
    onClick = onClick,
    customColor = customColor,
    colors = colors,
    shape = shape,
    showIndicatorDot = showIndicatorDot,
    fontSize = fontSize,
    fontWeight = fontWeight,
    contentPadding = contentPadding,
    enabled = enabled
)

/**
 * Aspect ratio chip wrapper delegating to [WireframePreviewChip].
 */
@Composable
fun AppAspectRatioChip(
    aspectRatio: MediaAspectRatio,
    modifier: Modifier = Modifier,
    label: String = aspectRatio.label,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    showWireframePreview: Boolean = true,
    accentColor: Color = AppChipDefaults.AccentGold,
    bgOpacity: Float? = null,
    selectedBgOpacity: Float? = null,
    colors: AppChipColors = AppChipDefaults.colors(
        accentColor = accentColor,
        containerAlpha = bgOpacity ?: 0.12f,
        selectedContainerAlpha = selectedBgOpacity ?: (bgOpacity?.let { (it * 2.5f).coerceIn(0f, 1f) } ?: 0.35f)
    ),
    shape: Shape = AppChipDefaults.DefaultShape,
    enabled: Boolean = true
) = WireframePreviewChip(
    label = label,
    ratio = aspectRatio.ratio,
    modifier = modifier,
    isSelected = isSelected,
    onClick = onClick,
    showWireframePreview = showWireframePreview,
    accentColor = accentColor,
    bgOpacity = bgOpacity,
    selectedBgOpacity = selectedBgOpacity,
    colors = colors,
    shape = shape,
    enabled = enabled
)

@Composable
fun MediaAspectRatioChip(
    aspectRatio: MediaAspectRatio,
    modifier: Modifier = Modifier,
    label: String = aspectRatio.label,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    showWireframePreview: Boolean = true,
    accentColor: Color = AppChipDefaults.AccentGold,
    bgOpacity: Float? = null,
    selectedBgOpacity: Float? = null,
    colors: AppChipColors = AppChipDefaults.colors(
        accentColor = accentColor,
        containerAlpha = bgOpacity ?: 0.12f,
        selectedContainerAlpha = selectedBgOpacity ?: (bgOpacity?.let { (it * 2.5f).coerceIn(0f, 1f) } ?: 0.35f)
    ),
    shape: Shape = AppChipDefaults.DefaultShape,
    enabled: Boolean = true
) = AppAspectRatioChip(
    aspectRatio = aspectRatio,
    modifier = modifier,
    label = label,
    isSelected = isSelected,
    onClick = onClick,
    showWireframePreview = showWireframePreview,
    accentColor = accentColor,
    bgOpacity = bgOpacity,
    selectedBgOpacity = selectedBgOpacity,
    colors = colors,
    shape = shape,
    enabled = enabled
)

@Composable
fun AspectRatioWireframeIcon(
    aspectRatio: MediaAspectRatio,
    color: Color,
    modifier: Modifier = Modifier.size(15.dp, 12.dp)
) = GeometricWireframeIcon(
    ratio = aspectRatio.ratio,
    color = color,
    modifier = modifier
)

/**
 * Tool & crop chip wrapper delegating to [IconActionChip].
 */
@Composable
fun AppCropChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.Crop,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    accentColor: Color = AppChipDefaults.AccentGold,
    colors: AppChipColors = AppChipDefaults.colors(accentColor = accentColor),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = false,
    fontSize: TextUnit = 11.5.sp,
    enabled: Boolean = true
) = IconActionChip(
    label = label,
    modifier = modifier,
    icon = icon,
    isSelected = isSelected,
    onClick = onClick,
    accentColor = accentColor,
    colors = colors,
    shape = shape,
    showIndicatorDot = showIndicatorDot,
    fontSize = fontSize,
    enabled = enabled
)

@Composable
fun MediaCropChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.Crop,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    accentColor: Color = AppChipDefaults.AccentGold,
    colors: AppChipColors = AppChipDefaults.colors(accentColor = accentColor),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = false,
    fontSize: TextUnit = 11.5.sp,
    enabled: Boolean = true
) = AppCropChip(
    label = label,
    modifier = modifier,
    icon = icon,
    isSelected = isSelected,
    onClick = onClick,
    accentColor = accentColor,
    colors = colors,
    shape = shape,
    showIndicatorDot = showIndicatorDot,
    fontSize = fontSize,
    enabled = enabled
)

/**
 * Text, Font and Sticker wrapper delegating to [TypographyTagChip].
 */
@Composable
fun AppTextStickerChip(
    label: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: ImageVector? = null,
    fontFamily: FontFamily? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    accentColor: Color = AppChipDefaults.AccentGold,
    colors: AppChipColors = AppChipDefaults.colors(accentColor = accentColor),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = isSelected && emoji == null && icon == null,
    fontSize: TextUnit = 11.5.sp,
    enabled: Boolean = true
) = TypographyTagChip(
    label = label,
    modifier = modifier,
    emoji = emoji,
    icon = icon,
    fontFamily = fontFamily,
    isSelected = isSelected,
    onClick = onClick,
    accentColor = accentColor,
    colors = colors,
    shape = shape,
    showIndicatorDot = showIndicatorDot,
    fontSize = fontSize,
    enabled = enabled
)

@Composable
fun MediaTextStickerChip(
    label: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: ImageVector? = null,
    fontFamily: FontFamily? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    accentColor: Color = AppChipDefaults.AccentGold,
    colors: AppChipColors = AppChipDefaults.colors(accentColor = accentColor),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = isSelected && emoji == null && icon == null,
    fontSize: TextUnit = 11.5.sp,
    enabled: Boolean = true
) = AppTextStickerChip(
    label = label,
    modifier = modifier,
    emoji = emoji,
    icon = icon,
    fontFamily = fontFamily,
    isSelected = isSelected,
    onClick = onClick,
    accentColor = accentColor,
    colors = colors,
    shape = shape,
    showIndicatorDot = showIndicatorDot,
    fontSize = fontSize,
    enabled = enabled
)

@Composable
fun MediaStudioChip(
    label: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    accentColor: Color? = null,
    colors: AppChipColors = AppChipDefaults.colors(accentColor = accentColor),
    shape: Shape = AppChipDefaults.DefaultShape,
    showIndicatorDot: Boolean = false,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    fontFamily: FontFamily? = null,
    contentPadding: PaddingValues = if (onClick != null) AppChipDefaults.DefaultContentPadding else AppChipDefaults.BadgeContentPadding,
    enabled: Boolean = true
) = AppChip(
    label = label,
    modifier = modifier,
    isSelected = isSelected,
    onClick = onClick,
    icon = icon,
    leadingContent = leadingContent,
    trailingContent = trailingContent,
    accentColor = accentColor,
    colors = colors,
    shape = shape,
    showIndicatorDot = showIndicatorDot,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    contentPadding = contentPadding,
    enabled = enabled
)
