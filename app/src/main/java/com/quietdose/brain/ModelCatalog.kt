package com.quietdose.brain

/**
 * A single prebuilt, on-device LLM model packaged as a MediaPipe LLM Inference
 * `.task` bundle. These are the only kind of model [MediaPipeLlmEngine] can load
 * (drop one at `filesDir/brain.task`); everything here is metadata to help a user
 * choose one that fits their device and obtain it.
 *
 * Honesty matters more than completeness:
 *  - [approxBytes] / [minRamMb] are rough, real-world figures, not promises.
 *  - [directUrl] is non-null **only** when a stable, un-authenticated direct link
 *    is known to exist. Most Gemma `.task` files are license-gated on Kaggle /
 *    Hugging Face and must be downloaded from their page (after accepting the
 *    licence) and then imported — for those, [directUrl] is null and the user is
 *    pointed at [sourcePageUrl].
 *
 * @property id            stable identifier, e.g. "gemma3-1b-it-int4"
 * @property displayName   human label, e.g. "Gemma 3 1B-IT (int4)"
 * @property family        coarse grouping, e.g. "Gemma 3"
 * @property approxBytes   approximate download / on-disk size in bytes
 * @property minRamMb      rough minimum device RAM (total) to run comfortably
 * @property backend       "CPU" or "GPU" — the delegate this build targets
 * @property fileName      the name it is saved as on disk (always brain.task today)
 * @property directUrl     Hugging Face direct download (resolve URL), or null.
 *                         Gated repos still need a one-time licence acceptance +
 *                         a token; the file fetch then works.
 * @property sourcePageUrl the Hugging Face page to obtain the file from (always set)
 * @property kaggleUrl     Kaggle download API URL (returns the version archive),
 *                         or null. Needs a Kaggle username + key (Basic auth).
 * @property kagglePageUrl the clean Kaggle page for manual download, or null
 * @property license       short licence name, e.g. "Gemma Terms of Use"
 * @property notes         caveats: gating, accuracy, why no direct link, etc.
 */
data class OnDeviceModel(
    val id: String,
    val displayName: String,
    val family: String,
    val approxBytes: Long,
    val minRamMb: Int,
    val backend: String,
    val fileName: String,
    val directUrl: String?,
    val sourcePageUrl: String,
    val license: String,
    val notes: String,
    val kaggleUrl: String? = null,
    val kagglePageUrl: String? = null,
)

/** Catalog of MediaPipe-compatible on-device LLM `.task` models. */
object ModelCatalog {

    private const val GB = 1024L * 1024L * 1024L
    private const val MB = 1024L * 1024L

