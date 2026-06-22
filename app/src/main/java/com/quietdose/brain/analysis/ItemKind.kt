package com.quietdose.brain.analysis

/**
 * The coarse *kind* of a tracked item. It decides which lens the analysis uses
 * (a toner must not be judged like a pill) and which fields matter (a supplement
 * has a dose; skincare has an AM/PM application; a device has a usage frequency).
 *
 * The label can also be a free, SLM-named sub-category (e.g. "Hydrating toner")
 * via [CategoryProfile.categoryLabel]; this enum is only the routing bucket.
 */
enum class ItemKind(val label: String) {
    SUPPLEMENT("Supplement"),
    SKINCARE("Skincare"),
    HAIRCARE("Haircare"),
    DEVICE("Device"),
    FOOD("Functional food"),
    OTHER("Other");

    /** Supplements/foods are *ingested* (dose matters); the rest are applied/used. */
    val isIngested: Boolean get() = this == SUPPLEMENT || this == FOOD
}

/**
 * Best-effort, deterministic guess of an item's [ItemKind] from its name + any
 * label/description text — the floor under the SLM's own classification, and the
 * answer when no model is present. Specific (skincare/device) keywords win over
 * the generic supplement signal.
 */
object KindDetector {

    private val SKINCARE = listOf(
        "toner", "serum", "moisturiser", "moisturizer", "cleanser", "essence", "ampoule",
        "sunscreen", "spf", "retinol", "retinoid", "niacinamide", "hyaluronic", "salicylic",
        "glycolic", "lactic", "exfoliant", "peel", "face mask", "sheet mask", "eye cream",
        "face cream", "facial", "rice toner", "snail mucin", "ceramide", "azelaic", "tretinoin",
        "bha", "aha", "pha", "benzoyl peroxide", "vitamin c serum", "ascorbic", "ascorbate",
        "squalane", "squalene", "peptide", "centella", "cica", "tranexamic", "mandelic",
        "kojic", "alpha arbutin", "arbutin", "panthenol", "allantoin", "rosehip", "clay mask",
        "micellar", "sleeping mask", "spot treatment", "blackhead", "pore", "day cream",
        "night cream", "anti-aging", "anti-ageing", "lip balm", "facewash", "face wash",
        "hydrating", "brightening", "exfoliating", "tinted", "primer", "mist", "cleansing oil",
        "double cleanse", "vitamin c", "bakuchiol", "adapalene", "retinaldehyde",
    )
    private val HAIRCARE = listOf(
        "shampoo", "conditioner", "hair oil", "scalp", "minoxidil", "hair serum", "hair mask",
        "rosemary oil", "ketoconazole", "hair growth", "leave-in", "leave in", "scalp serum",
        "scalp scrub", "clarifying", "sulfate-free", "sulphate-free", "dry shampoo",
        "hair tonic", "anti-dandruff", "dandruff", "redensyl", "biotin shampoo", "argan oil",
        "castor oil", "hair conditioner", "deep conditioner", "hair fall", "hair loss",
        "finasteride", "caffeine shampoo", "onion oil", "hair growth serum",
    )
    private val DEVICE = listOf(
        "derma roller", "dermaroller", "microneedle", "led mask", "red light", "gua sha",
        "ice roller", "facial massager", "cleansing brush", "high frequency", "microcurrent",
        "device", "wand", "massage tool", "jade roller", "face roller", "facial steamer",
        "steamer", "blackhead remover", "pore vacuum", "ipl", "led therapy", "light therapy",
        "nano mist", "ultrasonic", "skin scrubber", "spatula", "scalp massager", "comb",
    )
    private val FOOD = listOf(
        "protein powder", "whey", "electrolyte", "collagen powder", "greens powder", "fibre",
        "fiber supplement", "meal replacement", "bar ", "apple cider vinegar", "acv",
        "green tea", "matcha", "kefir", "kombucha", "kimchi", "sauerkraut", "yogurt",
        "yoghurt", "psyllium husk", "isabgol", "chia", "flaxseed", "flax seed", "oats",
        "honey", "ginger shot", "bone broth", "spirulina", "chlorella", "wheatgrass",
        "beetroot", "amla juice",
    )
    private val SUPPLEMENT = listOf(
        "capsule", "tablet", "softgel", "supplement", "vitamin", "mineral", "gummies",
        "mcg", " iu", "probiotic", "omega", "magnesium", "ashwagandha",
    )

    fun detect(name: String, text: String?): ItemKind {
        val hay = (name + " " + (text ?: "")).lowercase()
        fun any(words: List<String>) = words.any { hay.contains(it) }
        // A known supplement ingredient is a strong, trustworthy signal.
        if (IngredientCatalog.match(name) != null && !any(SKINCARE) && !any(DEVICE)) return ItemKind.SUPPLEMENT
        return when {
            any(DEVICE) -> ItemKind.DEVICE
            any(HAIRCARE) -> ItemKind.HAIRCARE
            any(SKINCARE) -> ItemKind.SKINCARE
            any(FOOD) -> ItemKind.FOOD
            any(SUPPLEMENT) -> ItemKind.SUPPLEMENT
            else -> ItemKind.OTHER
        }
    }
}
