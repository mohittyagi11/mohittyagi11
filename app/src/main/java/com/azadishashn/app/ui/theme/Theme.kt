package com.azadishashn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Azadi (freedom) palette — saffron, India green, navy chakra blue on cream.
private val Saffron = Color(0xFFFF9933)
private val IndiaGreen = Color(0xFF138808)
private val ChakraNavy = Color(0xFF1A237E)
private val Cream = Color(0xFFFFF8EE)
private val DeepInk = Color(0xFF1B1B17)

private val LightColors = lightColorScheme(
    primary = ChakraNavy,
    onPrimary = Color.White,
    secondary = Saffron,
    onSecondary = DeepInk,
    tertiary = IndiaGreen,
    onTertiary = Color.White,
    background = Cream,
    onBackground = DeepInk,
    surface = Color.White,
    onSurface = DeepInk,
)

private val DarkColors = darkColorScheme(
    primary = Saffron,
    onPrimary = DeepInk,
    secondary = Saffron,
    tertiary = IndiaGreen,
)

@Composable
fun AzadiShashnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
