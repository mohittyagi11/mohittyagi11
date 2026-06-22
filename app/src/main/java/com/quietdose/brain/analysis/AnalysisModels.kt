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
 * The full contextual analysis of a candidate item against the current stack.
 * [grounded] lists the validated facts used (so the read is transparent), and
 * [byModel] says whether the on-device model enriched the narration.
 */
data class AnalysisReport(
    val title: String,
    val matchedIngredientKey: String?,
    val sections: List<AnalysisSection>,
    val synthesis: Synthesis,
    val grounded: List<String>,
    val byModel: Boolean,
)
