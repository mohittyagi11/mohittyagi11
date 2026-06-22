package com.quietdose.trigger

import org.json.JSONObject

/**
 * Parses the per-group [com.quietdose.data.entity.GroupEntity.triggerConfig] JSON
 * into typed shapes. The blob is deliberately schemaless in the DB so new trigger
 * kinds ship without a migration; the typing lives here instead.
 *
 * Parsing is defensive: malformed/empty JSON yields a `null` typed config and the
 * scheduler simply skips that group rather than crashing a broadcast receiver.
 */
object TriggerConfig {

    /** TIME_WINDOW: `{"startMin":840,"endMin":960}` (minutes from local midnight). */
    data class TimeWindow(val startMin: Int, val endMin: Int) {
        /** Midpoint minute of the window, clamped into a sane 0..1439 day. */
        val midMin: Int get() = ((startMin + endMin) / 2).coerceIn(0, 1439)
    }

    /** CADENCE_DAYS: `{"interval":2,"anchor":20300,"atMin":540}` (anchor = epoch day). */
    data class Cadence(val interval: Int, val anchorEpochDay: Long, val atMin: Int)

    /** MONTHLY: `{"days":[1],"run":3,"atMin":540}` (days of month, optional run length). */
    data class Monthly(val days: List<Int>, val run: Int, val atMin: Int)

    fun timeWindow(json: String): TimeWindow? = runCatching {
        val o = JSONObject(json)
        TimeWindow(
            startMin = o.optInt("startMin", -1),
            endMin = o.optInt("endMin", -1),
        ).takeIf { it.startMin in 0..1439 && it.endMin in 0..1439 && it.endMin >= it.startMin }
    }.getOrNull()

    fun cadence(json: String): Cadence? = runCatching {
        val o = JSONObject(json)
        val interval = o.optInt("interval", -1)
        if (interval < 1) return@runCatching null
        Cadence(
            interval = interval,
            anchorEpochDay = o.optLong("anchor", 0L),
            atMin = o.optInt("atMin", DEFAULT_AT_MIN).coerceIn(0, 1439),
        )
    }.getOrNull()

    fun monthly(json: String): Monthly? = runCatching {
        val o = JSONObject(json)
        val arr = o.optJSONArray("days") ?: return@runCatching null
        val days = buildList { for (i in 0 until arr.length()) add(arr.optInt(i)) }
            .filter { it in 1..31 }
        if (days.isEmpty()) return@runCatching null
        Monthly(
            days = days,
            run = o.optInt("run", 1).coerceAtLeast(1),
            atMin = o.optInt("atMin", DEFAULT_AT_MIN).coerceIn(0, 1439),
        )
    }.getOrNull()

    /** Default fire minute when a cadence/monthly config omits `atMin` (09:00). */
    const val DEFAULT_AT_MIN = 540
}
