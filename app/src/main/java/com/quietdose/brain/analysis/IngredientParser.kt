package com.quietdose.brain.analysis

import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemIngredient

/**
 * Turns a label/description blob into the item's ingredient list — the step that
 * "ingredientizes" an item. It scans the text for known [IngredientCatalog]
 * ingredients and the dose printed next to each, so a single-ingredient product
 * yields one entry and a formula yields many. Pure and deterministic; when there's
 * no usable text it falls back to matching the product name (single ingredient).
 */
object IngredientParser {

    private val DOSE = Regex("(\\d+(?:\\.\\d+)?)\\s*(mcg|µg|mg|iu|g|ml)", RegexOption.IGNORE_CASE)
    private const val MAX = 16

    fun parse(text: String?, fallbackName: String): List<ItemIngredient> {
        val blob = text?.takeIf { it.isNotBlank() }
        if (blob == null) {
            // No panel text — treat the product as its named ingredient if we know it.
            val m = IngredientCatalog.match(fallbackName) ?: return emptyList()
            return listOf(ItemIngredient(m.key, m.displayName, null, m.doseUnit))
        }
        val lower = blob.lowercase()
        val found = LinkedHashMap<String, ItemIngredient>() // keyed by catalog key, in order of appearance
        // Order candidates by where they first appear so the list reads top-to-bottom.
        IngredientCatalog.ALL
            .mapNotNull { ing -> firstIndexOf(lower, ing)?.let { idx -> Triple(idx, ing, idx) } }
            .sortedBy { it.first }
            .forEach { (idx, ing, _) ->
                if (found.containsKey(ing.key) || found.size >= MAX) return@forEach
                val dose = doseNear(blob, idx)
                found[ing.key] = ItemIngredient(
                    key = ing.key,
                    name = ing.displayName,
                    doseAmount = dose?.first,
                    doseUnit = dose?.second ?: ing.doseUnit,
                )
            }
        if (found.isEmpty()) {
            // Nothing matched the KB — fall back to the product name as one ingredient.
            val m = IngredientCatalog.match(fallbackName)
            if (m != null) return listOf(ItemIngredient(m.key, m.displayName, null, m.doseUnit))
        }
        return found.values.toList()
    }

    /** True multi-ingredient formula vs a single active. */
    fun isFormula(list: List<ItemIngredient>): Boolean = list.size > 1

    private fun firstIndexOf(lowerText: String, ing: Ingredient): Int? {
        val needles = (listOf(ing.displayName) + ing.aliases).map { it.lowercase() }.filter { it.length >= 3 }
        return needles.mapNotNull { n -> lowerText.indexOf(n).takeIf { it >= 0 } }.minOrNull()
    }

    /** The dose printed within a short window after the ingredient name. */
    private fun doseNear(text: String, atIndex: Int): Pair<Double, DoseUnit>? {
        val window = text.substring(atIndex.coerceIn(0, text.length), (atIndex + 48).coerceAtMost(text.length))
        val m = DOSE.find(window) ?: return null
        val amt = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = when (m.groupValues[2].lowercase()) {
            "mg" -> DoseUnit.MG
            "mcg", "µg" -> DoseUnit.MCG
            "g" -> DoseUnit.G
            "iu" -> DoseUnit.IU
            "ml" -> DoseUnit.ML
            else -> return null
        }
        return amt to unit
    }
}
