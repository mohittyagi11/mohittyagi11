package com.quietdose.brain.analysis

import android.content.Context
import com.quietdose.brain.BrainProvider
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.ItemFlags

/**
 * The incremental brain for "should this join the stack, and how?". It grounds
 * every claim in the validated [IngredientCatalog] (a deterministic logic-tree
 * over a known fact graph), then — scaled by [ModelTier] — lets the on-device
 * model reason OVER those facts to enrich the synthesis. With no model it still
 * produces a complete, honest, offline analysis. Facts and deductions are
 * written back to [ContextStore] so the intelligence accumulates.
 *
 * Nothing here is medical advice; language stays hedged and the model is only
 * ever allowed to rephrase/connect grounded facts, never to invent doses or
 * interactions.
 */
object StackAnalyzer {

    suspend fun analyze(
        context: Context,
        name: String,
        category: String?,
        currentStack: List<ItemEntity>,
        intent: String?,
    ): AnalysisReport {
        val app = context.applicationContext
        val tier = ModelCapability.tier(app)
        val matched = IngredientCatalog.match(name)
        val grounded = mutableListOf<String>()

        // --- Resolve the current stack into known ingredients ---------------
        val stackIng = currentStack.mapNotNull { item ->
            IngredientCatalog.match(item.name)?.let { it to item }
        }
        val stackKeys = stackIng.map { it.first.key }.toSet()

        // --- Deterministic sections (the validated read) --------------------
        val contextLines = mutableListOf<AnalysisLine>()
        if (!intent.isNullOrBlank()) contextLines += AnalysisLine("You're adding this for: ${intent.trim()}")
        if (matched != null) {
            matched.benefits.forEach { contextLines += AnalysisLine(it, Severity.GOOD) }
            grounded += "${matched.displayName}: ${matched.benefits.joinToString("; ")}"
            contextLines += AnalysisLine(
                "Typical range ${doseRange(matched)} · ${matched.category.lowercase()} · ${tierWord(matched.tier)}",
            )
        } else {
            contextLines += AnalysisLine("Not in the curated reference yet — basing this on what you entered.")
        }

        // --- Fit: synergies, conflicts, timing against the current stack ----
        val fitLines = mutableListOf<AnalysisLine>()
        val dependencies = mutableListOf<String>()
        val cautions = mutableListOf<String>()
        if (matched != null) {
            // synergies already present
            stackIng.forEach { (ing, _) ->
                if (matched.pairsWith.contains(ing.key) || ing.pairsWith.contains(matched.key)) {
                    fitLines += AnalysisLine("Pairs well with ${ing.displayName}, already in your stack", Severity.GOOD)
                }
            }
            // synergies still missing → dependencies to add
            matched.pairsWith.forEach { k ->
                if (k !in stackKeys) {
                    IngredientCatalog.byKey(k)?.let { dep ->
                        dependencies += dep.displayName
                        fitLines += AnalysisLine("Often paired with ${dep.displayName} — consider adding it", Severity.NEUTRAL)
                    }
                }
            }
            // conflicts present in the stack
            stackIng.forEach { (ing, _) ->
                if (matched.avoidWith.contains(ing.key) || ing.avoidWith.contains(matched.key)) {
                    val msg = "Separate in time from ${ing.displayName} (they compete for absorption)"
                    fitLines += AnalysisLine(msg, Severity.CAUTION)
                    cautions += msg
                    grounded += "${matched.displayName} ↔ ${ing.displayName}: separate timing"
                }
            }
            timingLine(matched)?.let { fitLines += it }
            if (fitLines.none { it.severity == Severity.CAUTION } && stackIng.isNotEmpty()) {
                fitLines += AnalysisLine("No interaction flags with your current stack", Severity.GOOD)
            }
        }

        // --- Peer review & recent research (from curated evidence) ----------
        val peerLines = buildList {
            if (matched != null && matched.evidenceNote.isNotBlank()) {
                add(AnalysisLine(matched.evidenceNote, severityForTier(matched.tier)))
            }
            add(AnalysisLine("Summarised from curated references, not live scraping."))
        }
        val researchLines = buildList {
            if (matched != null) {
                when (matched.tier) {
                    1 -> add(AnalysisLine("Body of evidence is mature and broadly consistent.", Severity.GOOD))
                    2 -> add(AnalysisLine("Supportive evidence; still maturing.", Severity.NEUTRAL))
                    else -> add(AnalysisLine("Emerging/early evidence — promising but not settled.", Severity.CAUTION))
                }
            } else {
                add(AnalysisLine("No curated research flags for this one yet.", Severity.NEUTRAL))
            }
        }

        // --- Deterministic synthesis ----------------------------------------
        val verdict = when {
            matched == null -> "Worth tracking"
            cautions.isNotEmpty() -> "Fits — mind the timing"
            else -> "Good fit"
        }
        val placement = matched?.let { placementText(it) }
        var rationale = buildRationale(matched, intent, dependencies, cautions)

        // --- Model enrichment, scaled by tier -------------------------------
        var byModel = false
        if (tier.isModel && ModelCapability.engineReady(app)) {
            val enriched = runCatching {
                val engine = BrainProvider.engine(app)
                val prompt = synthesisPrompt(name, matched, intent, stackIng.map { it.first.displayName }, dependencies, cautions, tier)
                engine.complete(prompt).trim()
            }.getOrNull()
            val cleaned = enriched?.let { sanitize(it) }
            if (!cleaned.isNullOrBlank()) {
                rationale = cleaned
                byModel = true
            }
        }

        // --- Accrue facts/deductions into the reusable context store --------
        runCatching {
            val itemKey = name
            ContextStore.recordIntent(app, itemKey, intent.orEmpty())
            val entries = buildList {
                grounded.forEach { add(ContextStore.entry(ContextKind.FACT, it, "catalog", 1f)) }
                cautions.forEach { add(ContextStore.entry(ContextKind.DEDUCTION, it, if (byModel) "model+catalog" else "logic", 0.9f)) }
                add(ContextStore.entry(ContextKind.DEDUCTION, "$verdict — $rationale", if (byModel) "model" else "logic", if (byModel) 0.8f else 0.7f))
            }
            ContextStore.add(app, itemKey, entries)
        }

        return AnalysisReport(
            title = matched?.displayName ?: name,
            matchedIngredientKey = matched?.key,
            sections = listOf(
                AnalysisSection("Context", contextLines),
                AnalysisSection("Fit with your stack", fitLines.ifEmpty { listOf(AnalysisLine("Your stack is empty — this would be the first.")) }),
                AnalysisSection("Peer review", peerLines),
                AnalysisSection("Recent research", researchLines),
            ),
            synthesis = Synthesis(
                verdict = verdict,
                rationale = rationale,
                placement = placement,
                dependencies = dependencies,
                cautions = cautions,
            ),
            grounded = grounded,
            byModel = byModel,
        )
    }

