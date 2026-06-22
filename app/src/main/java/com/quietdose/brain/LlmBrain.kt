package com.quietdose.brain

import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.ui.viz.DayEvent
import com.quietdose.ui.viz.VizSpec
import com.quietdose.util.Format

/**
 * A [Brain] that prefers an on-device [LlmEngine] when it's ready and otherwise
 * delegates to [HeuristicBrain].
 *
 * Important: [Brain.narrate] and [Brain.design] are synchronous, while model
 * inference is suspending and may be slow. So those two methods *always* return
 * the heuristic result immediately — the UI never blocks and is always correct
 * with no model present. To get model-written copy, call the suspending
 * [narrateAsync] / [designAsync] from a coroutine and swap the text in when it
 * arrives. If the engine isn't ready or returns nothing usable, these fall back
 * to the heuristic too.
 */
class LlmBrain(
    private val engine: LlmEngine,
    private val fallback: HeuristicBrain = HeuristicBrain(),
) : Brain {

    override fun narrate(
        group: GroupEntity,
        items: List<ItemEntity>,
        takenCount: Int,
        status: String,
        hour: Int,
    ): String = fallback.narrate(group, items, takenCount, status, hour)

    override fun design(events: List<DayEvent>): VizSpec = fallback.design(events)

    /**
     * Model-written one-liner, with a heuristic fallback. Safe to call from any
     * coroutine; inference runs off the main thread inside the engine.
     */
    suspend fun narrateAsync(
        group: GroupEntity,
        items: List<ItemEntity>,
        takenCount: Int,
        status: String,
        hour: Int,
    ): String {
        val heuristic = fallback.narrate(group, items, takenCount, status, hour)
        if (!engine.isReady()) return heuristic
        val prompt = narratePrompt(group, items, takenCount, status, hour, heuristic)
        val raw = runCatching { engine.complete(prompt) }.getOrDefault("")
        return sanitize(raw) ?: heuristic
    }

    /**
     * Lets the model refine the spec's *copy* (title + caption) while the layout
     * and channel bindings stay the heuristic's — those drive the renderer and
     * must remain valid. Falls back to the heuristic spec wholesale.
     */
    suspend fun designAsync(events: List<DayEvent>): VizSpec {
        val base = fallback.design(events)
        if (events.isEmpty() || !engine.isReady()) return base
        val prompt = designPrompt(events, base.title, base.caption)
        val raw = runCatching { engine.complete(prompt) }.getOrDefault("")
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val title = lines.getOrNull(0)?.let { sanitize(it) }
        val caption = lines.getOrNull(1)?.let { sanitize(it) }
        return base.copy(
            title = title ?: base.title,
            caption = caption ?: base.caption,
        )
    }

    // --- prompts -------------------------------------------------------------

    private fun narratePrompt(
        group: GroupEntity,
        items: List<ItemEntity>,
        takenCount: Int,
        status: String,
        hour: Int,
        heuristic: String,
    ): String {
        val names = items.joinToString(", ") { it.name }
        val behaviour = items.flatMap { Format.behaviour(it) }.distinct().joinToString(", ")
        val paired = if (items.any { it.pairWithItemId != null }) "yes" else "no"
        return buildString {
            appendLine("You write one short, calm line for a supplement reminder timeline.")
            appendLine("Rules: at most 8 words. No emoji. No medical claims. No nagging.")
            appendLine("Reply with only the line, nothing else.")
            appendLine()
            appendLine("Routine: ${group.name}")
            appendLine("Items: $names")
            appendLine("Taken so far: $takenCount of ${items.size}")
            appendLine("Status: $status")
            appendLine("Hour of day: $hour")
            if (behaviour.isNotBlank()) appendLine("Notes: $behaviour")
            appendLine("Items pair together: $paired")
            appendLine("A safe baseline (you may improve on it): $heuristic")
        }
    }

    private fun designPrompt(events: List<DayEvent>, baseTitle: String, baseCaption: String): String {
        val cats = events.map { it.category }.distinct().joinToString(", ")
        val done = events.count { it.done }
        return buildString {
            appendLine("You title a calm day-graph of someone's supplement routines.")
            appendLine("Rules: line 1 = a 1-3 word title; line 2 = a short caption (max 8 words).")
            appendLine("No emoji. No medical claims. Reply with exactly those two lines.")
            appendLine()
            appendLine("Routines: $cats")
            appendLine("Done: $done of ${events.size}")
            appendLine("Baseline title: $baseTitle")
            appendLine("Baseline caption: $baseCaption")
        }
    }

    /** Trim model output to a single clean line; null if unusable. */
    private fun sanitize(raw: String): String? {
        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return null
        val cleaned = line
            .removeSurrounding("\"")
            .removePrefix("- ")
            .trim()
        return cleaned.takeIf { it.isNotBlank() && it.length <= 80 }
    }
}