    /**
     * Curated, conservative list. Sizes are approximate and minRamMb is a rough
     * "this won't thrash" floor for the quantised `.task` bundle plus runtime.
     *
     * Direct URLs: only LiteRT-community Hugging Face repos that are publicly
     * resolvable are given a [directUrl]; Kaggle / gated Gemma pages get null.
     * The exact filename on the page can change, so even direct links may break —
     * the page is always the authoritative source.
     */
    val ALL: List<OnDeviceModel> = listOf(
        OnDeviceModel(
            id = "gemma3-1b-it-int4",
            displayName = "Gemma 3 1B-IT (int4)",
            family = "Gemma 3",
            approxBytes = 555L * MB,
            minRamMb = 2048,
            backend = "CPU",
            fileName = MediaPipeLlmEngine.DEFAULT_MODEL_NAME,
            // LiteRT-community mirrors the 1B int4 .task publicly. Even so, the
            // page is authoritative; the resolve link can change with revisions.
            directUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            sourcePageUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
            license = "Gemma Terms of Use",
            notes = "Smallest, fastest. Best fit for most phones. Subject to Google's " +
                "Gemma Terms of Use. Download from Hugging Face (token) or Kaggle " +
                "(username + key). If a direct download fails (revisioned file or " +
                "gating not yet accepted), open the page and import the .task.",
            kaggleUrl = "https://www.kaggle.com/api/v1/models/google/gemma-3/tfLite/gemma3-1b-it-int4/1/download",
            kagglePageUrl = "https://www.kaggle.com/models/google/gemma-3/tfLite/gemma3-1b-it-int4",
        ),
        OnDeviceModel(
            id = "gemma3-1b-it-int8",
            displayName = "Gemma 3 1B-IT (int8)",
            family = "Gemma 3",
            approxBytes = 1024L * MB,
            minRamMb = 3072,
            backend = "CPU",
            fileName = MediaPipeLlmEngine.DEFAULT_MODEL_NAME,
            directUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int8.task",
            sourcePageUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
            license = "Gemma Terms of Use",
            notes = "Higher-quality 8-bit variant of the 1B model, same repo as int4. " +
                "Gated: accept the licence on the page once, then download with a " +
                "Hugging Face token or Kaggle credentials. Or import the .task.",
            kaggleUrl = "https://www.kaggle.com/api/v1/models/google/gemma-3/tfLite/gemma3-1b-it-int8/1/download",
            kagglePageUrl = "https://www.kaggle.com/models/google/gemma-3/tfLite/gemma3-1b-it-int8",
        ),
        OnDeviceModel(
            id = "gemma3n-e2b-it-int4",
            displayName = "Gemma 3n E2B-IT (int4)",
            family = "Gemma 3n",
            approxBytes = 3L * GB,
            minRamMb = 6144,
            backend = "CPU",
            fileName = MediaPipeLlmEngine.DEFAULT_MODEL_NAME,
            // Gemma 3n .task bundles are distributed via Kaggle / gated HF repos.
            directUrl = null,
            sourcePageUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview",
            license = "Gemma Terms of Use (gated)",
            notes = "Efficient 3n architecture (E2B ≈ 2B effective params). Mid-range/" +
                "flagship phones. License-gated. Easiest via Kaggle (username + key); " +
                "or accept terms on the page and import the .task.",
            kaggleUrl = "https://www.kaggle.com/api/v1/models/google/gemma-3n/tfLite/gemma-3n-e2b-it-int4/1/download",
            kagglePageUrl = "https://www.kaggle.com/models/google/gemma-3n/tfLite/gemma-3n-e2b-it-int4",
        ),
        OnDeviceModel(
            id = "gemma3n-e4b-it-int4",
            displayName = "Gemma 3n E4B-IT (int4)",
            family = "Gemma 3n",
            approxBytes = 4L * GB + 300L * MB,
            minRamMb = 8192,
            backend = "CPU",
            fileName = MediaPipeLlmEngine.DEFAULT_MODEL_NAME,
            directUrl = null,
            sourcePageUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview",
            license = "Gemma Terms of Use (gated)",
            notes = "Larger 3n (E4B ≈ 4B effective). Flagship-class RAM only. " +
                "License-gated. Easiest via Kaggle (username + key); or accept terms " +
                "on the page and import the .task.",
            kaggleUrl = "https://www.kaggle.com/api/v1/models/google/gemma-3n/tfLite/gemma-3n-e4b-it-int4/1/download",
            kagglePageUrl = "https://www.kaggle.com/models/google/gemma-3n/tfLite/gemma-3n-e4b-it-int4",
        ),
        OnDeviceModel(
            id = "gemma2-2b-it-int8",
            displayName = "Gemma 2 2B-IT (int8)",
            family = "Gemma 2",
            approxBytes = 2L * GB + 600L * MB,
            minRamMb = 6144,
            backend = "CPU",
            fileName = MediaPipeLlmEngine.DEFAULT_MODEL_NAME,
            directUrl = null,
            sourcePageUrl = "https://www.kaggle.com/models/google/gemma-2/tfLite",
            license = "Gemma Terms of Use",
            notes = "Solid all-rounder, larger than the 1B. Distributed on Kaggle " +
                "(LiteRT / .task). Download with Kaggle credentials, or accept the " +
                "licence on the page and import.",
            kaggleUrl = "https://www.kaggle.com/api/v1/models/google/gemma-2/tfLite/gemma2-2b-it-cpu-int8/1/download",
            kagglePageUrl = "https://www.kaggle.com/models/google/gemma-2/tfLite/gemma2-2b-it-cpu-int8",
        ),
        OnDeviceModel(
            id = "phi2-int8",
            displayName = "Phi-2 (int8)",
            family = "Phi",
            approxBytes = 3L * GB + 200L * MB,
            minRamMb = 6144,
            backend = "CPU",
            fileName = MediaPipeLlmEngine.DEFAULT_MODEL_NAME,
            directUrl = null,
            sourcePageUrl = "https://www.kaggle.com/models/Microsoft/phi/tfLite",
            license = "MIT (model weights)",
            notes = "Non-Gemma alternative (~2.7B). MediaPipe-converted builds appear " +
                "on Kaggle; availability varies by conversion. Permissive MIT licence, " +
                "but no verified stable direct link — obtain from the page and import.",
        ),
    )

    /** Look up by [OnDeviceModel.id], or null. */
    fun byId(id: String): OnDeviceModel? = ALL.firstOrNull { it.id == id }
}
