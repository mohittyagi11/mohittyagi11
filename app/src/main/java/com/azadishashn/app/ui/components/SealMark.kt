package com.azadishashn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azadishashn.app.ui.theme.Gold
import com.azadishashn.app.ui.theme.GoldBright
import com.azadishashn.app.ui.theme.GoldDim

/** Draw the crown-in-ring sigil centred in the current DrawScope. */
private fun DrawScope.drawSeal(
    center: Offset,
    ringRadius: Float,
    ringBrush: Brush,
    crownBrush: Brush,
    pegColor: Color,
    strokeWidth: Float,
    fillCrown: Boolean = true,
) {
    val u = ringRadius / 28f
    fun p(dx: Float, dy: Float) = Offset(center.x + dx * u, center.y + dy * u)
    drawCircle(ringBrush, radius = ringRadius, center = center, style = Stroke(width = strokeWidth))
    val crown = Path().apply {
        moveTo(p(-12f, 8f).x, p(-12f, 8f).y)
        lineTo(p(-12f, -8f).x, p(-12f, -8f).y)
        lineTo(p(-6f, 0f).x, p(-6f, 0f).y)
        lineTo(p(0f, -12f).x, p(0f, -12f).y)
        lineTo(p(6f, 0f).x, p(6f, 0f).y)
        lineTo(p(12f, -8f).x, p(12f, -8f).y)
        lineTo(p(12f, 8f).x, p(12f, 8f).y)
        close()
    }
    if (fillCrown) {
        drawPath(crown, crownBrush)
        drawCircle(pegColor, radius = 2f * u, center = p(-12f, -8f))
        drawCircle(pegColor, radius = 2.4f * u, center = p(0f, -12f))
        drawCircle(pegColor, radius = 2f * u, center = p(12f, -8f))
    } else {
        drawPath(crown, crownBrush, style = Stroke(width = strokeWidth * 0.7f))
    }
}

/** The crisp gold seal — ring + crown + peg finials. */
@Composable
fun SealMark(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val ring = Brush.verticalGradient(listOf(GoldBright, GoldDim))
    val crown = Brush.verticalGradient(listOf(GoldBright, Gold))
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension * 0.40f
        drawSeal(
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            ringRadius = r,
            ringBrush = ring,
            crownBrush = crown,
            pegColor = GoldBright,
            strokeWidth = this.size.minDimension * 0.045f,
        )
    }
}

/** A huge, ghosted seal behind heroes and empty states. */
@Composable
fun SealWatermark(modifier: Modifier = Modifier, alpha: Float = 0.05f) {
    val tint = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
    val brush = SolidColor(tint)
    Canvas(modifier) {
        val r = this.size.minDimension * 0.42f
        drawSeal(
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            ringRadius = r,
            ringBrush = brush,
            crownBrush = brush,
            pegColor = tint,
            strokeWidth = this.size.minDimension * 0.012f,
            fillCrown = false,
        )
    }
}

/** Gold hairline rule with a small centred seal — the recurring section ornament. */
@Composable
fun SealDivider(modifier: Modifier = Modifier) {
    val line = Brush.horizontalGradient(
        listOf(Color.Transparent, GoldDim.copy(alpha = 0.7f), Color.Transparent),
    )
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.weight(1f).height(1.dp)) { drawRect(line) }
        SealMark(size = 22.dp, modifier = Modifier.padding(horizontal = 10.dp))
        Canvas(Modifier.weight(1f).height(1.dp)) { drawRect(line) }
    }
}