    // --- helpers -----------------------------------------------------------

    private fun doseRange(i: Ingredient): String {
        fun n(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
        val unit = i.doseUnit.name.lowercase()
        return if (i.typicalDoseLow == i.typicalDoseHigh) "${n(i.typicalDoseLow)} $unit"
        else "${n(i.typicalDoseLow)}–${n(i.typicalDoseHigh)} $unit"
    }

    private fun tierWord(tier: Int) = when (tier) {
        1 -> "well-established"; 2 -> "supportive evidence"; else -> "emerging"
    }

    private fun severityForTier(tier: Int) = when (tier) {
        1 -> Severity.GOOD; 2 -> Severity.NEUTRAL; else -> Severity.CAUTION
    }

    private fun timingLine(i: Ingredient): AnalysisLine? {
        val f = i.timingFlags
        val parts = buildList {
            if (f and ItemFlags.FAT_SOLUBLE != 0 || f and ItemFlags.WITH_FOOD != 0) add("with a meal")
            if (f and ItemFlags.EMPTY_STOMACH != 0 || f and ItemFlags.FASTED != 0) add("on an empty stomach")
            if (f and ItemFlags.AVOID_CAFFEINE != 0) add("away from tea/coffee")
            if (f and ItemFlags.AVOID_CALCIUM != 0) add("away from calcium")
        }
        return if (parts.isEmpty()) null else AnalysisLine("Best taken ${parts.joinToString(", ")}")
    }

    private fun placementText(i: Ingredient): String? {
        val f = i.timingFlags
        return when {
            f and ItemFlags.EMPTY_STOMACH != 0 || f and ItemFlags.FASTED != 0 -> "Morning, fasted — clear of food and tea/coffee."
            f and ItemFlags.FAT_SOLUBLE != 0 || f and ItemFlags.WITH_FOOD != 0 -> "With your main meal so it absorbs with fat."
            i.category == "Sleep" -> "Evening, before sleep."
            else -> null
        }
    }

    private fun buildRationale(
        matched: Ingredient?,
        intent: String?,
        deps: List<String>,
        cautions: List<String>,
    ): String = buildString {
        if (matched == null) {
            append("Not in the curated reference, so this is tracked on your input. You can still add it and refine later.")
            return@buildString
        }
        append("${matched.displayName} is ${tierWord(matched.tier)}")
        if (!intent.isNullOrBlank()) append(" and lines up with your reason for adding it")
        append(". ")
        if (cautions.isNotEmpty()) append("Keep it apart from ${cautions.size} interacting item${if (cautions.size > 1) "s" else ""} in your stack. ")
        if (deps.isNotEmpty()) append("Pairs best with ${deps.joinToString(", ")}.")
        if (cautions.isEmpty() && deps.isEmpty()) append("It slots into your stack without conflicts.")
    }.trim()

    private fun synthesisPrompt(
        name: String,
        matched: Ingredient?,
        intent: String?,
        stack: List<String>,
        deps: List<String>,
        cautions: List<String>,
        tier: ModelTier,
    ): String = buildString {
        appendLine("You are a calm, precise supplement assistant. Using ONLY the grounded facts below,")
        appendLine("write a short synthesis (${if (tier == ModelTier.CAPABLE) "2-3 sentences" else "1-2 sentences"}) on whether and how this fits.")
        appendLine("Do NOT invent doses or interactions. No medical claims. Hedged, plain language.")
        appendLine()
        appendLine("Item: $name${matched?.let { " (${it.displayName})" } ?: ""}")
        if (!intent.isNullOrBlank()) appendLine("User's reason: $intent")
        if (matched != null) {
            appendLine("Facts: ${matched.benefits.joinToString("; ")}. Evidence: ${matched.evidenceNote} (${tierWord(matched.tier)}).")
        }
        appendLine("Current stack: ${if (stack.isEmpty()) "empty" else stack.joinToString(", ")}")
        if (deps.isNotEmpty()) appendLine("Suggested pairings: ${deps.joinToString(", ")}")
        if (cautions.isNotEmpty()) appendLine("Cautions: ${cautions.joinToString("; ")}")
        appendLine()
        appendLine("Reply with only the synthesis text.")
    }

    private fun sanitize(raw: String): String? {
        val cleaned = raw.lineSequence()
            .map { it.trim().removePrefix("- ").removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        return cleaned.takeIf { it.length in 10..600 }
    }
}
