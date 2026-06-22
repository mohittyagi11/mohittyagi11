package com.quietdose.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.quietdose.data.model.ItemType

/**
 * Procedural supplement icons — drawn, not generated. Each form-factor renders
 * as a clean, consistent, slightly glossy shape in the group's tint, so every
 * item gets a recognizable "muted tinted pill" with zero config and zero model.
 * Consistent by construction, instant, and fully offline.
 */
@androidx.compose.runtime.Composable
fun ItemIcon(type: ItemType, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawItem(type, tint) }
}

private fun DrawScope.drawItem(type: ItemType, tint: Color) {
    val light = lerp(tint, Color.White, 0.34f)
    val dark = lerp(tint, Color.Black, 0.22f)
    when (type) {
        ItemType.CAPSULE, ItemType.OTHER -> drawCapsule(tint, light, dark)
        ItemType.SOFTGEL -> drawSoftgel(tint, light)
        ItemType.TABLET -> drawTablet(tint, light, dark)
        ItemType.SPRAY -> drawSpray(tint, light, dark)
        ItemType.POWDER -> drawScoop(tint, light)
        ItemType.GUMMY -> drawGummy(tint, light)
        ItemType.LIQUID -> drawDroplet(tint, light)
        ItemType.SUBLINGUAL -> drawLozenge(tint, light)
    }
}

private fun DrawScope.sheen(rect: Rect) {
    drawOval(
        color = Color.White.copy(alpha = 0.16f),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
    )
}

private fun DrawScope.drawCapsule(a: Color, b: Color, seam: Color) {
    val s = size.minDimension
    rotate(degrees = -40f) {
        val w = s * 0.84f
        val h = s * 0.40f
        val l = (size.width - w) / 2f
        val t = (size.height - h) / 2f
        val path = Path().apply {
            addRoundRect(RoundRect(l, t, l + w, t + h, CornerRadius(h / 2f, h / 2f)))
        }
        clipPath(path) {
            drawRect(a, topLeft = Offset(l, t), size = Size(w / 2f, h))
            drawRect(b, topLeft = Offset(l + w / 2f, t), size = Size(w / 2f, h))
            // soft sheen along the top of the left half
            sheen(Rect(l + w * 0.08f, t + h * 0.16f, l + w * 0.48f, t + h * 0.5f))
        }
        drawLine(seam, Offset(l + w / 2f, t), Offset(l + w / 2f, t + h), strokeWidth = s * 0.02f)
    }
}

private fun DrawScope.drawSoftgel(tint: Color, light: Color) {
    val s = size.minDimension
    rotate(degrees = -28f) {
        val w = s * 0.52f
        val h = s * 0.68f
        val l = (size.width - w) / 2f
        val t = (size.height - h) / 2f
        drawOval(tint, topLeft = Offset(l, t), size = Size(w, h))
        drawOval(
            light.copy(alpha = 0.5f),
            topLeft = Offset(l + w * 0.16f, t + h * 0.12f),
            size = Size(w * 0.30f, h * 0.42f),
        )
    }
}

private fun DrawScope.drawTablet(tint: Color, light: Color, dark: Color) {
    val s = size.minDimension
    val r = s * 0.34f
    drawCircle(tint, radius = r, center = center)
    drawLine(
        dark,
        Offset(center.x - r * 0.7f, center.y),
        Offset(center.x + r * 0.7f, center.y),
        strokeWidth = s * 0.028f,
    )
    drawOval(
        light.copy(alpha = 0.45f),
        topLeft = Offset(center.x - r * 0.55f, center.y - r * 0.72f),
        size = Size(r * 0.7f, r * 0.42f),
    )
}

private fun DrawScope.drawSpray(tint: Color, light: Color, dark: Color) {
    val s = size.minDimension
    val bodyW = s * 0.40f
    val bodyH = s * 0.46f
    val bx = center.x - bodyW / 2f
    val by = size.height * 0.42f
    drawRoundRect(
        tint,
        topLeft = Offset(bx, by),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(s * 0.07f, s * 0.07f),
    )
    val capW = s * 0.22f
    val capH = s * 0.14f
    drawRoundRect(
        light,
        topLeft = Offset(center.x - capW / 2f, by - capH * 0.9f),
        size = Size(capW, capH),
        cornerRadius = CornerRadius(s * 0.03f, s * 0.03f),
    )
    // nozzle
    drawRect(dark, topLeft = Offset(center.x + capW * 0.35f, by - capH * 0.75f), size = Size(s * 0.12f, s * 0.05f))
    sheen(Rect(bx + bodyW * 0.12f, by + bodyH * 0.1f, bx + bodyW * 0.4f, by + bodyH * 0.6f))
}

private fun DrawScope.drawScoop(tint: Color, light: Color) {
    val s = size.minDimension
    val w = s * 0.52f
    val h = s * 0.36f
    val l = center.x - w / 2f
    val t = center.y - h * 0.1f
    drawOval(tint, topLeft = Offset(l, t), size = Size(w, h))
    drawLine(
        light,
        Offset(l + w * 0.78f, t + h * 0.2f),
        Offset(l + w * 1.05f, t - h * 0.9f),
        strokeWidth = s * 0.07f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawGummy(tint: Color, light: Color) {
    val s = size.minDimension
    val sz = s * 0.58f
    val tl = Offset((size.width - sz) / 2f, (size.height - sz) / 2f)
    drawRoundRect(
        lerp(tint, light, 0.3f),
        topLeft = tl,
        size = Size(sz, sz),
        cornerRadius = CornerRadius(sz * 0.36f, sz * 0.36f),
    )
    sheen(Rect(tl.x + sz * 0.16f, tl.y + sz * 0.14f, tl.x + sz * 0.46f, tl.y + sz * 0.42f))
}

private fun DrawScope.drawDroplet(tint: Color, light: Color) {
    val s = size.minDimension
    val cx = center.x
    val r = s * 0.26f
    val cy = size.height * 0.60f
    val tip = size.height * 0.20f
    val path = Path().apply {
        moveTo(cx, tip)
        quadraticBezierTo(cx + r, cy - r, cx + r, cy)
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 0f, 180f, false)
        quadraticBezierTo(cx - r, cy - r, cx, tip)
        close()
    }
    drawPath(path, tint)
    drawOval(
        light.copy(alpha = 0.5f),
        topLeft = Offset(cx - r * 0.5f, cy - r * 0.5f),
        size = Size(r * 0.5f, r * 0.7f),
    )
}

private fun DrawScope.drawLozenge(tint: Color, light: Color) {
    val s = size.minDimension
    val w = s * 0.62f
    val h = s * 0.32f
    val l = center.x - w / 2f
    val t = center.y - h / 2f
    drawRoundRect(
        tint,
        topLeft = Offset(l, t),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f, h / 2f),
    )
    sheen(Rect(l + w * 0.1f, t + h * 0.18f, l + w * 0.45f, t + h * 0.55f))
}
