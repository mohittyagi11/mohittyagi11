package com.quietdose.trigger

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.SleepSegmentRequest
import com.quietdose.data.model.TriggerType
import com.quietdose.data.settings.Settings
import com.quietdose.di.ServiceLocator
import com.quietdose.notify.Notifier
import com.quietdose.util.DateUtils
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Infers "you're up" from fused, low-cost signals and fires the WAKE group once
 * per day — early enough to matter, late enough not to misfire on a 3 AM glance.
 *
 * Signals, each weighted in [Settings] (sum crosses [Settings.wakeFireThreshold]):
 *  - Health Connect sleep-session **end** ([com.quietdose.trigger.HealthConnectSleep]).
 *  - Google **Sleep API** "awake" classification ([SleepReceiver]).
 *  - **ActivityTransition** STILL→WALKING ([ActivityReceiver]).
 *  - (Sustained usage is wired by the host app if/when it tracks screen-on; the
 *    weight exists so it can contribute without code change.)
 *
 * Confidence is *sustained*: a transient walk at 4 AM is gated by
 * [Settings.earliestWakeHour]; once a real morning is confirmed the day is
 * **locked** via [TriggerState] so neither another signal nor the latest-wake
 * fallback can double-fire.
 *
 * Registration of the underlying Play Services updates is best-effort and fully
 * guarded: if Play Services is missing or a permission is absent, registration is
 * skipped and the latest-wake fallback alarm still guarantees the dose.
 */
object WakeInferenceEngine {

    /** Default confidence assigned to each signal kind before weighting. */
    private const val SIGNAL_PRESENT = 1f

    /** Signal kinds the receivers report back to [onSignal]. */
    enum class Signal { HEALTH_CONNECT_SLEEP_END, SLEEP_API_AWAKE, ACTIVITY_WALK, SUSTAINED_USAGE }

    // ---- Registration (best-effort) --------------------------------------

    /** Register activity-transition + Sleep API updates. Safe to call repeatedly. */
    fun register(context: Context) {
        registerActivityTransitions(context)
        registerSleepUpdates(context)
    }

    fun unregister(context: Context) {
        runCatching {
            ActivityRecognition.getClient(context).removeActivityTransitionUpdates(activityPi(context))
        }
        runCatching {
            ActivityRecognition.getClient(context).removeSleepSegmentUpdates(sleepPi(context))
        }
    }

    private fun registerActivityTransitions(context: Context) {
        if (!hasActivityRecognition(context)) return
        val transitions = listOf(
            transition(DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.ON_FOOT, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_EXIT),
        )
        runCatching {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(
                    ActivityTransitionRequest(transitions),
                    activityPi(context),
                )
        }
    }

    private fun registerSleepUpdates(context: Context) {
        // Sleep API needs ACTIVITY_RECOGNITION on API 29+ as well.
        if (!hasActivityRecognition(context)) return
        runCatching {
            ActivityRecognition.getClient(context)
                .requestSleepSegmentUpdates(
                    sleepPi(context),
                    // Events give us "awake" classifications as they happen.
                    SleepSegmentRequest.getDefaultSleepSegmentRequest(),
                )
        }
    }

    private fun transition(activity: Int, type: Int): ActivityTransition =
        ActivityTransition.Builder()
            .setActivityType(activity)
            .setActivityTransitionType(type)
            .build()

    // ---- Signal fusion ----------------------------------------------------

    /**
     * A signal arrived. Compute weighted confidence; if it crosses threshold and
     * we're past [Settings.earliestWakeHour] and the morning isn't locked, fire
     * the WAKE group(s) and lock the day. Returns true if it fired.
     *
     * Single-signal model: each broadcast already represents a *sustained*
     * classification from the OS (Sleep API segments, transition events, a
     * completed Health Connect session), so a single strong signal whose weight
     * meets the threshold is sufficient; weaker signals simply won't reach it.
     */
    suspend fun onSignal(context: Context, signal: Signal): Boolean {
        val app = context.applicationContext
        val settings = ServiceLocator.settings(app).settings.first()
        val now = LocalDateTime.now(ZoneId.systemDefault())
        if (now.hour < settings.earliestWakeHour) return false // still night

        val today = DateUtils.today(settings.dayRolloverHour)
        val state = TriggerState(app)
        if (state.morningAlreadyFired(today)) return false // locked already

        val confidence = weightFor(signal, settings) * SIGNAL_PRESENT
        if (confidence < settings.wakeFireThreshold) return false

        val repo = ServiceLocator.repository(app)
        val wakeGroups = repo.observeGroups().first().filter { it.trigger == TriggerType.WAKE }
        for (g in wakeGroups) Notifier.fireGroup(app, g.id)
        state.markMorningFired(today) // lock for the day
        return true
    }

    private fun weightFor(signal: Signal, s: Settings): Float = when (signal) {
        Signal.HEALTH_CONNECT_SLEEP_END -> s.weightHealthConnect
        Signal.SLEEP_API_AWAKE -> s.weightSleepApi
        Signal.ACTIVITY_WALK -> s.weightActivityWalk
        Signal.SUSTAINED_USAGE -> s.weightSustainedUsage
    }

    // ---- PendingIntents + permission guards ------------------------------

    internal fun activityPi(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TriggerContract.RC_ACTIVITY,
            Intent(context, ActivityReceiver::class.java)
                .setAction(TriggerContract.ACTION_ACTIVITY),
            mutablePiFlags(),
        )

    internal fun sleepPi(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            TriggerContract.RC_SLEEP,
            Intent(context, SleepReceiver::class.java)
                .setAction(TriggerContract.ACTION_SLEEP),
            mutablePiFlags(),
        )

    /**
     * Sleep/activity APIs deliver results into the intent extras, so the
     * PendingIntent must be **mutable**. That is safe here: the intent targets an
     * explicit, non-exported receiver in our own package.
     */
    private fun mutablePiFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private fun hasActivityRecognition(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
}
