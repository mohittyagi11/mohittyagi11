package com.quietdose.brain.skills

import com.quietdose.brain.LlmEngine
import com.quietdose.data.entity.ItemEntity
import com.quietdose.util.Format
import org.json.JSONArray

/**
 * Reviews the current stack and returns a few short, calm tips — timing,
 * pairings, interactions (e.g. "take iron away from calcium"). Model-only:
 * returns an empty list when no model is ready or the reply can't be parsed.
 * Never medical advice; phrased as gentle suggestions.
 */
class SuggestStackSkill : Skill<List<ItemEntity>, List<String>> {

    override val id: String = "suggest_stack"

    override suspend fun run(engine: LlmEngine, input: List<ItemEntity>): List<String> {
        if (!engine.isReady() || input.isEmpty()) return emptyList()
        val raw = runCatching { engine.complete(prompt(input)) }.getOrDefault("")
        return parse(raw)
    }

    private fun prompt(items: List<ItemEntity>): String = buildString {
        appendLine("You review a personal supplement stack and give at most 3 short, calm tips")
        appendLine("about timing, pairing or interactions. Each tip <= 14 words. No medical")
        appendLine("claims, no diagnosis, no dosing changes. Reply with ONLY a JSON array of strings.")
        appendLine()
        appendLine("Stack:")
        items.take(40).forEach { item ->
            val behaviour = Format.behaviour(item).joinToString(", ")
            append("- ${item.name}")
            if (behaviour.isNotBlank()) append(" ($behaviour)")
            appendLine()
        }
    }

    private fun parse(raw: String): List<String> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val arr = runCatching { JSONArray(raw.substring(start, end + 1)) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotBlank() && s != "null") add(s)
            }
        }.take(3)
    }
}
