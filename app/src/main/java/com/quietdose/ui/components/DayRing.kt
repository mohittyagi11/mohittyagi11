package com.quietdose.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.quietdose.ui.theme.Outline

/**
 * The day's completion as a calm ring. Animates as doses are confirmed — the
 * glanceable "moment" that anchors the home screen.
 */
@Composable
fun DayRing(
    taken: Int,
    due: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val target = if (due == 0) 1f else (taken.toFloat() / due).coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, tween(750), label = "dayRing")

    Canvas(modifier) {
        val stroke = size.minDimension * 0.11f
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        val arcSize = Size(d, d)

        drawArc(
            color = Outline.copy(alpha = 0.7f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (progress > 0f) {
            drawArc(
                color = lerp(tint, Color.White, 0.05f),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
