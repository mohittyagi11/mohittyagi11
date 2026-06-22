package com.quietdose.brain

import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.entity.ItemEntity
import com.quietdose.ui.viz.Channel
import com.quietdose.ui.viz.DayEvent
import com.quietdose.ui.viz.DefaultVizDesigner
import com.quietdose.ui.viz.EventSource
import com.quietdose.ui.viz.VizLayout
import com.quietdose.ui.viz.VizSpec
import com.quietdose.util.Format

/**
 * The deterministic, no-model default. It ships green and needs nothing on disk.
 *
 * It is meaningfully richer than the old hard-coded copy: it reads the
 * time-of-day, the routine's behavioural flags (fasted / with food / no
 * tea-coffee …), pairing hints, and the taken/total ratio to compose a calm,
 * specific one-liner per node — and it enriches the seeded [DefaultVizDesigner]
 * spec with a contextual title and caption drawn from the day's events.
 *
 * Tone rules: lower-case-ish, calm, never nagging, never medical advice. Short.
 */
class HeuristicBrain : Brain {

    override fun narrate(
        group: GroupEntity,
        items: List<ItemEntity>,
        takenCount: Int,
        status: String,
        hour: Int,
    ): String {
        val total = items.size
        // Most-salient behavioural tag across the routine (fasted, with food, …).
        val behaviour = items.firstNotNullOfOrNull { Format.behaviour(it).firstOrNull() }
        val paired = items.any { it.pairWithItemId != null }
        val partOfDay = partOfDay(hour)

        return when (statusKind(status)) {
            StatusKind.DONE -> done(total, partOfDay)
            StatusKind.NOW -> joinDot(
                "Take now",
                behaviour?.let { phraseFor(it) },
                if (paired) "they pair up" else null,
            )
            StatusKind.DUE -> {
                val remaining = (total - takenCount).coerceAtLeast(0)
                joinDot(
                    if (takenCount in 1 until total) "$remaining still pending" else "Still pending",
                    behaviour?.let { phraseFor(it) },
                )
            }
            StatusKind.UPCOMING -> upcoming(group, partOfDay, behaviour)
            StatusKind.UNKNOWN -> joinDot("Coming up", behaviour?.let { phraseFor(it) })
        }
    }

    override fun design(events: List<DayEvent>): VizSpec {
        // Start from the seeded recipe so the renderer contract never drifts…
        val base = DefaultVizDesigner.design(events)
        if (events.isEmpty()) return base

        val sources = events.map { it.source }.distinct()
        val doneCount = events.count { it.done }
        val total = events.size
        val multiSource = sources.size > 1
        val spread = (events.maxOf { it.minuteOfDay } - events.minOf { it.minuteOfDay })

        // …then enrich: a tighter day reads better radially; a sprawling,
        // multi-source day reads better as a linear timeline.
        val layout = if (multiSource && spread > 10 * 60) VizLayout.LINEAR_TIME else VizLayout.RADIAL_DAY

        val title = when {
            total > 0 && doneCount == total -> "A full day"
            multiSource -> "Your day, woven"
            else -> "Your day"
        }
        val caption = buildString {
            append(
                when {
                    doneCount == total -> "Everything taken"
                    doneCount == 0 -> "$total to come"
                    else -> "$doneCount of $total done"
                },
            )
            append(" · ")
            append(if (multiSource) sourcePhrase(sources) else "around the clock")
        }

        return base.copy(
            layout = layout,
            laneBy = Channel.CATEGORY,
            sizeBy = Channel.MAGNITUDE,
            colorBy = if (multiSource) Channel.CATEGORY else Channel.STATUS,
            title = title,
            caption = caption,
        )
    }

    // --- narration helpers ---------------------------------------------------

    private enum class StatusKind { DONE, NOW, DUE, UPCOMING, UNKNOWN }

    private fun statusKind(status: String): StatusKind = when (status.uppercase()) {
        "DONE" -> StatusKind.DONE
        "NOW" -> StatusKind.NOW
        "DUE" -> StatusKind.DUE
        "UPCOMING" -> StatusKind.UPCOMING
        else -> StatusKind.UNKNOWN
    }

    private enum class PartOfDay { MORNING, MIDDAY, EVENING, NIGHT }

    private fun partOfDay(hour: Int): PartOfDay = when (hour) {
        in 5..10 -> PartOfDay.MORNING
        in 11..16 -> PartOfDay.MIDDAY
        in 17..21 -> PartOfDay.EVENING
        else -> PartOfDay.NIGHT
    }

    private fun done(total: Int, part: PartOfDay): String = when (part) {
        PartOfDay.MORNING -> "Done — a clean start"
        PartOfDay.MIDDAY -> "All taken"
        PartOfDay.EVENING -> "Wrapped up"
        PartOfDay.NIGHT -> if (total > 1) "All set for sleep" else "Set for sleep"
    }

    private fun upcoming(group: GroupEntity, part: PartOfDay, behaviour: String?): String {
        // A "when" hint from the group's own identity, kept calm.
        val whenHint = whenHintFor(group)
        val tail = behaviour?.let { phraseFor(it) }
        return joinDot(whenHint ?: comingPhrase(part), tail)
    }

    private fun comingPhrase(part: PartOfDay): String = when (part) {
        PartOfDay.MORNING -> "Later today"
        PartOfDay.MIDDAY -> "This afternoon"
        PartOfDay.EVENING -> "This evening"
        PartOfDay.NIGHT -> "Before sleep"
    }

    /** A short "when" hint keyed off the group's icon, mirroring GroupStyle. */
    private fun whenHintFor(group: GroupEntity): String? = when (group.iconKey) {
        "sun" -> "On waking"
        "droplet" -> "Mid-afternoon"
        "home" -> "With the evening meal"
        "moon" -> "Before sleep"
        "calendar" -> "Monthly"
        else -> null
    }

    /** Turn a Format.behaviour tag into a gentle phrase. */
    private fun phraseFor(tag: String): String = when (tag) {
        "fasted" -> "fasted"
        "empty stomach" -> "on an empty stomach"
        "with food" -> "with food"
        "no tea/coffee" -> "away from tea or coffee"
        "no calcium" -> "away from calcium"
        "fat-soluble" -> "with a little fat"
        else -> tag
    }

    private fun sourcePhrase(sources: List<EventSource>): String {
        // Exhaustive over EventSource so new sources force a decision here.
        val names = sources.map {
            when (it) {
                EventSource.SUPPLEMENT -> "supplements"
                EventSource.TRIGGER -> "moments"
                EventSource.DEVICE -> "device"
                EventSource.SKINCARE -> "skincare"
            }
        }
        return when (names.size) {
            0 -> "across the day"
            1 -> names.first()
            else -> names.dropLast(1).joinToString(", ") + " & " + names.last()
        }
    }

    private fun joinDot(vararg parts: String?): String =
        parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
}
