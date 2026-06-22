package com.quietdose.brain.analysis

/**
 * The chapters of a *complete* item analysis — the broad dimensions the brain
 * reasons through, in order, building small grounded contexts that finally roll
 * up into one big-picture verdict. Each aspect is one focused question; the
 * deterministic logic tree fills what it can from validated facts and the
 * product page, and the on-device model reasons WITHIN each chapter (never
 * inventing new ones). [SafetyCategory] is the sub-structure of [SAFETY].
 *
 * Order is the order they're reasoned and shown.
 */
enum class AnalysisAspect(val title: String, val question: String) {
    WHAT_FOR(
        "What it's for",
        "What does it genuinely help with — and what it won't?",
    ),
    QUALITY(
        "Quality & bioavailability",
        "Is the form well-absorbed and the dose meaningful, not a fairy-dusted token amount?",
    ),
    FIT_CONDITION(
        "Fit for you",
        "Does it actually target your goal or your specific situation/condition?",
    ),
    CLAIMS(
        "Claims & honesty",
        "Are the marketed claims supported by evidence, or overstated?",
    ),
    TRUST_SOURCE(
        "Trust & source",
        "How credible is where this came from — credentials, research and peer support?",
    ),
    VALUE(
        "Price & value",
        "Is the price reasonable for the dose and quality you get?",
    ),
    STACK(
        "Fit with your stack",
        "Overlaps, interactions, amplification or diminishing returns with what you already take?",
    ),
    SAFETY(
        "Safety",
        "Is it safe at this dose, for you, alongside your stack and any medicines?",
    );
}

/** How an aspect came out, for the calm state chip in the dynamic page. */
enum class AspectState { GOOD, MIXED, CAUTION, CLEAR, NOT_ASSESSED }
