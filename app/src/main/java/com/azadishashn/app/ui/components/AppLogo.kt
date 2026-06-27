package com.azadishashn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.IdeologyTheme

/**
 * The brand mark (mirrors the launcher icon): one luminous ring whose colour
 * flows through the four ideology hues, converging on a bright core — the
 * "wheel of debate" on a navy field.
 */
@Composable
fun AppLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val navy = Color(0xFF141A40)
    // Order around the wheel matches the launcher sweep.
    val hues = listOf(
        IdeologyTheme.of("Capitalist").brand,
        IdeologyTheme.of("Supremo").brand,
        IdeologyTheme.of("Idealist").brand,
        IdeologyTheme.of("Showstopper").brand,
        IdeologyTheme.of("Capitalist").brand,
    )
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val center = Offset(cx, cy)
        drawRoundRect(color = navy, cornerRadius = CornerRadius(s * 0.28f, s * 0.28f))
        // colour-flow ring
        drawCircle(Brush.sweepGradient(hues, center), radius = s * 0.34f, center = center)
        // carve the hole
        drawCircle(navy, radius = s * 0.16f, center = center)
        // luminous core
        drawCircle(Color.White.copy(alpha = 0.92f), radius = s * 0.085f, center = center)
        drawCircle(Color.White.copy(alpha = 0.30f), radius = s * 0.14f, center = center)
    }
}
