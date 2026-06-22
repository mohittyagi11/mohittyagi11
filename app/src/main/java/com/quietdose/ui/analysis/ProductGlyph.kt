package com.quietdose.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quietdose.brain.analysis.ItemKind
import com.quietdose.brain.analysis.KindDetector
import com.quietdose.brain.enrich.ImagePalette
import com.quietdose.data.model.ItemType
import com.quietdose.ui.theme.BenefitOrbit
import com.quietdose.ui.theme.GlyphCapSink
import com.quietdose.ui.theme.GlyphDevice
import com.quietdose.ui.theme.GlyphHaircare
import com.quietdose.ui.theme.GlyphNeutral
import com.quietdose.ui.theme.GlyphSkincare
import com.quietdose.ui.theme.GlyphSupplement
import com.quietdose.ui.theme.GlyphSurfaceSink
import com.quietdose.ui.theme.Ink
import kotlin.math.cos
import kotlin.math.sin

/**
 * A procedural, on-device "lookalike" of a product — drawn, never generated.
 *
 * The LLM is text-only and can't make images, so we render a stylised premium
 * container with Compose [Canvas]: the *form* (dropper bottle, jar, tube…) is
 * inferred from the item's name/category/type, and the *palette* is hashed
 * stably from the brand + name so each product reads distinct yet on-brand. Two
 * initials are printed small on the body. Fully deterministic, instant, offline —
 * the same product always draws the same glyph.
 */

/** The container family we draw — inferred, not stored. */
enum class ContainerForm {
    DROPPER_BOTTLE, // serum, essence, ampoule, oil → glass body + dropper cap
    PUMP_BOTTLE,    // lotion, foam, longer body → pump head
    BOTTLE,         // toner, mist, water-like → tall capped bottle
    TUBE,           // sunscreen, cleanser, cream tube → soft squeeze tube
    JAR,            // cream, balm, mask, butter → squat wide jar
    SACHET,         // sheet mask, powder packet → flat pouch
    DEVICE,         // roller, wand, tool → handle + head
    PILL,           // supplement / food → reuse the capsule idiom
}

/**
 * Infer the [ContainerForm] from the item, specific keywords winning over the
 * coarse [ItemKind]/[ItemType]. Mirrors KindDetector's vocabulary so the glyph
 * agrees with the rest of the analysis.
 */
fun inferContainerForm(name: String, category: String?, type: ItemType): ContainerForm {
    val hay = (name + " " + (category ?: "")).lowercase()
    fun has(vararg words: String) = words.any { hay.contains(it) }

    val kind = KindDetector.detect(name, category)
    if (kind == ItemKind.DEVICE) return ContainerForm.DEVICE
    if (kind.isIngested) {
        if (has("powder", "scoop", "greens", "collagen", "protein", "sachet", "packet")) return ContainerForm.SACHET
        return ContainerForm.PILL
    }

    return when {
        has("dropper", "serum", "essence", "ampoule", "facial oil", "face oil", "rosehip", "squalane", "snail mucin", "retinol", "vitamin c serum") -> ContainerForm.DROPPER_BOTTLE
        has("jar", "cream", "balm", "butter", "mask", "moisturiser", "moisturizer", "gel cream", "sleeping mask", "clay") -> ContainerForm.JAR
        has("tube", "sunscreen", "spf", "cleanser", "wash", "facewash", "face wash", "cleansing", "scrub", "peel", "exfoliant") -> ContainerForm.TUBE
        has("sheet mask", "sachet", "packet", "pouch") -> ContainerForm.SACHET
        has("toner", "lotion", "mist", "spray", "tonic", "water", "softener") -> ContainerForm.BOTTLE
        has("shampoo", "conditioner", "foam", "pump") -> ContainerForm.PUMP_BOTTLE
        kind == ItemKind.HAIRCARE -> ContainerForm.BOTTLE
        else -> ContainerForm.BOTTLE
    }
}

/** A small derived palette: a body tint, a lighter sheen, a darker cap. */
private data class GlyphPalette(val body: Color, val light: Color, val dark: Color, val cap: Color)

/**
 * A stable hash → hue nudge, blended toward the category base so products in a
 * family stay related (all serums cool-ish) while reading individually. Pure
 * function of brand+name, so it never flickers between recompositions.
 */
