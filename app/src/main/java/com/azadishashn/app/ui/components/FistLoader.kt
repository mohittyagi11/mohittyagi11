package com.azadishashn.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import com.azadishashn.app.ui.theme.Gold
import com.azadishashn.app.ui.theme.GoldBright
import com.azadishashn.app.ui.theme.GoldDim
import com.azadishashn.app.ui.theme.IdeologyTheme
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (2.0 * Math.PI).toFloat()

private fun deg(d: Float) = (d * Math.PI / 180f).toFloat()

/**
 * The loader as a cute, bouncy "Fall Guys"-style hand: a chunky rounded palm
 * that squash-bounces while four long, floppy jester fingers wiggle in a wave,
 * each tipped with a big colour bead (the four SHASN ideologies). Playful, not a
 * stern fist. The hand is the whole loader.
 */
@Composable
fun FistLoader(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "hand")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "wave",
    )
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce",
    )
    val tips = IdeologyTheme.ALL.map { it.brand }
    Canvas(modifier.size(size)) { drawHand(phase, bounce, tips) }
}

/** A long, floppy finger drawn as a smooth arc with a cute bead at the tip. */
private fun DrawScope.drawFinger(
    base: Offset,
    fanAngle: Float,
    length: Float,
    width: Float,
    curl: Float,
    bead: Color,
    beadR: Float,
) {
    val segments = 10
    val total = 26f + 52f * curl          // gentle end-hook; the finger stays long
    val segLen = length / segments
    var angle = fanAngle
    var p = base
    val path = Path().apply { moveTo(p.x, p.y) }
    for (i in 0 until segments) {
        // curl concentrates toward the tip so the base stays straight and only
        // the end hooks — a floppy, jaunty wiggle.
        angle += total / segments * (0.5f + i.toFloat() / segments)
        p = Offset(p.x + segLen * sin(deg(angle)), p.y - segLen * cos(deg(angle)))
        path.lineTo(p.x, p.y)
    }
    drawPath(path, GoldBright, style = Stroke(width = width, cap = StrokeCap.Round))
    // Big cute bead + a little specular highlight.
    drawCircle(bead, radius = beadR, center = p)
    drawCircle(Color.White.copy(alpha = 0.55f), radius = beadR * 0.32f, center = Offset(p.x - beadR * 0.3f, p.y - beadR * 0.35f))
}

private fun DrawScope.drawHand(phase: Float, bounce: Float, tips: List<Color>) {
    val w = size.width
    val h = size.height
    val u = minOf(w, h) / 100f
    val cx = w / 2f
    val cy = h * 0.60f

    // Squash-and-stretch bounce: lift up, and squash a touch at the bottom.
    val lift = -7f * u * bounce
    val sx = 1f + 0.05f * (1f - bounce)
    val sy = 1f - 0.05f * (1f - bounce)

    translate(left = 0f, top = lift) {
        scale(sx, sy, pivot = Offset(cx, cy + 22f * u)) {
            // Chunky rounded palm — dark with a soft gold rim.
            val palmW = 52f * u
            val palmH = 48f * u
            val palmTL = Offset(cx - palmW / 2f, cy - palmH * 0.30f)
            drawOval(Color(0xFF15161A), palmTL, Size(palmW, palmH))
            drawOval(GoldDim, palmTL, Size(palmW, palmH), style = Stroke(width = 2.4f * u))
            // Glossy highlight for cuteness.
            drawOval(
                Color.White.copy(alpha = 0.10f),
                topLeft = Offset(cx - palmW * 0.28f, cy - palmH * 0.18f),
                size = Size(palmW * 0.42f, palmH * 0.30f),
            )

            // Four long floppy fingers fanning up from the top of the palm, each
            // wiggling on its own phase so they ripple like a wave.
            val baseY = cy - palmH * 0.18f
            val fingers = listOf(-15f to -26f, -5.5f to -10f, 5.5f to 10f, 15f to 26f)
            fingers.forEachIndexed { i, (dx, fan) ->
                val curl = 0.5f + 0.5f * sin(phase + i * 0.9f)
                drawFinger(
                    base = Offset(cx + dx * u, baseY),
                    fanAngle = fan,
                    length = 40f * u,
                    width = 9f * u,
                    curl = curl,
                    bead = tips.getOrElse(i) { Gold },
                    beadR = 6.6f * u,
                )
            }

            // A short stubby thumb nub on the left for character.
            val thumbCurl = 0.5f + 0.5f * sin(phase + 3.6f)
            drawFinger(
                base = Offset(cx - palmW * 0.46f, cy + 6f * u),
                fanAngle = -78f + 10f * thumbCurl,
                length = 17f * u,
                width = 10f * u,
                curl = thumbCurl,
                bead = Gold,
                beadR = 5.2f * u,
            )
        }
    }
}
