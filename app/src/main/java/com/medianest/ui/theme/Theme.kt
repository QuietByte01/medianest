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

@Composable
fun MediaNestTheme(
    themeMode: String = "DARK", // "DARK", "LIGHT", "SYSTEM"
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        "SYSTEM" -> darkTheme
        else -> darkTheme
    }

    val colorScheme = if (isDark) PremiumDarkColorScheme else SmokeWhiteLightColorScheme

    CompositionLocalProvider(LocalDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