private fun paletteFor(name: String, brand: String?, kind: ItemKind): GlyphPalette {
    val seed = (brand.orEmpty() + "|" + name).lowercase().fold(0) { acc, c -> acc * 31 + c.code }
    val base = when (kind) {
        ItemKind.SKINCARE -> GlyphSkincare
        ItemKind.HAIRCARE -> GlyphHaircare
        ItemKind.DEVICE -> GlyphDevice
        ItemKind.SUPPLEMENT, ItemKind.FOOD -> GlyphSupplement
        else -> GlyphNeutral
    }
    // Pull a second hue from the hash and blend, so two serums differ but both
    // stay cool. We blend in HSL-ish RGB space — restrained (max ~38% toward the
    // hashed accent) to keep it on-brand and calm.
    val hashed = hueColor(((seed ushr 8) and 0xFFFF))
    val body = lerp(base, hashed, 0.30f).desaturateToward(GlyphNeutral, 0.18f)
    return GlyphPalette(
        body = body,
        light = lerp(body, Color.White, 0.38f),
        dark = lerp(body, Color.Black, 0.30f),
        cap = lerp(body, Color.Black, 0.16f),
    )
}

/** A calm, mid-luminance colour from a 0..65535 hash — six soft anchor hues. */
private fun hueColor(h: Int): Color {
    val anchors = listOf(
        Color(0xFF8FB7E0), // cool blue
        Color(0xFF9B8CE0), // violet
        Color(0xFF6FCF97), // green
        Color(0xFFE0B877), // gold
        Color(0xFFC58A78), // clay
        Color(0xFF8FB89A), // sage
    )
    val span = 65536f / anchors.size
    val idx = (h / span).toInt().coerceIn(0, anchors.size - 1)
    val next = (idx + 1) % anchors.size
    val f = (h - idx * span) / span
    return lerp(anchors[idx], anchors[next], f.coerceIn(0f, 1f))
}

private fun Color.desaturateToward(grey: Color, amount: Float): Color = lerp(this, grey, amount)

/**
 * Build a [GlyphPalette] from colours sampled off the REAL packaging photo. We
 * blend the sampled body toward the calm dark surface so it stays premium (never
 * a raw, blown-out photo colour), keep a slightly brighter sheen, and let the
 * sampled accent drive the cap so the product reads true to life. The category
 * fallback ([paletteFor]) is used when no photo was sampled.
 */
private fun paletteFromSample(primary: Color, accent: Color): GlyphPalette {
    // Anchor toward the dark surface — premium, calm — while still reading as the
    // packaging colour (keep ~62% of the true hue on the body).
    val body = lerp(GlyphSurfaceSink, primary, 0.62f)
    val cap = lerp(GlyphCapSink, accent, 0.58f)
    return GlyphPalette(
        body = body,
        light = lerp(body, Color.White, 0.34f),
        dark = lerp(body, Color.Black, 0.30f),
        cap = cap,
    )
}

/** Up to two initials from brand (preferred) or product name. */
private fun initialsFor(name: String, brand: String?): String {
    val src = (brand?.takeIf { it.isNotBlank() } ?: name).trim()
    if (src.isEmpty()) return ""
    val words = src.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
        else -> words[0].take(2).uppercase()
    }
}

/**
 * Draw the product lookalike. [name]/[brand]/[category] derive the form + initials.
 * The *palette* prefers [sampled] colours pulled from the real packaging photo
 * (via [ImagePalette]); when absent it falls back to the category-derived hash so
 * the glyph never blocks on the network. Calm rounded vector shapes, a cap, a
 * subtle sheen — the ItemIcon aesthetic, scaled up to a hero glyph.
 *
 * [showInitials] prints 1–2 letters on the body (off for tiny renders).
 * [benefits] (the product's primary benefits) become small tinted **orbit dots**
 * around the glyph circle — a quiet visual reference whose colours match the
 * benefit chips' legend in the report.
 */
