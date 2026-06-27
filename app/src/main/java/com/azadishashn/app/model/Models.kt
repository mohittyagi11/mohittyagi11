package com.azadishashn.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The fixed set of ideology "cards". Scenarios always pick their four options
 * from this list, so vote tallies and each player's collected-card counts stay
 * consistent across rounds. This mirrors the physical deck's ideology cards.
 */
object Ideologies {
    val ALL: List<String> = listOf(
        "Liberalism",
        "Conservatism",
        "Socialism",
        "Libertarianism",
        "Nationalism",
        "Authoritarianism",
        "Anarchism",
        "Theocracy",
        "Environmentalism",
        "Technocracy",
        "Populism",
        "Communism",
    )
}

// ---------------------------------------------------------------------------
// API DTOs — these are what Claude returns (validated via structured outputs).
// ---------------------------------------------------------------------------

@Serializable
data class RoundData(
    val scenario: Scenario,
    val dilemma: Dilemma,
    val options: List<OptionCard>,
)

@Serializable
data class Scenario(
    val title: String,
    val setting: String,
    val era: String,
    val situation: String,
)

@Serializable
data class Dilemma(
    val question: String,
    @SerialName("real_world_note") val realWorldNote: String,
)

@Serializable
data class OptionCard(
    val id: String,
    val ideology: String,
    val label: String,
    val summary: String,
)

@Serializable
data class Adjudication(
    @SerialName("matched_ideology") val matchedIdeology: String,
    val reasoning: String,
    @SerialName("historical_outcome") val historicalOutcome: String,
)

// ---------------------------------------------------------------------------
// Game state — lives in memory, driven by the ViewModel.
// ---------------------------------------------------------------------------

data class Player(
    val id: Int,
    val name: String,
    /** ideology -> number of that ideology card collected. The "baseline" input. */
    val counts: Map<String, Int> = emptyMap(),
) {
    val total: Int get() = counts.values.sum()
}

/** Result of resolving a turn: who earned what, and why it was (or wasn't) awarded. */
data class AwardResult(
    val playerName: String,
    val ideology: String,
    val awarded: Boolean,
    val convinced: Int,
    val required: Int,
    val bravery: Boolean,
    val explanation: String,
)
