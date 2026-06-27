package com.azadishashn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The brand mark (mirrors the launcher icon): a fine champagne-gold ring framing
 * a majestic crown (Shashn / rule) tipped with peg finials, on deep navy.
 */
@Composable
fun AppLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val navy = Color(0xFF101631)
    val gold = Brush.verticalGradient(listOf(Color(0xFFF4E2AC), Color(0xFFC9A156)))
    val ringGold = Brush.verticalGradient(listOf(Color(0xFFF1DCA0), Color(0xFFC29A53)))
    val peg = Color(0xFFF6E7B6)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val ring = s * 0.30f
        val u = ring / 28f // map 108-viewport units → canvas
        fun p(dx: Float, dy: Float) = Offset(cx + dx * u, cy + dy * u)

        drawRoundRect(color = navy, cornerRadius = CornerRadius(s * 0.28f, s * 0.28f))
        drawCircle(brush = ringGold, radius = ring, center = Offset(cx, cy), style = Stroke(width = s * 0.034f))

        val crown = Path().apply {
            moveTo(p(-12f, 8f).x, p(-12f, 8f).y)
            lineTo(p(-12f, -8f).x, p(-12f, -8f).y)
            lineTo(p(-6f, 0f).x, p(-6f, 0f).y)
            lineTo(p(0f, -12f).x, p(0f, -12f).y)
            lineTo(p(6f, 0f).x, p(6f, 0f).y)
            lineTo(p(12f, -8f).x, p(12f, -8f).y)
            lineTo(p(12f, 8f).x, p(12f, 8f).y)
            close()
        }
        drawPath(crown, brush = gold)
        drawCircle(peg, radius = 2f * u, center = p(-12f, -8f))
        drawCircle(peg, radius = 2.4f * u, center = p(0f, -12f))
        drawCircle(peg, radius = 2f * u, center = p(12f, -8f))
    }
}