@Composable
fun ProductGlyph(
    name: String,
    brand: String?,
    category: String?,
    type: ItemType,
    modifier: Modifier = Modifier,
    showInitials: Boolean = true,
    sampled: ImagePalette.PaletteResult? = null,
    benefits: List<String> = emptyList(),
) {
    val kind = remember(name, category) { KindDetector.detect(name, category) }
    val form = remember(name, category, type) { inferContainerForm(name, category, type) }
    val palette = remember(name, brand, kind, sampled) {
        if (sampled != null) paletteFromSample(sampled.primary, sampled.accent)
        else paletteFor(name, brand, kind)
    }
    val initials = remember(name, brand) { if (showInitials) initialsFor(name, brand) else "" }
    // Cap the orbit to keep it calm; colours come from the shared BenefitOrbit
    // spectrum so dot N == chip N in the legend.
    val orbitColors = remember(benefits) {
        benefits.take(6).mapIndexed { i, _ -> BenefitOrbit[i % BenefitOrbit.size] }
    }
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        if (orbitColors.isNotEmpty()) drawOrbitDots(orbitColors)
        drawGlyph(form, palette, initials, measurer)
    }
}

/** The shared mapping benefit → orbit/chip colour, so the screen's chips match the dots. */
fun benefitColor(index: Int): Color = BenefitOrbit[index % BenefitOrbit.size]

/**
 * Small tinted dots circling the glyph — one per primary benefit. Placed on a ring
 * just outside the container, faint and evenly spaced, each with a soft halo so it
 * reads as a calm "orb" rather than a sticker. Subtle by design.
 */
private fun DrawScope.drawOrbitDots(colors: List<Color>) {
    if (colors.isEmpty()) return
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val ringR = s * 0.46f          // just inside the canvas edge
    val dotR = s * 0.045f
    val start = -90.0              // first dot at top
    val step = 360.0 / colors.size
    colors.forEachIndexed { i, c ->
        val ang = Math.toRadians(start + i * step)
        val x = cx + (ringR * cos(ang)).toFloat()
        val y = cy + (ringR * sin(ang)).toFloat()
        // soft halo
        drawCircle(c.copy(alpha = 0.18f), radius = dotR * 2.1f, center = Offset(x, y))
        // a thin dark seat so the dot sits on the dark surface cleanly
        drawCircle(Ink.copy(alpha = 0.55f), radius = dotR * 1.35f, center = Offset(x, y))
        drawCircle(c.copy(alpha = 0.92f), radius = dotR, center = Offset(x, y))
    }
}

private fun DrawScope.drawGlyph(
    form: ContainerForm,
    p: GlyphPalette,
    initials: String,
    measurer: TextMeasurer,
) {
    when (form) {
        ContainerForm.DROPPER_BOTTLE -> drawDropperBottle(p, initials, measurer)
        ContainerForm.PUMP_BOTTLE -> drawPumpBottle(p, initials, measurer)
        ContainerForm.BOTTLE -> drawBottle(p, initials, measurer)
        ContainerForm.TUBE -> drawTube(p, initials, measurer)
        ContainerForm.JAR -> drawJar(p, initials, measurer)
        ContainerForm.SACHET -> drawSachet(p, initials, measurer)
        ContainerForm.DEVICE -> drawDevice(p)
        ContainerForm.PILL -> drawPill(p)
    }
}

/* ----------------------------- shared helpers ----------------------------- */

private fun DrawScope.sheen(rect: Rect, alpha: Float = 0.16f) {
    drawRoundRect(
        color = Color.White.copy(alpha = alpha),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(rect.width / 2f, rect.width / 2f),
    )
}

private fun DrawScope.label(
    text: String,
    measurer: TextMeasurer,
    center: Offset,
    color: Color,
) {
    if (text.isEmpty()) return
    val s = size.minDimension
    val style = TextStyle(
        color = color,
        fontSize = (s * 0.11f).toSp(),  // DrawScope is a Density — px → sp
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    )
    val layout = measurer.measure(text, style)
    drawText(
        layout,
        topLeft = Offset(
            center.x - layout.size.width / 2f,
            center.y - layout.size.height / 2f,
        ),
    )
}

/* ----------------------------- forms ----------------------------- */

