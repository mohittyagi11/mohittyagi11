package com.quietdose.brain.analysis

import org.json.JSONArray
import org.json.JSONObject

/**
 * The generic, category-agnostic *template the SLM fills* for ANY item — the
 * workflow the brain runs when the curated knowledge base has nothing (skincare,
 * a device, a novel product). It deliberately generalises "dose": [usage] captures
 * how and when something is used, whether that's "1 capsule with breakfast", "apply
 * a few drops PM, leave on", or "roll 1–2× a week". Plain language, no jargon.
 *
 * Curated facts (when we have them) override/augment this; otherwise it stands on
 * its own, grounded in the fetched source material and web reviews.
 */
data class CategoryProfile(
    val kind: ItemKind,
    /** Free sub-category the SLM names, e.g. "Hydrating toner", "LED face mask". */
    val categoryLabel: String,
    /** One plain sentence: what this actually is. */
    val whatItIs: String,
    /** What it genuinely helps with / targets. */
    val goodFor: List<String> = emptyList(),
    /** How and WHEN to use it — AM/PM, apply vs ingest, frequency. (generalises dose) */
    val usage: String = "",
    /** Irritants, don't-mix, interactions, who-should-avoid — surfaced, not hidden. */
    val watchOuts: List<String> = emptyList(),
    /** Concrete things the user should check before trusting it. */
    val verify: List<String> = emptyList(),
    /** 0..100 — how confident the fill is (low when the model guessed). */
    val confidence: Int = 50,
)

/** Serialise a [CategoryProfile] to/from JSON (for the SLM fill and for persistence). */
object ProfileCodec {

    fun encode(p: CategoryProfile): String = JSONObject().apply {
        put("kind", p.kind.name)
        put("categoryLabel", p.categoryLabel)
        put("whatItIs", p.whatItIs)
        put("goodFor", JSONArray(p.goodFor))
        put("usage", p.usage)
        put("watchOuts", JSONArray(p.watchOuts))
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
                usage = o.optString("usage"),
                watchOuts = o.optJSONArray("watchOuts").toStringList(),
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
