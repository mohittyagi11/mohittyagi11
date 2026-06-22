package com.quietdose.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.quietdose.data.model.TriggerType
import com.quietdose.di.ServiceLocator
import com.quietdose.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Turns home-geofence transitions into reminders:
 *  - ENTER → fire every ARRIVE_HOME group.
 *  - EXIT  → optional "leaving" checkpoint → fire every LEAVE group.
 *
 * DB work runs off the main thread via [goAsync]. Errors in the event payload are
 * swallowed so a malformed broadcast can never crash the receiver.
 */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TriggerContract.ACTION_GEOFENCE) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition
        val isHome = event.triggeringGeofences
            ?.any { it.requestId == TriggerContract.GEOFENCE_HOME } ?: false
        if (!isHome) return

        val trigger = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> TriggerType.ARRIVE_HOME
            Geofence.GEOFENCE_TRANSITION_EXIT -> TriggerType.LEAVE
            else -> return
        }

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.repository(app)
                val groups = repo.observeGroups().first().filter { it.trigger == trigger }
                for (g in groups) Notifier.fireGroup(app, g.id)
            } finally {
                pending.finish()
            }
        }
    }
}
