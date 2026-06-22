package com.quietdose.brain

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [LlmEngine] backed by MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`),
 * loading a Gemma `.task` model from [Context.getFilesDir] **if present**.
 *
 * Everything MediaPipe-specific is isolated to this one file. If the dependency
 * is removed or the artifact can't resolve, deleting this file alone is enough —
 * the rest of the brain compiles and runs on [HeuristicBrain]. Nothing here
 * touches the network; inference is on-device only.
 *
 * Lifecycle: lazy. The first [complete] (or [isReady] forcing a load) attempts to
 * build the [LlmInference] session off the main thread. If the model file is
 * absent or the load fails, [isReady] reports false forever and [complete]
 * returns an empty string, so the caller falls back cleanly.
 *
 * The default model file name is [DEFAULT_MODEL_NAME]; drop any Gemma `.task`
 * file there (see INTEGRATION.md) and restart the app to enable it.
 */
class MediaPipeLlmEngine(
    context: Context,
    private val modelName: String = DEFAULT_MODEL_NAME,
    private val maxTokens: Int = 256,
) : LlmEngine {

    private val appContext = context.applicationContext
    private val modelFile: File = File(appContext.filesDir, modelName)

    @Volatile private var engine: LlmInference? = null
    @Volatile private var loadFailed: Boolean = false

    /**
     * We can only know readiness by trying to load. To keep [isReady] cheap and
     * synchronous (it's used to pick a Brain), it reports "could be ready":
     * the model file exists and a prior load hasn't failed. The real, guarded
     * load happens inside [complete] on a background dispatcher.
     */
    override fun isReady(): Boolean = !loadFailed && (engine != null || modelFile.exists())

    override suspend fun complete(prompt: String): String = withContext(Dispatchers.Default) {
        val session = ensureLoaded() ?: return@withContext ""
        runCatching { session.generateResponse(prompt) }
            .getOrElse {
                Log.w(TAG, "Inference failed", it)
                ""
            }
            .trim()
    }

    /** Build the session once, off the main thread; null if unavailable. */
    private fun ensureLoaded(): LlmInference? {
        engine?.let { return it }
        if (loadFailed || !modelFile.exists()) {
            loadFailed = loadFailed || !modelFile.exists()
            return null
        }
        return synchronized(this) {
            engine ?: runCatching {
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(maxTokens)
                    .build()
                LlmInference.createFromOptions(appContext, options).also { engine = it }
            }.getOrElse {
                Log.w(TAG, "Model load failed; falling back to heuristic", it)
                loadFailed = true
                null
            }
        }
    }

    companion object {
        private const val TAG = "MediaPipeLlmEngine"

        /** Drop a Gemma `.task` model at filesDir/<this> to enable the model. */
        const val DEFAULT_MODEL_NAME = "brain.task"

        /** Does a model file exist on disk? Cheap pre-check for [BrainProvider]. */
        fun modelPresent(context: Context, modelName: String = DEFAULT_MODEL_NAME): Boolean =
            File(context.applicationContext.filesDir, modelName).exists()
    }
}
