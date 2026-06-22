package com.quietdose.trigger

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.quietdose.data.settings.Settings
import com.quietdose.di.ServiceLocator
import kotlinx.coroutines.flow.first

/**
 * Registers a single **home** geofence from [Settings] (lat/lng/radius). ENTER
 * fires the ARRIVE_HOME group; EXIT is an optional "leaving" checkpoint handled by
 * [GeofenceReceiver] for LEAVE groups.
 *
 * All registration is best-effort and guarded: a missing home (lat/lng == 0.0),
 * absent FINE+BACKGROUND location permission, or no Play Services simply skips
 * registration. Geofences survive reboot only if re-registered, so [Reconciler]
 * calls [register] on boot/app-start.
 */
object GeofenceManager {

    /** Below this the home is considered unset (Settings default is 0.0). */
    private const val UNSET = 0.0
    private const val MIN_RADIUS_M = 30f

    private fun client(context: Context): GeofencingClient =
        LocationServices.getGeofencingClient(context.applicationContext)

    /** (Re)register the home geofence. No-ops if home unset / permission missing. */
    @SuppressLint("MissingPermission") // gated by [hasLocationPermissions]
    suspend fun register(context: Context) {
        val app = context.applicationContext
        val settings = ServiceLocator.settings(app).settings.first()
        if (!isHomeSet(settings)) return
        if (!hasLocationPermissions(app)) return

        val geofence = Geofence.Builder()
            .setRequestId(TriggerContract.GEOFENCE_HOME)
            .setCircularRegion(
                settings.homeLat,
                settings.homeLng,
                settings.homeRadiusM.coerceAtLeast(MIN_RADIUS_M),
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT,
            )
            // Brief loiter avoids firing on a drive-by past the house.
            .setLoiteringDelay(60_000)
            .build()

        val request = GeofencingRequest.Builder()
            // No INITIAL_TRIGGER: we don't want a spurious ENTER just for being
            // home when the geofence is (re)registered.
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        runCatching { client(app).addGeofences(request, pendingIntent(app)) }
    }

    fun unregister(context: Context) {
        runCatching {
            client(context).removeGeofences(listOf(TriggerContract.GEOFENCE_HOME))
        }
    }

    private fun isHomeSet(s: Settings): Boolean = s.homeLat != UNSET || s.homeLng != UNSET

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TriggerContract.RC_GEOFENCE,
            Intent(context, GeofenceReceiver::class.java)
                .setAction(TriggerContract.ACTION_GEOFENCE),
            // Geofencing writes the transition into the intent → must be mutable.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            },
        )

    /**
     * Geofencing requires FINE location and, on API 29+, BACKGROUND location to
     * receive transitions while the app isn't in the foreground.
     */
    fun hasLocationPermissions(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return fine && background
    }
}
