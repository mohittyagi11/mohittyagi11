package com.quietdose.brain

import com.quietdose.brain.skills.DraftItem
import com.quietdose.brain.skills.IdentifyProductSkill
import com.quietdose.brain.skills.Skill
import com.quietdose.brain.skills.SuggestStackSkill
import com.quietdose.data.entity.ItemEntity

/**
 * The agentic layer: one loaded model ([engine]) driving many [Skill]s. Features
 * call the agent for whatever job they need — narration and viz design stay on
 * the [Brain] interface (heuristic-friendly, always-on), while richer jobs that
 * genuinely need the model (identify a product, suggest stack tweaks, …) run
 * here and degrade to null/empty when no model is loaded.
 */
class Agent(private val engine: LlmEngine) {

    /** True when a model is loaded and skills can actually run. */
    fun isReady(): Boolean = engine.isReady()

    /** Run any skill against the shared engine. */
    suspend fun <I, O> run(skill: Skill<I, O>, input: I): O = skill.run(engine, input)

    // --- Convenience entry points for current features ---------------------

    suspend fun identifyProduct(text: String): DraftItem? =
        if (engine.isReady()) IdentifyProductSkill().run(engine, text) else null

    suspend fun suggestStack(items: List<ItemEntity>): List<String> =
        if (engine.isReady()) SuggestStackSkill().run(engine, items) else emptyList()
}
