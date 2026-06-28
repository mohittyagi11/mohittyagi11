package com.azadishashn.app.ui.components.causal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A deliberately *different* register from the app's glow-glass: a restrained
 * analyst "exhibit" — a flat blueprint panel with a faint graph-paper grid, a
 * hairline accent edge, and tight padding. Houses the causal exhibits so the
 * analysis reads as a report, not a card.
 */
@Composable
fun BlueprintSurface(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val base = if (dark) Color(0xFF0A0F1E) else Color(0xFFFBFAF4)
    val grid = (if (dark) Color.White else Color.Black).copy(alpha = 0.05f)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(base)
            .drawBehind {
                val step = 22.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
            }
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .padding(16.dp),
        content = content,
    )
}
