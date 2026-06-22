package com.quietdose.brain.analysis

/**
 * A dynamically-assembled analysis page: an ordered list of [ReportBlock]s.
 * Each block is a *template* the brain fills — the deterministic logic tree
 * seeds the grounded fields (lines, facts, scores) and the on-device model fills
 * the narrative ([Aspect.summary], [Verdict.rationale]). The UI renderer simply
 * walks the list and draws each template, so the page is composed, not fixed:
 * the brain decides which chapters appear and what they say.
 */
sealed interface ReportBlock {

    /** The big picture — synthesized last from every small context above it. */
    data class Verdict(
        val verdict: String,
        val rationale: String,
        val score: Int?,          // 0..100 overall confidence/fit, or null
        val tags: List<String>,   // short chips, e.g. "Good fit", "Verify source"
        val byModel: Boolean,
        /** Per-parameter ratings the verdict is built from — rendered as calm labelled
         *  bars under the overall score (Fit, Quality, Safety, Routine, Trust). Each
         *  carries a [RatingDim.why] so the bar can be tapped for "how this was scored". */
        val dimensions: List<RatingDim> = emptyList(),
        /** The product's primary benefits in 1–2 words each (e.g. "Hydration", "Anti-ageing")
         *  — shown as small tags/orbs around the product glyph. */
        val benefits: List<String> = emptyList(),
    ) : ReportBlock

    /** The actives/ingredients read off the label, each with a plain role + grounded note. */
    data class Ingredients(
        val items: List<IngredientLine>,
    ) : ReportBlock

    /** The product's marketing claims, each checked and given an honest status + basis. */
    data class Claims(
        val items: List<ClaimLine>,
    ) : ReportBlock

    /** Distilled review intelligence — what people actually say, as keywords, not a URL wall. */
    data class Reviews(
        val takeaway: String,          // one calm line summarising the consensus
        val loved: List<String>,       // recurring praise keywords
        val watch: List<String>,       // recurring complaint / watch-out keywords
        val sources: List<String>,     // domains the read is drawn from
        val state: AspectState,
    ) : ReportBlock

    /** One chapter. [summary] is the brain's prose; [lines] are grounded points. */
    data class Aspect(
        val aspect: AnalysisAspect,
        val summary: String?,
        val lines: List<AnalysisLine>,
        val score: Int?,          // 0..100 for this chapter, or null
        val state: AspectState,
    ) : ReportBlock

    /** A free-titled chapter (for category-aware, non-supplement analyses where the
     *  fixed [AnalysisAspect] titles don't fit — e.g. "How & when to use"). */
    data class Chapter(
        val title: String,
        val summary: String?,
        val lines: List<AnalysisLine>,
        val state: AspectState,
    ) : ReportBlock

    /** A labelled scale — dose against its typical band/ceiling, or price-per-day. */
    data class Meter(
        val label: String,
        val valueText: String,
        val fraction: Float,      // 0..1 fill on the track
        val bandLow: Float?,      // typical band start (0..1), or null
        val bandHigh: Float?,     // typical band end (0..1), or null
        val markerFraction: Float?, // upper-limit tick (0..1), or null
        val caption: String?,     // human-readable, e.g. "typical 1000–4000 IU · ceiling 4000 IU"
    ) : ReportBlock

    /** Key→value facts pulled from the product page (brand, price, rating, form). */
    data class Facts(
        val title: String,
        val rows: List<Pair<String, String>>,
    ) : ReportBlock

    /** The grounded reasoning trail, for transparency. */
    data class Reasoning(
        val items: List<String>,
        val byModel: Boolean,
    ) : ReportBlock
}

/** One verdict rating dimension — its 0..100 [score] and a one-line [why] for the drill-down. */
data class RatingDim(val label: String, val score: Int, val why: String = "")

/** One ingredient/active read off the product, with a plain role and a grounded note. */
data class IngredientLine(
    val name: String,
    val role: String = "",        // e.g. "Humectant", "Active", "Soothing", "Preservative"
    val note: String = "",        // a grounded one-liner (from the curated KB or the model)
    val severity: Severity = Severity.NEUTRAL,
)

/** How a claim held up once checked across mechanism, evidence and what people report. */
enum class ClaimStatus { SUPPORTED, PLAUSIBLE, UNVERIFIED, OVERREACH }

/** One marketing claim with its checked [status] and a one-line [basis] for the verdict. */
data class ClaimLine(
    val claim: String,
    val status: ClaimStatus,
    val basis: String = "",
)
