package com.quietdose.brain

import android.content.Context
import java.io.File

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
     * model is loaded once and reused for every skill. The implementation is
     * chosen by the installed model's format: a `.task` (a zip, "PK") runs on
     * MediaPipe; anything else is treated as a `.litertlm` and runs on LiteRT-LM.
     * Always returns an engine; [LlmEngine.isReady] is false until a model loads.
     */
    fun engine(context: Context): LlmEngine =
        engineRef ?: synchronized(this) {
            engineRef ?: chooseEngine(context.applicationContext).also { engineRef = it }
        }

    private fun build(appContext: Context): Brain =
        if (MediaPipeLlmEngine.modelPresent(appContext)) {
            LlmBrain(engine(appContext))
        } else {
            HeuristicBrain()
        }

    private fun chooseEngine(appContext: Context): LlmEngine =
        if (isTaskBundle(appContext)) MediaPipeLlmEngine(appContext) else LiteRtLmEngine(appContext)

    /** A MediaPipe `.task` is a zip → starts with "PK". Default to true when absent. */
    private fun isTaskBundle(appContext: Context): Boolean {
        val f = File(appContext.filesDir, MediaPipeLlmEngine.DEFAULT_MODEL_NAME)
        if (!f.exists() || f.length() <= 0L) return true
        val head = ByteArray(2)
        val n = runCatching { f.inputStream().use { it.read(head) } }.getOrDefault(-1)
        return n < 2 || (head[0].toInt() == 'P'.code && head[1].toInt() == 'K'.code)
    }

    /**
     * Drop the cached brain + engine so the next access re-checks for a model
     * file. Useful after a user installs/removes a `.task` model on-device.
     */
    fun reset() {
        synchronized(this) {
            instance = null
            engineRef = null
        }
    }
}
