package com.quietdose.brain

import android.content.Context

/**
 * Thread-safe singleton entry point for the brain. Returns an [LlmBrain] when an
 * on-device model file is present, otherwise the deterministic [HeuristicBrain].
 *
 * Mirrors the style of [com.quietdose.di.ServiceLocator]: a hand-rolled,
 * application-scoped, double-checked singleton. Wire it into ServiceLocator as
 * described in INTEGRATION.md.
 */
object BrainProvider {

    @Volatile private var instance: Brain? = null

    /** The app-scoped [Brain]. Safe to call from any thread. */
    fun get(context: Context): Brain =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(appContext: Context): Brain =
        if (MediaPipeLlmEngine.modelPresent(appContext)) {
            LlmBrain(MediaPipeLlmEngine(appContext))
        } else {
            HeuristicBrain()
        }

    /**
     * Drop the cached instance so the next [get] re-checks for a model file.
     * Useful after a user has just installed a `.task` model on-device.
     */
    fun reset() {
        synchronized(this) { instance = null }
    }
}
