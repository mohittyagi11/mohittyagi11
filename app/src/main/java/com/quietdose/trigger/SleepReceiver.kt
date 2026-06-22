package com.quietdose.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives Google Sleep API updates. Two event kinds may arrive on the same
 * intent:
 *  - [SleepSegmentEvent]: a *completed* sleep segment — i.e. the user has woken,
 *    so the night is over.
 *  - [SleepClassifyEvent]: periodic classifications; a low sleep-confidence with
 *    high motion/light is treated as "awake".
 *
 * Either forwards a SLEEP_API_AWAKE signal to [WakeInferenceEngine], which gates on
 * the earliest-wake hour and the daily lock. Fully guarded for missing results.
 */
class SleepReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TriggerContract.ACTION_SLEEP) return

        val awake = when {
            SleepSegmentEvent.hasEvents(intent) ->
                SleepSegmentEvent.extractEvents(intent).isNotEmpty()
            SleepClassifyEvent.hasEvents(intent) ->
                SleepClassifyEvent.extractEvents(intent).any { it.confidence < AWAKE_CONFIDENCE }
            else -> false
        }
        if (!awake) return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WakeInferenceEngine.onSignal(app, WakeInferenceEngine.Signal.SLEEP_API_AWAKE)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** Sleep confidence below this (with motion/light) reads as "awake". */
        const val AWAKE_CONFIDENCE = 10
    }
}