private fun DrawScope.drawDropperBottle(p: GlyphPalette, initials: String, m: TextMeasurer) {
    val s = size.minDimension
    val bodyW = s * 0.46f
    val bodyH = s * 0.46f
    val cx = size.width / 2f
    val bodyTop = size.height * 0.44f
    val bodyLeft = cx - bodyW / 2f
    val r = s * 0.08f

    // The pipette glass tube descending INTO the body — the signal that says
    // "dropper", drawn first so the body overlaps its lower end.
    drawLine(
        p.light.copy(alpha = 0.55f),
        Offset(cx, bodyTop - s * 0.30f),
        Offset(cx, bodyTop + bodyH * 0.62f),
        strokeWidth = s * 0.028f,
        cap = StrokeCap.Round,
    )

    // glass body
    drawRoundRect(
        p.body,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(r, r),
    )

    // sloped shoulders into a narrow neck — reads as a serum bottle, not a box
    val neckW = bodyW * 0.40f
    val shoulder = Path().apply {
        moveTo(bodyLeft + bodyW * 0.14f, bodyTop + r)
        quadraticBezierTo(cx - neckW / 2f, bodyTop - s * 0.02f, cx - neckW / 2f, bodyTop - s * 0.06f)
        lineTo(cx + neckW / 2f, bodyTop - s * 0.06f)
        quadraticBezierTo(cx + neckW / 2f, bodyTop - s * 0.02f, bodyLeft + bodyW * 0.86f, bodyTop + r)
        close()
    }
    drawPath(shoulder, p.body)
    // neck collar
    drawRoundRect(
        p.dark,
        topLeft = Offset(cx - neckW / 2f, bodyTop - s * 0.12f),
        size = Size(neckW, s * 0.10f),
        cornerRadius = CornerRadius(s * 0.015f, s * 0.015f),
    )

    // dropper cap: a tall ribbed cap + the rubber bulb on top (the pipette squeeze)
    val capW = bodyW * 0.52f
    drawRoundRect(
        p.cap,
        topLeft = Offset(cx - capW / 2f, bodyTop - s * 0.28f),
        size = Size(capW, s * 0.18f),
        cornerRadius = CornerRadius(s * 0.03f, s * 0.03f),
    )
    // rubber bulb (rounded teat)
    drawCircle(
        lerp(p.cap, Color.White, 0.18f),
        radius = capW * 0.34f,
        center = Offset(cx, bodyTop - s * 0.32f),
    )

    sheen(Rect(bodyLeft + bodyW * 0.12f, bodyTop + bodyH * 0.12f, bodyLeft + bodyW * 0.28f, bodyTop + bodyH * 0.86f), 0.18f)
    label(initials, m, Offset(cx, bodyTop + bodyH * 0.60f), p.dark)
}

private fun DrawScope.drawPumpBottle(p: GlyphPalette, initials: String, m: TextMeasurer) {
    val s = size.minDimension
    val bodyW = s * 0.50f
    val bodyH = s * 0.56f
    val cx = size.width / 2f
    val bodyTop = size.height * 0.40f
    val bodyLeft = cx - bodyW / 2f
    val r = s * 0.09f

    drawRoundRect(p.body, topLeft = Offset(bodyLeft, bodyTop), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(r, r))
    // pump head: collar + stem + spout
    val collarW = bodyW * 0.46f
    drawRoundRect(p.cap, topLeft = Offset(cx - collarW / 2f, bodyTop - s * 0.10f), size = Size(collarW, s * 0.10f), cornerRadius = CornerRadius(s * 0.02f, s * 0.02f))
    drawRoundRect(p.dark, topLeft = Offset(cx - s * 0.03f, bodyTop - s * 0.24f), size = Size(s * 0.06f, s * 0.16f), cornerRadius = CornerRadius(s * 0.02f, s * 0.02f))
    drawRoundRect(p.cap, topLeft = Offset(cx - s * 0.14f, bodyTop - s * 0.26f), size = Size(s * 0.18f, s * 0.06f), cornerRadius = CornerRadius(s * 0.03f, s * 0.03f))
    sheen(Rect(bodyLeft + bodyW * 0.12f, bodyTop + bodyH * 0.08f, bodyLeft + bodyW * 0.30f, bodyTop + bodyH * 0.80f), 0.18f)
    label(initials, m, Offset(cx, bodyTop + bodyH * 0.56f), p.dark)
}

