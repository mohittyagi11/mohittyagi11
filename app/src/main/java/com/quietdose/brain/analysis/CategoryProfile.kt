package com.quietdose.brain.analysis

import org.json.JSONArray
import org.json.JSONObject

/**
 * The generic, category-agnostic template the SLM fills for ANY item the curated
 * KB can't cover. The brain reasons the SAME way across categories — what it's for,
 * who it suits, how it's absorbed, how to use it, safety, what it clashes with,
 * trust — but the *impact is scoped* to the category: a supplement's "dose +
 * bioavailability + drug interactions" becomes, for skincare, "per-use amount +
 * skin absorption + don't-layer-with". Plain language, no jargon, no invented numbers.
 */
data class CategoryProfile(
    val kind: ItemKind,
    /** Free sub-category the SLM names — the product TYPE, not the brand. e.g. "Hydrating essence". */
    val categoryLabel: String,
    /** One plain sentence: what this actually is. */
    val whatItIs: String,
    /** What it genuinely helps with / targets. */
    val goodFor: List<String> = emptyList(),
    /** Who it suits — skin types, hair types, situations. */
    val fitsWho: List<String> = emptyList(),
    /** How AND when to use it — AM/PM, where in the routine, frequency. (generalises timing) */
    val usage: String = "",
    /** Suggested amount per use, with [recommendedUnit] — the "how many drops" answer. */
    val recommendedAmount: Double? = null,
    val recommendedUnit: String? = null,
    /** How it works / how well it's absorbed — the "quality" lens, scoped to the category. */
    val absorption: String = "",
    /** Safety — irritation, allergens, pregnancy, who-should-avoid. */
    val safety: List<String> = emptyList(),
    /** What NOT to combine/layer it with — interactions, scoped (e.g. retinol + AHA). */
    val dontCombine: List<String> = emptyList(),
    /** Concrete things the user should check before trusting it. */
    val verify: List<String> = emptyList(),
    /** 0..100 — how confident the fill is (low when the model guessed). */
    val confidence: Int = 50,
)

/** Serialise a [CategoryProfile] to/from JSON (the SLM fill + persistence). Never throws. */
object ProfileCodec {

    fun encode(p: CategoryProfile): String = JSONObject().apply {
        put("kind", p.kind.name)
        put("categoryLabel", p.categoryLabel)
        put("whatItIs", p.whatItIs)
        put("goodFor", JSONArray(p.goodFor))
        put("fitsWho", JSONArray(p.fitsWho))
        put("usage", p.usage)
        p.recommendedAmount?.let { put("recommendedAmount", it) }
        p.recommendedUnit?.let { put("recommendedUnit", it) }
        put("absorption", p.absorption)
        put("safety", JSONArray(p.safety))
        put("dontCombine", JSONArray(p.dontCombine))
        put("verify", JSONArray(p.verify))
        put("confidence", p.confidence)
    }.toString()

    fun decode(s: String?): CategoryProfile? {
        if (s.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(s)
            CategoryProfile(
                kind = runCatching { ItemKind.valueOf(o.optString("kind")) }.getOrDefault(ItemKind.OTHER),
                categoryLabel = o.optString("categoryLabel").ifBlank { "Item" },
                whatItIs = o.optString("whatItIs"),
                goodFor = o.optJSONArray("goodFor").toStringList(),
                fitsWho = o.optJSONArray("fitsWho").toStringList(),
                usage = o.optString("usage"),
                recommendedAmount = if (o.has("recommendedAmount")) o.optDouble("recommendedAmount").takeIf { !it.isNaN() } else null,
                recommendedUnit = o.optString("recommendedUnit").ifBlank { null },
                absorption = o.optString("absorption"),
                safety = o.optJSONArray("safety").toStringList(),
                dontCombine = o.optJSONArray("dontCombine").toStringList(),
                verify = o.optJSONArray("verify").toStringList(),
                confidence = o.optInt("confidence", 50),
            )
        }.getOrNull()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).trim().ifBlank { null } }
    }
}
