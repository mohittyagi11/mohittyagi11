package com.quietdose.trigger

import android.app.AlarmManager
import android.content.Context

/**
 * Shared vocabulary for the trigger engine: intent actions, extra keys, request
 * codes and small helpers. Keeping these in one place means receivers and the
 * scheduler can never drift apart on a magic string.
 *
 * Request codes for [android.app.PendingIntent] must be stable and unique per
 * logical alarm/geofence so that re-arming *updates* the existing pending intent
 * instead of leaking a new one. Group-scoped intents fold the group id into the
 * request code; singletons (wake fallback, sleep/activity) use fixed codes in a
 * reserved range that group ids never reach (group ids start at 1 and the offset
 * keeps them well clear).
 */
object TriggerContract {

    // ---- Intent actions ---------------------------------------------------

    const val ACTION_ALARM_GROUP = "com.quietdose.trigger.action.ALARM_GROUP"
    const val ACTION_WAKE_FALLBACK = "com.quietdose.trigger.action.WAKE_FALLBACK"
    const val ACTION_DAILY_REARM = "com.quietdose.trigger.action.DAILY_REARM"

    const val ACTION_ACTIVITY = "com.quietdose.trigger.action.ACTIVITY"
    const val ACTION_SLEEP = "com.quietdose.trigger.action.SLEEP"
    const val ACTION_GEOFENCE = "com.quietdose.trigger.action.GEOFENCE"

    const val ACTION_BOOT_REARM = "com.quietdose.trigger.action.BOOT_REARM"

    // ---- Extra keys -------------------------------------------------------

    const val EXTRA_GROUP_ID = "com.quietdose.trigger.extra.GROUP_ID"

    // ---- Geofence ids -----------------------------------------------------

    const val GEOFENCE_HOME = "com.quietdose.geofence.HOME"

    // ---- PendingIntent request codes -------------------------------------

    /** Group-scoped alarms (TIME_WINDOW / CADENCE_DAYS / MONTHLY) live above this. */
    private const val GROUP_ALARM_BASE = 100_000

    /** Singleton request codes, in a range group offsets never reach. */
    const val RC_WAKE_FALLBACK = 1
    const val RC_DAILY_REARM = 2
    const val RC_ACTIVITY = 3
    const val RC_SLEEP = 4
    const val RC_GEOFENCE = 5

    fun groupAlarmRequestCode(groupId: Long): Int = (GROUP_ALARM_BASE + groupId).toInt()

    /** True when the OS will actually let us post exact alarms (API 31+ gate). */
    fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