private fun DrawScope.drawBottle(p: GlyphPalette, initials: String, m: TextMeasurer) {
    val s = size.minDimension
    val bodyW = s * 0.44f
    val bodyH = s * 0.60f
    val cx = size.width / 2f
    val bodyTop = size.height * 0.36f
    val bodyLeft = cx - bodyW / 2f
    val r = s * 0.08f

    drawRoundRect(p.body, topLeft = Offset(bodyLeft, bodyTop), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(r, r))
    // narrow neck + cap
    val neckW = bodyW * 0.40f
    drawRoundRect(p.dark, topLeft = Offset(cx - neckW / 2f, bodyTop - s * 0.10f), size = Size(neckW, s * 0.12f), cornerRadius = CornerRadius(s * 0.02f, s * 0.02f))
    val capW = bodyW * 0.46f
    drawRoundRect(p.cap, topLeft = Offset(cx - capW / 2f, bodyTop - s * 0.22f), size = Size(capW, s * 0.13f), cornerRadius = CornerRadius(s * 0.03f, s * 0.03f))
    sheen(Rect(bodyLeft + bodyW * 0.14f, bodyTop + bodyH * 0.08f, bodyLeft + bodyW * 0.32f, bodyTop + bodyH * 0.82f), 0.18f)
    label(initials, m, Offset(cx, bodyTop + bodyH * 0.54f), p.dark)
}

private fun DrawScope.drawTube(p: GlyphPalette, initials: String, m: TextMeasurer) {
    val s = size.minDimension
    val bodyW = s * 0.40f
    val bodyH = s * 0.60f
    val cx = size.width / 2f
    val bodyTop = size.height * 0.32f
    val bodyLeft = cx - bodyW / 2f

    // tube: a narrow shouldered top tapering to a screw neck, wide soft body,
    // crimped flat bottom — a clear sunscreen/cleanser squeeze tube.
    val neckW = bodyW * 0.34f
    val path = Path().apply {
        // crimped flat base
        moveTo(bodyLeft, bodyTop + bodyH)
        lineTo(bodyLeft, bodyTop + s * 0.10f)
        // left shoulder sloping up to the neck
        quadraticBezierTo(bodyLeft, bodyTop, cx - neckW / 2f, bodyTop)
        lineTo(cx + neckW / 2f, bodyTop)
        // right shoulder back down
        quadraticBezierTo(bodyLeft + bodyW, bodyTop, bodyLeft + bodyW, bodyTop + s * 0.10f)
        lineTo(bodyLeft + bodyW, bodyTop + bodyH)
        close()
    }
    drawPath(path, p.body)
    // crimp seam at the base
    drawRoundRect(p.dark, topLeft = Offset(bodyLeft, bodyTop + bodyH - s * 0.05f), size = Size(bodyW, s * 0.05f), cornerRadius = CornerRadius(s * 0.01f, s * 0.01f))
    // screw neck + cap at top
    drawRoundRect(p.dark, topLeft = Offset(cx - neckW / 2f, bodyTop - s * 0.05f), size = Size(neckW, s * 0.06f), cornerRadius = CornerRadius(s * 0.01f, s * 0.01f))
    val capW = neckW * 1.12f
    drawRoundRect(p.cap, topLeft = Offset(cx - capW / 2f, bodyTop - s * 0.17f), size = Size(capW, s * 0.13f), cornerRadius = CornerRadius(s * 0.025f, s * 0.025f))
    sheen(Rect(bodyLeft + bodyW * 0.14f, bodyTop + bodyH * 0.14f, bodyLeft + bodyW * 0.30f, bodyTop + bodyH * 0.82f), 0.18f)
    label(initials, m, Offset(cx, bodyTop + bodyH * 0.54f), p.dark)
}

private fun DrawScope.drawJar(p: GlyphPalette, initials: String, m: TextMeasurer) {
    val s = size.minDimension
    val bodyW = s * 0.62f
    val bodyH = s * 0.40f
    val cx = size.width / 2f
    val bodyTop = size.height * 0.46f
    val bodyLeft = cx - bodyW / 2f
    val r = s * 0.08f

    // squat glass base
    drawRoundRect(p.body, topLeft = Offset(bodyLeft, bodyTop), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(r, r))
    // a thin neck band where the lid screws on — separates lid from jar so it
    // doesn't read as a single block
    drawRoundRect(p.dark.copy(alpha = 0.6f), topLeft = Offset(bodyLeft + bodyW * 0.04f, bodyTop), size = Size(bodyW * 0.92f, s * 0.04f), cornerRadius = CornerRadius(s * 0.02f, s * 0.02f))
    // wide screw lid, overhanging the base — the unmistakable cream-jar silhouette
    val lidW = bodyW * 1.06f
    val lidH = s * 0.20f
    drawRoundRect(p.cap, topLeft = Offset(cx - lidW / 2f, bodyTop - lidH * 0.78f), size = Size(lidW, lidH), cornerRadius = CornerRadius(s * 0.07f, s * 0.07f))
    // lid sheen
    drawRoundRect(p.light.copy(alpha = 0.5f), topLeft = Offset(cx - lidW / 2f + s * 0.04f, bodyTop - lidH * 0.6f), size = Size(lidW * 0.30f, lidH * 0.4f), cornerRadius = CornerRadius(s * 0.03f, s * 0.03f))
    sheen(Rect(bodyLeft + bodyW * 0.10f, bodyTop + bodyH * 0.22f, bodyLeft + bodyW * 0.26f, bodyTop + bodyH * 0.80f), 0.16f)
    label(initials, m, Offset(cx, bodyTop + bodyH * 0.60f), p.dark)
}

