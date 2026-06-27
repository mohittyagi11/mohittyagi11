package com.azadishashn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.IdeologyTheme

/**
 * The four-dot brand motif (mirrors the launcher icon) on a rounded navy field —
 * four overlapping translucent ideology circles blending at the centre.
 */
@Composable
fun AppLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val seeds = IdeologyTheme.ALL.map { it.brand }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val corner = s * 0.28f
        drawRoundRect(
            color = Color(0xFF141A40),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        )
        val r = s * 0.20f
        val cx = s / 2f
        val cy = s / 2f
        val offset = s * 0.16f
        val centers = listOf(
            Offset(cx, cy - offset),       // top
            Offset(cx + offset, cy),       // right
            Offset(cx, cy + offset),       // bottom
            Offset(cx - offset, cy),       // left
        )
        centers.forEachIndexed { i, c ->
            drawCircle(color = seeds.getOrElse(i) { Color.White }.copy(alpha = 0.9f), radius = r, center = c)
        }
        // Bright centre where they meet.
        drawCircle(color = Color.White.copy(alpha = 0.35f), radius = r * 0.5f, center = Offset(cx, cy))
    }
}
