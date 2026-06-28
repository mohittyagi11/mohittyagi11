package com.azadishashn.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.Gold
import com.azadishashn.app.ui.theme.GoldBright
import com.azadishashn.app.ui.theme.GoldDim
import com.azadishashn.app.ui.theme.IdeologyTheme
import kotlin.math.cos
import kotlin.math.sin

/** Smoothstep — eased 0..1 for organic motion. */
private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun deg(d: Float) = (d * Math.PI / 180f).toFloat()

/**
 * The brand mark in motion: just the raised fist, its fingers rolling closed in
 * a smooth CIRCULAR curl (each finger sweeps through a widening arc into the
 * palm) and opening again, staggered index→pinky so it ripples. Drawn in the
 * icon's gold-on-black with the four ideology-coloured fingertips. No backdrop —
 * the hand is the whole loader.
 */
@Composable
fun FistLoader(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fist")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "clench",
    )
    val tips = IdeologyTheme.ALL.map { it.brand }
    Canvas(modifier.size(size)) { drawHand(t, tips) }
}

/** Draw a single finger as a smooth arc that curls more as [curl] → 1. */
private fun DrawScope.drawFinger(
    base: Offset,
    length: Float,
    width: Float,
    curl: Float,
    tip: Color,
) {
    val segments = 7
    val total = curl * 200f          // degrees of roll-up at full clench
    val segLen = length / segments
    var angle = 0f                   // 0° = straight up
    var p = base
    val path = Path().apply { moveTo(p.x, p.y) }
    for (i in 0 until segments) {
        angle += total / segments
        p = Offset(p.x + segLen * sin(deg(angle)), p.y - segLen * cos(deg(angle)))
        path.lineTo(p.x, p.y)
    }
    drawPath(path, GoldBright, style = Stroke(width = width, cap = StrokeCap.Round))
    // Knuckle bead at the base joint + ideology-coloured fingertip.
    drawCircle(GoldDim, radius = width * 0.34f, center = base)
    drawCircle(tip, radius = width * 0.52f, center = p)
}

private fun DrawScope.drawHand(t: Float, tips: List<Color>) {
    val w = size.width
    val h = size.height
    val u = minOf(w, h) / 100f
    val cx = w / 2f

    // Palm — near-black with a gold rim, like the icon's ground.
    val palmW = 48f * u
    val palmH = 34f * u
    val palmTop = h * 0.52f
    val palmTL = Offset(cx - palmW / 2f, palmTop)
    val radius = CornerRadius(palmW * 0.34f, palmW * 0.34f)
    drawRoundRect(Color(0xFF141418), palmTL, Size(palmW, palmH), radius)
    drawRoundRect(GoldDim, palmTL, Size(palmW, palmH), radius, style = Stroke(width = 2.2f * u))

    val window = 0.5f
    val stagger = 0.12f
    val fingerW = 9f * u
    val baseXs = listOf(-15.5f, -5.2f, 5.2f, 15.5f).map { cx + it * u }
    val lengths = listOf(28f, 32f, 31f, 26f).map { it * u } // index, middle, ring, pinky
    val baseY = palmTop + 3f * u

    baseXs.forEachIndexed { i, bx ->
        val curl = smooth((t - i * stagger) / window)
        drawFinger(Offset(bx, baseY), lengths[i], fingerW, curl, tips.getOrElse(i) { Gold })
    }

    // Thumb curls across the front last, also along an arc.
    val thumbCurl = smooth((t - 4f * stagger) / window)
    val anchor = Offset(cx - 21f * u, palmTop + 17f * u)
    val openTip = anchor + Offset(-16f * u, -10f * u)
    val midTip = anchor + Offset(4f * u, -20f * u)
    val closedTip = anchor + Offset(24f * u, -12f * u)
    // Quadratic-ish sweep open → mid → closed for a rounded path.
    val a = lerp(openTip, midTip, thumbCurl)
    val b = lerp(midTip, closedTip, thumbCurl)
    val thumbTip = lerp(a, b, thumbCurl)
    val thumb = Path().apply {
        moveTo(anchor.x, anchor.y)
        quadraticBezierTo(a.x, a.y, thumbTip.x, thumbTip.y)
    }
    drawPath(thumb, GoldBright, style = Stroke(width = 9.5f * u, cap = StrokeCap.Round))
    drawCircle(Gold, radius = 4.8f * u, center = thumbTip)
}

private fun lerp(a: Offset, b: Offset, f: Float) =
    Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
