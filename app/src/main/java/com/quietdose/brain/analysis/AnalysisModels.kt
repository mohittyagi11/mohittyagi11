package com.quietdose.brain.analysis

/** Tone of a single analysis line, for the calm severity dot in the UI. */
enum class Severity { GOOD, NEUTRAL, CAUTION }

data class AnalysisLine(val text: String, val severity: Severity = Severity.NEUTRAL)

/** One titled section of the report (Context, Fit, Peer review, Recent research). */
data class AnalysisSection(
    val title: String,
    val lines: List<AnalysisLine>,
)

/**
 * The synthesised recommendation — what the brain concludes after grounding in
 * the knowledge base and (when a model is present) reasoning over it.
 */
data class Synthesis(
    val verdict: String,            // e.g. "Good fit" / "Fits, with timing care"
    val rationale: String,          // one calm paragraph
    val placement: String?,         // where/when to take it to maximise benefit
    val dependencies: List<String>, // things to add/pair (e.g. "Vitamin C with iron")
    val cautions: List<String>,     // interactions to respect
)

/**
 * A concrete, *adjustable* recommendation for how to add the item: where it
 * lands, the dose (on a meaningful scale), timing flags, and pairings to add.
 * The UI pre-fills these and lets the user drag/tweak before committing.
 */
data class Recommendation(
    val groupId: Long?,           // best-fit existing group, or null
    val groupReason: String?,     // why that group (e.g. "fasted → morning")
    val doseAmount: Double,
    val doseUnit: com.quietdose.data.model.DoseUnit,
    val typicalLow: Double?,      // for the dose scale's "typical" band
    val typicalHigh: Double?,
    val flags: Int,               // ItemFlags timing bitmask
    val pairings: List<String>,   // ingredient display names to consider adding
)

/**
 * The full contextual analysis of a candidate item against the current stack.
 * [grounded] lists the validated facts used (so the read is transparent), and
 * [byModel] says whether the on-device model enriched the narration.
 */
data class AnalysisReport(
    val title: String,
    val matchedIngredientKey: String?,
    val sections: List<AnalysisSection>,
    val synthesis: Synthesis,
    val recommendation: Recommendation,
    /** Structured safety review — findings keyed to [SafetyCategory] (the spine). */
    val safety: List<SafetyFinding>,
    /** Categories that were reviewed and came back clear (shown for honesty/structure). */
    val safetyReviewedClear: List<SafetyCategory>,
    /** The dynamic, brain-composed page: ordered template blocks the UI renders. */
    val blocks: List<ReportBlock>,
    /** The item's parsed ingredients (single active or a formula); persisted on add. */
    val ingredients: List<com.quietdose.data.model.ItemIngredient>,
    val grounded: List<String>,
    val byModel: Boolean,
)
