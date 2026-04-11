package com.gourav.investnest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = FreshGreen,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurfaceRaised,
    onPrimaryContainer = LightGreen,
    secondary = BrightTeal,
    onSecondary = Cloud,
    tertiary = LightGreen,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = Cloud,
    surface = DarkSurface,
    onSurface = Cloud,
    surfaceVariant = DarkSurfaceTint,
    onSurfaceVariant = ColorTokens.DarkSurfaceVariantText,
    outline = OutlineDark,
)

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Cloud,
    primaryContainer = LightGreen,
    onPrimaryContainer = DeepTeal,
    secondary = BrightTeal,
    onSecondary = Cloud,
    tertiary = FreshGreen,
    onTertiary = DeepTeal,
    background = LightBackground,
    onBackground = Ink,
    surface = LightSurface,
    onSurface = Ink,
    surfaceVariant = LightSurfaceTint,
    onSurfaceVariant = ColorTokens.LightSurfaceVariantText,
    outline = OutlineLight,
)

private object ColorTokens {
    val LightSurfaceVariantText = Color(0xFF26474C)
    val DarkSurfaceVariantText = Color(0xFFBEDFD1)
}

@Composable
fun InvestNestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
