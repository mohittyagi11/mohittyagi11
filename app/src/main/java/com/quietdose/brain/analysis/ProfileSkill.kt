package com.quietdose.brain.analysis

import com.quietdose.brain.LlmEngine

/**
 * The brain's workflow for profiling ANY item it doesn't have curated facts for.
 * It hands the SLM the real source material (product text + reviews), a guessed
 * kind, and the user's existing routine, and asks it to fill the
 * [CategoryProfile] template as JSON — reasoning the SAME way it does for a
 * supplement (what it's for, who it suits, how it's absorbed, how & when to use,
 * safety, what it clashes with) but scoping the impact to the category. Tolerant:
 * it digs the JSON object out of whatever the model returns, and falls back
 * cleanly to a deterministic profile when there's no model or the fill is unusable.
 */
object ProfileSkill {

    suspend fun fill(
        engine: LlmEngine,
        name: String,
        sourceMaterial: String,
        kindHint: ItemKind,
        currentItems: List<String> = emptyList(),
    ): CategoryProfile? {
        val raw = runCatching { engine.complete(prompt(name, sourceMaterial, kindHint, currentItems)) }.getOrNull()?.trim()
        if (raw.isNullOrBlank()) return null
        return parse(raw)?.let { p ->
            // Trust the deterministic kind unless the model clearly disagrees with reason.
            if (p.kind == ItemKind.OTHER && kindHint != ItemKind.OTHER) p.copy(kind = kindHint) else p
        }
    }

    /** A no-model profile so non-supplement items still get a sensible, plain page. */
    fun fallback(name: String, kind: ItemKind): CategoryProfile = CategoryProfile(
        kind = kind,
        categoryLabel = kind.label,
        whatItIs = "A ${kind.label.lowercase()} you're tracking — details from what you entered.",
        usage = if (kind.isIngested) "Take as directed on the label." else "Use as directed — note whether it's an AM or PM step.",
        absorption = if (kind == ItemKind.SKINCARE) "Apply to clean skin so it can absorb properly." else "",
        verify = listOf("Check the label for how and when to use it", "See a couple of independent reviews"),
        confidence = 25,
    )

    private fun prompt(name: String, sourceMaterial: String, kindHint: ItemKind, currentItems: List<String>): String = buildString {
        appendLine("You profile a product for a personal-care + supplement tracker. You reason like a careful person:")
        appendLine("what it's for, who it suits, how well it's absorbed, how AND when to use it, its safety, and what it clashes with —")
        appendLine("but you SCOPE everything to the category (a skincare item has a per-use amount + skin absorption + don't-layer-with,")
        appendLine("not a 'dose / bioavailability / drug interaction'). Return ONLY a JSON object, no prose.")
        appendLine()
        appendLine("Keys:")
        appendLine("  kind: one of SUPPLEMENT, SKINCARE, HAIRCARE, DEVICE, FOOD, OTHER")
        appendLine("  categoryLabel: the product TYPE, NOT the brand — e.g. \"Hydrating toner\", \"Vitamin C serum\", \"LED mask\"")
        appendLine("  whatItIs: one plain sentence")
        appendLine("  goodFor: array of short phrases — what it genuinely helps with")
        appendLine("  fitsWho: array — who it suits (skin/hair types, situations) and who should skip it")
        appendLine("  usage: how AND when — AM/PM, where in the routine, frequency, apply vs take")
        appendLine("  recommendedAmount + recommendedUnit: the amount per use, e.g. 2 + \"drops\", 1 + \"pump\", 0.5 + \"ml\". The 'how much' answer — use a sensible amount for this product type; omit only if truly unknowable.")
        appendLine("  absorption: how it works / how well it sinks in — the 'quality' lens, scoped to the category")
        appendLine("  safety: array — irritation, allergens, pregnancy, who should avoid")
        appendLine("  dontCombine: array — what NOT to layer/combine it with (e.g. retinol + AHA), especially against the user's current routine below")
        appendLine("  verify: array — concrete things the user should check")
        appendLine("  confidence: 0-100, low when you're guessing")
        appendLine()
        appendLine("Use ONLY the material below. Plain words, no jargon. Label marketing as claims. Do NOT invent certifications or fake numbers.")
        appendLine()
        appendLine("Item: $name")
        appendLine("Likely kind: ${kindHint.name}")
        if (currentItems.isNotEmpty()) {
            appendLine("User's current routine (flag anything that clashes in dontCombine): ${currentItems.take(12).joinToString(", ")}")
        }
        appendLine("Material (product's own text + web — treat marketing as claims):")
        appendLine(sourceMaterial.take(2600).ifBlank { name })
        appendLine()
        appendLine("Reply with only the JSON object.")
    }

    /** Pull the JSON object out of the model's reply and decode it. */
    private fun parse(raw: String): CategoryProfile? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return ProfileCodec.decode(raw.substring(start, end + 1))
    }
}
