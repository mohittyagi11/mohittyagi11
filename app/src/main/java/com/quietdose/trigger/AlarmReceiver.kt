package com.quietdose.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quietdose.data.model.TriggerType
import com.quietdose.di.ServiceLocator
import com.quietdose.notify.Notifier
import com.quietdose.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives the [AlarmScheduler]'s alarms and turns them into a single
 * [Notifier.fireGroup] call (or, for the wake fallback, fires the WAKE group only
 * if inference hasn't already). Because alarms are one-shot, the receiver also
 * re-arms the engine after each fire so the next occurrence is always scheduled.
 *
 * All DB/DataStore work runs off the main thread via [goAsync] + a coroutine, per
 * the project's receiver convention.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    TriggerContract.ACTION_ALARM_GROUP -> {
                        val groupId = intent.getLongExtra(TriggerContract.EXTRA_GROUP_ID, -1L)
                        if (groupId >= 0L) Notifier.fireGroup(app, groupId)
                    }

                    TriggerContract.ACTION_WAKE_FALLBACK -> fireWakeFallback(app)

                    TriggerContract.ACTION_DAILY_REARM -> Unit // re-arm below covers it
                }
                // One-shot alarms: re-arm the whole set so the next occurrence
                // (and tomorrow's fallback) is scheduled even without app launch.
                AlarmScheduler.scheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }

    /** Fire every WAKE group, but only if the morning hasn't already fired today. */
    private suspend fun fireWakeFallback(app: Context) {
        val settings = ServiceLocator.settings(app).settings.first()
        val state = TriggerState(app)
        val today = DateUtils.today(settings.dayRolloverHour)
        if (state.morningAlreadyFired(today)) return

        val repo = ServiceLocator.repository(app)
        val wakeGroups = repo.observeGroups().first().filter { it.trigger == TriggerType.WAKE }
        for (g in wakeGroups) Notifier.fireGroup(app, g.id)
        state.markMorningFired(today)
    }
}
