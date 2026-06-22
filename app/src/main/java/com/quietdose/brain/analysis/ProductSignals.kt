package com.quietdose.brain.analysis

/**
 * Strongly-validated signals lifted from the product page itself (never guessed):
 * brand, price, and peer rating. These feed the Price/Value, Claims and
 * Trust/Source chapters with *grounded* data when an item is added from a link.
 * Everything is optional — missing fields are simply marked "check on the store
 * page" rather than fabricated.
 */
data class ProductSignals(
    val brand: String? = null,
    val priceText: String? = null,     // e.g. "₹1,299" exactly as printed
    val priceAmount: Double? = null,   // numeric value if parseable
    val priceCurrency: String? = null, // e.g. "INR", "USD"
    val ratingValue: Double? = null,   // aggregate rating, e.g. 4.3
    val ratingCount: Int? = null,      // number of reviews
    val servings: Int? = null,         // units per pack, for price-per-day
    val ingredientsText: String? = null, // label/description blob to ingredientize from
    val directionsText: String? = null,  // the page's "how to use / directions" section, if found
    val packSize: String? = null,        // net volume/count as printed, e.g. "100 ml" — NOT a per-use dose
    val imageUrl: String? = null,         // og:image / product photo URL (a colour cue for the glyph, never a per-use fact)
    val sourceTitle: String? = null,
    val url: String? = null,
) {
    val isEmpty: Boolean
        get() = brand == null && priceText == null && ratingValue == null &&
            servings == null && sourceTitle == null
}
