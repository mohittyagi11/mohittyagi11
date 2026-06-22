package com.quietdose.trigger

import android.content.Context

/**
 * Brings every real-world trigger back into a known-good state in one place:
 * alarms (TIME_WINDOW / CADENCE_DAYS / MONTHLY + the daily wake fallback + daily
 * re-arm), the home geofence, the activity/sleep updates, and an opportunistic
 * Health Connect sleep poll.
 *
 * It's the single idempotent entry point used on boot ([BootReceiver]), on app
 * start (DoseApp.onCreate), after the daily re-arm alarm, and whenever the user
 * changes a trigger/home/permission. Every step is independently guarded so a
 * missing dependency (Play Services, Health Connect, a permission) degrades that
 * step alone — the rest still arm.
 */
object Reconciler {

    /** (Re)arm everything. Suspend: does DataStore/DB reads and HC I/O. */
    suspend fun reArmAll(context: Context) {
        val app = context.applicationContext

        // 1. Alarms — always available, no runtime permission for inexact alarms.
        runCatching { AlarmScheduler.scheduleAll(app) }

        // 2. Geofence — guarded internally (home set + location perms + Play Svcs).
        runCatching { GeofenceManager.register(app) }

        // 3. Activity + Sleep API updates — guarded internally (ACTIVITY_RECOGNITION).
        runCatching { WakeInferenceEngine.register(app) }

        // 4. Health Connect: catch up on a sleep session we may have missed while
        //    not running. No-ops if HC unavailable / permission ungranted.
        runCatching {
            if (HealthConnectSleep.hasWakeGroups(app)) HealthConnectSleep.checkOnce(app)
        }
    }
}
