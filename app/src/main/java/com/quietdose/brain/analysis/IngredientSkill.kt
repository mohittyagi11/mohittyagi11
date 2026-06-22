package com.quietdose.brain.analysis

import com.quietdose.brain.LlmEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * A focused SLM pass that reads a non-supplement product's own text (label +
 * directions + web) and names the KEY actives, each with a plain role. Every
 * extracted active is then GROUNDED against the validated knowledge base
 * ([CuratedProfiles] for skincare/haircare/food, [IngredientCatalog] for the
 * supplement catalog): a recognised active gets a curated one-line note and an
 * honest [Severity] (GOOD for a known beneficial active, CAUTION for a known
 * strong/irritant active like retinol / AHA / BHA, else NEUTRAL).
 *
 * With no model it degrades to a deterministic scan: it looks for known actives
 * by alias-containment in the source text, so a skincare item still surfaces its
 * recognised ingredients honestly. Never throws.
 */
object IngredientSkill {

    /** At most this many actives — keeps the block readable and the budget bounded. */
    private const val MAX_ACTIVES = 8

    suspend fun extract(
        engine: LlmEngine,
        name: String,
        sourceMaterial: String,
    ): List<IngredientLine> {
        // Model pass first (when available); fall back to a deterministic scan.
        val fromModel = if (engine.isReady()) {
            runCatching { engine.complete(prompt(name, sourceMaterial)).trim() }
                .getOrNull()
                ?.let { parse(it) }
                .orEmpty()
        } else emptyList()

        val raw = fromModel.ifEmpty { scan(name, sourceMaterial) }
        // Ground each, de-dupe by lowercase name, cap.
        val seen = LinkedHashSet<String>()
        return raw.mapNotNull { (n, role) ->
            val clean = n.trim()
            if (clean.isBlank() || !seen.add(clean.lowercase())) null
            else ground(clean, role.trim())
        }.take(MAX_ACTIVES)
    }

    /** The catalog key for an active name, when it matches the supplement catalog — else null. */
    fun keyFor(name: String): String? = IngredientCatalog.match(name)?.key

    // --- grounding ---------------------------------------------------------

    /** Strong/irritant actives — surfaced at face value as CAUTION (ease-in, sun sensitivity, etc.). */
    private val STRONG_ACTIVES = listOf(
        "retinol", "retinoid", "retinal", "retinaldehyde", "tretinoin", "adapalene",
        "aha", "alpha hydroxy", "glycolic", "lactic acid", "mandelic",
        "bha", "beta hydroxy", "salicylic", "benzoyl peroxide", "azelaic",
    )

    /** Well-established gentle/beneficial actives — surfaced as GOOD when recognised. */
    private val GOOD_ACTIVES = listOf(
        "niacinamide", "hyaluronic", "sodium hyaluronate", "ceramide", "panthenol",
        "centella", "cica", "madecassoside", "allantoin", "squalane", "snail mucin",
        "peptide", "bakuchiol", "vitamin c", "ascorbic", "ascorbate", "glycerin",
        "vitamin e", "tocopherol", "aloe", "shea", "green tea",
    )

    /**
     * Ground an extracted active against the curated KB. A curated match supplies a
     * grounded note (its plain "what it is") and confirms the role/severity; otherwise
     * we keep the model's role and read severity from the well-known active lists.
     */
    private fun ground(name: String, modelRole: String): IngredientLine {
        val lower = name.lowercase()
        val curated = CuratedProfiles.match(name)
        val severity = when {
            STRONG_ACTIVES.any { lower.contains(it) } -> Severity.CAUTION
            GOOD_ACTIVES.any { lower.contains(it) } -> Severity.GOOD
            else -> Severity.NEUTRAL
        }
        // Prefer a curated fact for the note; else the model's role read; else nothing.
        val note = curated?.whatItIs?.takeIf { it.isNotBlank() }.orEmpty()
        val role = modelRole.ifBlank { roleFromCurated(curated) }
        return IngredientLine(name = name, role = role, note = note.take(120), severity = severity)
    }

