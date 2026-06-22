package com.quietdose.brain

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [LlmEngine] backed by **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`),
 * the runtime Google now recommends. It loads the newer `.litertlm` model files
 * (e.g. Gemma 3n) that the MediaPipe `.task` path can't open.
 *
 * Everything LiteRT-LM-specific is isolated to this one file — mirror of
 * [MediaPipeLlmEngine]. [BrainProvider] picks between the two by sniffing the
 * installed model: a `.task` is a zip ("PK"), anything else is treated as a
 * `.litertlm` and routed here. Nothing here touches the network.
 *
 * Lifecycle: lazy. The first [complete] builds + `initialize()`s the engine off
 * the main thread (this can take several seconds). On any failure [isReady] flips
 * to false and [complete] returns "", so the caller falls back to the heuristic.
 */
class LiteRtLmEngine(
    context: Context,
    modelName: String = MediaPipeLlmEngine.DEFAULT_MODEL_NAME,
) : LlmEngine {

    private val appContext = context.applicationContext
    private val modelFile: File = File(appContext.filesDir, modelName)

    @Volatile private var engine: Engine? = null
    @Volatile private var loadFailed: Boolean = false
    @Volatile private var errorMessage: String? = null

    override fun lastError(): String? = errorMessage

    override fun isReady(): Boolean = !loadFailed && (engine != null || modelFile.exists())

    override suspend fun complete(prompt: String): String = withContext(Dispatchers.IO) {
        val e = ensureLoaded() ?: return@withContext ""
        runCatching {
            val conversation = e.createConversation()
            try {
                conversation.sendMessage(prompt).text.trim()
            } finally {
                runCatching { conversation.close() }
            }
        }.getOrElse {
            Log.w(TAG, "Inference failed", it)
            errorMessage = "Inference failed: ${it.message ?: it.javaClass.simpleName}"
            ""
        }
    }

    /** Build + initialize the engine once, off the main thread; null if unavailable. */
    private fun ensureLoaded(): Engine? {
        engine?.let { return it }
        if (!modelFile.exists()) {
            loadFailed = true
            errorMessage = "No model file at ${modelFile.name}."
            return null
        }
        if (loadFailed) return null
        return synchronized(this) {
            engine ?: runCatching {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                )
                Engine(config).also {
                    it.initialize()
                    engine = it
                    errorMessage = null
                }
            }.getOrElse {
                Log.w(TAG, "LiteRT-LM load failed; falling back to heuristic", it)
                loadFailed = true
                errorMessage = "Load failed: ${it.message ?: it.javaClass.simpleName}"
                null
            }
        }
    }

    companion object {
        private const val TAG = "LiteRtLmEngine"
    }
}
