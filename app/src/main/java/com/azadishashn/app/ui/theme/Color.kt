package com.azadishashn.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand spine — derived from the launcher icon (four ideology dots on navy).
// ---------------------------------------------------------------------------
internal val Navy = Color(0xFF141A40)
internal val NavyDeep = Color(0xFF0E1230)
internal val Saffron = Color(0xFFFF9933)

// Ideology brand seeds (the launcher dot colours; see Ideology.kt for usage).
internal val IdeoGold = Color(0xFFFFCA28)
internal val IdeoRed = Color(0xFFFF6E6E)
internal val IdeoTeal = Color(0xFF2EE6C6)
internal val IdeoPurple = Color(0xFF9E7BFF)

// ---------------------------------------------------------------------------
// LIGHT — warm editorial "paper", not pure white, so colour pops gently.
// ---------------------------------------------------------------------------
private val PaperBg = Color(0xFFF7F4EC)
private val PaperSurface = Color(0xFFFFFFFF)
private val PaperContainer = Color(0xFFFDFBF5)
private val InkOnLight = Color(0xFF1B1B17)

val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF0A1033),
    secondary = Color(0xFFB5651D),          // burnt saffron — readable on paper
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2C2),
    onSecondaryContainer = Color(0xFF3A2200),
    tertiary = Color(0xFF1B8A6B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFF6EE),
    onTertiaryContainer = Color(0xFF05372B),
    background = PaperBg,
    onBackground = InkOnLight,
    surface = PaperSurface,
    onSurface = InkOnLight,
    surfaceVariant = Color(0xFFEDE7DA),
    onSurfaceVariant = Color(0xFF504A3D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF9F2),
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = Color(0xFFF1ECE0),
    surfaceContainerHighest = Color(0xFFEBE5D7),
    outline = Color(0xFFB8B0A0),
    outlineVariant = Color(0xFFE0D9C9),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF000000),
)

// ---------------------------------------------------------------------------
// DARK — deep navy-charcoal canvas; ideology colours read as neon accents.
// ---------------------------------------------------------------------------
private val InkBg = Color(0xFF101321)
private val InkSurface = Color(0xFF161A2C)
private val InkContainer = Color(0xFF1C2138)

val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF111A4D),
    primaryContainer = Color(0xFF2A3470),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Saffron,
    onSecondary = Color(0xFF3A2200),
    secondaryContainer = Color(0xFF5A3A12),
    onSecondaryContainer = Color(0xFFFFE2C2),
    tertiary = IdeoTeal,
    onTertiary = Color(0xFF00382B),
    tertiaryContainer = Color(0xFF0A5343),
    onTertiaryContainer = Color(0xFFCFF6EE),
    background = InkBg,
    onBackground = Color(0xFFE8E6F0),
    surface = InkSurface,
    onSurface = Color(0xFFE8E6F0),
    surfaceVariant = Color(0xFF2A2F44),
    onSurfaceVariant = Color(0xFFC3C2D0),
    surfaceContainerLowest = Color(0xFF0C0F1A),
    surfaceContainerLow = Color(0xFF141828),
    surfaceContainer = InkContainer,
    surfaceContainerHigh = Color(0xFF232843),
    surfaceContainerHighest = Color(0xFF2C3252),
    outline = Color(0xFF565B73),
    outlineVariant = Color(0xFF2E3349),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
)
