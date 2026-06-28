package com.azadishashn.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * A one-shot spark burst from the centre — fired once on first composition for a
 * celebratory verdict. GPU-cheap, self-terminating; no randomness (deterministic
 * per index so it survives recomposition).
 */
@Composable
fun Particles(color: Color, modifier: Modifier = Modifier, count: Int = 20) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val prog = progress.value
        if (prog >= 1f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = size.minDimension * 0.52f
        val fade = (1f - prog).coerceIn(0f, 1f)
        repeat(count) { i ->
            val jitter = ((i * 1327) % 100) / 100f
            val angle = (i.toFloat() / count) * (2f * Math.PI.toFloat()) + jitter * 0.3f
            val dist = maxR * prog * (0.55f + 0.45f * jitter)
            val x = cx + dist * cos(angle)
            val y = cy + dist * sin(angle)
            drawCircle(
                color = color.copy(alpha = fade),
                radius = (2.6f * fade + 0.6f),
                center = Offset(x, y),
            )
        }
    }
}
