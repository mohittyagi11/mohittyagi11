package com.azadishashn.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/** Unit direction at [angleDeg] measured clockwise from straight up. */
private fun dir(angleDeg: Float) = Offset(sin(deg(angleDeg)), -cos(deg(angleDeg)))

/**
 * The brand mark in motion: a raised fist that clenches finger by finger and
 * releases, echoing the launcher icon. At the peak of each clench the real app
 * icon materialises, then recedes as the hand opens. The four fingertips carry
 * the SHASN ideology colours; the hand is drawn in the icon's gold-on-black.
 */
@Composable
fun FistLoader(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fist")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "clench",
    )
    // The app icon appears only in the last stretch of the clench.
    val iconAlpha = smooth((t - 0.55f) / 0.45f)
    val handAlpha = 1f - 0.82f * iconAlpha
    val tips = IdeologyTheme.ALL.map { it.brand }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().alpha(handAlpha)) { drawFist(t, tips) }
        AppLogo(size = size * 0.40f, modifier = Modifier.alpha(iconAlpha))
    }
}

private fun DrawScope.drawFist(t: Float, tips: List<Color>) {
    val w = size.width
    val h = size.height
    val u = minOf(w, h) / 100f
    val cx = w / 2f

    // Palm — near-black with a gold rim, like the icon's ground.
    val palmW = 46f * u
    val palmH = 32f * u
    val palmTop = h * 0.50f
    val palmTL = Offset(cx - palmW / 2f, palmTop)
    val palmSize = Size(palmW, palmH)
    val radius = CornerRadius(palmW * 0.34f, palmW * 0.34f)
    drawRoundRect(Color(0xFF141418), palmTL, palmSize, radius)
    drawRoundRect(GoldDim, palmTL, palmSize, radius, style = Stroke(width = 2.2f * u))

    val window = 0.42f
    val stagger = 0.13f
    val fingerW = 8.5f * u
    val l1 = 16f * u
    val l2 = 14f * u
    val baseXs = listOf(-15f, -5f, 5f, 15f).map { cx + it * u }
    val baseY = palmTop + 2f * u

    // Four fingers fold in sequence (index → pinky).
    baseXs.forEachIndexed { i, bx ->
        val curl = smooth((t - i * stagger) / window)
        val a1 = curl * 72f
        val a2 = a1 + 60f + curl * 64f
        val base = Offset(bx, baseY)
        val knuckle = base + dir(a1) * l1
        val tip = knuckle + dir(a2) * l2
        drawLine(GoldBright, base, knuckle, strokeWidth = fingerW, cap = StrokeCap.Round)
        drawLine(Gold, knuckle, tip, strokeWidth = fingerW * 0.92f, cap = StrokeCap.Round)
        // Knuckle bead + ideology-coloured fingertip.
        drawCircle(GoldDim, radius = fingerW * 0.34f, center = knuckle)
        drawCircle(tips.getOrElse(i) { Gold }, radius = fingerW * 0.52f, center = tip)
    }

    // Thumb wraps across last.
    val thumbCurl = smooth((t - 4f * stagger) / window)
    val anchor = Offset(cx - 20f * u, palmTop + 15f * u)
    val openTip = anchor + Offset(-17f * u, -9f * u)
    val closedTip = anchor + Offset(25f * u, -13f * u)
    val thumbTip = Offset(
        openTip.x + (closedTip.x - openTip.x) * thumbCurl,
        openTip.y + (closedTip.y - openTip.y) * thumbCurl,
    )
    drawLine(GoldBright, anchor, thumbTip, strokeWidth = 9f * u, cap = StrokeCap.Round)
    drawCircle(Gold, radius = 4.6f * u, center = thumbTip)
}
