package com.quietdose.brain.analysis

/**
 * Where the user heard about an item — central to judging it honestly. A doctor
 * or a paper is weighed very differently from an ad or an influencer clip, and
 * the analysis says so plainly, with what to verify for the weaker sources.
 */
enum class SourceKind(val label: String, val credibility: Int) {
    DOCTOR("Doctor", 5),
    RESEARCH("Research / paper", 5),
    SELF("My own reading", 3),
    FRIEND("A friend", 2),
    YOUTUBE("YouTube", 2),
    INFLUENCER("Influencer / IG", 1),
    BRAND("Brand / ad", 1);

    /** A calm, honest read on how much to lean on this source. */
    val trust: String
        get() = when (this) {
            DOCTOR -> "A clinician who knows your history is the strongest reason to add something."
            RESEARCH -> "Primary research is solid — just check it's about your dose and your situation."
            SELF -> "Your own reading is a fair start; cross-check it against a second, neutral source."
            FRIEND -> "What works for a friend may not fit you — treat it as a lead, not a prescription."
            YOUTUBE -> "Useful for ideas, but creators vary widely — verify the specific claim and dose."
            INFLUENCER -> "Often sponsored. Treat as a lead and verify the claim, dose and who it's actually for."
            BRAND -> "Marketing overstates benefits — verify the claim independently before relying on it."
        }

    /** Concrete things to confirm before trusting a weaker source. */
    val verify: List<String>
        get() = when (this) {
            DOCTOR, RESEARCH -> emptyList()
            SELF, FRIEND -> listOf("A neutral second source", "The right dose for you")
            YOUTUBE, INFLUENCER, BRAND -> listOf(
                "Is the claim backed by research, not just testimonials?",
                "Is the dose realistic and safe?",
                "Who is it actually for — does that include you?",
            )
        }

    val skeptical: Boolean get() = credibility <= 2
}
