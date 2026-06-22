package com.quietdose.trigger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController

/**
 * One stop for every runtime/special permission the trigger engine needs, with
 * `check*` predicates and `*intent` / contract factories the UI uses to request
 * them. Nothing here requests anything by itself — the caller (onboarding screen
 * or a settings row) decides *when* to ask, so the prompts stay contextual.
 *
 * Request order that works best in practice (each gated on the previous):
 *   POST_NOTIFICATIONS → ACTIVITY_RECOGNITION → ACCESS_FINE_LOCATION →
 *   ACCESS_BACKGROUND_LOCATION → (special) exact-alarm + battery exemption →
 *   Health Connect read (sleep).
 *
 * Background location and Health Connect each MUST be requested on their own,
 * after a clear rationale, per platform policy.
 */
object TriggerPermissions {

    /** Runtime permission strings, in the recommended request order. */
    val runtimePermissions: List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        // ACCESS_BACKGROUND_LOCATION must be requested separately, after FINE.
    }

    // ---- Simple runtime checks -------------------------------------------

    private fun granted(context: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }

    fun hasActivityRecognition(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(context, Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            true
        }

    fun hasFineLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            true
        }

    /** Geofencing needs FINE + BACKGROUND together. */
    fun hasGeofenceLocation(context: Context): Boolean =
        hasFineLocation(context) && hasBackgroundLocation(context)

    // ---- Exact alarms (special access) -----------------------------------

    /**
     * We schedule *inexact* windowed alarms, which need no permission. This is
     * exposed only so a power-user toggle can request exact delivery if ever
     * desired; the engine works fully without it.
     */
    fun canScheduleExactAlarms(context: Context): Boolean =
        TriggerContract.canScheduleExactAlarms(context)

    /** Intent to the system screen where the user can grant exact-alarm access. */
    fun requestExactAlarmIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            null
        }

    // ---- Battery-optimisation exemption ----------------------------------

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent that pops the system dialog to request an exemption. Note: Play
     * Store policy restricts this; use sparingly and behind a clear rationale.
     */
    fun requestBatteryExemptionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )

    /** Fallback: the battery-optimisation settings list (always available). */
    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    // ---- Health Connect (sleep read) -------------------------------------

    val healthConnectPermissions: Set<String> = HealthConnectSleep.requiredPermissions

    fun healthConnectAvailable(context: Context): Boolean =
        HealthConnectSleep.isAvailable(context)

    suspend fun hasHealthConnectSleep(context: Context): Boolean =
        HealthConnectSleep.hasPermission(context)

    /**
     * Contract for requesting Health Connect permissions. Use with
     * `registerForActivityResult(...)`; launch with [healthConnectPermissions].
     */
    fun healthConnectRequestContract() =
        PermissionController.createRequestPermissionResultContract()

    /** Deep link to install/open Health Connect if the SDK reports an update is needed. */
    fun healthConnectProviderPackage(): String = "com.google.android.apps.healthdata"

    // ---- Convenience contracts for the UI --------------------------------

    fun multiplePermissionsContract() =
        ActivityResultContracts.RequestMultiplePermissions()

    fun singlePermissionContract() =
        ActivityResultContracts.RequestPermission()

    /** Quick summary the onboarding screen can render as a checklist. */
    data class Status(
        val notifications: Boolean,
        val activityRecognition: Boolean,
        val fineLocation: Boolean,
        val backgroundLocation: Boolean,
        val exactAlarms: Boolean,
        val batteryExempt: Boolean,
        val healthConnectAvailable: Boolean,
    )

    fun status(context: Context): Status = Status(
        notifications = hasNotifications(context),
        activityRecognition = hasActivityRecognition(context),
        fineLocation = hasFineLocation(context),
        backgroundLocation = hasBackgroundLocation(context),
        exactAlarms = canScheduleExactAlarms(context),
        batteryExempt = isIgnoringBatteryOptimizations(context),
        healthConnectAvailable = healthConnectAvailable(context),
    )
}
