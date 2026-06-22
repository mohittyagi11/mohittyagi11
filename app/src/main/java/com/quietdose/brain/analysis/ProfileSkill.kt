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
        // Curated ground truth wins: consult the validated KB FIRST, exactly the way
        // a supplement leans on IngredientCatalog. The model is only for gaps / unknowns.
        CuratedProfiles.match(name, kindHint)?.let { curated ->
            // Let the model enrich missing fields when one is present, but the curated facts win.
            val raw = runCatching { engine.complete(prompt(name, sourceMaterial, kindHint, currentItems)) }.getOrNull()?.trim()
            val enriched = if (!raw.isNullOrBlank()) parse(raw) else null
            return if (enriched != null) mergeCuratedFirst(curated, enriched) else curated
        }
        val raw = runCatching { engine.complete(prompt(name, sourceMaterial, kindHint, currentItems)) }.getOrNull()?.trim()
        if (raw.isNullOrBlank()) return null
        return parse(raw)?.let { p ->
            // Trust the deterministic kind unless the model clearly disagrees with reason.
            if (p.kind == ItemKind.OTHER && kindHint != ItemKind.OTHER) p.copy(kind = kindHint) else p
        }
    }

    /**
     * Merge a model fill into a curated profile WITHOUT ever overwriting curated facts:
     * the curated entry's text, lists, amounts, kind and confidence all win; the model
     * is only allowed to fill fields the curated entry genuinely left blank.
     */
    private fun mergeCuratedFirst(curated: CategoryProfile, model: CategoryProfile): CategoryProfile = curated.copy(
        whatItIs = curated.whatItIs.ifBlank { model.whatItIs },
        goodFor = curated.goodFor.ifEmpty { model.goodFor },
        fitsWho = curated.fitsWho.ifEmpty { model.fitsWho },
        usage = curated.usage.ifBlank { model.usage },
        routineStep = curated.routineStep.ifBlank { model.routineStep },
        recommendedAmount = curated.recommendedAmount ?: model.recommendedAmount,
        recommendedUnit = curated.recommendedUnit ?: model.recommendedUnit,
        absorption = curated.absorption.ifBlank { model.absorption },
        safety = curated.safety.ifEmpty { model.safety },
        dontCombine = curated.dontCombine.ifEmpty { model.dontCombine },
        verify = curated.verify.ifEmpty { model.verify },
    )

    /**
     * A no-model profile so non-supplement items still get a SENSIBLE, specific page —
     * never the bare "Skincare / 1 units / verify ingredients" placeholder. Everything is
     * derived deterministically from the product NAME (real product TYPE, a per-use amount
     * by form, honest benefits from name keywords) — nothing is fabricated beyond what the
     * name plainly says. Confidence stays modest; the overall score now comes from the
     * dimensions, not this number.
     */
    fun fallback(name: String, kind: ItemKind): CategoryProfile {
        val lower = name.lowercase()
        val form = SkincareForm.of(lower, kind)
        val type = form?.typeLabel ?: kind.label
        val (amount, unit) = form?.perUse ?: (null to null)
        val benefits = benefitsFromName(lower)
        return CategoryProfile(
            kind = kind,
            categoryLabel = type,
            whatItIs = if (form != null) "A ${type.lowercase()} you're tracking — details from what you entered."
            else "A ${kind.label.lowercase()} you're tracking — details from what you entered.",
            goodFor = benefits,
            usage = form?.usage ?: if (kind.isIngested) "Take as directed on the label."
            else "Use as directed — note whether it's an AM or PM step.",
            routineStep = form?.routineStep ?: "",
            recommendedAmount = amount,
            recommendedUnit = unit,
            absorption = if (kind == ItemKind.SKINCARE) "Apply to clean, dry skin so it can sink in before the next layer." else "",
            verify = verifyFor(kind),
            confidence = 45,
        )
    }

    /** Honest benefits inferred from name keywords only — never beyond what the name says. */
    private fun benefitsFromName(lower: String): List<String> = buildList {
        if (lower.contains("anti-age") || lower.contains("anti-ageing") || lower.contains("anti-aging") ||
            lower.contains("ageing") || lower.contains("aging") || lower.contains("wrinkle") || lower.contains("retino")) add("Anti-ageing")
        if (lower.contains("hydrat") || lower.contains("moistur") || lower.contains("hyaluronic")) add("Hydration")
        if (lower.contains("brighten") || lower.contains("glow") || lower.contains("radian") || lower.contains("vitamin c")) add("Brightening")
        if (lower.contains("acne") || lower.contains("blemish") || lower.contains("spot") || lower.contains("salicylic")) add("Blemish control")
        if (lower.contains("barrier") || lower.contains("repair") || lower.contains("ceramide")) add("Barrier repair")
        if (lower.contains("soothe") || lower.contains("soothing") || lower.contains("cica") || lower.contains("centella") || lower.contains("calm")) add("Soothing")
    }.distinct()

    /** Skincare-appropriate checks — never "verify ingredients" when none are shown. */
    private fun verifyFor(kind: ItemKind): List<String> = when (kind) {
        ItemKind.SKINCARE, ItemKind.HAIRCARE -> listOf(
            "Patch-test on your inner arm first",
            "Check the label's directions for how much and when",
            "See a couple of independent reviews",
        )
        else -> listOf(
            "Check the label for how and when to use it",
            "See a couple of independent reviews",
        )
    }

    /**
     * A derived skin/hair-care FORM read straight from the product name — the real product
     * TYPE plus its sane per-use amount, routine placement and usage. This is what turns the
     * confirm screen from "Skincare · 1 units" into "Serum · 3 drops". Matched most-specific
     * first (eye cream before cream). Returns null when the name gives nothing to go on.
     */
    private data class SkincareForm(
        val typeLabel: String,
        val perUse: Pair<Double?, String?>,
        val routineStep: String,
        val usage: String,
    ) {
        companion object {
            // Dropper / fluid products → drops. Creams → pea-size. Sunscreen → two-finger, etc.
            fun of(lower: String, kind: ItemKind): SkincareForm? {
                fun has(vararg kw: String) = kw.any { lower.contains(it) }
                return when {
                    has("eye cream", "eye serum", "under eye", "under-eye") -> SkincareForm(
                        "Eye cream", 1.0 to "pea-size",
                        "Tap gently around the eye; AM and PM", "A little goes a long way — pat in, don't rub.",
                    )
                    has("sunscreen", "spf", "sunblock") -> SkincareForm(
                        "Sunscreen", null to "two-finger",
                        "Last step, AM; reapply through the day", "Apply two-finger lengths as the final morning step; reapply every few hours outdoors.",
                    )
                    has("essence") -> SkincareForm(
                        "Essence", null to "a few drops",
                        "After toner, before serum; AM and PM", "Press a few drops into damp skin after cleansing.",
                    )
                    has("toner", "rice toner") -> SkincareForm(
                        "Toner", null to "a few swipes",
                        "Right after cleansing; AM and PM", "Sweep over clean skin with a cotton pad or hands.",
                    )
                    has("serum", "ampoule", "booster") -> SkincareForm(
                        "Serum", 3.0 to "drops",
                        "After toner, before moisturiser; AM and/or PM", "Smooth 3 drops over clean skin before your moisturiser.",
                    )
                    has("face oil", "facial oil", "rosehip", "squalane oil") -> SkincareForm(
                        "Face oil", 3.0 to "drops",
                        "Last skincare step before SPF; usually PM", "Warm a few drops between palms and press into skin.",
                    )
                    has("cleansing oil", "cleanser", "face wash", "facewash", "micellar") -> SkincareForm(
                        "Cleanser", null to "pea-size",
                        "First step; AM and PM", "Massage over skin, then rinse with lukewarm water.",
                    )
                    has("sheet mask", "face mask", "clay mask", "sleeping mask", "mask") -> SkincareForm(
                        "Mask", null to "a layer",
                        "Once or twice a week, usually PM", "Apply an even layer (or sheet), leave on, then remove as directed.",
                    )
                    has("lotion", "milk") -> SkincareForm(
                        "Lotion", null to "pea-size",
                        "After serum; AM and/or PM", "Smooth a thin layer over the face and neck.",
                    )
                    has("moisturiser", "moisturizer", "cream", "gel cream", "day cream", "night cream") -> SkincareForm(
                        "Moisturiser", null to "pea-size",
                        "After serum; AM and/or PM", "Apply a pea-size amount over the face after your serum.",
                    )
                    has("lip balm") -> SkincareForm(
                        "Lip balm", null to "a swipe",
                        "Whenever lips feel dry", "Swipe over the lips as needed.",
                    )
                    kind == ItemKind.HAIRCARE && has("oil") -> SkincareForm(
                        "Hair oil", null to "a few drops",
                        "Massage into scalp/lengths before washing", "Work a few drops through the scalp or lengths.",
                    )
                    kind == ItemKind.HAIRCARE && has("serum") -> SkincareForm(
                        "Hair serum", 3.0 to "drops",
                        "On damp or dry lengths as directed", "Smooth a few drops through the lengths.",
                    )
                    else -> null
                }
            }
        }
    }

    private fun prompt(name: String, sourceMaterial: String, kindHint: ItemKind, currentItems: List<String>): String = buildString {
        appendLine("You profile a product for a personal-care + supplement tracker. You reason like a careful person:")
        appendLine("what it's for, who it suits, how well it's absorbed, how AND when to use it, its safety, and what it clashes with —")
        appendLine("but you SCOPE everything to the category (a skincare item has a per-use amount + skin absorption + don't-layer-with,")
        appendLine("not a 'dose / bioavailability / drug interaction'). Return ONLY a JSON object, no prose.")
        appendLine()
        appendLine("Keys:")
        appendLine("  kind: one of SUPPLEMENT, SKINCARE, HAIRCARE, DEVICE, FOOD, OTHER")
        appendLine("  brand: the maker/brand name if the material states it, else \"\"")
        appendLine("  categoryLabel: the product TYPE, NOT the brand — e.g. \"Hydrating toner\", \"Vitamin C serum\", \"LED mask\"")
        appendLine("  whatItIs: one plain sentence")
        appendLine("  goodFor: array of short phrases — what it genuinely helps with")
        appendLine("  fitsWho: array — who it suits (skin/hair types, situations) and who should skip it")
        appendLine("  usage: how AND when — AM/PM, where in the routine, frequency, apply vs take")
        appendLine("  routineStep: where it sits in a skincare/haircare routine, e.g. \"After toner, before serum; AM and PM\" or \"Last step, AM\". This is routine PLACEMENT, NOT supplement timing (never fasted/with-food). Empty when it doesn't apply (devices, food).")
        appendLine("  recommendedAmount + recommendedUnit: the amount PER USE, read from the directions — e.g. 2 + \"drops\", 1 + \"pump\", 0.5 + \"ml\". The 'how much' answer. NEVER use the pack/bottle volume (e.g. \"100 ml\") as the per-use amount. Use a sensible amount for this product type; omit only if truly unknowable.")
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
        // Right-sized so the long prompt + a FULL JSON reply both fit the token budget —
        // an over-long source pushed the reply over the limit and it got truncated mid-JSON.
        appendLine(capSourceMaterial(sourceMaterial).ifBlank { name })
        appendLine()
        appendLine("Return ONLY a JSON object — no markdown fences, no prose, no trailing commentary.")
    }

    /**
     * Trim the source material to roughly [MAX_SOURCE_CHARS], but ALWAYS keep the
     * DIRECTIONS section intact — it's where the per-use amount and routine step are read
     * from, so it must survive the cap even if the product blurb is long. We trim the
     * product blurb first and append the (short) directions + web sections whole.
     */
    private fun capSourceMaterial(material: String): String {
        if (material.length <= MAX_SOURCE_CHARS) return material
        val dirIdx = material.indexOf("DIRECTIONS TO USE")
        if (dirIdx < 0) return material.take(MAX_SOURCE_CHARS)
        val head = material.take(dirIdx).trimEnd()
        val tail = material.substring(dirIdx) // directions (+ any web section after) — keep whole
        val budgetForHead = (MAX_SOURCE_CHARS - tail.length).coerceAtLeast(200)
        return (head.take(budgetForHead).trimEnd() + "\n\n" + tail).trim()
    }

    private const val MAX_SOURCE_CHARS = 1400

    /**
     * Pull the JSON object out of the model's reply and decode it. Tolerant of the two
     * real-world failure modes:
     *   (a) the model wrapped the JSON in ```json … ``` fences, and
     *   (b) the token budget cut the object off mid-way, so the closing braces/brackets
     *       are missing — we balance them and decode the partial object (ProfileCodec is
     *       already forgiving of missing keys), so a nearly-complete fill still yields a
     *       real profile instead of null. Never throws.
     */
    private fun parse(raw: String): CategoryProfile? {
        val candidate = JsonRepair.objectFrom(raw) ?: return null
        return ProfileCodec.decode(candidate)
    }
}

