package com.quietdose.brain.analysis

/**
 * The brain thinking out loud. As [StackAnalyzer] works a chapter at a time it
 * emits these — a short label, a first-person-ish commentary line, a [Mood] that
 * drives the screen's lighting, and an overall progress fraction. The analyzing
 * screen renders the stream so you can *watch it reason*, not just wait.
 */
data class AnalysisProgress(
    val label: String,
    val commentary: String,
    val mood: Mood,
    val fraction: Float,
)

/** Colours the moment — tints the orb/lighting and hints what the brain is feeling. */
enum class Mood { CALM, CURIOUS, FAVORABLE, CAUTIOUS, SKEPTICAL, REFLECTIVE }
