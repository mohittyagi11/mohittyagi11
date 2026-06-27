package com.azadishashn.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Text-to-Speech for reading questions, ideology cards, and rationale aloud.
 * Prefers Google's neural engine and the highest-quality available voice for a
 * more expressive read, falling back to the device default.
 */
class Speaker(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

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
                    // Google engine unavailable — fall back to the system default.
                    runCatching { tts?.shutdown() }
                    initEngine(null)
                }
            },
            engine,
        )
    }

    private fun onReady() {
        val engine = tts ?: return
        val locale = Locale("en", "IN")
        runCatching { engine.language = locale }
        selectBestVoice(engine, locale)
        engine.setPitch(1.06f)
        engine.setSpeechRate(0.95f)
        ready = true
        pending?.let { text ->
            pending = null
            speak(text)
        }
    }

    /** Pick the highest-quality on-device voice for the language (most expressive). */
    private fun selectBestVoice(engine: TextToSpeech, locale: Locale) {
        val voices: Set<Voice> = runCatching { engine.voices }.getOrNull() ?: return
        val sameLang = voices.filter { it.locale.language == locale.language }
        val best = sameLang.filter { !it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
            ?: sameLang.maxByOrNull { it.quality }
            ?: voices.filter { !it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
        best?.let { runCatching { engine.voice = it } }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = tts
        if (engine == null || !ready) {
            pending = clean
            return
        }
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "azadi")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val GOOGLE_TTS = "com.google.android.tts"
    }
}
