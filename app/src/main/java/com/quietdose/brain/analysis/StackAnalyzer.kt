package com.quietdose.brain.analysis

import android.content.Context
import com.quietdose.brain.BrainProvider
import com.quietdose.brain.web.WebSearch
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
        product: ProductSignals? = null,
        onProgress: ((AnalysisProgress) -> Unit)? = null,
    ): AnalysisReport {
        fun emit(label: String, commentary: String, mood: Mood, fraction: Float) =
            onProgress?.invoke(AnalysisProgress(label, commentary, mood, fraction))
        emit("Settling in", "Taking a first look at ${name.ifBlank { "this" }}…", Mood.CALM, 0.04f)
        val app = context.applicationContext
        val tier = ModelCapability.tier(app)
        val engineUp = tier.isModel && ModelCapability.engineReady(app)

        // Pick the lens. Anything that isn't a supplement (a toner, a device…) must NOT
        // be judged with dose/upper-limit/bioavailability — it gets the category-aware,
        // SLM-profiled path with the right fields (how & when to use, not a "dose").
        val kind = KindDetector.detect(name, product?.ingredientsText)
        if (kind != ItemKind.SUPPLEMENT) {
            return analyzeProfile(app, engineUp, tier, kind, name, currentStack, currentDoseAmount, currentDoseUnit, groups, source, goal, concern, product, onProgress)
        }

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

        // Ingredientize: parse the label/description (or fall back to the name).
        val ingredients = IngredientParser.parse(product?.ingredientsText, name)
        val isFormula = IngredientParser.isFormula(ingredients)

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

        // --- Live web research: what people actually say (key-free, time-boxed) ---
        emit("Listening to the web", "Seeing what reviewers and users actually say about it…", Mood.CURIOUS, 0.14f)
        val webResults = runCatching { researchWeb(name, product?.brand) }.getOrDefault(emptyList())
        webResults.take(3).forEach { grounded += "Web/${it.domain}: ${it.title}" }
        if (webResults.isNotEmpty()) {
            emit("Heard the room", "Picked up ${webResults.size} voice${if (webResults.size > 1) "s" else ""} from around the web.", Mood.CURIOUS, 0.22f)
        }

        // The actual raw material the model reasons over — the product's own text
        // (label/description or OCR) plus what the web says. This is what was missing:
        // the SLM was fed pre-digested bullets, not the real source.
        val sourceMaterial = buildSourceMaterial(product?.ingredientsText, webResults)

        // Brand reputation — the model's own hedged read, clearly labelled (opt-in).
        val brandTake: String? = if (engineUp && !product?.brand.isNullOrBlank()) {
            emit("Sizing up the brand", "Forming an honest impression of ${product!!.brand}…", Mood.SKEPTICAL, 0.28f)
            runCatching {
                BrainProvider.engine(app).complete(brandTakePrompt(product.brand!!, name)).trim()
            }.getOrNull()?.let { sanitize(it) }
        } else null

        emit("Mapping it out", "Laying out what matters, chapter by chapter…", Mood.CALM, 0.32f)
        // --- Aspect chapters: small grounded contexts, one per AnalysisAspect ---
        var aspectBlocks = buildAspectBlocks(
            matched, goodLines, fitLines, safety, safetyClear, source, goal, concern,
            currentDoseAmount, currentDoseUnit, dependencies, product, stackIng, alreadyHave,
            webResults, brandTake,
        )

        // Verdict label is deterministic; the rationale + per-aspect prose come from the model.
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
        // Walk the chapters out loud: emit each as a "thought", and on a capable
        // model let it write that chapter's prose (small contexts) as it goes.
        val total = aspectBlocks.size.coerceAtLeast(1)
        aspectBlocks = aspectBlocks.mapIndexed { i, block ->
            emit(block.aspect.title, chapterCommentary(block), moodFor(block), 0.35f + 0.5f * (i.toFloat() / total))
            if (engineUp && tier == ModelTier.CAPABLE && block.state != AspectState.NOT_ASSESSED) {
                val enriched = runCatching {
                    BrainProvider.engine(app).complete(aspectPrompt(name, matched, block, goal, concern, sourceMaterial)).trim()
                }.getOrNull()?.let { sanitize(it) }
                if (!enriched.isNullOrBlank()) { byModel = true; block.copy(summary = enriched) } else block
            } else block
        }
        // …then step back and synthesize the larger picture from those contexts.
        emit("The big picture", "Stepping back to weigh it all together…", Mood.REFLECTIVE, 0.9f)
        if (engineUp) {
            val enriched = runCatching {
                BrainProvider.engine(app).complete(
                    verdictPrompt(name, matched, goal, source, concern, verdict, aspectBlocks, sourceMaterial, tier),
                ).trim()
            }.getOrNull()?.let { sanitize(it) }
            if (!enriched.isNullOrBlank()) { rationale = enriched; byModel = true }
        }

        // Overall score = weighted roll-up of the chapter scores (safety counts double).
        val overallScore = rollUpScore(aspectBlocks)
        val verdictTags = buildList {
            add(verdict)
            if (isFormula) add("Formula · ${ingredients.size} ingredients") else if (ingredients.size == 1) add("Single ingredient")
            if (source?.skeptical == true) add("Verify source")
            if (alreadyHave) add("Possible duplicate")
            if (dependencies.isNotEmpty()) add("Pairs with ${dependencies.first()}")
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

        // --- Compose the dynamic page: big picture first, then facts, chapters, trail ---
        val blocks = buildList<ReportBlock> {
            add(ReportBlock.Verdict(verdict, rationale, overallScore, verdictTags, byModel))
            buildFactsBlock(matched, product)?.let { add(it) }
            buildIngredientsBlock(ingredients)?.let { add(it) }
            buildDoseMeter(matched, recDose, recUnit)?.let { add(it) }
            addAll(aspectBlocks)
            if (grounded.isNotEmpty()) add(ReportBlock.Reasoning(grounded.distinct().take(8), byModel))
        }

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
            blocks = blocks,
            ingredients = ingredients,
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

    // --- Aspect chapters (the dynamic page's small contexts) ----------------

    private fun stateFor(score: Int?): AspectState = when {
        score == null -> AspectState.NOT_ASSESSED
        score >= 80 -> AspectState.GOOD
        score >= 60 -> AspectState.MIXED
        else -> AspectState.CAUTION
    }

    private fun round2(d: Double): Double = Math.round(d * 100.0) / 100.0

    private fun rollUpScore(blocks: List<ReportBlock.Aspect>): Int? {
        val weighted = blocks.mapNotNull { b ->
            b.score?.let { s -> (if (b.aspect == AnalysisAspect.SAFETY) 2 else 1) to s }
        }
        if (weighted.isEmpty()) return null
        val num = weighted.sumOf { it.first * it.second }
        val den = weighted.sumOf { it.first }
        return Math.round(num.toDouble() / den).toInt()
    }

    /** Naive but honest goal-alignment: keyword overlap with benefits/category/name. */
    private fun goalAligns(goal: String, matched: Ingredient): Boolean {
        val g = goal.lowercase()
        val hay = (matched.benefits + matched.category + matched.displayName).joinToString(" ").lowercase()
        val tokens = g.split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }
        return tokens.any { hay.contains(it) }
    }

    private fun buildAspectBlocks(
        matched: Ingredient?,
        goodLines: List<AnalysisLine>,
        fitLines: List<AnalysisLine>,
        safety: List<SafetyFinding>,
        safetyClear: List<SafetyCategory>,
        source: SourceKind?,
        goal: String?,
        concern: String?,
        enteredDose: Double,
        enteredUnit: DoseUnit,
        dependencies: List<String>,
        product: ProductSignals?,
        stackIng: List<Pair<Ingredient, ItemEntity>>,
        alreadyHave: Boolean,
        webResults: List<WebSearch.WebResult>,
        brandTake: String?,
    ): List<ReportBlock.Aspect> {
        val out = mutableListOf<ReportBlock.Aspect>()
        fun add(aspect: AnalysisAspect, lines: List<AnalysisLine>, score: Int?, state: AspectState, summary: String) {
            out += ReportBlock.Aspect(aspect, summary, lines.ifEmpty { listOf(AnalysisLine("Nothing notable here.")) }, score, state)
        }

        // WHAT_FOR
        run {
            val lines = mutableListOf<AnalysisLine>()
            if (matched != null) {
                matched.benefits.forEach { lines += AnalysisLine(it, Severity.GOOD) }
                lines += AnalysisLine("It supports these — it won't fix everything around them.", Severity.NEUTRAL)
            } else lines += AnalysisLine("Not in the curated reference yet — tracked on what you entered.")
            val score = matched?.let { when (it.tier) { 1 -> 90; 2 -> 75; else -> 60 } } ?: 50
            val sum = if (matched != null) "Mainly for ${matched.benefits.firstOrNull()?.lowercase() ?: "general support"}." else "Tracked on what you entered."
            add(AnalysisAspect.WHAT_FOR, lines, score, stateFor(score), sum)
        }

        // QUALITY & bioavailability
        run {
            val lines = mutableListOf<AnalysisLine>()
            var score = 75
            if (matched != null) {
                val f = matched.timingFlags
                if (f and ItemFlags.FAT_SOLUBLE != 0) lines += AnalysisLine("Fat-soluble — needs dietary fat to absorb well", Severity.NEUTRAL)
                val unit = matched.doseUnit.name.lowercase()
                if (enteredUnit == matched.doseUnit && enteredDose > 0) {
                    when {
                        enteredDose < matched.typicalDoseLow * 0.5 -> { lines += AnalysisLine("Your ${fmt(enteredDose)} $unit looks under-dosed vs the typical ${doseRange(matched)}", Severity.CAUTION); score = 50 }
                        enteredDose in matched.typicalDoseLow..matched.typicalDoseHigh -> { lines += AnalysisLine("Your dose sits in the effective range (${doseRange(matched)})", Severity.GOOD); score = 85 }
                        enteredDose > matched.typicalDoseHigh -> { lines += AnalysisLine("Above the typical range (${doseRange(matched)}) — more isn't always better", Severity.CAUTION); score = 60 }
                        else -> lines += AnalysisLine("Near the effective range (${doseRange(matched)})")
                    }
                } else lines += AnalysisLine("Aim within the effective range, ${doseRange(matched)}", Severity.NEUTRAL)
            } else { lines += AnalysisLine("Can't judge form or dose without a reference match."); score = 50 }
            add(AnalysisAspect.QUALITY, lines, score, stateFor(score), "Form and dose checked against the typical effective range.")
        }

        // FIT_CONDITION
        run {
            if (goal.isNullOrBlank()) {
                add(AnalysisAspect.FIT_CONDITION, listOf(AnalysisLine("Tell me your goal and I'll judge the fit.")), null, AspectState.NOT_ASSESSED, "No goal given yet.")
            } else {
                val lines = mutableListOf<AnalysisLine>()
                val aligned = matched != null && goalAligns(goal, matched)
                if (aligned) lines += AnalysisLine("Looks aligned with “${goal.trim()}”", Severity.GOOD)
                else lines += AnalysisLine("Not an obvious match for “${goal.trim()}” — confirm it targets it", Severity.CAUTION)
                if (!concern.isNullOrBlank()) lines += AnalysisLine("Your worry — ${concern.trim()}", Severity.NEUTRAL)
                val score = if (aligned) 80 else 55
                add(AnalysisAspect.FIT_CONDITION, lines, score, stateFor(score), if (aligned) "Fits your stated goal." else "May not target your goal — verify.")
            }
        }

        // CLAIMS & honesty
        run {
            if (matched == null) {
                add(AnalysisAspect.CLAIMS, listOf(AnalysisLine("No reference claims to weigh.")), null, AspectState.NOT_ASSESSED, "No reference to compare claims against.")
            } else {
                val lines = mutableListOf<AnalysisLine>()
                lines += AnalysisLine("Marketed for: ${matched.benefits.joinToString(", ")}", Severity.NEUTRAL)
                val honesty = when (matched.tier) {
                    1 -> "Claims generally match the evidence."
                    2 -> "Some claims run ahead of the evidence."
                    else -> "Marketing likely overstates early findings."
                }
                lines += AnalysisLine(honesty, severityForTier(matched.tier))
                if (source?.skeptical == true) lines += AnalysisLine("From a promotional source — verify bold claims independently", Severity.CAUTION)
                var score = when (matched.tier) { 1 -> 85; 2 -> 70; else -> 55 }
                if (source?.skeptical == true) score -= 15
                add(AnalysisAspect.CLAIMS, lines, score, stateFor(score), honesty)
            }
        }

        // TRUST & source
        run {
            val lines = mutableListOf<AnalysisLine>()
            var score = 50
            if (source != null) {
                lines += AnalysisLine(source.trust, if (source.skeptical) Severity.CAUTION else Severity.GOOD)
                score = (source.credibility * 18).coerceAtMost(92)
                source.verify.forEach { lines += AnalysisLine("Verify — $it", Severity.NEUTRAL) }
            } else lines += AnalysisLine("No source given — add where you heard it for a credibility read.", Severity.NEUTRAL)
            matched?.let { lines += AnalysisLine("Research support: ${evidenceMaturity(it.tier).removeSuffix(".")}", severityForTier(it.tier)) }
            if (product?.ratingValue != null) {
                val cnt = product.ratingCount?.let { " across $it reviews" } ?: ""
                lines += AnalysisLine("Peers rate it ${product.ratingValue}/5$cnt", if (product.ratingValue >= 4.0) Severity.GOOD else Severity.NEUTRAL)
                if (product.ratingValue >= 4.3 && (product.ratingCount ?: 0) >= 50) score = (score + 6).coerceAtMost(95)
            } else lines += AnalysisLine("No peer rating captured — check reviews on the store page.", Severity.NEUTRAL)
            brandTake?.let { lines += AnalysisLine("Model's take on ${product?.brand ?: "the brand"} — $it (opinion, not verified)", Severity.NEUTRAL) }
            add(AnalysisAspect.TRUST_SOURCE, lines, score, stateFor(score), "Weighs the source, the research and what peers say.")
        }

        // REVIEWS — what the live web says (fetched, hedged as unverified).
        run {
            if (webResults.isEmpty()) {
                add(AnalysisAspect.REVIEWS, listOf(AnalysisLine("No web results pulled — add a link or check stores/Reddit yourself.")), null, AspectState.NOT_ASSESSED, "No web reviews fetched.")
            } else {
                val lines = mutableListOf<AnalysisLine>()
                webResults.take(5).forEach { r ->
                    val snip = r.snippet.take(160).ifBlank { r.title }
                    lines += AnalysisLine("${r.domain}: $snip", Severity.NEUTRAL)
                }
                lines += AnalysisLine("From the open web — weigh the source; treat as leads, not proof.", Severity.CAUTION)
                add(AnalysisAspect.REVIEWS, lines, null, AspectState.MIXED, "What reviewers and users are saying online.")
            }
        }

        // VALUE
        run {
            if (product?.priceText != null) {
                val lines = mutableListOf<AnalysisLine>()
                lines += AnalysisLine("Listed at ${product.priceText}", Severity.NEUTRAL)
                product.servings?.let { lines += AnalysisLine("~$it servings per pack", Severity.NEUTRAL) }
                if (product.priceAmount != null && (product.servings ?: 0) > 0) {
                    lines += AnalysisLine("≈ ${product.priceCurrency.orEmpty()} ${round2(product.priceAmount / product.servings!!)} per serving", Severity.NEUTRAL)
                }
                lines += AnalysisLine("Compare cost per day against other brands before committing.", Severity.NEUTRAL)
                add(AnalysisAspect.VALUE, lines, null, AspectState.MIXED, "Price captured — compare value across brands.")
            } else {
                add(AnalysisAspect.VALUE, listOf(AnalysisLine("No price captured — add it from a link to weigh value.")), null, AspectState.NOT_ASSESSED, "No price to assess.")
            }
        }

        // STACK
        run {
            val lines = mutableListOf<AnalysisLine>()
            lines += fitLines
            safety.filter { it.category == SafetyCategory.STACK_OVERLAP }.forEach { lines += AnalysisLine(it.text, it.severity) }
            val cautionCount = lines.count { it.severity == Severity.CAUTION }
            val score = if (stackIng.isEmpty()) null else (100 - 25 * cautionCount).coerceIn(30, 100)
            val state = if (stackIng.isEmpty()) AspectState.CLEAR else stateFor(score)
            val sum = when {
                stackIng.isEmpty() -> "Your stack is empty — this would be the first."
                cautionCount > 0 -> "Some overlap to manage with what you take."
                else -> "Sits alongside your stack cleanly."
            }
            add(AnalysisAspect.STACK, lines, score, state, sum)
        }

        // SAFETY (full taxonomy, in order)
        run {
            val lines = mutableListOf<AnalysisLine>()
            safety.sortedBy { it.category.ordinal }.forEach { lines += AnalysisLine("${it.category.title}: ${it.text}", it.severity) }
            if (safetyClear.isNotEmpty()) {
                lines += AnalysisLine("Checked & clear — ${safetyClear.sortedBy { it.ordinal }.joinToString(", ") { it.title.lowercase() }}", Severity.GOOD)
            }
            val cautionCount = safety.count { it.severity == Severity.CAUTION }
            val score = if (matched == null && safety.isEmpty()) null else (100 - 25 * cautionCount).coerceIn(30, 100)
            add(AnalysisAspect.SAFETY, lines, score, stateFor(score), if (cautionCount == 0) "Nothing major flagged." else "$cautionCount thing${if (cautionCount > 1) "s" else ""} to mind.")
        }

        return out
    }

    /** The brain's mood as it works a chapter — drives the screen's lighting. */
    private fun moodFor(block: ReportBlock.Aspect): Mood = when {
        block.aspect == AnalysisAspect.SAFETY && block.state == AspectState.CAUTION -> Mood.CAUTIOUS
        block.aspect == AnalysisAspect.REVIEWS -> Mood.CURIOUS
        block.aspect == AnalysisAspect.TRUST_SOURCE && block.state == AspectState.CAUTION -> Mood.SKEPTICAL
        block.state == AspectState.GOOD || block.state == AspectState.CLEAR -> Mood.FAVORABLE
        block.state == AspectState.CAUTION -> Mood.CAUTIOUS
        block.state == AspectState.MIXED -> Mood.CURIOUS
        else -> Mood.CALM
    }

    /** A short, first-person-ish line for the chapter being weighed right now. */
    private fun chapterCommentary(block: ReportBlock.Aspect): String {
        val base = when (block.aspect) {
            AnalysisAspect.WHAT_FOR -> "Getting a feel for what this really does"
            AnalysisAspect.QUALITY -> "Checking the form and dose — is it the real thing"
            AnalysisAspect.FIT_CONDITION -> "Does this actually fit what you're after"
            AnalysisAspect.CLAIMS -> "Holding the claims up to the evidence"
            AnalysisAspect.TRUST_SOURCE -> "Weighing who's vouching for it"
            AnalysisAspect.REVIEWS -> "Sitting with what people are saying"
            AnalysisAspect.VALUE -> "Asking whether it's worth the money"
            AnalysisAspect.STACK -> "Seeing how it sits with your stack"
            AnalysisAspect.SAFETY -> "Walking the safety checklist, carefully"
        }
        val tail = when (block.state) {
            AspectState.GOOD, AspectState.CLEAR -> " — looking good."
            AspectState.CAUTION -> " — something to flag."
            AspectState.MIXED -> " — it's nuanced."
            else -> "…"
        }
        return base + tail
    }

    /** Two time-boxed web queries — general reviews + red flags — merged and de-duped. */
    private suspend fun researchWeb(name: String, brand: String?): List<WebSearch.WebResult> {
        if (name.isBlank()) return emptyList()
        val base = listOfNotNull(brand?.takeIf { it.isNotBlank() }, name).joinToString(" ")
        val primary = WebSearch.search("$base supplement review", max = 4)
        val flags = WebSearch.search("$name side effects complaints", max = 3)
        val merged = LinkedHashMap<String, WebSearch.WebResult>()
        (primary + flags).forEach { r -> if (r.domain.isNotBlank()) merged.putIfAbsent(r.domain + r.title.take(16), r) }
        return merged.values.take(6).toList()
    }

    private fun brandTakePrompt(brand: String, name: String): String = buildString {
        appendLine("You are a cautious supplement advisor. In ONE short sentence (max 25 words), give your honest, hedged impression of the brand below for a product like this.")
        appendLine("If you don't actually know the brand, say so plainly. This is opinion, not verified fact — do not invent specifics, certifications, or numbers.")
        appendLine()
        appendLine("Brand: $brand")
        appendLine("Product: $name")
        appendLine()
        appendLine("Reply with only the sentence.")
    }

    /**
     * Category-aware analysis for anything that isn't a supplement — a toner, a
     * device, a hair serum. No catalog to lean on, so the SLM fills a [CategoryProfile]
     * over the real source material, and we present plain, few chapters with the RIGHT
     * fields (how & when to use — not "dose / bioavailability / upper limit").
     */
    private suspend fun analyzeProfile(
        app: Context,
        engineUp: Boolean,
        tier: ModelTier,
        kind: ItemKind,
        name: String,
        currentStack: List<ItemEntity>,
        enteredDose: Double,
        enteredUnit: DoseUnit,
        groups: List<GroupEntity>,
        source: SourceKind?,
        goal: String?,
        concern: String?,
        product: ProductSignals?,
        onProgress: ((AnalysisProgress) -> Unit)?,
    ): AnalysisReport {
        fun emit(label: String, commentary: String, mood: Mood, fraction: Float) =
            onProgress?.invoke(AnalysisProgress(label, commentary, mood, fraction))

        emit("Settling in", "Taking a first look at ${name.ifBlank { "this" }}…", Mood.CALM, 0.06f)
        emit("Listening to the web", "Seeing what people actually say about it…", Mood.CURIOUS, 0.2f)
        val webResults = runCatching { researchWeb(name, product?.brand) }.getOrDefault(emptyList())
        val sourceMaterial = buildSourceMaterial(product?.ingredientsText, webResults)

        emit("Working out what it is", "Reading it as ${kind.label.lowercase()}, not a pill…", Mood.CALM, 0.45f)
        val routineNames = currentStack.map { it.name }
        // Curated ground truth FIRST — the validated KB is the trusted base (same as a
        // supplement leans on IngredientCatalog). The model only fills gaps / unknowns.
        val curated = CuratedProfiles.match(name, kind)
        val profile = (if (engineUp) runCatching { ProfileSkill.fill(BrainProvider.engine(app), name, sourceMaterial, kind, routineNames) }.getOrNull() else null)
            ?: curated
            ?: ProfileSkill.fallback(name, kind)
        var byModel = engineUp && curated == null && profile.confidence >= 30

        emit("The big picture", "Weighing it up the way you would…", Mood.REFLECTIVE, 0.85f)
        var rationale = profile.whatItIs.ifBlank { "A ${kind.label.lowercase()} you're tracking." }
        if (engineUp) {
            val syn = runCatching {
                BrainProvider.engine(app).complete(profileVerdictPrompt(name, profile, source, goal, concern, webResults, tier)).trim()
            }.getOrNull()?.let { sanitize(it) }
            if (!syn.isNullOrBlank()) { rationale = syn; byModel = true }
        }

        val grounded = mutableListOf<String>()
        webResults.take(4).forEach { grounded += "Web/${it.domain}: ${it.title}" }
        profile.goodFor.take(4).forEach { grounded += "Good for: $it" }
        profile.dontCombine.take(4).forEach { grounded += "Don't combine: $it" }

        // Same reasoning as a supplement, scoped: cautions = safety + clashes.
        val hasCautions = profile.safety.isNotEmpty() || profile.dontCombine.isNotEmpty()
        val verdictLabel = when {
            hasCautions && source?.skeptical == true -> "Worth a look — verify first"
            hasCautions -> "Worth trying — mind the details"
            else -> "Worth tracking"
        }

        // The "how much" answer the user asked for — recommended amount + unit, in words.
        val amountText: String? = profile.recommendedAmount?.let { amt ->
            val unitWord = profile.recommendedUnit?.trim()?.ifBlank { null } ?: "per use"
            "${fmt(amt)} $unitWord per use"
        }
        val usageText = profile.usage.ifBlank {
            if (kind.isIngested) "Take as directed on the label." else "Use as directed — note whether it's an AM or PM step."
        }

        // Title the absorption/quality lens to the category, not "bioavailability".
        val absorbTitle = when (kind) {
            ItemKind.SKINCARE -> "Quality & skin absorption"
            ItemKind.HAIRCARE -> "Quality & how it works"
            ItemKind.DEVICE -> "How well it works"
            else -> "Quality & absorption"
        }

        // Match dontCombine against the user's actual routine (token overlap, both ways).
        val stackClashes = currentStack.filter { item ->
            val n = item.name.lowercase()
            profile.dontCombine.any { dc ->
                dc.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 4 }.any { tok -> n.contains(tok.lowercase()) }
            }
        }

        val blocks = buildList<ReportBlock> {
            add(ReportBlock.Verdict(verdictLabel, rationale, profile.confidence, listOf(profile.kind.label, profile.categoryLabel).filter { it.isNotBlank() }.distinct(), byModel))
            buildProfileFacts(profile, product)?.let { add(it) }

            // Good for & who it's for.
            if (profile.goodFor.isNotEmpty() || profile.fitsWho.isNotEmpty()) {
                val lines = profile.goodFor.map { AnalysisLine(it, Severity.GOOD) } +
                    profile.fitsWho.map { AnalysisLine("Suits — $it", Severity.NEUTRAL) }
                add(ReportBlock.Chapter("Good for & who it's for", null, lines, AspectState.GOOD))
            }

            // Quality & (skin) absorption — the category-scoped "is it the real thing" lens.
            if (profile.absorption.isNotBlank()) {
                add(ReportBlock.Chapter(absorbTitle, profile.absorption, emptyList(), AspectState.MIXED))
            }

            // How & when to use — with the auto-recommended amount up front.
            run {
                val lines = buildList {
                    amountText?.let { add(AnalysisLine("Suggested amount — $it", Severity.GOOD)) }
                }
                add(ReportBlock.Chapter("How & when to use", usageText, lines, AspectState.MIXED))
            }

            // Safety — scoped (irritation, allergens, pregnancy, who should avoid).
            if (profile.safety.isNotEmpty()) {
                add(ReportBlock.Chapter("Safety", null, profile.safety.map { AnalysisLine(it, Severity.CAUTION) }, AspectState.CAUTION))
            }

            // Plays with your routine — don't-layer-with + clashes already in their stack.
            if (profile.dontCombine.isNotEmpty() || stackClashes.isNotEmpty()) {
                val lines = buildList {
                    profile.dontCombine.forEach { add(AnalysisLine("Don't layer with $it", Severity.CAUTION)) }
                    if (stackClashes.isNotEmpty()) {
                        add(AnalysisLine("In your routine: ${stackClashes.joinToString(", ") { it.name }} — keep them at different times of day.", Severity.CAUTION))
                    } else if (currentStack.isNotEmpty()) {
                        add(AnalysisLine("Nothing in your current routine clashes with it.", Severity.GOOD))
                    }
                }
                add(ReportBlock.Chapter("Plays with your routine", null, lines, AspectState.CAUTION))
            }

            // Trust & reviews — what the web says.
            if (webResults.isNotEmpty()) {
                val lines = webResults.take(5).map { AnalysisLine("${it.domain}: ${(it.snippet.ifBlank { it.title }).take(160)}") } +
                    AnalysisLine("From the open web — weigh the source, treat as leads.", Severity.CAUTION)
                add(ReportBlock.Chapter("Trust & reviews", null, lines, AspectState.MIXED))
            }

            // Check yourself.
            if (profile.verify.isNotEmpty()) {
                add(ReportBlock.Chapter("Check yourself", null, profile.verify.map { AnalysisLine(it) }, AspectState.MIXED))
            }
            if (grounded.isNotEmpty()) add(ReportBlock.Reasoning(grounded.distinct().take(8), byModel))
        }

        // Pre-fill the add screen with the recommended amount + a sensible unit.
        val recAmount = profile.recommendedAmount ?: enteredDose
        val recUnit = if (profile.recommendedAmount != null) mapRecUnit(profile.recommendedUnit, kind) else enteredUnit
        val (recGroupId, groupReason) = pickGroup(groups, null)
        val recommendation = Recommendation(recGroupId, groupReason, recAmount, recUnit, null, null, 0, emptyList())

        return AnalysisReport(
            title = name,
            matchedIngredientKey = null,
            recommendation = recommendation,
            sections = emptyList(),
            synthesis = Synthesis(verdictLabel, rationale, null, emptyList(), profile.safety + profile.dontCombine),
            safety = emptyList(),
            safetyReviewedClear = emptyList(),
            blocks = blocks,
            ingredients = emptyList(),
            grounded = grounded.distinct(),
            byModel = byModel,
        )
    }

    /** Map the SLM's free-text unit ("drops", "pump", "pea-size") to a tracked [DoseUnit].
     *  Descriptive words we can't measure (pump, dab, pea-size) become a countable UNIT;
     *  the human wording is preserved in the "How & when to use" chapter. */
    private fun mapRecUnit(s: String?, kind: ItemKind): DoseUnit = when (s?.lowercase()?.trim()?.removeSuffix("s")) {
        "drop" -> DoseUnit.DROP
        "ml", "milliliter", "millilitre" -> DoseUnit.ML
        "g", "gram" -> DoseUnit.G
        "mg" -> DoseUnit.MG
        "mcg" -> DoseUnit.MCG
        "iu" -> DoseUnit.IU
        "scoop" -> DoseUnit.SCOOP
        else -> DoseUnit.UNIT
    }

    private fun buildProfileFacts(profile: CategoryProfile, product: ProductSignals?): ReportBlock.Facts? {
        val brand = product?.brand?.trim()
        // "Type" is the product TYPE, never the brand — guard against the SLM echoing it.
        val typeLabel = profile.categoryLabel.trim().takeIf {
            it.isNotBlank() && (brand == null || !it.equals(brand, ignoreCase = true))
        } ?: profile.kind.label
        val rows = buildList {
            add("Type" to typeLabel)
            brand?.takeIf { it.isNotBlank() }?.let { add("Brand" to it) }
            product?.priceText?.let { add("Price" to it) }
            product?.ratingValue?.let { r -> add("Rating" to "$r/5${product.ratingCount?.let { " ($it)" } ?: ""}") }
        }
        return if (rows.isEmpty()) null else ReportBlock.Facts("Product", rows)
    }

    private fun profileVerdictPrompt(
        name: String,
        profile: CategoryProfile,
        source: SourceKind?,
        goal: String?,
        concern: String?,
        web: List<WebSearch.WebResult>,
        tier: ModelTier,
    ): String = buildString {
        appendLine("You are a calm, honest, slightly skeptical advisor. In ${if (tier == ModelTier.CAPABLE) "2-3" else "1-2"} plain sentences,")
        appendLine("tell the user whether this ${profile.kind.label.lowercase()} is worth using and how to fit it in — using ONLY the profile and reviews below.")
        appendLine("Separate what's verified from what to check. Label marketing as a claim. No medical claims, no invented numbers. Conversational, no headings.")
        appendLine()
        appendLine("Item: $name (${profile.categoryLabel})")
        appendLine("What it is: ${profile.whatItIs}")
        if (profile.goodFor.isNotEmpty()) appendLine("Good for: ${profile.goodFor.joinToString(", ")}")
        if (profile.fitsWho.isNotEmpty()) appendLine("Suits: ${profile.fitsWho.joinToString(", ")}")
        if (profile.usage.isNotBlank()) appendLine("Usage: ${profile.usage}")
        profile.recommendedAmount?.let { appendLine("Amount per use: ${fmt(it)} ${profile.recommendedUnit ?: ""}".trim()) }
        if (profile.absorption.isNotBlank()) appendLine("Absorption: ${profile.absorption}")
        if (profile.safety.isNotEmpty()) appendLine("Safety: ${profile.safety.joinToString("; ")}")
        if (profile.dontCombine.isNotEmpty()) appendLine("Don't combine with: ${profile.dontCombine.joinToString(", ")}")
        if (!goal.isNullOrBlank()) appendLine("Their goal: $goal")
        if (!concern.isNullOrBlank()) appendLine("Their worry: $concern")
        if (source != null) appendLine("Heard from: ${source.label} (${if (source.skeptical) "weak — verify" else "credible"})")
        if (web.isNotEmpty()) {
            appendLine("Reviews:")
            web.take(4).forEach { appendLine("- ${it.domain}: ${(it.snippet.ifBlank { it.title }).take(180)}") }
        }
        appendLine()
        appendLine("Reply with only the synthesis.")
    }

    /** Combines the product's own text (label/description or OCR) and web reviews into
     *  the raw material the model reasons over — capped to stay prompt-friendly. */
    private fun buildSourceMaterial(productText: String?, web: List<WebSearch.WebResult>): String = buildString {
        productText?.takeIf { it.isNotBlank() }?.let {
            appendLine("PRODUCT TEXT (its own label/description — marketing, treat as claims):")
            appendLine(it.take(2500))
            appendLine()
        }
        if (web.isNotEmpty()) {
            appendLine("FROM THE WEB (independent reviews / sources):")
            web.take(5).forEach { r -> appendLine("- ${r.domain}: ${(r.snippet.ifBlank { r.title }).take(220)}") }
        }
    }.trim()

    private fun aspectPrompt(
        name: String,
        matched: Ingredient?,
        block: ReportBlock.Aspect,
        goal: String?,
        concern: String?,
        sourceMaterial: String,
    ): String = buildString {
        appendLine("You are a calm, precise, slightly skeptical advisor. Write ONE short sentence (max 32 words) for this chapter.")
        appendLine("Reason from BOTH the grounded points AND the source material. You may summarize what the product or reviewers say, but label marketing as a claim (\"claims to…\"), separate verified from what to check, and NEVER invent doses, certifications, prices or numbers. No medical claims. Conversational, no labels, no quotes.")
        appendLine()
        appendLine("Item: $name${matched?.let { " (${it.displayName})" } ?: ""}")
        appendLine("Chapter: ${block.aspect.title} — ${block.aspect.question}")
        if (!goal.isNullOrBlank()) appendLine("Their goal: $goal")
        if (!concern.isNullOrBlank()) appendLine("Their worry: $concern")
        appendLine("Grounded points (verified):")
        block.lines.forEach { appendLine("- ${it.text}") }
        if (sourceMaterial.isNotBlank()) {
            appendLine()
            appendLine("Source material:")
            appendLine(sourceMaterial.take(1800))
        }
        appendLine()
        appendLine("Reply with only the sentence.")
    }

    private fun verdictPrompt(
        name: String,
        matched: Ingredient?,
        goal: String?,
        source: SourceKind?,
        concern: String?,
        verdict: String,
        blocks: List<ReportBlock.Aspect>,
        sourceMaterial: String,
        tier: ModelTier,
    ): String = buildString {
        appendLine("You are a calm, sharp, skeptical advisor confirming whether this product lives up to its claims —")
        appendLine("the way a careful person does: first a gut read, then asking around, then checking the evidence.")
        appendLine()
        appendLine("Item: $name${matched?.let { " (${it.displayName})" } ?: ""}")
        if (!goal.isNullOrBlank()) appendLine("Their goal: $goal")
        if (source != null) appendLine("Source they heard it from: ${source.label} (${if (source.skeptical) "weak — verify" else "credible"})")
        if (!concern.isNullOrBlank()) appendLine("Their worry: $concern")
        appendLine("Working verdict: $verdict")
        appendLine()
        if (sourceMaterial.isNotBlank()) {
            appendLine("SOURCE MATERIAL (the product's own words + what the web says — treat marketing as CLAIMS, not facts):")
            appendLine(sourceMaterial.take(2600))
            appendLine()
        }
        appendLine("GROUNDED CHECKS (verified — from the curated knowledge base and their stack):")
        blocks.forEach { b ->
            val body = b.summary?.takeIf { it.isNotBlank() } ?: b.lines.joinToString("; ") { it.text }
            appendLine("- ${b.aspect.title} [${b.state.name.lowercase()}${b.score?.let { ", $it/100" } ?: ""}]: $body")
        }
        appendLine()
        appendLine("Reason in three quick passes, then synthesize:")
        appendLine("1) Intuitive — does the core claim even make sense on its face?")
        appendLine("2) Enquiry — do the source's credibility and the real reviews support or undercut it?")
        appendLine("3) Empirical — what does hard evidence (ingredients, dose, safety, research) actually say?")
        appendLine()
        appendLine("Write ${if (tier == ModelTier.CAPABLE) "2-4 sentences" else "1-2 sentences"}: the synthesized verdict — note where intuition, enquiry")
        appendLine("and evidence AGREE and where they CONFLICT, and separate what's verified from what to check yourself.")
        appendLine("Label marketing as a claim. Do NOT fabricate doses, certifications, prices or numbers. No medical claims. Conversational, no headings.")
        appendLine()
        appendLine("Reply with only the synthesis text.")
    }

    /** Brand / price / rating facts lifted from the product page (grounded). */
    private fun buildFactsBlock(matched: Ingredient?, product: ProductSignals?): ReportBlock.Facts? {
        val rows = buildList {
            product?.brand?.let { add("Brand" to it) }
            (matched?.category)?.let { add("Category" to it) }
            product?.priceText?.let { add("Price" to it) }
            product?.servings?.let { add("Servings" to "$it") }
            product?.ratingValue?.let { r ->
                val c = product.ratingCount?.let { " ($it)" } ?: ""
                add("Rating" to "$r/5$c")
            }
        }
        return if (rows.isEmpty()) null else ReportBlock.Facts("Product", rows)
    }

    /** The item's ingredients — single active or the formula's actives, with doses. */
    private fun buildIngredientsBlock(ingredients: List<com.quietdose.data.model.ItemIngredient>): ReportBlock.Facts? {
        if (ingredients.isEmpty()) return null
        val rows = ingredients.map { ing ->
            val dose = if (ing.doseAmount != null && ing.doseUnit != null) "${fmt(ing.doseAmount)} ${ing.doseUnit.name.lowercase()}" else "—"
            ing.name to dose
        }
        val title = if (ingredients.size > 1) "Ingredients (${ingredients.size})" else "Ingredient"
        return ReportBlock.Facts(title, rows)
    }

    /** Dose as a position on its typical band, with the upper limit marked. */
    private fun buildDoseMeter(matched: Ingredient?, recDose: Double, recUnit: DoseUnit): ReportBlock.Meter? {
        if (matched == null) return null
        val unit = recUnit.name.lowercase()
        val ceiling = matched.upperLimitDose
        val trackMax = maxOf(matched.typicalDoseHigh * 1.4, ceiling ?: 0.0, recDose).takeIf { it > 0 } ?: return null
        fun frac(v: Double) = (v / trackMax).toFloat().coerceIn(0f, 1f)
        val caption = buildString {
            append("typical ${doseRange(matched)}")
            ceiling?.let { append(" · ceiling ${fmt(it)} $unit") }
        }
        return ReportBlock.Meter(
            label = "Suggested dose",
            valueText = "${fmt(recDose)} $unit",
            fraction = frac(recDose),
            bandLow = frac(matched.typicalDoseLow),
            bandHigh = frac(matched.typicalDoseHigh),
            markerFraction = ceiling?.let { frac(it) },
            caption = caption,
        )
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
