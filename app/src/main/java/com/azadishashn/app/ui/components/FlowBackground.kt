package com.azadishashn.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.azadishashn.app.ui.theme.IdeologyTheme
import kotlin.math.abs

/**
 * The "edgy, dynamic flow": a single Canvas painting a handful of soft,
 * slowly-drifting radial blobs in the four ideology brand colours at very low
 * alpha over the theme background. GPU-cheap — one infinite transition, no
 * blur/shadow, ≤4 circles.
 */
@Composable
fun FlowBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "flow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )
    val background = MaterialTheme.colorScheme.background
    val seeds = IdeologyTheme.ALL.map { it.brand }

    Canvas(modifier) {
        drawRect(background)
        val radius = size.minDimension * 0.62f
        seeds.forEachIndexed { i, color ->
            val cx = size.width * (0.18f + 0.64f * triangle(phase + i * 0.25f))
            val cy = size.height * (0.12f + 0.74f * triangle(phase * 0.7f + i * 0.4f))
            val center = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.11f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

/** Smooth 0..1..0 triangle wave from an unbounded input — keeps blobs in frame. */
private fun triangle(t: Float): Float {
    val frac = t - kotlin.math.floor(t)
    return 1f - abs(frac * 2f - 1f)
}
