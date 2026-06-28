package com.azadishashn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The brand mark (mirrors the launcher icon): four ascending bars — the four
 * ideologies as a stark brushed-chrome chart on a near-black field.
 */
@Composable
fun AppLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val field = Color(0xFF0E1320)
    val chrome = Brush.verticalGradient(listOf(Color(0xFFEDF2F8), Color(0xFFA6B2C0), Color(0xFF586574)))
    val edge = Color(0xFF2C333F)
    // bars as fractions of the 108 viewport: (xLeft, top) with width 10, base 80.
    val bars = listOf(28f to 58f, 42f to 46f, 56f to 38f, 70f to 50f)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val u = s / 108f
        drawRoundRect(color = field, cornerRadius = CornerRadius(s * 0.28f, s * 0.28f))
        // baseline
        drawLine(Color(0xFF7E8A99), Offset(26f * u, 80f * u), Offset(82f * u, 80f * u), strokeWidth = 1.4f * u)
        bars.forEach { (x, top) ->
            val left = x * u
            val t = top * u
            val w = 10f * u
            val h = (80f - top) * u
            drawRect(brush = chrome, topLeft = Offset(left, t), size = Size(w, h))
            drawRect(color = edge, topLeft = Offset(left, t), size = Size(w, h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f * u))
        }
    }
}
