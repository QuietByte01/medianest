package com.medianest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalDarkTheme = staticCompositionLocalOf { true }
val LocalIsGlossy = staticCompositionLocalOf { true }

private val PremiumDarkColorScheme = darkColorScheme(
    primary = AccentViolet,
    secondary = AccentVioletLight,
    tertiary = AccentVioletDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = GlassBorderDark,
    outlineVariant = Color(0x1AFFFFFF)
)

private val SmokeWhiteLightColorScheme = lightColorScheme(
    primary = AccentViolet,
    secondary = AccentVioletLight,
    tertiary = AccentVioletDark,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = GlassBorderLight,
    outlineVariant = LightDivider
)

private val StandardDarkColorScheme = darkColorScheme()
private val StandardLightColorScheme = lightColorScheme()

@Composable
fun MediaNestTheme(
    themeMode: String = "DARK", // "DARK", "LIGHT", "STANDARD_DARK", "STANDARD_LIGHT", "SYSTEM"
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isGlossy = when (themeMode.uppercase()) {
        "DARK", "LIGHT" -> true
        "STANDARD_DARK", "STANDARD_LIGHT" -> false
        "SYSTEM" -> true // Default system to glossy? Or depends on preference? 
        else -> true
    }

    val isDark = when (themeMode.uppercase()) {
        "LIGHT", "STANDARD_LIGHT" -> false
        "DARK", "STANDARD_DARK" -> true
        "SYSTEM" -> darkTheme
        else -> darkTheme
    }

    val colorScheme = when (themeMode.uppercase()) {
        "DARK" -> PremiumDarkColorScheme
        "LIGHT" -> SmokeWhiteLightColorScheme
        "STANDARD_DARK" -> StandardDarkColorScheme
        "STANDARD_LIGHT" -> StandardLightColorScheme
        "SYSTEM" -> if (darkTheme) PremiumDarkColorScheme else SmokeWhiteLightColorScheme
        else -> if (isDark) PremiumDarkColorScheme else SmokeWhiteLightColorScheme
    }

    CompositionLocalProvider(
        LocalDarkTheme provides isDark,
        LocalIsGlossy provides isGlossy
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

