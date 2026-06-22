package com.quietdose.data.source

import com.quietdose.ui.viz.DayEvent

/**
 * A pluggable producer of [DayEvent]s for a single day. The "day graph" and
 * timeline span more than supplements — triggers, device/health data and
 * skincare all contribute — so each domain lives behind this one seam and the
 * [DaySourceRegistry] fuses them.
 *
 * Implementations MUST be resilient: missing providers, denied permissions or
 * empty data return an empty list, never an exception.
 */
fun interface DaySource {
    /** Events for the given local epoch day (see [com.quietdose.util.DateUtils]). */
    suspend fun events(epochDay: Long): List<DayEvent>
}