private fun DrawScope.drawSachet(p: GlyphPalette, initials: String, m: TextMeasurer) {
    val s = size.minDimension
    val bodyW = s * 0.48f
    val bodyH = s * 0.62f
    val cx = size.width / 2f
    val bodyTop = size.height * 0.30f
    val bodyLeft = cx - bodyW / 2f

    drawRoundRect(p.body, topLeft = Offset(bodyLeft, bodyTop), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(s * 0.03f, s * 0.03f))
    // serrated top seam (a thin darker band)
    drawRoundRect(p.dark, topLeft = Offset(bodyLeft, bodyTop), size = Size(bodyW, s * 0.06f), cornerRadius = CornerRadius(s * 0.01f, s * 0.01f))
    // notch cut
    val notch = Path().apply {
        moveTo(bodyLeft + bodyW, bodyTop + s * 0.03f)
        lineTo(bodyLeft + bodyW - s * 0.06f, bodyTop + s * 0.09f)
        lineTo(bodyLeft + bodyW, bodyTop + s * 0.12f)
        close()
    }
    drawPath(notch, p.body)
    sheen(Rect(bodyLeft + bodyW * 0.14f, bodyTop + bodyH * 0.18f, bodyLeft + bodyW * 0.30f, bodyTop + bodyH * 0.80f), 0.14f)
    label(initials, m, Offset(cx, bodyTop + bodyH * 0.54f), p.dark)
}

private fun DrawScope.drawDevice(p: GlyphPalette) {
    val s = size.minDimension
    val cx = size.width / 2f
    // handle
    val handleW = s * 0.14f
    val handleH = s * 0.46f
    val handleTop = size.height * 0.46f
    drawRoundRect(p.body, topLeft = Offset(cx - handleW / 2f, handleTop), size = Size(handleW, handleH), cornerRadius = CornerRadius(handleW / 2f, handleW / 2f))
    // rounded head / roller
    val headR = s * 0.20f
    drawCircle(p.cap, radius = headR, center = Offset(cx, handleTop - headR * 0.5f))
    drawCircle(p.light.copy(alpha = 0.5f), radius = headR * 0.34f, center = Offset(cx - headR * 0.3f, handleTop - headR * 0.8f))
    // small forked yoke
    drawLine(p.dark, Offset(cx - headR * 0.5f, handleTop - headR * 0.1f), Offset(cx - headR * 0.5f, handleTop + s * 0.04f), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
    drawLine(p.dark, Offset(cx + headR * 0.5f, handleTop - headR * 0.1f), Offset(cx + headR * 0.5f, handleTop + s * 0.04f), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
}

private fun DrawScope.drawPill(p: GlyphPalette) {
    val s = size.minDimension
    val w = s * 0.66f
    val h = s * 0.34f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val l = cx - w / 2f
    val t = cy - h / 2f
    val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(l, t, l + w, t + h, CornerRadius(h / 2f, h / 2f))) }
    clipPath(path) {
        drawRect(p.body, topLeft = Offset(l, t), size = Size(w / 2f, h))
        drawRect(p.light, topLeft = Offset(l + w / 2f, t), size = Size(w / 2f, h))
        sheen(Rect(l + w * 0.06f, t + h * 0.16f, l + w * 0.42f, t + h * 0.52f), 0.18f)
    }
    drawLine(p.dark, Offset(cx, t), Offset(cx, t + h), strokeWidth = s * 0.02f)
}
