package com.azadishashn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The brand mark (mirrors the launcher icon): one fine champagne-gold ring with
 * a single point at its centre, on a deep near-black navy field. Quiet luxury.
 */
@Composable
fun AppLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val navy = Color(0xFF101631)
    val gold = Brush.verticalGradient(listOf(Color(0xFFF1DCA0), Color(0xFFC29A53)))
    val goldSolid = Color(0xFFE6C982)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawRoundRect(color = navy, cornerRadius = CornerRadius(s * 0.28f, s * 0.28f))
        drawCircle(brush = gold, radius = s * 0.30f, center = center, style = Stroke(width = s * 0.034f))
        drawCircle(color = goldSolid, radius = s * 0.05f, center = center)
    }
}
