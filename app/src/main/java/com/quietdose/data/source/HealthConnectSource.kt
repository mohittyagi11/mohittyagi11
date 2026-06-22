package com.quietdose.data.source

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.quietdose.ui.theme.TintEvening
import com.quietdose.ui.theme.TintMorning
import com.quietdose.ui.theme.TintNight
import com.quietdose.ui.viz.DayEvent
import com.quietdose.ui.viz.EventSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reads on-device health signals via Health Connect (the Ultrahuman ring and
 * other wearables write here) and projects them as DEVICE [DayEvent]s:
 *  - last night's [SleepSessionRecord] → a "Woke" mark at wake time,
 *  - the day's [StepsRecord] total → a "Steps" mark,
 *  - the day's [ActiveCaloriesBurnedRecord] total → an "Active energy" mark.
 *
 * Health Connect is fully local; nothing leaves the device. Every failure mode —
 * the app/SDK not installed, the provider unavailable, permissions not yet
 * granted, or a read error — collapses to an empty list so the graph never
 * crashes on a device without the data.
 */
class HealthConnectSource(context: Context) : DaySource {

    private val appContext = context.applicationContext

    /** The permissions this source needs; surfaced so the UI can request them. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    override suspend fun events(epochDay: Long): List<DayEvent> {
        val client = client() ?: return emptyList()

        // Only read what we're actually allowed to — never throws on partial grant.
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        if (granted.isEmpty()) return emptyList()

        val zone = ZoneId.systemDefault()
        val date = LocalDate.ofEpochDay(epochDay)
        val dayStart = date.atStartOfDay(zone).toInstant()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

        val out = ArrayList<DayEvent>(3)

        if (granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
            wakeEvent(client, zone, date, dayStart, dayEnd)?.let(out::add)
        }
        if (granted.contains(HealthPermission.getReadPermission(StepsRecord::class))) {
            stepsEvent(client, dayStart, dayEnd)?.let(out::add)
        }
        if (granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))) {
            energyEvent(client, dayStart, dayEnd)?.let(out::add)
        }
        return out
    }

    /** Last night's sleep → a "Woke" DEVICE event at the wake time. */
    private suspend fun wakeEvent(
        client: HealthConnectClient,
        zone: ZoneId,
        date: LocalDate,
        dayStart: Instant,
        dayEnd: Instant,
    ): DayEvent? = runCatching {
        // A session that ends today may have started the previous evening.
        val from = date.minusDays(1).atStartOfDay(zone).toInstant()
        val sessions = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, dayEnd),
            ),
        ).records
        // The session whose wake (end) falls within this day, latest first.
        val session = sessions
            .filter { !it.endTime.isBefore(dayStart) && it.endTime.isBefore(dayEnd) }
            .maxByOrNull { it.endTime } ?: return@runCatching null

        DayEvent(
            minuteOfDay = minuteOfDay(session.endTime, session.endZoneOffset?.let { ZoneId.ofOffset("UTC", it) } ?: zone),
            category = "Sleep",
            label = "Woke",
            magnitude = 1f,
            done = true, // a recorded event, already happened
            color = TintMorning,
            source = EventSource.DEVICE,
        )
    }.getOrNull()

    /** The day's step total → a "Steps" DEVICE event, anchored mid-morning. */
    private suspend fun stepsEvent(
        client: HealthConnectClient,
        dayStart: Instant,
        dayEnd: Instant,
    ): DayEvent? = runCatching {
        val total = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
            ),
        ).records.sumOf { it.count }
        if (total <= 0L) return@runCatching null

        DayEvent(
            minuteOfDay = 12 * 60, // a daily roll-up; anchor at midday
            category = "Activity",
            label = "$total steps",
            // Compress to a calm 0..3 mark size (10k steps ≈ full).
            magnitude = (total.toFloat() / 10_000f).coerceIn(0.3f, 3f),
            done = true,
            color = TintEvening,
            source = EventSource.DEVICE,
        )
    }.getOrNull()

    /** The day's active energy → an "Active energy" DEVICE event. */
    private suspend fun energyEvent(
        client: HealthConnectClient,
        dayStart: Instant,
        dayEnd: Instant,
    ): DayEvent? = runCatching {
        val kcal = client.readRecords(
            ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
            ),
        ).records.sumOf { it.energy.inKilocalories }
        if (kcal <= 0.0) return@runCatching null

        DayEvent(
            minuteOfDay = 12 * 60 + 30,
            category = "Activity",
            label = "${kcal.toInt()} kcal active",
            magnitude = (kcal.toFloat() / 500f).coerceIn(0.3f, 3f),
            done = true,
            color = TintNight,
            source = EventSource.DEVICE,
        )
    }.getOrNull()

    /** A usable client, or null if Health Connect is unavailable on this device. */
    private fun client(): HealthConnectClient? = runCatching {
        if (HealthConnectClient.getSdkStatus(appContext) != HealthConnectClient.SDK_AVAILABLE) {
            null
        } else {
            HealthConnectClient.getOrCreate(appContext)
        }
    }.getOrNull()

    private fun minuteOfDay(instant: Instant, zone: ZoneId): Int {
        val t: LocalTime = instant.atZone(zone).toLocalTime()
        return (t.hour * 60 + t.minute).coerceIn(0, 1439)
    }
}
