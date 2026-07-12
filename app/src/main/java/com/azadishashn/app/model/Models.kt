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
    /** 2-3 stakeholder blocs watching this decision (farmers, army, media barons…). */
    val blocs: List<String> = emptyList(),
    /** The question's political mood: which ideology has the wind, which faces it. */
    val mood: Mood? = null,
)

/**
 * The moment's political weather. Arguing the [favors] ideology gets an easier
 * bar (-1 required); arguing [suspects] a harder one (+1) — so the questioner
 * shapes the wind with theme picks, and the answerer chooses to ride it or
 * fight it for the card their board build needs.
 */
@Serializable
data class Mood(
    val favors: String = "",
    val suspects: String = "",
    val note: String = "",
)

/**
 * A localized rendering of a scenario/verdict in one language
 * ("en" | "hinglish" | "hi"): [title] + [text] are shown on screen (Hinglish in
 * Roman script); [speak] is what the TTS reads — for Hinglish/Hindi that's
 * Devanagari so a Hindi voice pronounces it correctly instead of an English
 * voice mangling Romanised words.
 */
@Serializable
data class NarrationLine(
    val lang: String,
    val title: String = "",
    val text: String = "",
    val speak: String = "",
)

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

/** How the same answer is spun by one partisan outlet's front page. */
@Serializable
data class Headline(
    val outlet: String,
    val slant: String = "",     // the outlet's leaning, e.g. "pro-market daily"
    val headline: String = "",
)

/** One archived front page — THE PAPERS remember every morning after. */
@Serializable
data class NewsItem(
    val round: Int,
    val playerName: String,     // whose answer made the news
    val outlet: String,
    val slant: String = "",
    val headline: String = "",
)

/** How one stakeholder bloc received the answer. */
@Serializable
data class BlocReaction(
    val bloc: String,
    val reaction: String = "",  // one line, in the bloc's voice
    val delta: Int = 0,         // -2..2 support movement
)

/** The judge's read of this answer against the player's public record. */
@Serializable
data class Consistency(
    val verdict: String = "",   // first_stand | consistent | evolved | flipflop
    val note: String = "",      // one wry line, as the press would put it
)

/** How enacting the answer nudges the nation meters (-3..3 each). */
@Serializable
data class NationEffects(
    val economy: Int = 0,
    val liberty: Int = 0,
    val stability: Int = 0,
    val trust: Int = 0,
)

/** The living nation: four 0..100 meters the game's choices push around. */
@Serializable
data class NationState(
    val economy: Int = 50,
    val liberty: Int = 50,
    val stability: Int = 50,
    val trust: Int = 50,
) {
    fun applied(fx: NationEffects?): NationState = if (fx == null) this else NationState(
        (economy + fx.economy).coerceIn(0, 100),
        (liberty + fx.liberty).coerceIn(0, 100),
        (stability + fx.stability).coerceIn(0, 100),
        (trust + fx.trust).coerceIn(0, 100),
    )
}

/** One entry in the public record — a position a player took, on the books forever. */
@Serializable
data class DossierEntry(
    val playerId: Int,
    val playerName: String,
    val roundTitle: String,
    val ideology: String,       // the ideology the argument served
    val stance: String,         // one-line record of the position taken
    val strength: Int = 0,      // the verdict's 1..10 (for end-of-game awards)
)

/** A skeleton surfacing from a player's own record, demanding a public response. */
@Serializable
data class Scandal(
    val headline: String = "",
    val story: String = "",
    val question: String = "",  // what the press is demanding an answer to
)

/**
 * A politics-earned resource payout (or forfeit), instructing a physical take:
 * whip patronage, an endorsing bloc's gift, a mandate milestone, a weathered
 * crisis. [delta] is +1 / -1 of [ideology]'s resource.
 */
@Serializable
data class ResourceGrant(
    val label: String,
    val ideology: String,
    val delta: Int,
    val note: String = "",
)

/** The generated end-of-game closing chapter. */
@Serializable
data class Epilogue(
    val title: String = "",
    val text: String = "",
)

/** The judge's score of a scandal response — pure damage control. */
@Serializable
data class ScandalVerdict(
    val handling: Int = 5,      // 1..10 — how well the response defused it
    val note: String = "",
    @SerialName("poll_delta") val pollDelta: Int = 0,
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
    /** One-line public record of the position taken (feeds the dossier). */
    @SerialName("stance_summary") val stanceSummary: String = "",
    /** The same answer spun by two opposing front pages. */
    val headlines: List<Headline> = emptyList(),
    /** The judge's read against the player's past positions. */
    val consistency: Consistency? = null,
    /** How each watching bloc received the answer. */
    @SerialName("bloc_reactions") val blocReactions: List<BlocReaction> = emptyList(),
    /** Snap-poll approval movement, -10..10. */
    @SerialName("poll_delta") val pollDelta: Int = 0,
    /** How enacting this would nudge the nation meters. */
    @SerialName("nation_effects") val nationEffects: NationEffects? = null,
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
    // -- Political-realism extras (all optional; offline rounds leave them empty) --
    val headlines: List<Headline> = emptyList(),
    val consistency: Consistency? = null,
    val blocReactions: List<BlocReaction> = emptyList(),
    val pollDelta: Int = 0,
    /** The player's approval AFTER this verdict (0..100), for the snap-poll flash. */
    val approvalAfter: Int = -1,
    val nationEffects: NationEffects? = null,
    /** Party-lines mode: the ideology the player was ASSIGNED to argue (else ""). */
    val assigned: String = "",
    // -- The whip, revealed (v2.1) — obey quietly, or rebel and face the party --
    val whip: String = "",              // the ideology the whip demanded ("" = no whip)
    val whipOutcome: String = "",       // obeyed | rebel | punished
    val whipPollAdj: Int = 0,
    // -- Tripwire crisis stakes (v2.1) --
    val crisisTarget: String = "",      // the ideology the crisis attacked ("" = none fired)
    val crisisOutcome: String = "",     // weathered | claimed | swerved
    /** Blocs that newly endorsed the player at this verdict (crossed +3). */
    val newEndorsements: List<String> = emptyList(),
    /** Blocs STOLEN from a rival this verdict: bloc -> the name it defected from. */
    val defections: Map<String, String> = emptyMap(),
    // -- v2.2: everything cashes out into the game's real currencies --
    /** Politics-earned resource payouts/forfeits — physical takes at the table. */
    val bonusResources: List<ResourceGrant> = emptyList(),
    /** Ideologue milestone hit by this card (2/4/6 of one ideology), else "". */
    val milestone: String = "",
    /** How the round's mood moved the bar for this argument ("" if neutral). */
    val moodNote: String = "",
    /** Press credits banked tonight (0 when nothing was feat-worthy or the cap bit). */
    val pressCreditsGained: Int = 0,
    /** The feats that earned them — shown so the table knows WHY. */
    val pressCreditReasons: List<String> = emptyList(),
)
