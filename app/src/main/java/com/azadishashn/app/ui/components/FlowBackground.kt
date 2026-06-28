package com.azadishashn.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.azadishashn.app.ui.theme.Abyss
import com.azadishashn.app.ui.theme.DeepField
import com.azadishashn.app.ui.theme.IdeologyTheme
import kotlin.math.abs

/**
 * The cinematic field: a layered deep gradient, ideology-coloured glow blobs
 * drifting at two parallax speeds, and an edge vignette that focuses the centre.
 * GPU-cheap — one infinite transition, alpha-capped, no blur. Background-only.
 */
@Composable
fun FlowBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "flow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
    val seeds = IdeologyTheme.ALL.map { it.brand }

    Canvas(modifier) {
        // Layered base for depth.
        if (dark) {
            drawRect(Brush.verticalGradient(listOf(Abyss, DeepField, Abyss)))
        } else {
            drawRect(background)
        }
        val glowAlpha = if (dark) 0.13f else 0.10f
        seeds.forEachIndexed { i, color ->
            // Parallax: even blobs drift faster than odd ones.
            val speed = if (i % 2 == 0) 1f else 0.6f
            val radius = size.minDimension * (0.58f + 0.08f * (i % 2))
            val cx = size.width * (0.16f + 0.66f * triangle(phase * speed + i * 0.27f))
            val cy = size.height * (0.10f + 0.78f * triangle(phase * 0.65f * speed + i * 0.41f))
            val center = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        // Edge vignette.
        if (dark) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0xFF05060C).copy(alpha = 0.55f)),
                    center = c,
                    radius = size.maxDimension * 0.75f,
                ),
            )
        }
    }
}

/** Smooth 0..1..0 triangle wave from an unbounded input — keeps blobs in frame. */
private fun triangle(t: Float): Float {
    val frac = t - kotlin.math.floor(t)
    return 1f - abs(frac * 2f - 1f)
}
