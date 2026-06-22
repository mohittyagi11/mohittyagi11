package com.quietdose.brain.analysis

import android.content.Context
import com.quietdose.brain.BrainProvider
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemFlags
import com.quietdose.data.model.TriggerType

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
        groups: List<GroupEntity>,
        currentDoseAmount: Double,
        currentDoseUnit: DoseUnit,
        goal: String?,
        source: SourceKind?,
        sourceName: String?,
        concern: String?,
    ): AnalysisReport {
        val app = context.applicationContext
        val tier = ModelCapability.tier(app)
        val matched = IngredientCatalog.match(name)
        val grounded = mutableListOf<String>()
        val sections = mutableListOf<AnalysisSection>()
        val cautions = mutableListOf<String>()
        val dependencies = mutableListOf<String>()

        val stackIng = currentStack.mapNotNull { item ->
            IngredientCatalog.match(item.name)?.let { it to item }
        }
        val stackKeys = stackIng.map { it.first.key }.toSet()
        val alreadyHave = matched != null && matched.key in stackKeys

        // 1) Your reason — source + goal, with an honest credibility read.
        val reasonLines = mutableListOf<AnalysisLine>()
        if (source != null) {
            val who = if (!sourceName.isNullOrBlank()) "${source.label}: ${sourceName.trim()}" else source.label
            reasonLines += AnalysisLine("Heard about it from — $who", if (source.skeptical) Severity.CAUTION else Severity.GOOD)
            reasonLines += AnalysisLine(source.trust, if (source.skeptical) Severity.CAUTION else Severity.NEUTRAL)
            grounded += "Source: $who (${if (source.skeptical) "treat as a lead" else "credible"})"
        }
        if (!goal.isNullOrBlank()) reasonLines += AnalysisLine("Your goal — ${goal.trim()}")
        if (!concern.isNullOrBlank()) {
            reasonLines += AnalysisLine("Your worry — ${concern.trim()}. Kept in view below.", Severity.NEUTRAL)
        }
        if (reasonLines.isNotEmpty()) sections += AnalysisSection("Your reason", reasonLines)

        // 2) What it's good for.
        val goodLines = mutableListOf<AnalysisLine>()
        if (matched != null) {
            matched.benefits.forEach { goodLines += AnalysisLine(it, Severity.GOOD) }
            goodLines += AnalysisLine("Typical ${doseRange(matched)} \u00b7 ${tierWord(matched.tier)}")
            grounded += "${matched.displayName}: ${matched.benefits.joinToString("; ")}"
            if (!goal.isNullOrBlank()) {
                goodLines += AnalysisLine("Weigh these against your goal — does it really target it?", Severity.NEUTRAL)
            }
        } else {
            goodLines += AnalysisLine("Not in the curated reference yet — going on what you entered.")
        }
        sections += AnalysisSection("What it's good for", goodLines)

        // 3) Fit with your stack — synergy, pairings to add, dose-unit sanity.
        //    (Redundancy & absorption conflicts live in the structured safety review.)
        val fitLines = mutableListOf<AnalysisLine>()
        if (matched != null) {
            stackIng.forEach { (ing, _) ->
                if (matched.pairsWith.contains(ing.key) || ing.pairsWith.contains(matched.key)) {
                    fitLines += AnalysisLine("Pairs well with ${ing.displayName}, already in your stack", Severity.GOOD)
                }
            }
            matched.pairsWith.forEach { k ->
                if (k !in stackKeys) IngredientCatalog.byKey(k)?.let { dep ->
                    dependencies += dep.displayName
                    fitLines += AnalysisLine("Often paired with ${dep.displayName} — consider adding it")
                }
            }
            if (currentDoseUnit != matched.doseUnit && currentDoseAmount > 0) {
                val m = "You entered ${fmt(currentDoseAmount)} ${currentDoseUnit.name.lowercase()}, but ${matched.displayName} is usually ${matched.doseUnit.name.lowercase()} (${doseRange(matched)}) — check the unit"
                fitLines += AnalysisLine(m, Severity.CAUTION)
                grounded += m
            }
            if (fitLines.none { it.severity == Severity.CAUTION } && stackIng.isNotEmpty()) {
                fitLines += AnalysisLine("Sits alongside your current stack cleanly", Severity.GOOD)
            }
        }
        if (fitLines.isEmpty()) {
            fitLines += AnalysisLine(if (stackIng.isEmpty()) "Your stack is empty — this would be the first." else "Nothing notable against your current stack.")
        }
        cautions += fitLines.filter { it.severity == Severity.CAUTION }.map { it.text }
        sections += AnalysisSection("Fit with your stack", fitLines)

        // 4) Complete safety review — the SafetyCategory taxonomy, walked in order.
        val recDoseForSafety = if (matched != null) {
            roundDose((matched.typicalDoseLow + matched.typicalDoseHigh) / 2.0)
        } else currentDoseAmount
        val (safety, safetyClear) = buildSafetyReview(
            matched, currentDoseAmount, currentDoseUnit, recDoseForSafety, stackIng, alreadyHave, source,
        )
        cautions += safety.filter { it.severity == Severity.CAUTION }.map { it.text }
        grounded += safety.filter { it.severity == Severity.CAUTION }.map { "Safety/${it.category.title}: ${it.text}" }
        // De-duplicate so the synthesis and verdict don't double-count.
        val cautionsDistinct = cautions.distinct()
        cautions.clear(); cautions.addAll(cautionsDistinct)

        // Verdict + rationale.
        val verdict = when {
            alreadyHave -> "Already in your stack"
            matched == null -> "Worth tracking"
            source?.skeptical == true && cautions.isNotEmpty() -> "Promising — verify first"
            cautions.isNotEmpty() -> "Fits — mind the details"
            else -> "Good fit"
        }
        val placement = matched?.let { placementText(it) }
        var rationale = buildRationale(matched, goal, dependencies, cautions)
        var byModel = false
        if (tier.isModel && ModelCapability.engineReady(app)) {
            val enriched = runCatching {
                BrainProvider.engine(app).complete(
                    synthesisPrompt(name, matched, goal, source, concern, stackIng.map { it.first.displayName }, dependencies, safety, safetyClear, tier),
                ).trim()
            }.getOrNull()
            val cleaned = enriched?.let { sanitize(it) }
            if (!cleaned.isNullOrBlank()) { rationale = cleaned; byModel = true }
        }

        // Accrue context for reuse.
        runCatching {
            val itemKey = name
            source?.let {
                ContextStore.recordIntent(app, itemKey, "source=${it.label}" + if (!sourceName.isNullOrBlank()) " (${sourceName.trim()})" else "")
            }
            if (!goal.isNullOrBlank()) ContextStore.recordIntent(app, itemKey, "goal=${goal.trim()}")
            val entries = buildList {
                grounded.forEach { add(ContextStore.entry(ContextKind.FACT, it, "catalog", 1f)) }
                cautions.forEach { add(ContextStore.entry(ContextKind.DEDUCTION, it, if (byModel) "model+catalog" else "logic", 0.9f)) }
                add(ContextStore.entry(ContextKind.DEDUCTION, "$verdict \u2014 $rationale", if (byModel) "model" else "logic", 0.8f))
            }
            ContextStore.add(app, itemKey, entries)
        }

        // --- Adjustable recommendation (where / dose / timing / pairings) ---
        val recDose = if (matched != null) {
            roundDose((matched.typicalDoseLow + matched.typicalDoseHigh) / 2.0)
        } else {
            currentDoseAmount
        }
        val recUnit = matched?.doseUnit ?: currentDoseUnit
        val (recGroupId, groupReason) = pickGroup(groups, matched)
        val recommendation = Recommendation(
            groupId = recGroupId,
            groupReason = groupReason,
            doseAmount = recDose,
            doseUnit = recUnit,
            typicalLow = matched?.typicalDoseLow,
            typicalHigh = matched?.typicalDoseHigh,
            flags = matched?.timingFlags ?: 0,
            pairings = dependencies,
        )

        return AnalysisReport(
            title = matched?.displayName ?: name,
            matchedIngredientKey = matched?.key,
            recommendation = recommendation,
            sections = sections,
            synthesis = Synthesis(
                verdict = verdict,
                rationale = rationale,
                placement = placement,
                dependencies = dependencies,
                cautions = cautions,
            ),
            safety = safety,
            safetyReviewedClear = safetyClear,
            grounded = grounded.distinct(),
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

    private fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

    private fun severityForTier(tier: Int) = when (tier) {
        1 -> Severity.GOOD; 2 -> Severity.NEUTRAL; else -> Severity.CAUTION
    }

    /** Best-fit existing group for the item's timing, with a short reason. */
    private fun pickGroup(groups: List<GroupEntity>, matched: Ingredient?): Pair<Long?, String?> {
        if (groups.isEmpty()) return null to null
        val flags = matched?.timingFlags ?: 0
        fun firstWith(vararg t: TriggerType) = groups.firstOrNull { it.trigger in t }
        val (chosen, reason) = when {
            flags and ItemFlags.EMPTY_STOMACH != 0 || flags and ItemFlags.FASTED != 0 ->
                firstWith(TriggerType.WAKE) to "fasted → morning"
            matched?.category == "Sleep" ->
                firstWith(TriggerType.BEFORE_SLEEP) to "evening → before sleep"
            flags and ItemFlags.WITH_FOOD != 0 || flags and ItemFlags.FAT_SOLUBLE != 0 ->
                firstWith(TriggerType.ARRIVE_HOME, TriggerType.TIME_WINDOW) to "with food → mealtime"
            else -> null to null
        }
        return (chosen?.id ?: groups.first().id) to reason
    }

    /** Round to a tidy dose value for the suggested midpoint. */
    private fun roundDose(d: Double): Double = when {
        d >= 100 -> (Math.round(d / 50.0) * 50).toDouble()
        d >= 10 -> (Math.round(d / 5.0) * 5).toDouble()
        d % 1.0 == 0.0 -> d
        else -> Math.round(d * 10.0) / 10.0
    }

    private fun timingText(i: Ingredient): String? {
        val f = i.timingFlags
        val parts = buildList {
            if (f and ItemFlags.FAT_SOLUBLE != 0 || f and ItemFlags.WITH_FOOD != 0) add("with a meal")
            if (f and ItemFlags.EMPTY_STOMACH != 0 || f and ItemFlags.FASTED != 0) add("on an empty stomach")
            if (f and ItemFlags.AVOID_CAFFEINE != 0) add("away from tea/coffee")
            if (f and ItemFlags.AVOID_CALCIUM != 0) add("away from calcium")
        }
        return if (parts.isEmpty()) null else "Best taken ${parts.joinToString(", ")}"
    }

    private fun evidenceMaturity(tier: Int): String = when (tier) {
        1 -> "Evidence is mature and broadly consistent."
        2 -> "Evidence is supportive but still maturing."
        else -> "Evidence is emerging — promising, not settled."
    }

    /**
     * Walk the full [SafetyCategory] taxonomy in order, filling each category from
     * the validated facts. Returns the findings plus the categories that were
     * genuinely reviewed and came back clear — so the report shows both what's
     * flagged and what was checked. This is the structured logic tree the model
     * then reasons within (it never adds categories or invents findings).
     */
    private fun buildSafetyReview(
        matched: Ingredient?,
        enteredDose: Double,
        enteredUnit: DoseUnit,
        recommendedDose: Double,
        stackIng: List<Pair<Ingredient, ItemEntity>>,
        alreadyHave: Boolean,
        source: SourceKind?,
    ): Pair<List<SafetyFinding>, List<SafetyCategory>> {
        val findings = mutableListOf<SafetyFinding>()
        val reviewed = linkedSetOf<SafetyCategory>()
        val flagged = mutableSetOf<SafetyCategory>()
        fun flag(cat: SafetyCategory, text: String, sev: Severity) {
            findings += SafetyFinding(cat, text, sev); reviewed += cat
            if (sev == Severity.CAUTION) flagged += cat
        }
        fun reviewedClear(cat: SafetyCategory) { reviewed += cat }

        // DOSE_CEILING — only when we have a firm upper limit encoded.
        if (matched?.upperLimitDose != null) {
            val ul = matched.upperLimitDose
            val unit = matched.doseUnit.name.lowercase()
            val entered = if (enteredUnit == matched.doseUnit && enteredDose > 0) enteredDose else null
            when {
                entered != null && entered > ul ->
                    flag(SafetyCategory.DOSE_CEILING, "Your ${fmt(entered)} $unit is over the usual upper limit of ${fmt(ul)} $unit — lower it unless a clinician set it.", Severity.CAUTION)
                entered != null && entered > ul * 0.8 ->
                    flag(SafetyCategory.DOSE_CEILING, "Your dose is close to the upper limit (${fmt(ul)} $unit) — stay under it.", Severity.CAUTION)
                recommendedDose > ul ->
                    flag(SafetyCategory.DOSE_CEILING, "Keep the daily total under the upper limit of ${fmt(ul)} $unit.", Severity.NEUTRAL)
                else -> reviewedClear(SafetyCategory.DOSE_CEILING)
            }
        }

        // DRUG_INTERACTIONS
        if (matched?.drugInteractions?.isNotEmpty() == true) {
            flag(SafetyCategory.DRUG_INTERACTIONS, "Can interact with ${matched.drugInteractions.joinToString(", ")}. If you take any, check with whoever prescribes them.", Severity.CAUTION)
        } else if (matched != null) {
            reviewedClear(SafetyCategory.DRUG_INTERACTIONS)
        }

        // STACK_OVERLAP — exact duplicate, absorption competition, same-purpose overlap.
        if (matched != null) {
            if (alreadyHave) {
                flag(SafetyCategory.STACK_OVERLAP, "You already have ${matched.displayName} — adding it again may double the dose.", Severity.CAUTION)
            }
            stackIng.forEach { (ing, _) ->
                if (ing.key != matched.key && (matched.avoidWith.contains(ing.key) || ing.avoidWith.contains(matched.key))) {
                    flag(SafetyCategory.STACK_OVERLAP, "Competes with ${ing.displayName} for absorption — take them a couple of hours apart.", Severity.CAUTION)
                }
            }
            val samePurpose = stackIng.filter { it.first.key != matched.key && it.first.category == matched.category }
            if (samePurpose.isNotEmpty() && matched.category in OVERLAP_SENSITIVE) {
                flag(SafetyCategory.STACK_OVERLAP, "Overlaps with ${samePurpose.joinToString(", ") { it.first.displayName }} for ${matched.category.lowercase()} — the combined effect can be stronger than expected.", Severity.NEUTRAL)
            }
            if (SafetyCategory.STACK_OVERLAP !in reviewed) reviewedClear(SafetyCategory.STACK_OVERLAP)
        }

        // CONTRAINDICATIONS
        if (matched?.contraindications?.isNotEmpty() == true) {
            matched.contraindications.forEach { flag(SafetyCategory.CONTRAINDICATIONS, it, Severity.CAUTION) }
        } else if (matched != null) {
            reviewedClear(SafetyCategory.CONTRAINDICATIONS)
        }

        // SIDE_EFFECTS
        if (matched?.sideEffects?.isNotEmpty() == true) {
            flag(SafetyCategory.SIDE_EFFECTS, "Usually mild: ${matched.sideEffects.joinToString(", ")}.", Severity.NEUTRAL)
        } else if (matched != null) {
            reviewedClear(SafetyCategory.SIDE_EFFECTS)
        }

        // TIMING
        matched?.let { ing ->
            val t = timingText(ing)
            if (t != null) flag(SafetyCategory.TIMING, t, Severity.NEUTRAL) else reviewedClear(SafetyCategory.TIMING)
        }

        // QUALITY_FORM & DURATION — routed from curated cautions by intent.
        matched?.cautions?.forEach { c ->
            val isDuration = listOf("cycl", "indefinit", "occasional", "short").any { c.contains(it, ignoreCase = true) }
            flag(if (isDuration) SafetyCategory.DURATION else SafetyCategory.QUALITY_FORM, c, Severity.NEUTRAL)
        }

        // EVIDENCE — always informational when matched.
        matched?.let { flag(SafetyCategory.EVIDENCE, evidenceMaturity(it.tier), severityForTier(it.tier)) }

        // SOURCE
        if (source != null) {
            if (source.skeptical) {
                flag(SafetyCategory.SOURCE, source.trust, Severity.CAUTION)
                source.verify.forEach { flag(SafetyCategory.SOURCE, "Verify — $it", Severity.NEUTRAL) }
            } else {
                flag(SafetyCategory.SOURCE, source.trust, Severity.GOOD)
            }
        }

        val clear = reviewed.filter { it !in flagged }
        // Keep findings in taxonomy order for a stable, structured read.
        val ordered = findings.sortedBy { it.category.ordinal }
        return ordered to clear
    }

    private val OVERLAP_SENSITIVE = setOf("Sleep")

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
        goal: String?,
        source: SourceKind?,
        concern: String?,
        stack: List<String>,
        deps: List<String>,
        safety: List<SafetyFinding>,
        safetyClear: List<SafetyCategory>,
        tier: ModelTier,
    ): String = buildString {
        appendLine("You are a calm, precise, slightly skeptical supplement advisor. Using ONLY the grounded facts below,")
        appendLine("write a short, natural synthesis (${if (tier == ModelTier.CAPABLE) "2-3 sentences" else "1-2 sentences"}) — conversational, no bullet labels.")
        appendLine("Reason WITHIN the fixed safety framework below — do not introduce new categories, doses, or interactions.")
        appendLine("Lead with their goal and the source's credibility; if a safety category is flagged, weave the most important one in plainly. No medical claims.")
        appendLine()
        appendLine("Item: $name${matched?.let { " (${it.displayName})" } ?: ""}")
        if (!goal.isNullOrBlank()) appendLine("Their goal: $goal")
        if (source != null) appendLine("Heard from: ${source.label} (${if (source.skeptical) "weak source — verify" else "credible"})")
        if (!concern.isNullOrBlank()) appendLine("Their worry: $concern")
        if (matched != null) {
            appendLine("Benefits: ${matched.benefits.joinToString("; ")}.")
        }
        appendLine("Current stack: ${if (stack.isEmpty()) "empty" else stack.joinToString(", ")}")
        if (deps.isNotEmpty()) appendLine("Suggested pairings: ${deps.joinToString(", ")}")
        appendLine()
        appendLine("SAFETY FRAMEWORK (the only dimensions you may reason over):")
        SafetyCategory.entries.forEach { cat ->
            val hits = safety.filter { it.category == cat }
            val state = when {
                hits.any { it.severity == Severity.CAUTION } -> "FLAGGED: " + hits.joinToString(" | ") { it.text }
                hits.isNotEmpty() -> "noted: " + hits.joinToString(" | ") { it.text }
                cat in safetyClear -> "checked, clear"
                else -> "not assessed"
            }
            appendLine("- ${cat.title}: $state")
        }
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