/**
 * Coaxes a decodable JSON object out of a raw SLM reply. Two jobs:
 *  - strip ```json … ``` (or bare ```) markdown fences the model sometimes adds, and
 *  - rescue a TRUNCATED object: from the first `{`, walk the text tracking string/escape
 *    state, then append whatever closing `}` / `]` are still owed to balance it. A reply
 *    that was cut off by the token budget therefore decodes to a real (partial) object
 *    instead of failing outright.
 *
 * Never throws; returns null only when there's no `{` to start from.
 */
internal object JsonRepair {

    /** Best-effort balanced JSON-object substring from a raw model reply, or null. */
    fun objectFrom(raw: String): String? {
        val unfenced = stripFences(raw)
        val start = unfenced.indexOf('{')
        if (start < 0) return null
        // Fast path: a clean, already-balanced object.
        val end = unfenced.lastIndexOf('}')
        if (end > start) {
            val slice = unfenced.substring(start, end + 1)
            if (isBalanced(slice)) return slice
        }
        return balance(unfenced.substring(start))
    }

    /** Remove leading/trailing markdown code fences (```json … ``` or bare ```). */
    private fun stripFences(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```")
            // Drop an optional language tag on the first line (e.g. "json").
            val nl = s.indexOf('\n')
            if (nl in 0..12 && s.take(nl).all { it.isLetter() }) s = s.substring(nl + 1)
        }
        val fenceEnd = s.lastIndexOf("```")
        if (fenceEnd >= 0) s = s.substring(0, fenceEnd)
        return s.trim()
    }

