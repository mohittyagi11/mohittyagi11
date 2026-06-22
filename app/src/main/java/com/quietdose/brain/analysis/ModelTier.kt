package com.quietdose.brain.analysis

import android.content.Context
import com.quietdose.brain.BrainProvider
import com.quietdose.brain.ModelManager

/**
 * How hard we may lean on the installed on-device model. The app scales its
 * intelligence to whatever model the user actually downloaded — a small model is
 * used lightly, a capable (4B-class) model is used to its full potential, and
 * with no model at all the deterministic knowledge-base logic still stands.
 *
 * Tier is inferred from the installed model file size (a good proxy for class):
 *  - none     : no model file present.
 *  - light    : a small model (≈1B, sub-~1.6 GB) — phrasing + short summaries.
 *  - capable  : a larger model (≈3–4B+) — full multi-step reasoning & synthesis.
 */
enum class ModelTier { NONE, LIGHT, CAPABLE;

    val isModel: Boolean get() = this != NONE
    /** Generous token budget for generations, scaled to the model class. */
    val maxTokens: Int get() = when (this) { NONE -> 0; LIGHT -> 160; CAPABLE -> 512 }
}

object ModelCapability {

    private const val CAPABLE_MIN_BYTES = 1_600L * 1024 * 1024 // ~1.6 GB → 3B/4B class

    /** Current tier from the installed model file (cheap; reads file size only). */
    fun tier(context: Context): ModelTier {
        val info = ModelManager.installedModelInfo(context) ?: return ModelTier.NONE
        return if (info.sizeBytes >= CAPABLE_MIN_BYTES) ModelTier.CAPABLE else ModelTier.LIGHT
    }

    /** True only when a model is actually loadable right now. */
    fun engineReady(context: Context): Boolean =
        runCatching { BrainProvider.engine(context).isReady() }.getOrDefault(false)
}
