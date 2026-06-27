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

/** Claude's impartial verdict on an argument: which ideology it makes the case for, and how strong. */
@Serializable
data class Verdict(
    @SerialName("matched_ideology") val matchedIdeology: String,
    val score: Int, // 0..3 — strength/genuineness of the case
    val reasoning: String,
    @SerialName("historical_outcome") val historicalOutcome: String,
)

// ---------------------------------------------------------------------------
// Game state — lives in memory, driven by the ViewModel.
// ---------------------------------------------------------------------------

data class Player(
    val id: Int,
    val name: String,
    /** ideology -> ideology points (political capital) collected. The "baseline" input. */
    val counts: Map<String, Int> = emptyMap(),
) {
    val total: Int get() = counts.values.sum()
}

/** Result of resolving a turn: who earned what, and why it was (or wasn't) awarded. */
data class AwardResult(
    val playerName: String,
    val ideology: String,
    val awarded: Boolean,
    val score: Int,   // 0..3 when Claude judged; -1 offline (no AI score)
    val bar: Int,     // the score the argument had to clear; 0 offline
    val reasoning: String,       // judge's reasoning, or "" offline
    val historicalNote: String,  // one-line real-world outcome, or ""
    val explanation: String,
)
