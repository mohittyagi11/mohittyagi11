package com.azadishashn.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.LaserAmber
import com.azadishashn.app.ui.theme.LaserBlue
import com.azadishashn.app.ui.theme.LaserGreen
import com.azadishashn.app.ui.theme.LaserRed
import com.azadishashn.app.ui.theme.OrbCore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The diffraction-glass loader: a central light source that radiates organic,
 * ideology-hued contour rings outward — the app's whole visual language in
 * motion. Rings are born at the core, bloom outward and fade like light
 * diffracting through cut glass; the warm-white core breathes. GPU-cheap.
 */
@Composable
fun DiffractionLoader(modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val t = rememberInfiniteTransition(label = "diffLoader")
    val emit by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "emit",
    )
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val pulse by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxR = this.size.minDimension * 0.5f
        val hues = listOf(LaserBlue, LaserGreen, LaserRed, LaserAmber, LaserBlue)
        val rings = hues.size

        // soft bloom behind the core
        drawCircle(
            Brush.radialGradient(
                listOf(LaserBlue.copy(alpha = 0.14f), Color.Transparent),
                center = c, radius = maxR,
            ),
            radius = maxR, center = c,
        )

        // emanating organic contour rings
        for (k in 0 until rings) {
            val local = (emit + k.toFloat() / rings) % 1f
            val eased = local * (2f - local)                     // ease-out expansion
            val r = maxR * (0.1f + 0.92f * eased)
            val fadeIn = (local / 0.1f).coerceAtMost(1f)
            val fade = 1f - local
            val a = fadeIn * fade * fade * 0.9f
            if (a <= 0.01f) continue
            val col = hues[k]
            // per-ring organic shape (stable harmonics), rotating slowly
            val a2 = 0.10f; val a3 = 0.06f
            val p2 = k * 1.7f; val p3 = k * 2.9f
            withTransform({ rotate(spin + k * 34f, c) }) {
                val path = Path()
                val steps = 54
                for (i in 0..steps) {
                    val ang = i / steps.toFloat() * (2f * PI.toFloat())
                    val wob = 1f + a2 * sin(2f * ang + p2) + a3 * sin(3f * ang + p3)
                    val px = c.x + r * wob * cos(ang)
                    val py = c.y + r * wob * 0.92f * sin(ang)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                drawPath(path, col.copy(alpha = a * 0.35f), style = Stroke(3.2.dp.toPx()))
                drawPath(path, col.copy(alpha = a), style = Stroke(1.3.dp.toPx()))
            }
        }

        // rotating spectral tick — a single bright facet catching the light
        withTransform({ rotate(-spin * 1.6f, c) }) {
            val rr = maxR * 0.34f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.5f), LaserBlue.copy(alpha = 0.2f), Color.Transparent),
                    center = Offset(c.x + rr, c.y), radius = maxR * 0.12f,
                ),
                radius = maxR * 0.12f, center = Offset(c.x + rr, c.y),
            )
        }

        // breathing warm-white core
        val coreR = maxR * (0.15f + 0.03f * sin(pulse * PI.toFloat()))
        drawCircle(
            Brush.radialGradient(
                listOf(OrbCore.copy(alpha = 0.95f), OrbCore.copy(alpha = 0.3f), Color.Transparent),
                center = c, radius = coreR,
            ),
            radius = coreR, center = c,
        )
    }
}
