package com.azadishashn.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.azadishashn.app.R

// Bundled OFL display face — Space Grotesk (geometric, edgy, premium) for
// headings; body/label keep the system sans for maximum legibility.
private val Display = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

private val base = Typography()

val AzadiTypography = base.copy(
    displayLarge = base.displayLarge.copy(
        fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
    ),
    displayMedium = base.displayMedium.copy(
        fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp,
    ),
    displaySmall = base.displaySmall.copy(
        fontFamily = Display, fontWeight = FontWeight.Bold,
    ),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = Display, fontWeight = FontWeight.Bold,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = Display, fontWeight = FontWeight.Bold,
    ),
    headlineSmall = base.headlineSmall.copy(
        fontFamily = Display, fontWeight = FontWeight.Bold,
    ),
    titleLarge = base.titleLarge.copy(
        fontFamily = Display, fontWeight = FontWeight.Medium,
    ),
)
