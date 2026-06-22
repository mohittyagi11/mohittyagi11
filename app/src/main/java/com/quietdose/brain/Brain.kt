package com.quietdose.brain

import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.ui.viz.DayEvent
import com.quietdose.ui.viz.VizSpec

/**
 * The on-device "brain" of Dose. It does two jobs, both of which used to be
 * hard-coded rule copy:
 *
 *  1. [narrate] — a short, calm, contextual one-liner for a single timeline
 *     node (e.g. "Take now · fasted, before tea").
 *  2. [design] — turn the day's [DayEvent]s into a declarative [VizSpec] for the
 *     day-graph renderer.
 *
 * Everything runs on-device. There are no cloud calls of any kind. The default
 * implementation ([HeuristicBrain]) is fully deterministic and ships green; a
 * small local language model ([LlmBrain]) layers on top *only* when a model file
 * is present, and silently falls back to the heuristic otherwise.
 */
interface Brain {

    /**
     * A short one-liner for a timeline node.
     *
     * @param group   the routine the node represents.
     * @param items   the items due in that routine today.
     * @param takenCount how many of [items] are already confirmed.
     * @param status  the node's status, as the [com.quietdose.ui.home.NodeStatus]
     *                name ("DONE" / "NOW" / "DUE" / "UPCOMING"). Passed as a
     *                string to keep the brain free of any UI dependency.
     * @param hour    the current hour of day (0..23), for time-of-day colour.
     */
    fun narrate(
        group: GroupEntity,
        items: List<ItemEntity>,
        takenCount: Int,
        status: String,
        hour: Int,
    ): String

    /** Design the day-graph spec for [events]. */
    fun design(events: List<DayEvent>): VizSpec
}
