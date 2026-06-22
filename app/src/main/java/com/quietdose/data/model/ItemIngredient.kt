package com.quietdose.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * One ingredient inside an item. An item is "ingredientized": a single-ingredient
 * product (Selenium) has one of these; a formula (a multivitamin) has many, each
 * with its own dose. [key] links to the curated knowledge base when recognised,
 * so the analyzer can reason per ingredient.
 */
data class ItemIngredient(
    val key: String?,            // catalog key, e.g. "selenium"; null if not in the KB
    val name: String,
    val doseAmount: Double? = null,
    val doseUnit: DoseUnit? = null,
)

/**
 * Serializes the ingredient list to a compact JSON string for the single
 * [com.quietdose.data.entity.ItemEntity.ingredients] column — no schema explosion,
 * and decoding never throws (a bad blob simply yields an empty list).
 */
object IngredientCodec {

    fun encode(list: List<ItemIngredient>): String? {
        if (list.isEmpty()) return null
        val arr = JSONArray()
        list.forEach { ing ->
            arr.put(
                JSONObject().apply {
                    ing.key?.let { put("k", it) }
                    put("n", ing.name)
                    ing.doseAmount?.let { put("a", it) }
                    ing.doseUnit?.let { put("u", it.name) }
                },
            )
        }
        return arr.toString()
    }

    fun decode(s: String?): List<ItemIngredient> {
        if (s.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("n").trim()
                if (name.isBlank()) return@mapNotNull null
                ItemIngredient(
                    key = o.optString("k").ifBlank { null },
                    name = name,
                    doseAmount = if (o.has("a")) o.optDouble("a").takeIf { !it.isNaN() } else null,
                    doseUnit = o.optString("u").ifBlank { null }?.let { u -> runCatching { DoseUnit.valueOf(u) }.getOrNull() },
                )
            }
        }.getOrDefault(emptyList())
    }
}
