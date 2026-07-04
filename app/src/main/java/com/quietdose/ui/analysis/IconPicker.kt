package com.quietdose.ui.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.quietdose.brain.analysis.ItemKind
import com.quietdose.brain.analysis.KindDetector
import com.quietdose.brain.analysis.ProductLook
import com.quietdose.brain.enrich.ImagePalette
import com.quietdose.data.entity.ItemEntity
import com.quietdose.ui.stack.androidxClickable
import com.quietdose.ui.theme.Accent
import com.quietdose.ui.theme.BenefitOrbit
import com.quietdose.ui.theme.GlyphDevice
import com.quietdose.ui.theme.GlyphHaircare
import com.quietdose.ui.theme.GlyphNeutral
import com.quietdose.ui.theme.GlyphSkincare
import com.quietdose.ui.theme.GlyphSupplement
import com.quietdose.ui.theme.Outline
import com.quietdose.ui.theme.Surface2

/**
 * The pick-your-own-icon surface. A saved item can carry a drawn [ProductLook]; here the
 * user chooses one from a small set of DISTINCT, sensible variants for the item — all drawn
 * on-device, instant, no model calls. The variants combine plausible container forms for the
 * item's kind with a calm fixed palette (plus the item's real packaging colour if we have it).
 */

/** A stored ARGB [Long] (unsigned) for a [Color], round-tripping via [Color.toArgb]/[Color]. */
private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/**
 * The item's primary benefits (for the icon's benefit orbs) — honest and DETERMINISTIC, no
 * model. Skincare/haircare: inferred from the product name's keywords; supplements/food and
 * anything else: pulled from the curated catalogs. Empty when nothing is recognised. Used by
 * the "Generate all icons" batch so the drawn orbs have real data to depict.
 */
fun benefitsFor(item: ItemEntity): List<String> {
    val kind = KindDetector.detect(item.name, item.category)
    val lower = item.name.lowercase()
    if (!kind.isIngested && kind != ItemKind.DEVICE) {
        val out = buildList {
            if (Regex("anti.?ag|ageing|aging|wrinkle|retino").containsMatchIn(lower)) add("Anti-ageing")
            if (Regex("hydrat|moistur|hyaluronic|snail|mucin|essence").containsMatchIn(lower)) add("Hydration")
            if (Regex("brighten|glow|radian|vitamin c").containsMatchIn(lower)) add("Brightening")
            if (Regex("acne|blemish|spot|salicylic|\\bbha\\b").containsMatchIn(lower)) add("Blemish control")
            if (Regex("barrier|repair|ceramide").containsMatchIn(lower)) add("Barrier repair")
            if (Regex("sooth|cica|centella|calm|redness").containsMatchIn(lower)) add("Soothing")
        }
        if (out.isNotEmpty()) return out.take(4)
    }
    com.quietdose.brain.analysis.IngredientCatalog.match(item.name)?.benefits
        ?.takeIf { it.isNotEmpty() }?.let { return it.take(4) }
    com.quietdose.brain.analysis.CuratedProfiles.match(item.name, kind)?.goodFor
        ?.takeIf { it.isNotEmpty() }?.let { return it.take(4) }
    return emptyList()
}

/** The calm category base colour for an item's kind — the deterministic palette fallback. */
private fun categoryColourFor(kind: ItemKind): Color = when (kind) {
    ItemKind.SKINCARE -> GlyphSkincare
    ItemKind.HAIRCARE -> GlyphHaircare
    ItemKind.DEVICE -> GlyphDevice
    ItemKind.SUPPLEMENT, ItemKind.FOOD -> GlyphSupplement
    else -> GlyphNeutral
}

/**
 * Build ~8 DISTINCT, sensible [ProductLook] variants for [item]: the inferred container
 * form plus a few plausible alternates for its kind, crossed with a small calm palette
 * (the item's stored packaging colour first, then the category base + the benefit spectrum).
 * The item's current look (if any) is kept FIRST so it reads as the current selection.
 * Deterministic, no model calls — safe to call for every item in a batch.
 */
