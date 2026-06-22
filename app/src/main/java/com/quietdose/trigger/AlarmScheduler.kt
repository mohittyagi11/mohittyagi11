package com.quietdose.trigger

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.quietdose.data.entity.GroupEntity
import com.quietdose.data.model.TriggerType
import com.quietdose.data.settings.Settings
import com.quietdose.di.ServiceLocator
import com.quietdose.util.DateUtils
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the clock-anchored half of the trigger engine via [AlarmManager]:
 *
 *  - TIME_WINDOW  → an inexact (windowed) alarm at the *midpoint* of the window,
 *    because the window is intentionally loose — we never want an exact-to-the-
 *    second ping for "mid-afternoon".
 *  - CADENCE_DAYS → the same, on the next day the cadence is due.
 *  - MONTHLY      → the next matching day-of-month.
 *  - WAKE         → a single daily *fallback* alarm at `latestWakeFallbackHour`
 *    that fires the WAKE group only if inference hasn't already fired it. This is
 *    the guarantee that the morning dose surfaces even with no sensors.
 *
 * Everything is inexact (`setWindow` / `setInexactRepeating`-style one-shots
 * rearmed daily) so we are gentle on the battery and don't need the exact-alarm
 * permission. Alarms are *one-shot* and re-armed by [AlarmReceiver] after each
 * fire and by [Reconciler] on boot/app-start, which keeps them self-healing.
 */
object AlarmScheduler {

    /** A tolerance window for inexact alarms (the OS may slide within this). */
    private const val WINDOW_SLACK_MS = 15 * 60 * 1000L // 15 min

    /** (Re)schedule every alarm-backed group plus the daily wake fallback. */
    suspend fun scheduleAll(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val repo = ServiceLocator.repository(app)
        val settings = ServiceLocator.settings(app).settings.first()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)

        val groups = repo.observeGroups().first()
        for (group in groups) {
            when (group.trigger) {
                TriggerType.TIME_WINDOW -> scheduleTimeWindow(app, am, group, now, zone)
                TriggerType.CADENCE_DAYS -> scheduleCadence(app, am, group, settings, now, zone)
                TriggerType.MONTHLY -> scheduleMonthly(app, am, group, now, zone)

                // Non-alarm triggers are handled elsewhere (wake inference,
                // geofence, manual) — listed explicitly to keep the when exhaustive.
                TriggerType.WAKE,
                TriggerType.ARRIVE_HOME,
                TriggerType.ARRIVE_PLACE,
                TriggerType.LEAVE,
                TriggerType.BEFORE_SLEEP,
                TriggerType.MANUAL -> Unit
            }
        }

