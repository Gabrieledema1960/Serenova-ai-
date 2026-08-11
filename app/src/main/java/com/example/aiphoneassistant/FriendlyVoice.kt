package com.example.aiphoneassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Friendly spoken responses. This is deliberately local: no conversation audio is uploaded. */
class FriendlyVoice(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.getDefault()
            tts.setSpeechRate(0.96f)
            tts.setPitch(1.02f)
        }
    }

    fun say(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_${System.currentTimeMillis()}")
    }

    fun destroy() {
        tts.stop()
        tts.shutdown()
    }
}
