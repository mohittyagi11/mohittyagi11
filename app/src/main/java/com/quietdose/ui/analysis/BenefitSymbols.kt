package com.quietdose.ui.analysis

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small **vocabulary** of benefit symbols and a **derivation** that picks one for a
 * given benefit word. The orbs around the product glyph aren't plain dots: each is a
 * tiny drawn symbol that *depicts* its benefit (a droplet for hydration, a sun for
 * brightening, a shield for barrier…). We don't hardcode a symbol per product — we
 * derive it from the benefit text the brain produced, choosing from this fixed set of
 * primitives. The same derivation legends the benefit chips, so chip ↔ orb agree.
 */
enum class BenefitSymbol { DROP, GLOW, SPARKLE, LEAF, SHIELD, TARGET, FIRM, GRAIN, SUN, STAR }

/** Derive a symbol from a benefit phrase by meaning — keyword buckets over the word. */
fun benefitSymbolFor(label: String): BenefitSymbol {
    val s = label.lowercase()
    fun has(vararg w: String) = w.any { s.contains(it) }
    return when {
        has("hydrat", "moist", "dewy", "water", "plump") -> BenefitSymbol.DROP
        has("spf", "uv", "sun protect", "sunscreen") -> BenefitSymbol.SHIELD
        has("bright", "glow", "radian", "even tone", "luminous", "dull") -> BenefitSymbol.GLOW
        has("anti-ag", "anti ag", "ageing", "aging", "wrinkle", "fine line", "youth") -> BenefitSymbol.SPARKLE
        has("sooth", "calm", "cica", "centella", "redness", "sensitiv", "irritat") -> BenefitSymbol.LEAF
        has("barrier", "repair", "protect", "strengthen", "ceramide") -> BenefitSymbol.SHIELD
        has("acne", "blemish", "spot", "pore", "blackhead", "breakout", "pimple") -> BenefitSymbol.TARGET
        has("firm", "lift", "elastic", "bounce", "tighten", "sag") -> BenefitSymbol.FIRM
        has("exfoliat", "peel", "smooth", "texture", "resurfac", "renew") -> BenefitSymbol.GRAIN
        has("nourish", "vitamin", "antioxidant", "energ") -> BenefitSymbol.SUN
        else -> BenefitSymbol.STAR
    }
}

/**
 * Draw [symbol] centred at [center] within radius [r], in [color]. Kept legible at the
 * small orb size with simple filled/stroked primitives. Pure DrawScope; no allocations
 * beyond a Path or two.
 */
