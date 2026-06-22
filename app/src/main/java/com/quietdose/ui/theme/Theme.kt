package com.quietdose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Dose is dark-first. We define a full dark scheme and a soft light fallback so
 * the app is coherent in either system mode, but the design language is tuned
 * for the dark.
 */
private val DoseDarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = AccentText,
    primaryContainer = AccentSoft,
    onPrimaryContainer = TextHigh,
    background = Ink,
    onBackground = TextHigh,
    surface = Surface1,
    onSurface = TextHigh,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    outline = Outline,
    outlineVariant = Outline,
    tertiary = Done,
)

private val DoseLightColors = lightColorScheme(
    primary = Accent,
    onPrimary = AccentText,
)

@Composable
fun DoseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DoseDarkColors else DoseLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = DoseTypography,
        content = content,
    )
}
