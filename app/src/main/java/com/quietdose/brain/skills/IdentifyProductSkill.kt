package com.quietdose.brain.skills

import com.quietdose.brain.LlmEngine
import com.quietdose.data.model.DoseUnit
import com.quietdose.data.model.ItemType
import org.json.JSONObject

/**
 * Turns free text — a product name, or the OCR'd text off a bottle label — into
 * a structured [DraftItem] the item editor can pre-fill. Model-only: returns
 * null when no model is ready or the reply can't be parsed.
 */
class IdentifyProductSkill : Skill<String, DraftItem?> {

    override val id: String = "identify_product"

    override suspend fun run(engine: LlmEngine, input: String): DraftItem? {
        if (!engine.isReady() || input.isBlank()) return null
        val raw = runCatching { engine.complete(prompt(input)) }.getOrDefault("")
        return parse(raw)
    }

    private fun prompt(text: String): String = buildString {
        appendLine("Extract one supplement from the text into JSON. Reply with ONLY the JSON object.")
        appendLine("Schema: {\"name\":string, \"brand\":string|null, \"category\":string|null,")
        appendLine(" \"type\":one of [CAPSULE,TABLET,SOFTGEL,SPRAY,POWDER,GUMMY,LIQUID,SUBLINGUAL,OTHER],")
        appendLine(" \"doseAmount\":number, \"doseUnit\":one of [UNIT,MG,MCG,G,IU,ML,DROP,SCOOP],")
        appendLine(" \"note\":string|null}")
        appendLine("No commentary, no markdown. If unknown, use sensible defaults.")
        appendLine()
        append("Text: ")
        append(text.take(1200))
    }

    private fun parse(raw: String): DraftItem? {
        val json = raw.substringAfter('{', "").let { if (it.isEmpty()) return null else "{$it" }
            .substringBeforeLast('}').let { "$it}" }
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val name = obj.optString("name").trim()
        if (name.isEmpty()) return null
        return DraftItem(
            name = name,
            brand = obj.optString("brand").trim().ifBlank { null }?.takeIf { it != "null" },
            category = obj.optString("category").trim().ifBlank { null }?.takeIf { it != "null" },
            type = enumOrDefault(obj.optString("type"), ItemType.entries, ItemType.CAPSULE),
            doseAmount = obj.optDouble("doseAmount", 1.0).takeIf { it > 0 } ?: 1.0,
            doseUnit = enumOrDefault(obj.optString("doseUnit"), DoseUnit.entries, DoseUnit.UNIT),
            note = obj.optString("note").trim().ifBlank { null }?.takeIf { it != "null" },
        )
    }

    private fun <T : Enum<T>> enumOrDefault(value: String, values: List<T>, default: T): T =
        values.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: default
}
