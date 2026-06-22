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
    @Volatile private var engineRef: LlmEngine? = null

    /** The app-scoped [Brain]. Safe to call from any thread. */
    fun get(context: Context): Brain =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    /**
     * The single on-device engine, shared by the [Brain] and the [Agent] so the
     * model is loaded once and reused for every skill. Always returns an engine;
     * [LlmEngine.isReady] is false until a loadable `.task` model is present.
     */
    fun engine(context: Context): LlmEngine =
        engineRef ?: synchronized(this) {
            // Built with MediaPipeLlmEngine's default maxTokens (4096) — large enough for a
            // long source-material prompt PLUS a full JSON fill. Do NOT pass a smaller budget
            // here: a tight budget truncates the JSON and drops profiles to the fallback.
            engineRef ?: MediaPipeLlmEngine(context.applicationContext).also { engineRef = it }
        }

    private fun build(appContext: Context): Brain =
        if (MediaPipeLlmEngine.modelPresent(appContext)) {
            LlmBrain(engine(appContext))
        } else {
            HeuristicBrain()
        }

    /**
     * Drop the cached brain + engine so the next access re-checks for a model
     * file. Useful after a user installs/removes a `.task` model on-device.
     */
    fun reset() {
        synchronized(this) {
            // Free the native model memory before dropping the reference, so we
            // never hold two models resident at once (OOM on large models).
            runCatching { engineRef?.close() }
            instance = null
            engineRef = null
        }
    }
}
