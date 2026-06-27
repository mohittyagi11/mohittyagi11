package com.azadishashn.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Thin wrapper over Android Text-to-Speech for reading questions, ideology cards,
 * and rationale aloud. Tuned slightly (pitch/rate) for a more expressive read.
 */
class Speaker(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.apply {
                    language = Locale("en", "IN")
                    setPitch(1.08f)
                    setSpeechRate(0.96f)
                }
                pending?.let { text ->
                    pending = null
                    speak(text)
                }
            }
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = tts
        if (engine == null || !ready) {
            pending = clean // speak once the engine finishes initialising
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
}
