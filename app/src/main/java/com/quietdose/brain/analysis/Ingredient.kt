package com.quietdose.brain.analysis

import com.quietdose.data.model.DoseUnit

/**
 * One curated, authoritative supplement ingredient — a *fact node* in the
 * knowledge base the [StackAnalyzer] reasons over. Everything here is encoded
 * conservatively and is meant to be undisputed and well-established; it is NOT
 * medical advice and never a diagnosis. Narration phrasing stays hedged
 * ("commonly noted", "often taken with…") by construction.
 *
 * The fields form a small graph: [pairsWith] and [avoidWith] reference other
 * ingredients by [key], so the analyzer can walk synergies and interactions
 * across the user's current stack deterministically — no model required.
 */
data class Ingredient(
    /** Stable, lowercase identifier, e.g. "vitamin_d3". The graph keys off this. */
    val key: String,
    val displayName: String,
    /** Names/spellings used on labels; matched case-insensitively, normalized. */
    val aliases: List<String> = emptyList(),
    /** Curated bucket, e.g. "Longevity", "Sleep", "Minerals", "Vitamins", "Omega", "Actives". */
    val category: String,
    /** Short, factual, undisputed benefits. Phrased plainly, no claims of cure. */
    val benefits: List<String> = emptyList(),

    // Typical, conservative dose range for a healthy adult (for orientation only).
    val typicalDoseLow: Double,
    val typicalDoseHigh: Double,
    val doseUnit: DoseUnit,

    /** Timing/handling, reusing [com.quietdose.data.model.ItemFlags] semantics. */
    val timingFlags: Int = 0,

    /** Ingredient keys this commonly synergizes with (co-absorption / pairing). */
    val pairsWith: List<String> = emptyList(),
    /** Ingredient keys that compete or interact — separate in time, e.g. iron↔calcium. */
    val avoidWith: List<String> = emptyList(),

    /** One calm, hedged line about the evidence. Curated, not scraped; never advice. */
    val evidenceNote: String = "",
    /** Honest criticism / who should be careful — surfaced, not hidden. */
    val cautions: List<String> = emptyList(),
    /** 1 = strong/established evidence; 2 = supportive; 3 = emerging/early. */
    val tier: Int = 2,
)
