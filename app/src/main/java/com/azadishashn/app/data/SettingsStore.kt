package com.azadishashn.app.data

import android.content.Context

/**
 * Persists the user-supplied Anthropic API key and chosen model on-device.
 *
 * v1 keeps the key on the phone (simplest for a friends' game). Because all
 * network access goes through [com.azadishashn.app.net.ClaudeClient], swapping
 * this for a backend proxy later only touches the networking layer.
 */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("azadi_settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    val hasKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"

        const val DEFAULT_MODEL = "claude-opus-4-8"

        /** label -> model id, shown in the Settings picker. */
        val MODELS: List<Pair<String, String>> = listOf(
            "Opus 4.8 — richest scenarios" to "claude-opus-4-8",
            "Sonnet 4.6 — fast & cheap (good for live play)" to "claude-sonnet-4-6",
            "Haiku 4.5 — fastest / cheapest" to "claude-haiku-4-5",
        )
    }
}
