package com.quietdose.brain.analysis

import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemFlags

/**
 * A curated, authoritative knowledge base of well-established supplement
 * ingredients — the *validated facts* the [StackAnalyzer] grounds its reasoning
 * in. Encoded conservatively from common, undisputed references; hedged by
 * construction and never medical advice. The on-device model (when present)
 * reasons OVER these facts; it never invents doses or interactions.
 *
 * Cross-references in [Ingredient.pairsWith]/[Ingredient.avoidWith] use [Ingredient.key].
 */
object IngredientCatalog {

    private fun f(vararg flags: Int): Int = flags.fold(0) { a, b -> a or b }

    val ALL: List<Ingredient> = listOf(
        Ingredient(
            key = "vitamin_d3", displayName = "Vitamin D3", category = "Vitamins",
            aliases = listOf("vitamin d", "d3", "cholecalciferol", "vit d"),
            benefits = listOf("Bone and immune support", "Works with K2 for calcium handling"),
            typicalDoseLow = 1000.0, typicalDoseHigh = 4000.0, doseUnit = DoseUnit.IU,
            timingFlags = f(ItemFlags.FAT_SOLUBLE, ItemFlags.WITH_FOOD),
            pairsWith = listOf("vitamin_k2", "magnesium"),
            evidenceNote = "Well established; absorbed best with dietary fat.", tier = 1,
        ),
        Ingredient(
            key = "vitamin_k2", displayName = "Vitamin K2", category = "Vitamins",
            aliases = listOf("k2", "menaquinone", "mk-7", "mk7"),
            benefits = listOf("Directs calcium to bone", "Commonly paired with D3"),
            typicalDoseLow = 90.0, typicalDoseHigh = 200.0, doseUnit = DoseUnit.MCG,
            timingFlags = f(ItemFlags.FAT_SOLUBLE, ItemFlags.WITH_FOOD),
            pairsWith = listOf("vitamin_d3"),
            evidenceNote = "Fat-soluble; typically taken alongside D3.", tier = 1,
        ),
        Ingredient(
            key = "iron", displayName = "Iron (bisglycinate)", category = "Minerals",
            aliases = listOf("iron", "ferrous", "bisglycinate", "iron bisglycinate"),
            benefits = listOf("Supports healthy iron levels", "Better absorbed with vitamin C"),
            typicalDoseLow = 18.0, typicalDoseHigh = 65.0, doseUnit = DoseUnit.MG,
            timingFlags = f(ItemFlags.EMPTY_STOMACH, ItemFlags.AVOID_CAFFEINE, ItemFlags.AVOID_CALCIUM),
            pairsWith = listOf("vitamin_c"),
            avoidWith = listOf("calcium", "magnesium", "zinc"),
            evidenceNote = "Absorption drops with tea/coffee and calcium; vitamin C helps.", tier = 1,
        ),
        Ingredient(
            key = "vitamin_c", displayName = "Vitamin C", category = "Vitamins",
            aliases = listOf("vitamin c", "ascorbic", "ascorbate", "vit c"),
            benefits = listOf("Antioxidant", "Enhances non-heme iron absorption"),
            typicalDoseLow = 250.0, typicalDoseHigh = 1000.0, doseUnit = DoseUnit.MG,
            pairsWith = listOf("iron"),
            evidenceNote = "Commonly taken with iron to aid absorption.", tier = 1,
        ),
        Ingredient(
            key = "calcium", displayName = "Calcium", category = "Minerals",
            aliases = listOf("calcium", "ca citrate", "calcium carbonate"),
            benefits = listOf("Bone support"),
            typicalDoseLow = 500.0, typicalDoseHigh = 1000.0, doseUnit = DoseUnit.MG,
            timingFlags = f(ItemFlags.WITH_FOOD),
            avoidWith = listOf("iron", "zinc", "magnesium"),
            evidenceNote = "Competes with iron, zinc and magnesium for absorption — separate them.", tier = 1,
        ),
        Ingredient(
            key = "magnesium", displayName = "Magnesium", category = "Minerals",
            aliases = listOf("magnesium", "mag", "glycinate", "mag glycinate", "mag7", "bisglycinate magnesium", "citrate magnesium"),
            benefits = listOf("Muscle and nervous-system support", "Often taken in the evening"),
            typicalDoseLow = 200.0, typicalDoseHigh = 400.0, doseUnit = DoseUnit.MG,
            pairsWith = listOf("vitamin_d3"),
            avoidWith = listOf("calcium", "iron"),
            evidenceNote = "Commonly taken at night; separate from calcium and iron.", tier = 1,
        ),
        Ingredient(
            key = "zinc", displayName = "Zinc", category = "Minerals",
            aliases = listOf("zinc", "zinc picolinate", "zinc citrate"),
            benefits = listOf("Immune support"),
            typicalDoseLow = 15.0, typicalDoseHigh = 30.0, doseUnit = DoseUnit.MG,
            timingFlags = f(ItemFlags.WITH_FOOD),
            pairsWith = listOf("copper"),
            avoidWith = listOf("calcium", "iron"),
            evidenceNote = "Long-term high zinc can lower copper — many pair the two.", tier = 1,
        ),
        Ingredient(
            key = "copper", displayName = "Copper", category = "Minerals",
            aliases = listOf("copper", "copper bisglycinate"),
            benefits = listOf("Balances zinc intake"),
            typicalDoseLow = 1.0, typicalDoseHigh = 2.0, doseUnit = DoseUnit.MG,
            pairsWith = listOf("zinc"),
            evidenceNote = "Often added to offset higher zinc intake.", tier = 2,
        ),
        Ingredient(
            key = "omega3", displayName = "Omega-3 (fish oil)", category = "Omega",
            aliases = listOf("omega", "omega 3", "omega-3", "fish oil", "epa", "dha"),
            benefits = listOf("Heart and brain support"),
            typicalDoseLow = 1000.0, typicalDoseHigh = 2000.0, doseUnit = DoseUnit.MG,
            timingFlags = f(ItemFlags.FAT_SOLUBLE, ItemFlags.WITH_FOOD),
            evidenceNote = "Taken with a meal to aid absorption and reduce reflux.", tier = 1,
        ),
        Ingredient(
            key = "selenium", displayName = "Selenium", category = "Minerals",
            aliases = listOf("selenium", "selenomethionine"),
            benefits = listOf("Antioxidant; thyroid support"),
            typicalDoseLow = 100.0, typicalDoseHigh = 200.0, doseUnit = DoseUnit.MCG,
            evidenceNote = "A narrow range — more is not better.", tier = 2,
        ),
        Ingredient(
            key = "folate", displayName = "L-Methylfolate", category = "Vitamins",
            aliases = listOf("folate", "methylfolate", "l-methylfolate", "5-mthf", "folic"),
            benefits = listOf("Methylation support", "Paired with B12"),
            typicalDoseLow = 400.0, typicalDoseHigh = 1000.0, doseUnit = DoseUnit.MCG,
            pairsWith = listOf("b12"),
            evidenceNote = "Commonly taken together with B12.", tier = 1,
        ),
        Ingredient(
            key = "b12", displayName = "Methyl-B12", category = "Vitamins",
            aliases = listOf("b12", "methylcobalamin", "cobalamin", "vitamin b12"),
            benefits = listOf("Energy metabolism; nerve support", "Paired with folate"),
            typicalDoseLow = 500.0, typicalDoseHigh = 2000.0, doseUnit = DoseUnit.MCG,
            pairsWith = listOf("folate"),
            evidenceNote = "Often co-located with folate.", tier = 1,
        ),
        Ingredient(
            key = "glycine", displayName = "Glycine", category = "Sleep",
            aliases = listOf("glycine"),
            benefits = listOf("Calming; sleep quality"),
            typicalDoseLow = 3000.0, typicalDoseHigh = 5000.0, doseUnit = DoseUnit.MG,
            evidenceNote = "Commonly taken before bed.", tier = 2,
        ),
        Ingredient(
            key = "nac", displayName = "NAC", category = "Actives",
            aliases = listOf("nac", "n-acetyl", "acetylcysteine", "cysteine"),
            benefits = listOf("Glutathione precursor; antioxidant"),
            typicalDoseLow = 600.0, typicalDoseHigh = 1200.0, doseUnit = DoseUnit.MG,
            evidenceNote = "Supportive evidence across several areas.", tier = 2,
        ),
        Ingredient(
            key = "melatonin", displayName = "Melatonin", category = "Sleep",
            aliases = listOf("melatonin"),
            benefits = listOf("Sleep-onset support"),
            typicalDoseLow = 0.5, typicalDoseHigh = 3.0, doseUnit = DoseUnit.MG,
            avoidWith = listOf("caffeine"),
            evidenceNote = "Low doses near bedtime are commonly noted; more isn't better.", tier = 1,
        ),
        Ingredient(
            key = "creatine", displayName = "Creatine", category = "Actives",
            aliases = listOf("creatine", "monohydrate"),
            benefits = listOf("Strength and recovery", "Cognitive support"),
            typicalDoseLow = 3000.0, typicalDoseHigh = 5000.0, doseUnit = DoseUnit.MG,
            evidenceNote = "Among the most well-studied supplements; daily timing is flexible.", tier = 1,
        ),
        Ingredient(
            key = "nmn", displayName = "NMN", category = "Longevity",
            aliases = listOf("nmn", "nicotinamide mononucleotide"),
            benefits = listOf("NAD+ precursor"),
            typicalDoseLow = 250.0, typicalDoseHigh = 500.0, doseUnit = DoseUnit.MG,
            timingFlags = f(ItemFlags.FASTED),
            evidenceNote = "Emerging human evidence; often taken in the morning.", tier = 3,
        ),
        Ingredient(
            key = "ca_akg", displayName = "Ca-AKG", category = "Longevity",
            aliases = listOf("ca-akg", "akg", "alpha-ketoglutarate", "calcium akg"),
            benefits = listOf("Cellular-aging research interest"),
            typicalDoseLow = 1000.0, typicalDoseHigh = 2000.0, doseUnit = DoseUnit.MG,
            evidenceNote = "Early evidence; popular in longevity stacks.", tier = 3,
        ),
        Ingredient(
            key = "spermidine", displayName = "Spermidine", category = "Longevity",
            aliases = listOf("spermidine"),
            benefits = listOf("Autophagy research interest"),
            typicalDoseLow = 1.0, typicalDoseHigh = 6.0, doseUnit = DoseUnit.MG,
            evidenceNote = "Emerging; commonly taken in the morning.", tier = 3,
        ),
        Ingredient(
            key = "urolithin_a", displayName = "Urolithin A", category = "Longevity",
            aliases = listOf("urolithin", "urolithin a", "mitopure"),
            benefits = listOf("Mitochondrial (mitophagy) support"),
            typicalDoseLow = 500.0, typicalDoseHigh = 1000.0, doseUnit = DoseUnit.MG,
            timingFlags = f(ItemFlags.WITH_FOOD),
            evidenceNote = "Growing human data; taken with a meal.", tier = 2,
        ),
        Ingredient(
            key = "curcumin", displayName = "Curcumin", category = "Actives",
            aliases = listOf("curcumin", "turmeric"),
            benefits = listOf("Antioxidant; joint comfort"),
            typicalDoseLow = 500.0, typicalDoseHigh = 1000.0, doseUnit = DoseUnit.MG,
            timingFlags = f(ItemFlags.FAT_SOLUBLE, ItemFlags.WITH_FOOD),
            evidenceNote = "Absorption improves with fat and piperine.", tier = 2,
        ),
        Ingredient(
            key = "coq10", displayName = "CoQ10", category = "Actives",
            aliases = listOf("coq10", "coenzyme q10", "ubiquinol", "ubiquinone"),
            benefits = listOf("Cellular energy; heart support"),
            typicalDoseLow = 100.0, typicalDoseHigh = 200.0, doseUnit = DoseUnit.MG,
            timingFlags = f(ItemFlags.FAT_SOLUBLE, ItemFlags.WITH_FOOD),
            evidenceNote = "Fat-soluble; taken with a meal.", tier = 2,
        ),
        Ingredient(
            key = "ashwagandha", displayName = "Ashwagandha", category = "Actives",
            aliases = listOf("ashwagandha", "ksm-66", "withania"),
            benefits = listOf("Stress and sleep support"),
            typicalDoseLow = 300.0, typicalDoseHigh = 600.0, doseUnit = DoseUnit.MG,
            evidenceNote = "Reasonable evidence for stress; often taken in the evening.", tier = 2,
        ),
        Ingredient(
            key = "berberine", displayName = "Berberine", category = "Actives",
            aliases = listOf("berberine"),
            benefits = listOf("Metabolic / glucose support"),
            typicalDoseLow = 500.0, typicalDoseHigh = 1500.0, doseUnit = DoseUnit.MG,
            timingFlags = f(ItemFlags.WITH_FOOD),
            evidenceNote = "Taken with meals, usually split through the day.", tier = 2,
        ),
        Ingredient(
            key = "taurine", displayName = "Taurine", category = "Actives",
            aliases = listOf("taurine"),
            benefits = listOf("Cardiovascular and exercise interest"),
            typicalDoseLow = 1000.0, typicalDoseHigh = 3000.0, doseUnit = DoseUnit.MG,
            evidenceNote = "Active research area; well tolerated.", tier = 3,
        ),
        Ingredient(
            key = "collagen", displayName = "Collagen", category = "Actives",
            aliases = listOf("collagen", "peptides"),
            benefits = listOf("Skin, hair and joint support"),
            typicalDoseLow = 10.0, typicalDoseHigh = 20.0, doseUnit = DoseUnit.G,
            evidenceNote = "Commonly taken as a daily powder.", tier = 2,
        ),
        Ingredient(
            key = "vitamin_b_complex", displayName = "B-complex", category = "Vitamins",
            aliases = listOf("b complex", "b-complex", "vitamin b"),
            benefits = listOf("Energy metabolism (B vitamins)"),
            typicalDoseLow = 1.0, typicalDoseHigh = 1.0, doseUnit = DoseUnit.UNIT,
            timingFlags = f(ItemFlags.WITH_FOOD),
            evidenceNote = "Usually taken earlier in the day.", tier = 2,
        ),
        Ingredient(
            key = "probiotics", displayName = "Probiotics", category = "Actives",
            aliases = listOf("probiotic", "probiotics", "lactobacillus", "bifidobacterium"),
            benefits = listOf("Gut-flora support"),
            typicalDoseLow = 1.0, typicalDoseHigh = 1.0, doseUnit = DoseUnit.UNIT,
            evidenceNote = "Strain-specific; effects vary by product.", tier = 2,
        ),
    )

    private val normalized: List<Pair<String, Ingredient>> by lazy {
        ALL.flatMap { ing ->
            (listOf(ing.displayName, ing.key) + ing.aliases).map { norm(it) to ing }
        }.sortedByDescending { it.first.length } // prefer the most specific alias
    }

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Best-effort match of a product/item name to a known ingredient, or null. */
    fun match(name: String): Ingredient? {
        val n = norm(name)
        if (n.isBlank()) return null
        // exact alias first, then containment (longest alias wins via the sort).
        normalized.firstOrNull { it.first == n }?.let { return it.second }
        return normalized.firstOrNull { n.contains(it.first) && it.first.length >= 3 }?.second
    }

    fun byKey(key: String): Ingredient? = ALL.firstOrNull { it.key == key }
}