fun DrawScope.drawBenefitSymbol(symbol: BenefitSymbol, center: Offset, r: Float, color: Color) {
    val cx = center.x
    val cy = center.y
    val stroke = (r * 0.30f).coerceAtLeast(1.2f)
    when (symbol) {
        BenefitSymbol.DROP -> {
            val p = Path().apply {
                moveTo(cx, cy - r)
                cubicTo(cx + r * 0.95f, cy - r * 0.05f, cx + r * 0.7f, cy + r, cx, cy + r)
                cubicTo(cx - r * 0.7f, cy + r, cx - r * 0.95f, cy - r * 0.05f, cx, cy - r)
                close()
            }
            drawPath(p, color)
        }
        BenefitSymbol.GLOW, BenefitSymbol.SUN -> {
            drawCircle(color, radius = r * 0.5f, center = center)
            val rays = if (symbol == BenefitSymbol.SUN) 8 else 6
            for (i in 0 until rays) {
                val a = Math.toRadians(i * 360.0 / rays)
                val ix = cx + (r * 0.68f * cos(a)).toFloat()
                val iy = cy + (r * 0.68f * sin(a)).toFloat()
                val ox = cx + (r * 1.0f * cos(a)).toFloat()
                val oy = cy + (r * 1.0f * sin(a)).toFloat()
                drawLine(color, Offset(ix, iy), Offset(ox, oy), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
            }
        }
        BenefitSymbol.SPARKLE -> {
            // a four-point sparkle: a tall pinch-waist diamond + a shorter one across
            fun star(scaleX: Float, scaleY: Float) = Path().apply {
                moveTo(cx, cy - r * scaleY)
                quadraticBezierTo(cx, cy, cx + r * scaleX, cy)
                quadraticBezierTo(cx, cy, cx, cy + r * scaleY)
                quadraticBezierTo(cx, cy, cx - r * scaleX, cy)
                quadraticBezierTo(cx, cy, cx, cy - r * scaleY)
                close()
            }
            drawPath(star(0.42f, 1.0f), color)
            drawPath(star(1.0f, 0.42f), color)
        }
        BenefitSymbol.LEAF -> {
            val p = Path().apply {
                moveTo(cx, cy - r)
                quadraticBezierTo(cx + r, cy - r * 0.1f, cx, cy + r)
                quadraticBezierTo(cx - r, cy - r * 0.1f, cx, cy - r)
                close()
            }
            drawPath(p, color)
            drawLine(
                color.copy(alpha = 0.45f),
                Offset(cx, cy - r * 0.7f), Offset(cx, cy + r * 0.7f),
                strokeWidth = stroke * 0.5f, cap = StrokeCap.Round,
            )
        }
        BenefitSymbol.SHIELD -> {
            val p = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.82f, cy - r * 0.55f)
                lineTo(cx + r * 0.82f, cy + r * 0.15f)
                quadraticBezierTo(cx + r * 0.7f, cy + r * 0.8f, cx, cy + r)
                quadraticBezierTo(cx - r * 0.7f, cy + r * 0.8f, cx - r * 0.82f, cy + r * 0.15f)
                lineTo(cx - r * 0.82f, cy - r * 0.55f)
                close()
            }
            drawPath(p, color)
        }
        BenefitSymbol.TARGET -> {
            drawCircle(color, radius = r, center = center, style = Stroke(width = stroke * 0.7f))
            drawCircle(color, radius = r * 0.5f, center = center, style = Stroke(width = stroke * 0.7f))
            drawCircle(color, radius = r * 0.14f, center = center)
        }
        BenefitSymbol.FIRM -> {
            // an upward double chevron — "lift"
            fun chevron(yOff: Float) {
                drawLine(color, Offset(cx - r * 0.8f, cy + yOff), Offset(cx, cy + yOff - r * 0.7f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy + yOff - r * 0.7f), Offset(cx + r * 0.8f, cy + yOff), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            chevron(r * 0.35f)
            chevron(r * 1.0f)
        }
        BenefitSymbol.GRAIN -> {
            // scattered grains — exfoliation / smoothing
            val pts = listOf(
                Offset(cx - r * 0.5f, cy - r * 0.4f), Offset(cx + r * 0.5f, cy - r * 0.5f),
                Offset(cx, cy + r * 0.05f), Offset(cx - r * 0.45f, cy + r * 0.55f),
                Offset(cx + r * 0.5f, cy + r * 0.5f),
            )
            pts.forEach { drawCircle(color, radius = r * 0.2f, center = it) }
        }
        BenefitSymbol.STAR -> {
            val p = Path()
            for (i in 0 until 5) {
                val outer = Math.toRadians(-90.0 + i * 72.0)
                val inner = Math.toRadians(-90.0 + i * 72.0 + 36.0)
                val ox = cx + (r * cos(outer)).toFloat()
                val oy = cy + (r * sin(outer)).toFloat()
                val ix = cx + (r * 0.45f * cos(inner)).toFloat()
                val iy = cy + (r * 0.45f * sin(inner)).toFloat()
                if (i == 0) p.moveTo(ox, oy) else p.lineTo(ox, oy)
                p.lineTo(ix, iy)
            }
            p.close()
            drawPath(p, color)
        }
    }
}
