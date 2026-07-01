package com.azadishashn.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import com.azadishashn.app.ui.theme.DiffAbyss
import com.azadishashn.app.ui.theme.DiffField
import com.azadishashn.app.ui.theme.DiffMid
import com.azadishashn.app.ui.theme.LaserAmber
import com.azadishashn.app.ui.theme.LaserBlue
import com.azadishashn.app.ui.theme.LaserGreen
import com.azadishashn.app.ui.theme.LaserRed
import com.azadishashn.app.ui.theme.OrbCore
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Diffraction glass — the shared vocabulary for the dark theme:
//   • the background "light field" (orb + laser streak + glowing ideology
//     contours + directional relief + vignette), drawn in screen space, and
//   • black glossy glass faces (top-lit sheen, hairline, chromatic edge) that
//     let the contour illustration read through as the only vibrant highlight.
// No blur (API-safe); depth and glow are built from gradients + stacked strokes.
// ---------------------------------------------------------------------------

/** The four SHASN ideology hues, laser-bright, in streak order. */
val LaserSpectrum = listOf(LaserRed, LaserAmber, LaserGreen, LaserBlue)

/** One slow shared clock (~16s) driving the orb bob and streak shimmer. */
@Composable
fun rememberDiffractionPhase(): Float {
    val t = rememberInfiniteTransition(label = "diffraction")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )
    return p
}

// --- Black glossy glass faces (dark mode) --------------------------------------

/** Deep near-black translucent body, so the fixed light field reads through. */
fun glassBase(dark: Boolean): Color =
    if (dark) Color(0xFF05070F).copy(alpha = 0.50f) else Color.White.copy(alpha = 0.74f)

/** Top-lit convex sheen + a bottom inner shade for thickness. */
fun glassSheen(dark: Boolean): Brush =
    if (dark) {
        Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = 0.17f),
            0.13f to Color.White.copy(alpha = 0.03f),
            0.34f to Color.Transparent,
            0.86f to Color.Transparent,
            1.00f to Color.Black.copy(alpha = 0.30f),
        )
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent))
    }

/** Hairline edge — bright top lip catching the light, fading down. */
fun glassEdge(dark: Boolean): Brush =
    Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.50f else 0.90f),
            Color.White.copy(alpha = if (dark) 0.05f else 0.20f),
        ),
    )

/**
 * Chromatic split fringing the vertical edges (red left, blue right) — the
 * "cut glass" light-split. Call inside a drawWithContent AFTER drawContent().
 */
fun DrawScope.drawChromaticEdge(intensity: Float = 1f) {
    val w = 6.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(LaserRed.copy(alpha = 0.42f * intensity), Color.Transparent),
            startX = 0f, endX = w,
        ),
        topLeft = Offset.Zero,
        size = Size(w, size.height),
    )
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, LaserBlue.copy(alpha = 0.46f * intensity)),
            startX = size.width - w, endX = size.width,
        ),
        topLeft = Offset(size.width - w, 0f),
        size = Size(w, size.height),
    )
}

// --- The background light field ------------------------------------------------

private data class Peak(
    val cx: Float, val cy: Float, val rot: Float,
    val color: Color, val index: Color?, val rings: Int, val spacing: Float,
)

/** Cheap deterministic pseudo-noise in [-1,1] from two ints. */
private fun jitter(a: Int, b: Int): Float {
    val s = sin(a * 12.9898f + b * 78.233f) * 43758.547f
    return (s - kotlin.math.floor(s)) * 2f - 1f
}

private const val TWO_PI = 6.2831855f

// A jittered grid of ridge centres covering the whole canvas — enough overlap
// that the organic contours weave a continuous allover topographic texture with
// no voids or lonely "radar" blobs. Portrait-tall, so more rows than columns.
private val BASE_HUES = listOf(LaserBlue, LaserGreen, LaserRed, LaserBlue, LaserGreen)
private val PEAKS: List<Peak> = run {
    val cols = 4; val rows = 9
    buildList {
        for (gy in 0 until rows) for (gx in 0 until cols) {
            val s = gy * cols + gx
            // brick-offset every other row + jitter, so rows don't line up
            val stagger = if (gy % 2 == 0) 0f else 0.5f / cols
            val cx = ((gx + 0.5f) / cols + stagger + 0.16f / cols * jitter(s, 7)).coerceIn(-0.05f, 1.05f)
            val cy = ((gy + 0.5f) / rows + 0.16f / rows * jitter(s, 8))
            val hue = BASE_HUES[(gx + gy) % BASE_HUES.size]
            val index = if (jitter(s, 9) > 0.55f) LaserAmber else null
            add(
                Peak(
                    cx = cx, cy = cy,
                    rot = 34f * jitter(s, 10),
                    color = hue, index = index,
                    rings = 7 + (s % 3),
                    spacing = 0.017f + 0.004f * jitter(s, 11),
                ),
            )
        }
    }
}

/**
 * The STATIC half of the field — deep base, glowing organic contours, vignette.
 * Reads no animation state, so on its own Canvas it's drawn once, not per frame.
 */