    /** A short role word inferred from the curated category label, when the model gave none. */
    private fun roleFromCurated(curated: CategoryProfile?): String {
        val label = curated?.categoryLabel?.lowercase().orEmpty()
        return when {
            label.contains("retin") || label.contains("acid") || label.contains("peroxide") -> "Active"
            label.contains("niacinamide") || label.contains("peptide") -> "Active"
            label.contains("hyaluronic") || label.contains("glycerin") -> "Humectant"
            label.contains("ceramide") || label.contains("squalane") || label.contains("oil") -> "Emollient"
            label.contains("centella") || label.contains("panthenol") || label.contains("allantoin") -> "Soothing"
            else -> ""
        }
    }

    // --- deterministic fallback (no model) ---------------------------------

    /**
     * Scan the source text for known actives by alias-containment. Walks the curated
     * skincare/haircare alias list and surfaces any whose alias appears in the text,
     * most-specific first, so an honest active list still renders with no model.
     */
    private fun scan(name: String, sourceMaterial: String): List<Pair<String, String>> {
        val hay = (name + " " + sourceMaterial).lowercase()
        val hits = LinkedHashSet<String>()
        // Pull from the curated alias vocabulary plus the strong/good lists.
        val vocab = (STRONG_ACTIVES + GOOD_ACTIVES).distinct().sortedByDescending { it.length }
        vocab.forEach { alias ->
            if (alias.length >= 3 && hay.contains(alias) && hits.none { it.contains(alias) || alias.contains(it) }) {
                hits.add(alias)
            }
        }
        return hits.map { it.replaceFirstChar(Char::uppercase) to "" }
    }

    // --- model prompt + parse ----------------------------------------------

    private fun prompt(name: String, sourceMaterial: String): String = buildString {
        appendLine("You read a personal-care product's own text and list ONLY its KEY active ingredients — the ones that actually do something — not the full INCI list, not water, not fragrance fillers.")
        appendLine("Return ONLY a JSON array, no prose, of at most $MAX_ACTIVES objects with these keys:")
        appendLine("  name: the ingredient's common name (e.g. \"Niacinamide\", \"Hyaluronic acid\", \"Retinol\")")
        appendLine("  role: ONE plain word for what it does — one of Humectant, Active, Soothing, Preservative, Emollient, Antioxidant, Exfoliant")
        appendLine("Use ONLY ingredients the material below actually names. Do NOT invent ingredients, concentrations or numbers. If none are clearly named, return [].")
        appendLine()
        appendLine("Product: $name")
        appendLine("Material (the product's own text + web — read the ingredients/actives from here):")
        appendLine(sourceMaterial.take(1400).ifBlank { name })
        appendLine()
        appendLine("Reply with only the JSON array.")
    }

    /**
     * Pull the JSON array out of the model's reply and decode it. Tolerant of ```json
     * fences and of a TRUNCATED array cut off by the token budget — we balance the owed
     * brackets so a near-complete reply still yields actives. Never throws.
     */
    private fun parse(raw: String): List<Pair<String, String>> {
        val candidate = arrayFrom(raw) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(candidate)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val n = o.optString("name").trim()
                if (n.isBlank()) null else n to o.optString("role").trim()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Best-effort balanced JSON-array substring from a raw model reply, or null. Strips
     * ```json fences, then from the first `[` either returns a clean balanced slice or
     * rescues a truncated one by dropping the dangling element and closing the brackets.
     */
    internal fun arrayFrom(raw: String): String? {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```")
            val nl = s.indexOf('\n')
            if (nl in 0..12 && s.take(nl).all { it.isLetter() }) s = s.substring(nl + 1)
            val fenceEnd = s.lastIndexOf("```")
            if (fenceEnd >= 0) s = s.substring(0, fenceEnd)
            s = s.trim()
        }
        val start = s.indexOf('[')
        if (start < 0) return null
        val end = s.lastIndexOf(']')
        if (end > start) {
            val slice = s.substring(start, end + 1)
            if (runCatching { JSONArray(slice) }.isSuccess) return slice
        }
        // Truncated: keep through the last fully-closed object, then close the array.
        val body = s.substring(start)
        val lastObj = body.lastIndexOf('}')
        if (lastObj < 0) return "[]"
        return body.substring(0, lastObj + 1).trimEnd().trimEnd(',') + "]"
    }
}
