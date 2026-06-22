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
        /** Per-parameter ratings the verdict is built from — label → 0..100. Rendered as
         *  calm labelled bars under the overall score (e.g. Fit, Quality, Safety, Trust). */
        val dimensions: List<Pair<String, Int>> = emptyList(),
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