    /** True when braces/brackets balance outside of strings (a clean object). */
    private fun isBalanced(s: String): Boolean = balance(s) == s

    /**
     * Walk from the first character (assumed `{`), tracking whether we're inside a string
     * and whether the previous char was an escape, and remember the order of open
     * `{`/`[`. Cut off any dangling key/comma, then append the owed closers in reverse.
     */
    private fun balance(s: String): String {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        var lastSignificant = -1 // index of the last char that can end a complete value
        for (i in s.indices) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> { inString = false; lastSignificant = i }
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> stack.addLast('{')
                '[' -> stack.addLast('[')
                '}' -> { if (stack.lastOrNull() == '{') stack.removeLast(); lastSignificant = i }
                ']' -> { if (stack.lastOrNull() == '[') stack.removeLast(); lastSignificant = i }
                ',', ':', ' ', '\n', '\r', '\t' -> {} // structural / whitespace, not value-ending
                else -> lastSignificant = i // number / literal char
            }
        }
        // Trim a trailing partial token (an unterminated string, a dangling key, or a stray
        // comma) so we don't feed half a token to the decoder.
        var body = if (inString) {
            // Cut the unterminated string back to the last clean boundary.
            if (lastSignificant >= 0) s.substring(0, lastSignificant + 1) else s
        } else if (lastSignificant >= 0) {
            s.substring(0, lastSignificant + 1)
        } else s
        body = body.trimEnd().trimEnd(',').trimEnd()
        // Append the owed closers, innermost first.
        val closers = StringBuilder()
        for (open in stack.asReversed()) closers.append(if (open == '{') '}' else ']')
        return body + closers
    }
}
