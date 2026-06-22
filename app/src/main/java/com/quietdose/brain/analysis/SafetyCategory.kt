package com.quietdose.brain.analysis

/**
 * The fixed taxonomy of a *complete* safety analysis — the structured spine the
 * [StackAnalyzer] walks for every candidate item, and the frame the on-device
 * model is asked to reason within (never outside). Each category is one explicit
 * question; the deterministic logic tree fills what it can from the validated
 * [IngredientCatalog], and the model reasons over those facts category by
 * category. Listing them out is the point: structure first, reasoning second.
 *
 * Order is the order they're reviewed and shown.
 */
enum class SafetyCategory(val title: String, val guidance: String) {
    DOSE_CEILING(
        "Dose & upper limit",
        "Is the amount within the safe ceiling (tolerable upper limit), not just the typical range?",
    ),
    DRUG_INTERACTIONS(
        "Medication interactions",
        "Could it interact with prescription or over-the-counter medicines?",
    ),
    STACK_OVERLAP(
        "Overlap with your stack",
        "Is the same active — or the same effect — already arriving from something you take?",
    ),
    CONTRAINDICATIONS(
        "Who should be careful",
        "Are there conditions or life stages where it's not advised (pregnancy, kidney/liver, thyroid, bleeding, surgery)?",
    ),
    SIDE_EFFECTS(
        "Common side effects",
        "What are the usual, expected adverse effects at a normal dose?",
    ),
    TIMING(
        "Timing & food",
        "Does it need food, fasting, or separation from other items to be taken safely and effectively?",
    ),
    QUALITY_FORM(
        "Form & quality",
        "Does the chemical form or product quality change how safe or absorbable it is?",
    ),
    DURATION(
        "Duration & cycling",
        "Is it meant for short courses or cycling rather than indefinite daily use?",
    ),
    EVIDENCE(
        "Evidence maturity",
        "How settled is the science behind the claim — established, supportive, or emerging?",
    ),
    SOURCE(
        "Source credibility",
        "How much can the source of this recommendation be trusted, and what should be verified?",
    );
}

/**
 * One concrete result of reviewing a [SafetyCategory] for this item: a grounded,
 * hedged line and how serious it is. Produced by the logic tree; the model may
 * rephrase but never invents these.
 */
data class SafetyFinding(
    val category: SafetyCategory,
    val text: String,
    val severity: Severity,
)
