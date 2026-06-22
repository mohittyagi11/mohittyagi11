package com.quietdose.brain

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Honest, simple device fit-checking for [OnDeviceModel]s.
 *
 * "Fit" is a coarse heuristic, not a guarantee: we compare a model's rough
 * [OnDeviceModel.minRamMb] against the device's total RAM with a headroom margin
 * so we don't recommend something that will thrash. We do not benchmark the GPU
 * or measure free RAM; the goal is to keep the user from picking something
 * obviously too big for the hardware.
 */
object DeviceCapability {

    /** Multiply a model's minRamMb by this — devices need slack for the OS + app. */
    private const val HEADROOM = 1.25f

    /** A model whose minRamMb exceeds total RAM by this is plainly hopeless. */
    private const val HARD_OVER_FACTOR = 1.0f

    /** Total physical RAM in MB (from [ActivityManager.MemoryInfo.totalMem]). */
    fun totalRamMb(context: Context): Int {
        val am = context.applicationContext
            .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 0
        val info = ActivityManager.MemoryInfo()
        return runCatching {
            am.getMemoryInfo(info)
            (info.totalMem / (1024L * 1024L)).toInt()
        }.getOrDefault(0)
    }

    /** Supported CPU ABIs, most-preferred first. Never empty in practice. */
    fun supportedAbis(): List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()

    /** True if the device is 64-bit (arm64-v8a / x86_64) — most MediaPipe builds want this. */
    fun is64Bit(): Boolean =
        supportedAbis().any { it == "arm64-v8a" || it == "x86_64" }

    /**
     * Models that comfortably fit this device, sorted best-fit first.
     *
     * "Comfortable" = total RAM >= minRamMb * [HEADROOM]. Among those, we prefer
     * the largest model that still fits (more capable), then smaller size as a
     * tiebreak, so the top suggestion is the best the device can reasonably run.
     */
    fun recommended(context: Context): List<OnDeviceModel> {
        val ram = totalRamMb(context)
        if (ram <= 0) {
            // Unknown RAM: don't pretend. Recommend only the smallest, safest model.
            return ModelCatalog.ALL.minByOrNull { it.minRamMb }?.let { listOf(it) }.orEmpty()
        }
        return ModelCatalog.ALL
            .filter { ram >= it.minRamMb * HEADROOM }
            .sortedWith(
                compareByDescending<OnDeviceModel> { it.minRamMb }
                    .thenBy { it.approxBytes },
            )
    }

    /**
     * Models that are heavier than this device can reasonably run, each paired
     * with a short, honest reason string. These are everything not in
     * [recommended] (so a model that's borderline-but-not-comfortable shows here
     * with a "tight" reason rather than silently vanishing).
     */
    fun tooLarge(context: Context): List<TooLargeModel> {
        val ram = totalRamMb(context)
        val recommendedIds = recommended(context).mapTo(HashSet()) { it.id }
        return ModelCatalog.ALL
            .filterNot { it.id in recommendedIds }
            .map { model -> TooLargeModel(model, reasonFor(model, ram)) }
            .sortedBy { it.model.minRamMb }
    }

    private fun reasonFor(model: OnDeviceModel, ramMb: Int): String = when {
        ramMb <= 0 ->
            "Couldn't read this device's RAM, so this larger model isn't recommended."
        ramMb < model.minRamMb * HARD_OVER_FACTOR ->
            "Needs about ${model.minRamMb / 1024} GB RAM; this device has " +
                "about ${"%.1f".format(ramMb / 1024f)} GB. Likely to fail or be very slow."
        else ->
            "Fits tightly (needs ~${model.minRamMb / 1024} GB RAM, device has " +
                "~${"%.1f".format(ramMb / 1024f)} GB). May be slow or unstable under load."
    }

    /** A model that doesn't comfortably fit, with a human-readable [reason]. */
    data class TooLargeModel(val model: OnDeviceModel, val reason: String)
}
