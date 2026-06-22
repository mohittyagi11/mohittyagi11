package com.quietdose.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quietdose.data.model.IntakeSource
import com.quietdose.di.ServiceLocator
import com.quietdose.notify.NotifyContract
import com.quietdose.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "Taken" action tapped directly in the notification. It logs the
 * whole group as taken and dismisses the reminder — the app is never opened.
 * DB work runs off the main thread via [goAsync].
 */
class TakenActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotifyContract.ACTION_TAKEN) return
        val groupId = intent.getLongExtra(NotifyContract.EXTRA_GROUP_ID, -1L)
        val epochDay = intent.getLongExtra(NotifyContract.EXTRA_EPOCH_DAY, -1L)
        if (groupId < 0L || epochDay < 0L) return

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServiceLocator.repository(app)
                    .markGroupTaken(groupId, epochDay, IntakeSource.NOTIFICATION)
                Notifier.cancel(app, groupId)
            } finally {
                pending.finish()
            }
        }
    }
}
