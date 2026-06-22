package com.quietdose.data.source

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.quietdose.ui.theme.TintNeutral
import com.quietdose.ui.viz.DayEvent
import com.quietdose.ui.viz.EventSource
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * OPTIONAL phone-usage signal: the day's first screen-on / unlock, as a proxy
 * for "the phone woke up" → a single DEVICE [DayEvent].
 *
 * Behind a hard guard: [PACKAGE_USAGE_STATS] is an AppOps-gated, user-granted
 * permission. If access has not been granted (or the manager is unavailable),
 * [hasAccess] is false and [events] returns an empty list — never a crash and
 * never a permission prompt from here.
 */
class DeviceUsageSource(context: Context) : DaySource {

    private val appContext = context.applicationContext

    /** Whether the user has granted Usage Access for this app (Settings toggle). */
    fun hasAccess(): Boolean = runCatching {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            appContext.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    override suspend fun events(epochDay: Long): List<DayEvent> {
        if (!hasAccess()) return emptyList()
        val manager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        return runCatching {
            val zone = ZoneId.systemDefault()
            val date = LocalDate.ofEpochDay(epochDay)
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val firstInteractive = firstInteractiveMs(manager, start, end)
                ?: return@runCatching emptyList()

            val t = java.time.Instant.ofEpochMilli(firstInteractive).atZone(zone).toLocalTime()
            listOf(
                DayEvent(
                    minuteOfDay = minuteOfDay(t),
                    category = "Phone",
                    label = "First unlock",
                    magnitude = 1f,
                    done = true,
                    color = TintNeutral,
                    source = EventSource.DEVICE,
                ),
            )
        }.getOrDefault(emptyList())
    }

    /** Timestamp of the first screen-interactive / unlock event in the window. */
    private fun firstInteractiveMs(manager: UsageStatsManager, start: Long, end: Long): Long? {
        val events: UsageEvents = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (isWake(event.eventType)) return event.timeStamp
        }
        return null
    }

    private fun isWake(type: Int): Boolean = when (type) {
        UsageEvents.Event.SCREEN_INTERACTIVE,
        UsageEvents.Event.KEYGUARD_HIDDEN,
        UsageEvents.Event.USER_INTERACTION,
        UsageEvents.Event.MOVE_TO_FOREGROUND -> true
        else -> false
    }

    private fun minuteOfDay(t: LocalTime): Int = (t.hour * 60 + t.minute).coerceIn(0, 1439)
}
