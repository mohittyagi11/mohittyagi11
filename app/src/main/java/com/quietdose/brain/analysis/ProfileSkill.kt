package com.quietdose.brain.analysis

import com.quietdose.brain.LlmEngine

/**
 * The brain's workflow for profiling ANY item it doesn't have curated facts for.
 * It hands the SLM the real source material (product text + reviews) and a guessed
 * kind, and asks it to fill the [CategoryProfile] template as JSON. Tolerant: it
 * digs the JSON object out of whatever the model returns, and falls back cleanly
 * to a deterministic profile when there's no model or the fill is unusable.
 */
object ProfileSkill {

    suspend fun fill(engine: LlmEngine, name: String, sourceMaterial: String, kindHint: ItemKind): CategoryProfile? {
        val raw = runCatching { engine.complete(prompt(name, sourceMaterial, kindHint)) }.getOrNull()?.trim()
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
        verify = listOf("Check the label for how and when to use it", "See a couple of independent reviews"),
        confidence = 25,
    )

    private fun prompt(name: String, sourceMaterial: String, kindHint: ItemKind): String = buildString {
        appendLine("You profile a product for a personal-care + supplement tracker. Return ONLY a JSON object, no prose.")
        appendLine("Keys: kind (one of SUPPLEMENT, SKINCARE, HAIRCARE, DEVICE, FOOD, OTHER), categoryLabel (short, e.g. \"Hydrating toner\"),")
        appendLine("whatItIs (one plain sentence), goodFor (array of short phrases), usage (how AND when — AM/PM, apply vs take, frequency),")
        appendLine("watchOuts (array — irritants, what NOT to layer/combine with, who should avoid), verify (array — what to check), confidence (0-100).")
        appendLine("Use ONLY the material below. Plain words, no jargon. Label marketing as claims. Do NOT invent numbers, doses or certifications.")
        appendLine()
        appendLine("Item: $name")
        appendLine("Likely kind: ${kindHint.name}")
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
