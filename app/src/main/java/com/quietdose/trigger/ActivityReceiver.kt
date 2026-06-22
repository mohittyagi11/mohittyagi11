package com.quietdose.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives ActivityTransition results from Play Services. A STILL→WALKING / ON_FOOT
 * ENTER after the earliest-wake hour is the strongest cheap "you're up" signal, so
 * it's forwarded to [WakeInferenceEngine.onSignal].
 *
 * Guarded end-to-end: if the result is missing or Play Services isn't present, the
 * receiver simply does nothing and the fallback alarm still covers the morning.
 */
class ActivityReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TriggerContract.ACTION_ACTIVITY) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        val walked = result.transitionEvents.any { event ->
            event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER &&
                (event.activityType == DetectedActivity.WALKING ||
                    event.activityType == DetectedActivity.ON_FOOT)
        }
        if (!walked) return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WakeInferenceEngine.onSignal(app, WakeInferenceEngine.Signal.ACTIVITY_WALK)
            } finally {
                pending.finish()
            }
        }
    }
}
