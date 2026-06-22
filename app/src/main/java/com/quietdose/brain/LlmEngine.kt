package com.quietdose.brain

/**
 * A minimal on-device text-completion engine. The only contract the rest of the
 * brain needs: ask if it's ready, and (suspending, off the main thread) complete
 * a prompt. Implementations must never make a network call.
 */
interface LlmEngine {

    /** True only when a model is loaded and inference can run. */
    fun isReady(): Boolean

    /**
     * Complete [prompt] and return the model's text. Must run off the main
     * thread. Callers should still guard with [isReady]; a not-ready engine
     * returns an empty string rather than throwing.
     */
    suspend fun complete(prompt: String): String

    /**
     * The reason the most recent load/inference failed, if any — surfaced to the
     * model self-test so a user can see *why* a model won't run. Null when there
     * is no error (or the implementation doesn't track one).
     */
    fun lastError(): String? = null
}
