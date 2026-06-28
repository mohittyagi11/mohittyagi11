package com.azadishashn.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The four SHASN ideologies. Every scenario offers one option per ideology, and
 * each player collects ideology points (their political capital) per ideology —
 * exactly the cards/points the physical game tracks.
 */
data class Ideology(val name: String, val resource: String, val blurb: String)

object Ideologies {
    val ALL: List<Ideology> = listOf(
        Ideology("Capitalist", "Funds", "Free markets, growth — money talks"),
        Ideology("Supremo", "Clout", "Identity & strength — the strongman"),
        Ideology("Showstopper", "Media", "Spectacle & narrative — the media game"),
        Ideology("Idealist", "Trust", "People's welfare & principle — the long game"),
    )

    val NAMES: List<String> = ALL.map { it.name }

    fun resourceOf(name: String): String = ALL.firstOrNull { it.name == name }?.resource ?: ""
    fun of(name: String): Ideology? = ALL.firstOrNull { it.name == name }
}

// ---------------------------------------------------------------------------
// API DTOs — these are what Claude returns (validated via structured outputs).
// ---------------------------------------------------------------------------

@Serializable
data class RoundData(
    val scenario: Scenario,
    val dilemma: Dilemma,
    val options: List<OptionCard>,
    /** Forecast for each ideology — the "compare the four paths" fan-out. */
    val paths: List<PathForecast> = emptyList(),
    /** Spoken read-aloud of the scenario, one entry per selected read-aloud language. */
    val narration: List<NarrationLine> = emptyList(),
)

/** A read-aloud rendering of some text in a given language ("en" | "hinglish" | "hi"). */
@Serializable
data class NarrationLine(val lang: String, val text: String = "")

/** Where one ideology's stance plausibly leads — for the fan-out exhibit. */
@Serializable
data class PathForecast(
    val ideology: String,
    val stance: String = "",
    val outcome: String = "",
    val risk: String = "",
)

/** One link in a consequence chain — for the verdict's analyst exhibit. */
@Serializable
data class CausalStep(
    val label: String,
    val mechanism: String = "",
    val horizon: String = "",   // immediate | short_term | long_term
    val polarity: String = "",  // gain | cost | mixed
)

@Serializable
data class Scenario(
    val title: String,
    val dimension: String = "", // the question's category/dimension, e.g. "Surveillance · Technology"
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

/** Claude's verdict: the dominant ideology in the answer and the next-strongest one. */
@Serializable
data class Verdict(
    @SerialName("primary_ideology") val primaryIdeology: String,
    @SerialName("secondary_ideology") val secondaryIdeology: String,
    val strength: Int, // 1..10 — how strongly the answer favoured the primary ideology
    val reasoning: String,
    @SerialName("historical_outcome") val historicalOutcome: String,
    /** Analyst-grade consequence chain of the chosen stance. */
    @SerialName("causal_chain") val causalChain: List<CausalStep> = emptyList(),
    /** The central cost-of-power tradeoff. */
    val tradeoff: String = "",
    /** Spoken read-aloud of the verdict, one entry per selected read-aloud language. */
    val narration: List<NarrationLine> = emptyList(),
)

// ---------------------------------------------------------------------------
// Game state — lives in memory, driven by the ViewModel.
// ---------------------------------------------------------------------------

@Serializable
data class Player(
    val id: Int,
    val name: String,
    /** ideology -> ideology points (political capital) collected. The "baseline" input. */
    val counts: Map<String, Int> = emptyMap(),
) {
    val total: Int get() = counts.values.sum()
}

/**
 * Result of a turn. Resources are always +2 [primary] / +1 [secondary]. Exactly
 * one ideology CARD is always awarded — a judgement always lands. The shifting
 * [required] baseline only decides WHICH card: if [strength] (1..10) clears the
 * bar for [primary] the player keeps stacking it ([cardIdeology] == [primary]);
 * otherwise the card is redirected to [secondary] ([diverted] = true) so a
 * player already accumulating an ideology needs a stronger argument to keep it.
 * [strength] is -1 offline (no AI rating; a simple anti-hoard rule applies).
 */
@Serializable
data class AwardResult(
    val playerName: String = "",
    val primary: String = "",
    val secondary: String = "",
    val strength: Int = -1,
    val required: Int = 0,
    val cardAwarded: Boolean = true,   // always true now — a card always lands
    val cardIdeology: String = "",     // the ideology that actually got the card
    val diverted: Boolean = false,     // true when redirected to [secondary]
    val reasoning: String = "",       // judge's reasoning, or "" offline
    val historicalNote: String = "",  // one-line real-world outcome, or ""
    val explanation: String = "",
    /** The verdict's consequence chain + tradeoff, for the analyst exhibit. */
    val causalChain: List<CausalStep> = emptyList(),
    val tradeoff: String = "",
    /** The verdict's spoken read-aloud per selected language. */
    val narration: List<NarrationLine> = emptyList(),
)
