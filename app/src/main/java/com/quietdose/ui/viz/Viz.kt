package com.quietdose.ui.viz

import androidx.compose.ui.graphics.Color

/**
 * Visualization building blocks. The day is modelled as a stream of [DayEvent]s
 * and rendered according to a *declarative* [VizSpec] — a recipe that maps data
 * channels (time, category, magnitude, status) onto visual ones (angle, radius,
 * size, colour). A [VizDesigner] produces the spec; today that's a seeded
 * default, later the on-device SLM, so the same renderer can express many
 * meaningful views without new chart code.
 */

/** Where a day event comes from — the graph spans more than supplements. */
enum class EventSource { SUPPLEMENT, TRIGGER, DEVICE, SKINCARE }

/** One thing that happens (or is due) in the day, in source-agnostic terms. */
data class DayEvent(
    val minuteOfDay: Int,   // 0..1439 — drives time/angle
    val category: String,   // lane / grouping label
    val label: String,
    val magnitude: Float,    // e.g. dose count / intensity → mark size
    val done: Boolean,       // status → fill
    val color: Color,
    val source: EventSource,
)

enum class VizLayout { RADIAL_DAY, LINEAR_TIME }

/** A semantic data dimension a visual channel can be bound to. */
enum class Channel { TIME, CATEGORY, MAGNITUDE, STATUS }

/**
 * A declarative visualization recipe. The renderer reads this rather than
 * hard-coding a chart, so the designer (rules now, SLM later) can re-map
 * channels to surface different insights.
 */
data class VizSpec(
    val layout: VizLayout,
    val laneBy: Channel,    // what concentric lane / y encodes
    val sizeBy: Channel,    // what mark size encodes
    val colorBy: Channel,   // what colour encodes
    val title: String,
    val caption: String,
)

/** Produces a [VizSpec] for a set of events. The SLM will implement this later. */
fun interface VizDesigner {
    fun design(events: List<DayEvent>): VizSpec
}

/**
 * The seeded default — a radial day-dial keyed by source lanes, dose-count mark
 * size and group colour. Stands in until the on-device SLM designs the spec.
 */
val DefaultVizDesigner = VizDesigner { events ->
    val sources = events.map { it.source }.distinct().size
    VizSpec(
        layout = VizLayout.RADIAL_DAY,
        laneBy = Channel.CATEGORY,
        sizeBy = Channel.MAGNITUDE,
        colorBy = Channel.CATEGORY,
        title = "Your day",
        caption = if (sources > 1) "Across the day, by source" else "Routines around the clock",
    )
}
