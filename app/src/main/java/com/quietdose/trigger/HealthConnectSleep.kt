package com.quietdose.trigger

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.quietdose.data.model.TriggerType
import com.quietdose.di.ServiceLocator
import com.quietdose.util.DateUtils
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads the most recent Health Connect sleep session and, if it ended this
 * morning (after the earliest-wake hour), feeds a HEALTH_CONNECT_SLEEP_END signal
 * to [WakeInferenceEngine] — the highest-trust wake source.
 *
 * Health Connect has no push for "sleep ended", so this is a *poll*: the host app
 * calls [checkOnce] opportunistically (app start, the daily re-arm alarm). The
 * whole class degrades gracefully — if Health Connect isn't installed/available or
 * the read permission isn't granted, it no-ops.
 */
object HealthConnectSleep {

    /** Read permission this engine needs (sleep sessions). */
    val requiredPermissions: Set<String> =
        setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    /** Availability of the on-device Health Connect provider. */
    fun availability(context: Context): Int =
        HealthConnectClient.getSdkStatus(context)

    fun isAvailable(context: Context): Boolean =
        availability(context) == HealthConnectClient.SDK_AVAILABLE

    private fun clientOrNull(context: Context): HealthConnectClient? =
        if (isAvailable(context)) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }

    suspend fun hasPermission(context: Context): Boolean {
        val client = clientOrNull(context) ?: return false
        return runCatching {
            client.permissionController.getGrantedPermissions()
                .containsAll(requiredPermissions)
        }.getOrDefault(false)
    }

    /**
     * Poll once. If a sleep session ended after [earliestWakeHour] today and the
     * morning isn't already locked, fire the WAKE group(s) and lock the day.
     * Returns true if it fired. Safe to call when HC is unavailable (no-ops).
     */
    suspend fun checkOnce(context: Context): Boolean {
        val app = context.applicationContext
        val client = clientOrNull(app) ?: return false
        if (!hasPermission(app)) return false

        val zone = ZoneId.systemDefault()
        val settings = ServiceLocator.settings(app).settings.first()
        val today = DateUtils.today(settings.dayRolloverHour, zone)

        val state = TriggerState(app)
        if (state.morningAlreadyFired(today)) return false

        val now = Instant.now()
        val sessions = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minusSeconds(24 * 3600), now),
                ),
            ).records
        }.getOrNull().orEmpty()

        val lastEnd = sessions.maxByOrNull { it.endTime }?.endTime ?: return false
        val endLocal = LocalDateTime.ofInstant(lastEnd, zone)
        val endDay = DateUtils.epochDayFor(endLocal, settings.dayRolloverHour)

        // Only a session whose end belongs to *today* and lands after the earliest
        // wake hour counts as this morning's wake.
        if (endDay != today || endLocal.hour < settings.earliestWakeHour) return false

        // Hand to the fusion engine (it re-checks the lock + threshold).
        return WakeInferenceEngine.onSignal(
            app, WakeInferenceEngine.Signal.HEALTH_CONNECT_SLEEP_END,
        )
    }

    /** Are there any WAKE groups worth polling Health Connect for? */
    suspend fun hasWakeGroups(context: Context): Boolean {
        val repo = ServiceLocator.repository(context.applicationContext)
        return repo.observeGroups().first().any { it.trigger == TriggerType.WAKE }
    }
}