fun lookVariants(item: ItemEntity): List<ProductLook> {
    val kind = KindDetector.detect(item.name, item.category)
    val inferred = inferContainerForm(item.name, item.category, item.type)

    // The inferred form first, then only plausible alternates for this kind — never nonsense
    // (a pill has no dropper; a device stays a device).
    val forms: List<ContainerForm> = buildList {
        add(inferred)
        when {
            kind.isIngested -> {
                add(ContainerForm.PILL); add(ContainerForm.SACHET); add(ContainerForm.BOTTLE)
            }
            kind == ItemKind.DEVICE -> add(ContainerForm.DEVICE)
            kind == ItemKind.HAIRCARE -> {
                add(ContainerForm.BOTTLE); add(ContainerForm.PUMP_BOTTLE); add(ContainerForm.TUBE)
            }
            else -> {
                add(ContainerForm.DROPPER_BOTTLE); add(ContainerForm.BOTTLE)
                add(ContainerForm.JAR); add(ContainerForm.TUBE)
            }
        }
    }.distinct()

    val current = com.quietdose.brain.analysis.ProductLookCodec.decode(item.look)

    // Calm colours, as ARGB longs: the real packaging colour (if stored) first so it's
    // offered at the front, then the category base and the shared benefit spectrum.
    val palette: List<Long> = buildList {
        current?.bodyArgb?.let { add(it) }
        add(categoryColourFor(kind).toArgbLong())
        BenefitOrbit.forEach { add(it.toArgbLong()) }
    }.distinct()

    val out = LinkedHashSet<ProductLook>()
    // Keep the item's current look first/selected, exactly as stored.
    current?.let { out.add(it) }
    // Then colour-major: show every form in the primary colour first, then step the
    // colour — so early tiles differ by FORM (the big visual cue), later ones by hue.
    // De-dup covers palette collisions (a category base can equal a benefit tint).
    outer@ for (colour in palette) {
        for (form in forms) {
            out.add(ProductLook(form = form.name, bodyArgb = colour, accentArgb = colour))
            if (out.size >= 8) break@outer
        }
    }
    return out.take(8).toList()
}

/**
 * A horizontally scrollable row of tappable glyph tiles — one per [lookVariants] entry.
 * [selected] highlights the active look (null → nothing highlighted, i.e. "keep auto").
 */
@Composable
fun IconPickerRow(
    item: ItemEntity,
    selected: ProductLook?,
    onPick: (ProductLook) -> Unit,
) {
    val variants = remember(item.name, item.brand, item.category, item.type, item.look) {
        lookVariants(item)
    }
    val fallback = remember(item.name, item.category) {
        categoryColourFor(KindDetector.detect(item.name, item.category)).toArgb().toLong() and 0xFFFFFFFFL
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        variants.forEach { look ->
            IconTile(
                item = item,
                look = look,
                fallbackArgb = fallback,
                selected = look == selected,
                onClick = { onPick(look) },
            )
        }
    }
}

@Composable
private fun IconTile(
    item: ItemEntity,
    look: ProductLook,
    fallbackArgb: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val body = look.bodyArgb ?: fallbackArgb
    val accent = look.accentArgb ?: body
    val form = remember(look.form) { runCatching { ContainerForm.valueOf(look.form) }.getOrNull() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .androidxClickable(onClick)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Accent else Outline),
                RoundedCornerShape(16.dp),
            ),
    ) {
        ProductGlyph(
            name = item.name,
            brand = item.brand,
            category = item.category,
            type = item.type,
            sampled = ImagePalette.PaletteResult(Color(body.toInt()), Color(accent.toInt())),
            formOverride = form,
            showBacking = true,
            modifier = Modifier.size(48.dp),
        )
    }
}