fun DrawScope.drawDiffractionBase() {
    val unit = size.minDimension

    // 1) Deep base.
    drawRect(Brush.verticalGradient(listOf(DiffField, DiffMid, DiffAbyss)))

    // 2) Glowing ideology contours. Each peak has an organic blob outline (a few
    //    harmonics) shared by all its nested rings, so the rings read as a
    //    topographic hill rather than a radar target. Small + many + dim → an
    //    allover woven texture. Each ring: a faint wide glow under a thin core.
    val glowW = 2.4.dp.toPx()
    val coreW = 0.85.dp.toPx()
    val idxW = 1.25.dp.toPx()
    PEAKS.forEachIndexed { pi, pk ->
        val center = Offset(size.width * pk.cx, size.height * pk.cy)
        val ratio = 0.82f + 0.08f * jitter(pi, 99)
        // per-peak organic shape harmonics (same for every ring of this peak)
        val a2 = 0.14f * jitter(pi, 1); val p2 = jitter(pi, 2) * TWO_PI
        val a3 = 0.09f * jitter(pi, 3); val p3 = jitter(pi, 4) * TWO_PI
        val a5 = 0.05f * jitter(pi, 5); val p5 = jitter(pi, 6) * TWO_PI
        withTransform({ rotate(pk.rot, center) }) {
            for (r in 1..pk.rings) {
                val f = r / pk.rings.toFloat()
                val base = unit * pk.spacing * r
                val path = Path()
                val steps = 46
                for (i in 0..steps) {
                    val ang = i / steps.toFloat() * TWO_PI
                    val wob = 1f + a2 * sin(2f * ang + p2) + a3 * sin(3f * ang + p3) + a5 * sin(5f * ang + p5)
                    val px = center.x + base * wob * cos(ang)
                    val py = center.y + base * wob * ratio * sin(ang)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                val isIndex = pk.index != null && r == (pk.rings + 1) / 2
                val col = if (isIndex) pk.index!! else pk.color
                val a = 0.2f * (1f - 0.26f * f)
                drawPath(path, col.copy(alpha = a * 0.32f), style = Stroke(glowW))
                drawPath(path, col.copy(alpha = a), style = Stroke(if (isIndex) idxW else coreW))
            }
        }
    }

    // 6) Vignette — edges recede into space (also fades the contours gracefully).
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Transparent, DiffAbyss.copy(alpha = 0.86f)),
            center = Offset(size.width * 0.5f, size.height * 0.2f),
            radius = size.maxDimension * 0.82f,
        ),
    )
}

/**
 * The ANIMATED half — the light orb (chromatic bloom + warm-white core, gentle
 * [phase] bob) and the diagonal laser streak. Drawn on a thin overlay Canvas
 * above the static base, so only these few cheap draws repaint each frame.
 */
fun DrawScope.drawDiffractionLight(phase: Float) {
    val unit = size.minDimension

    // 3) Orb position — high centre, gentle vertical bob.
    val orb = Offset(size.width * 0.5f, size.height * (0.19f + 0.012f * sin(phase * 6.2832f)))

    // Directional relief: a soft light lift around the orb (terrain catching light).
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFB6D0FF).copy(alpha = 0.10f), Color.Transparent),
            center = orb, radius = unit * 0.85f,
        ),
        radius = unit * 0.85f, center = orb,
    )

    // 4) Orb chromatic bloom (blue/red/green/amber) then a warm-white core.
    drawCircle(Brush.radialGradient(listOf(LaserBlue.copy(alpha = 0.42f), Color.Transparent), orb, unit * 0.52f), unit * 0.52f, orb)
    drawCircle(
        Brush.radialGradient(listOf(LaserRed.copy(alpha = 0.32f), Color.Transparent), orb + Offset(unit * 0.07f, unit * 0.03f), unit * 0.40f),
        unit * 0.40f, orb + Offset(unit * 0.07f, unit * 0.03f),
    )
    drawCircle(
        Brush.radialGradient(listOf(LaserGreen.copy(alpha = 0.24f), Color.Transparent), orb + Offset(-unit * 0.05f, unit * 0.06f), unit * 0.36f),
        unit * 0.36f, orb + Offset(-unit * 0.05f, unit * 0.06f),
    )
    drawCircle(
        Brush.radialGradient(
            listOf(OrbCore.copy(alpha = 0.85f), OrbCore.copy(alpha = 0.22f), Color.Transparent),
            orb, unit * 0.12f,
        ),
        unit * 0.12f, orb,
    )

    // 5) Laser streak — a thin diagonal beam, ideology-split, faint white core.
    //    Kept slim and low-alpha so it's an accent, not a banner.
    val bandBase = size.height * 0.13f
    withTransform({ rotate(-19f, Offset(size.width * 0.5f, bandBase)) }) {
        val left = -size.width * 0.3f
        val bandW = size.width * 1.6f
        // stacked glow bands (no blur available → fake it with decreasing alpha)
        listOf(22f to 0.08f, 12f to 0.14f, 5f to 0.24f).forEach { (h, a) ->
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, LaserRed, LaserAmber, LaserGreen, LaserBlue, Color.Transparent),
                    startX = left, endX = left + bandW,
                ),
                topLeft = Offset(left, bandBase - h.dp.toPx() / 2f),
                size = Size(bandW, h.dp.toPx()),
                alpha = a,
            )
        }
        // faint bright core line
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.6f), Color.Transparent),
                startX = left + bandW * 0.1f, endX = left + bandW * 0.9f,
            ),
            topLeft = Offset(left, bandBase - 0.7.dp.toPx()),
            size = Size(bandW, 1.4.dp.toPx()),
        )
    }
}
