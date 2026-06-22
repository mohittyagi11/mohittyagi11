package com.quietdose.trigger

import android.content.Context

/**
 * The trigger engine's public face — a [com.quietdose.di.ServiceLocator]-friendly
 * singleton the rest of the app talks to. Hosts call [reArmAll] from
 * `DoseApp.onCreate`, after onboarding, and whenever a trigger/home/permission
 * changes; everything underneath stays an implementation detail of the package.
 *
 * Suggested ServiceLocator wiring (not added here, see INTEGRATION.md):
 *   fun triggers(): TriggerEngine = TriggerEngine
 */
object TriggerEngine {

    /** (Re)schedule alarms, geofence and sensor updates. Idempotent. */
    suspend fun reArmAll(context: Context) = Reconciler.reArmAll(context)

    /** Re-arm only the alarm-backed triggers (e.g. after editing a TIME_WINDOW group). */
    suspend fun reArmAlarms(context: Context) = AlarmScheduler.scheduleAll(context)

    /** Re-register the home geofence (e.g. after the user sets/moves home). */
    suspend fun refreshGeofence(context: Context) = GeofenceManager.register(context)

    /** (Re)register activity + Sleep API updates (e.g. after granting ACTIVITY_RECOGNITION). */
    fun refreshWakeSensors(context: Context) = WakeInferenceEngine.register(context)

    /** Opportunistic Health Connect sleep poll (e.g. on resume). Returns true if it fired. */
    suspend fun pollHealthConnectSleep(context: Context): Boolean =
        HealthConnectSleep.checkOnce(context)

    /** Tear everything down (e.g. on disable/logout). */
    fun cancelSensors(context: Context) {
        WakeInferenceEngine.unregister(context)
        GeofenceManager.unregister(context)
    }
}
