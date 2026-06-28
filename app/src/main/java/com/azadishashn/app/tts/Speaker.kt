package com.azadishashn.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice

/**
 * Text-to-Speech for reading questions, ideology cards, and rationale aloud.
 *
 * Stays fully built-in (Android [TextToSpeech] + Google engine — no cloud API),
 * but picks the most natural voice available: it ranks the device's ENGLISH
 * voices by quality and prefers the high-quality neural voices (local first for
 * offline safety, then network), across all English regions, instead of the
 * robotic "compact" en-IN voice. Sentences are spoken with a short breath
 * between them so it reads like a narrator, not a machine.
 *
 * [onSpeaking] reports start/stop so the UI can toggle a read/stop button.
 * Callbacks fire on a background thread — marshal to the UI thread in the consumer.
 */
class Speaker(
    context: Context,
    private val onSpeaking: (Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    /** Target narration language ("en" | "hi") and preferred region, from Settings. */
    private var langCode: String = "en"
    private var regionPref: String = "IN"

    // Track first/last chunk ids so the read/stop button doesn't flicker between
    // sentences — we only flip ON at the first chunk and OFF at the last.
    private var firstUtteranceId: String? = null
    private var lastUtteranceId: String? = null

    init {
        initEngine(GOOGLE_TTS)
    }

    private fun initEngine(engine: String?) {
        tts = TextToSpeech(
            appContext,
            { status ->
                if (status == TextToSpeech.SUCCESS) {
                    onReady()
                } else if (engine != null) {
                    runCatching { tts?.shutdown() }
                    initEngine(null)
                }
            },
            engine,
        )
    }

    private fun onReady() {
        val engine = tts ?: return
        selectBestVoice(engine)
        engine.setPitch(1.0f)
        engine.setSpeechRate(0.96f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == firstUtteranceId) onSpeaking(true)
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == lastUtteranceId) onSpeaking(false)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) = onSpeaking(false)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onSpeaking(false)
        })
        ready = true
        pending?.let { text ->
            pending = null
            speak(text)
        }
    }

    /**
     * Choose the most natural voice for [langCode]: highest [Voice.quality]; among
     * equal quality prefer LOCAL (offline-safe) over network, then the enhanced
     * "-x-" neural voices, then the preferred region. Falls back to English, then
     * to the best installed voice of any locale, else the engine default.
     */
    private fun selectBestVoice(engine: TextToSpeech) {
        val voices: Set<Voice> = runCatching { engine.voices }.getOrNull() ?: return
        val installed = voices.filter { !isNotInstalled(it) }
        val comparator = compareBy<Voice>(
            { it.quality },
            { if (!it.isNetworkConnectionRequired) 1 else 0 },
            { if (isNeural(it)) 1 else 0 },
            { if (it.locale.country.equals(regionPref, ignoreCase = true)) 1 else 0 },
        )
        val inLang = installed.filter { it.locale.language == langCode }
        val english = installed.filter { it.locale.language == "en" }
        val best = inLang.maxWithOrNull(comparator)
            ?: english.maxWithOrNull(comparator)
            ?: installed.maxByOrNull { it.quality }
        best?.let { v ->
            runCatching { engine.voice = v }
            runCatching { engine.language = v.locale }
        }
    }

    private fun isNotInstalled(v: Voice): Boolean =
        v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true

    /** Google's enhanced/neural voices use an "-x-" segment in the name. */
    private fun isNeural(v: Voice): Boolean =
        v.name.contains("-x-") || v.quality >= Voice.QUALITY_VERY_HIGH

    /** True when the device has an installed Hindi voice (for natural Hinglish/Hindi audio). */
    fun hindiAvailable(): Boolean {
        val voices = runCatching { tts?.voices }.getOrNull() ?: return false
        return voices.any { !isNotInstalled(it) && it.locale.language == "hi" }
    }

    /**
     * Set the narration language and re-pick the best voice. English uses an
     * English voice; Hinglish and Hindi prefer a Hindi voice (they're fed
     * Devanagari) when one is installed, else fall back to English.
     */
    fun setLanguage(code: String?) {
        langCode = if (code == "en" || code == null) "en" else if (hindiAvailable()) "hi" else "en"
        val engine = tts
        if (engine != null && ready) selectBestVoice(engine)
    }

    /** Speak [text] in a specific language ("en" | "hinglish" | "hi"). */
    fun speak(text: String, langCode: String) {
        setLanguage(langCode)
        speak(text)
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = tts
        if (engine == null || !ready) {
            pending = clean
            return
        }
        val sentences = splitSentences(clean)
        if (sentences.isEmpty()) return
        firstUtteranceId = "u0"
        lastUtteranceId = "u${sentences.lastIndex}"
        sentences.forEachIndexed { i, sentence ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(sentence, mode, null, "u$i")
            // A short breath between sentences — natural narration cadence.
            if (i != sentences.lastIndex) {
                engine.playSilentUtterance(140L, TextToSpeech.QUEUE_ADD, "sil$i")
            }
        }
    }

    /** Split into sentences, keeping the punctuation, so each is spoken as a unit. */
    private fun splitSentences(text: String): List<String> =
        Regex("(?<=[.!?…])\\s+").split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun stop() {
        tts?.stop()
        onSpeaking(false)
    }

    fun shutdown() {
        tts?.stop()
        onSpeaking(false)
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val GOOGLE_TTS = "com.google.android.tts"
    }
}
