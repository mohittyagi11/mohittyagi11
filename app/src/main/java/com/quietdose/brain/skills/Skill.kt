package com.quietdose.brain.skills

import com.quietdose.brain.LlmEngine

/**
 * One job the on-device model can do. The brain is a single loaded model
 * ([LlmEngine]) plus many of these — each skill owns its own prompt and output
 * parser, so new capabilities are just new files routed through the same model
 * (an agentic pipeline, not one hard-coded use).
 *
 * Skills must be safe when the engine isn't ready: return a sensible empty/null
 * result rather than throwing. The [com.quietdose.brain.Agent] gates on
 * [LlmEngine.isReady] before calling, but skills should be defensive too.
 */
interface Skill<I, O> {
    /** Stable id, e.g. "identify_product". */
    val id: String

    /** Build a prompt from [input], run it on [engine], parse the reply. */
    suspend fun run(engine: LlmEngine, input: I): O
}
