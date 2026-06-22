package com.quietdose.ui.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.quietdose.ui.theme.Ink
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.TextLow
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a [VizSpec] over a set of [DayEvent]s. v1 draws the radial day-dial:
 * angle = time of day (midnight at top, clockwise), concentric lane = the
 * [VizSpec.laneBy] channel, mark size = [VizSpec.sizeBy], colour = the event's
 * resolved tint, hollow until taken. One renderer, many specs.
 */
@Composable
fun DayGraph(events: List<DayEvent>, spec: VizSpec, modifier: Modifier = Modifier) {
    val lanes = events.map { laneKey(it, spec.laneBy) }.distinct()

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 22.dp.toPx()
            val maxR = (size.minDimension / 2f) - pad
            val innerR = maxR * 0.36f
            val center = Offset(size.width / 2f, size.height / 2f)

            // outer 24h ring
            drawCircle(Outline, radius = maxR, center = center, style = Stroke(1.dp.toPx()))

            // cardinal ticks at 00 / 06 / 12 / 18
            listOf(0, 360, 720, 1080).forEach { m ->
                val a = angleDeg(m)
                drawLine(
                    color = Outline,
                    start = pointAt(center, maxR - 7.dp.toPx(), a),
                    end = pointAt(center, maxR, a),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // faint lane rings
            lanes.indices.forEach { i ->
                drawCircle(
                    color = Outline.copy(alpha = 0.3f),
                    radius = laneRadius(i, lanes.size, innerR, maxR),
                    center = center,
                    style = Stroke(1.dp.toPx()),
                )
            }

            // marks
            val base = 3.dp.toPx()
            val step = 1.3.dp.toPx()
            events.forEach { e ->
                val laneIdx = lanes.indexOf(laneKey(e, spec.laneBy)).coerceAtLeast(0)
                val r = laneRadius(laneIdx, lanes.size, innerR, maxR)
                val p = pointAt(center, r, angleDeg(e.minuteOfDay))
                val markR = base + markScale(e, spec.sizeBy) * step
                if (e.done) {
                    drawCircle(e.color, radius = markR, center = p)
                } else {
                    drawCircle(Ink, radius = markR, center = p)
                    drawCircle(e.color, radius = markR, center = p, style = Stroke(2.dp.toPx()))
                }
            }
        }

        TimeTick("12a", Alignment.TopCenter)
        TimeTick("6a", Alignment.CenterEnd)
        TimeTick("12p", Alignment.BottomCenter)
        TimeTick("6p", Alignment.CenterStart)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TimeTick(label: String, alignment: Alignment) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = TextLow,
        modifier = Modifier.align(alignment).padding(4.dp),
    )
}

/* ----------------------------- helpers ----------------------------- */

private fun laneKey(e: DayEvent, channel: Channel): String = when (channel) {
    Channel.CATEGORY -> e.category
    Channel.STATUS -> if (e.done) "done" else "todo"
    Channel.TIME -> (e.minuteOfDay / 360).toString() // quarter of day
    Channel.MAGNITUDE -> e.magnitude.toInt().toString()
}

private fun markScale(e: DayEvent, channel: Channel): Float = when (channel) {
    Channel.MAGNITUDE -> e.magnitude.coerceIn(1f, 6f)
    Channel.STATUS -> if (e.done) 4f else 2f
    Channel.TIME, Channel.CATEGORY -> 3f
}

private fun laneRadius(index: Int, count: Int, innerR: Float, maxR: Float): Float {
    if (count <= 1) return (innerR + maxR) / 2f
    val t = (index + 0.5f) / count
    return innerR + t * (maxR - innerR)
}

/** Midnight at the top, clockwise. */
private fun angleDeg(minuteOfDay: Int): Float = -90f + (minuteOfDay / 1440f) * 360f

private fun pointAt(center: Offset, radius: Float, angleDeg: Float): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}