        scheduleWakeFallback(app, am, settings, now, zone)
        scheduleDailyRearm(app, am, now, zone)
    }

    // ---- Per-trigger scheduling ------------------------------------------

    private fun scheduleTimeWindow(
        context: Context,
        am: AlarmManager,
        group: GroupEntity,
        now: LocalDateTime,
        zone: ZoneId,
    ) {
        val cfg = TriggerConfig.timeWindow(group.triggerConfig) ?: return
        val triggerAt = nextOccurrenceOfMinute(now, cfg.midMin, zone)
        setWindow(am, triggerAt, groupAlarmPi(context, group.id))
    }

    private fun scheduleCadence(
        context: Context,
        am: AlarmManager,
        group: GroupEntity,
        settings: Settings,
        now: LocalDateTime,
        zone: ZoneId,
    ) {
        val cfg = TriggerConfig.cadence(group.triggerConfig) ?: return
        val today = DateUtils.today(settings.dayRolloverHour, zone)
        // Find the next epoch day >= today that lands on the cadence.
        var day = today
        val limit = today + 366
        while (day <= limit && (day - cfg.anchorEpochDay).mod(cfg.interval.toLong()) != 0L) {
            day++
        }
        val candidate = atMinuteOnEpochDay(day, cfg.atMin, zone)
        // If today's slot already passed, roll to the next cadence day.
        val triggerAt = if (candidate.isAfter(now)) {
            candidate
        } else {
            atMinuteOnEpochDay(day + cfg.interval, cfg.atMin, zone)
        }
        setWindow(am, toEpochMs(triggerAt, zone), groupAlarmPi(context, group.id))
    }

    private fun scheduleMonthly(
        context: Context,
        am: AlarmManager,
        group: GroupEntity,
        now: LocalDateTime,
        zone: ZoneId,
    ) {
        val cfg = TriggerConfig.monthly(group.triggerConfig) ?: return
        val triggerAt = nextMonthlyOccurrence(now, cfg, zone) ?: return
        setWindow(am, toEpochMs(triggerAt, zone), groupAlarmPi(context, group.id))
    }

    private fun scheduleWakeFallback(
        context: Context,
        am: AlarmManager,
        settings: Settings,
        now: LocalDateTime,
        zone: ZoneId,
    ) {
        val triggerAt = nextOccurrenceOfMinute(now, settings.latestWakeFallbackHour * 60, zone)
        setWindow(am, triggerAt, wakeFallbackPi(context))
    }

    /**
     * A daily housekeeping alarm just after rollover that calls back into the
     * engine to re-arm everything — so cadence/monthly one-shots that fired today
     * get their *next* occurrence scheduled even if the app is never opened.
     */
    private fun scheduleDailyRearm(
        context: Context,
        am: AlarmManager,
        now: LocalDateTime,
        zone: ZoneId,
    ) {
        // 00:05 local — early, cheap, inexact.
        val triggerAt = nextOccurrenceOfMinute(now, 5, zone)
        setWindow(am, triggerAt, dailyRearmPi(context))
    }

    // ---- AlarmManager plumbing -------------------------------------------

    private fun setWindow(am: AlarmManager, triggerAtMs: Long, pi: PendingIntent) {
        // Inexact window: gentle on battery, no exact-alarm permission needed.
        // setWindow lets the OS batch within the slack; available since API 19.
        am.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            WINDOW_SLACK_MS,
            pi,
        )
    }

    fun cancelGroup(context: Context, groupId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(groupAlarmPi(context, groupId))
    }

    // ---- PendingIntents ---------------------------------------------------

    private fun groupAlarmPi(context: Context, groupId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TriggerContract.groupAlarmRequestCode(groupId),
            Intent(context, AlarmReceiver::class.java).apply {
                action = TriggerContract.ACTION_ALARM_GROUP
                putExtra(TriggerContract.EXTRA_GROUP_ID, groupId)
            },
            piFlags(),
        )

    private fun wakeFallbackPi(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TriggerContract.RC_WAKE_FALLBACK,
            Intent(context, AlarmReceiver::class.java)
                .setAction(TriggerContract.ACTION_WAKE_FALLBACK),
            piFlags(),
        )

    private fun dailyRearmPi(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TriggerContract.RC_DAILY_REARM,
            Intent(context, AlarmReceiver::class.java)
                .setAction(TriggerContract.ACTION_DAILY_REARM),
            piFlags(),
        )

    // Alarm intents carry no data the OS fills in, so they can be immutable.
    private fun piFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // ---- Date math --------------------------------------------------------

    /** Next wall-clock time today/tomorrow at [minuteOfDay], strictly after [now]. */
    private fun nextOccurrenceOfMinute(now: LocalDateTime, minuteOfDay: Int, zone: ZoneId): Long {
        val m = minuteOfDay.coerceIn(0, 1439)
        var t = now.toLocalDate().atTime(LocalTime.of(m / 60, m % 60))
        if (!t.isAfter(now)) t = t.plusDays(1)
        return toEpochMs(t, zone)
    }

    private fun atMinuteOnEpochDay(epochDay: Long, minuteOfDay: Int, zone: ZoneId): LocalDateTime {
        val m = minuteOfDay.coerceIn(0, 1439)
        return LocalDate.ofEpochDay(epochDay).atTime(LocalTime.of(m / 60, m % 60))
    }

    private fun nextMonthlyOccurrence(
        now: LocalDateTime,
        cfg: TriggerConfig.Monthly,
        zone: ZoneId,
    ): LocalDateTime? {
        val time = LocalTime.of(cfg.atMin / 60, cfg.atMin % 60)
        // Scan up to ~13 months out to handle short months / run lengths.
        var date = now.toLocalDate()
        repeat(400) {
            if (cfg.days.any { it <= date.lengthOfMonth() && it == date.dayOfMonth }) {
                val candidate = date.atTime(time)
                if (candidate.isAfter(now)) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun toEpochMs(t: LocalDateTime, zone: ZoneId): Long =
        t.atZone(zone).toInstant().toEpochMilli()
}
