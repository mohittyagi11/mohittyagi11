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

    /** The country/setting scenarios are framed in (global dilemmas, local context). */
    var context: String
        get() = prefs.getString(KEY_CONTEXT, DEFAULT_CONTEXT).orEmpty()
        set(value) = prefs.edit().putString(KEY_CONTEXT, value.trim().ifEmpty { DEFAULT_CONTEXT }).apply()

    /**
     * The single app language ("en" | "hinglish" | "hi"). Drives AI output,
     * read-aloud narration, and (auto) speech-input recognition. Migrates the old
     * `voice_lang` BCP-47 value if present.
     */
    var language: String
        get() {
            prefs.getString(KEY_LANGUAGE, null)?.let { return it }
            // One-time migration from the old voice-input tag (e.g. "hi-IN").
            val legacy = prefs.getString(KEY_VOICE, null)
            val migrated = if (legacy != null && legacy.startsWith("hi", true)) "hi" else "en"
            prefs.edit().putString(KEY_LANGUAGE, migrated).apply()
            return migrated
        }
        set(value) = prefs.edit().putString(
            KEY_LANGUAGE,
            if (value == "hi" || value == "hinglish") value else "en",
        ).apply()

    /**
     * Languages the read-aloud narration is available in (codes). Multi-select;
     * at least one. Defaults to English + Hinglish so the narrator can switch.
     */
    var readLangs: Set<String>
        get() = prefs.getString(KEY_READ, null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf("en", "hinglish")
        set(value) {
            val clean = value.filter { it == "en" || it == "hinglish" || it == "hi" }.toSet()
            val final = if (clean.isEmpty()) setOf("en") else clean
            prefs.edit().putString(KEY_READ, final.joinToString(",")).apply()
        }

    /** The historical era scenarios are set in ("" = range freely). */
    var era: String
        get() = prefs.getString(KEY_ERA, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ERA, value).apply()

    /** Party-lines mode: each answerer is secretly assigned the ideology to argue. */
    var partyLines: Boolean
        get() = prefs.getBoolean(KEY_PARTY, false)
        set(value) = prefs.edit().putBoolean(KEY_PARTY, value).apply()

    /** Judge with the fastest model (verdicts are classification-shaped) — snappier rounds. */
    var fastJudge: Boolean
        get() = prefs.getBoolean(KEY_FAST_JUDGE, false)
        set(value) = prefs.edit().putBoolean(KEY_FAST_JUDGE, value).apply()

    /** Scandals: skeletons occasionally surface from a player's own record. */
    var scandals: Boolean
        get() = prefs.getBoolean(KEY_SCANDALS, true)
        set(value) = prefs.edit().putBoolean(KEY_SCANDALS, value).apply()

    val hasKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_CONTEXT = "context"
        private const val KEY_VOICE = "voice_lang"   // legacy (pre-language) key
        private const val KEY_LANGUAGE = "language"
        private const val KEY_READ = "read_langs"
        private const val KEY_ERA = "era"
        private const val KEY_PARTY = "party_lines"
        private const val KEY_FAST_JUDGE = "fast_judge"
        private const val KEY_SCANDALS = "scandals"

        const val DEFAULT_MODEL = "claude-opus-4-8"
        const val DEFAULT_CONTEXT = "India"
        const val FAST_MODEL = "claude-haiku-4-5"

        /** label -> era prompt value ("" ranges freely), for the Settings picker. */
        val ERAS: List<Pair<String, String>> = listOf(
            "Range freely (default)" to "",
            "1947 · Partition & founding" to "the late 1940s — independence, partition, and nation-founding",
            "1975 · Emergency era" to "the mid-1970s — emergency powers, censorship, and resistance",
            "1991 · Liberalization" to "the early 1990s — economic liberalization and upheaval",
            "Present day" to "the present day",
            "2035 · Near future" to "a plausible near-future around 2035 — AI, climate, and new powers",
        )

        /** label -> model id, shown in the Settings picker. */
        val MODELS: List<Pair<String, String>> = listOf(
            "Opus 4.8 — richest scenarios" to "claude-opus-4-8",
            "Sonnet 4.6 — fast & cheap (good for live play)" to "claude-sonnet-4-6",
            "Haiku 4.5 — fastest / cheapest" to "claude-haiku-4-5",
        )

        /** label -> language code, for the language toggle. */
        val LANGUAGES: List<Pair<String, String>> = listOf(
            "English" to "en",
            "Hinglish · Hindi in Roman script" to "hinglish",
            "हिंदी · Hindi (Devanagari)" to "hi",
        )

        /**
         * BCP-47 tag for TTS/STT from a language code. Hinglish is Romanised, so
         * an English (India) voice/recogniser reads it best — only true Devanagari
         * Hindi uses hi-IN.
         */
        fun bcp47(lang: String): String = if (lang == "hi") "hi-IN" else "en-IN"
    }
}
