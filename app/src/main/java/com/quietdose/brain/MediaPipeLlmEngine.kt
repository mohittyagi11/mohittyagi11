package com.quietdose.brain

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    @Volatile private var errorMessage: String? = null

    /**
     * A MediaPipe LLM session can run only ONE generation at a time — two
     * concurrent `generateResponse` calls (e.g. the Insights tab kicking off a
     * caption and stack-suggestions together) crash the native runtime. Serialize
     * all inference through this lock so callers queue instead of colliding.
     */
    private val inferenceLock = Mutex()

    /** The reason the most recent load/inference failed, for the self-test. */
    override fun lastError(): String? = errorMessage

    /**
     * Release the native LLM session and its (potentially multi-GB) memory.
     * Crucial before loading another model — otherwise two models can be
     * resident at once and OOM-crash the process.
     */
    override fun close() {
        synchronized(this) {
            runCatching { engine?.close() }
            engine = null
            loadFailed = false
            errorMessage = null
        }
    }

    /**
     * We can only know readiness by trying to load. To keep [isReady] cheap and
     * synchronous (it's used to pick a Brain), it reports "could be ready":
     * the model file exists and a prior load hasn't failed. The real, guarded
     * load happens inside [complete] on a background dispatcher.
     */
    override fun isReady(): Boolean = !loadFailed && (engine != null || modelFile.exists())

    override suspend fun complete(prompt: String): String = withContext(Dispatchers.Default) {
        inferenceLock.withLock {
            val session = ensureLoaded() ?: return@withLock ""
            runCatching { session.generateResponse(prompt) }
                .getOrElse {
                    Log.w(TAG, "Inference failed", it)
                    errorMessage = "Inference failed: ${it.message ?: it.javaClass.simpleName}"
                    ""
                }
                .trim()
        }
    }

    /** Build the session once, off the main thread; null if unavailable. */
    private fun ensureLoaded(): LlmInference? {
        engine?.let { return it }
        if (!modelFile.exists()) {
            loadFailed = true
            errorMessage = "No model file at ${modelFile.name}."
            return null
        }
        if (loadFailed) return null
        // Guard a native OOM (uncatchable) only when memory is genuinely low. Do NOT
        // latch loadFailed here — it's transient, so we retry once memory recovers.
        if (!hasMemoryFor()) {
            errorMessage = "Low memory — skipping the model for now."
            Log.w(TAG, "Skipping model load: low memory")
            return null
        }
        return synchronized(this) {
            engine ?: runCatching {
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(maxTokens)
                    .build()
                LlmInference.createFromOptions(appContext, options).also {
                    engine = it
                    errorMessage = null
                }
            }.getOrElse {
                Log.w(TAG, "Model load failed; falling back to heuristic", it)
                loadFailed = true
                errorMessage = "Load failed: ${it.message ?: it.javaClass.simpleName}"
                null
            }
        }
    }

    /**
     * Enough free memory to *attempt* a load? A `.task` int4 model is memory-mapped
     * (weights page in on demand), so it does NOT need its whole multi-GB file
     * resident — only working headroom. So we bail only when the system reports low
     * memory or free memory is below a small floor. This still avoids the worst-case
     * native OOM without permanently disabling the model. Never throws.
     */
    private fun hasMemoryFor(): Boolean = runCatching {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return@runCatching true
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        !mi.lowMemory && mi.availMem > MIN_FREE_BYTES
    }.getOrDefault(true)

    companion object {
        private const val TAG = "MediaPipeLlmEngine"

        /** Working-memory floor to attempt a (memory-mapped) model load. */
        private const val MIN_FREE_BYTES = 600L * 1024 * 1024

        /** Drop a Gemma `.task` model at filesDir/<this> to enable the model. */
        const val DEFAULT_MODEL_NAME = "brain.task"

        /** Does a model file exist on disk? Cheap pre-check for [BrainProvider]. */
        fun modelPresent(context: Context, modelName: String = DEFAULT_MODEL_NAME): Boolean =
            File(context.applicationContext.filesDir, modelName).exists()
    }
}
