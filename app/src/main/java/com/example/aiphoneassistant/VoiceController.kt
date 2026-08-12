package com.example.aiphoneassistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Reliable one-shot voice capture with recovery from Android recognizer errors. */
class VoiceController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var attempt = 0
    private var delivered = false
    private var useSystemRecognizer = false

    fun start() {
        main.post {
            attempt = 0
            delivered = false
            useSystemRecognizer = false
            startInternal()
        }
    }

    private fun startInternal() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this phone.")
            return
        }

        destroyRecognizer()
        delivered = false

        try {
            recognizer = if (
                !useSystemRecognizer &&
                Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

                override fun onResults(results: android.os.Bundle?) {
                    if (delivered) return
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!text.isNullOrBlank()) {
                        delivered = true
                        destroyRecognizer()
                        onResult(text)
                    } else {
                        handleError(SpeechRecognizer.ERROR_NO_MATCH)
                    }
                }

                override fun onError(error: Int) = handleError(error)
            })

            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-NG")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-NG")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            })
        } catch (_: Throwable) {
            handleError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    private fun handleError(error: Int) {
        if (delivered) return
        destroyRecognizer()

        // Error 11 = ERROR_SERVER_DISCONNECTED. If the on-device recognizer
        // disconnected, immediately switch to Android's normal recognizer.
        if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED && !useSystemRecognizer) {
            useSystemRecognizer = true
            attempt = 0
            main.postDelayed({ startInternal() }, 300L)
            return
        }

        val transient = error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT

        if (transient && attempt < 3) {
            attempt++
            main.postDelayed({ startInternal() }, 450L * attempt)
            return
        }

        val message = when (error) {
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "The speech service disconnected. I restarted it. Try speaking again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "The speech service was busy. I reset it for you."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "The speech service couldn't connect. Check your internet connection and try again."
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "I didn't catch that. Speak clearly and a little closer to the phone."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "I need microphone permission before I can hear you."
            else -> "I couldn't hear that properly, so I reset the microphone."
        }
        onError(message)
    }

    private fun destroyRecognizer() {
        try { recognizer?.cancel() } catch (_: Throwable) {}
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
    }

    fun stop() { main.post { destroyRecognizer() } }

    fun destroy() {
        main.post {
            destroyRecognizer()
            main.removeCallbacksAndMessages(null)
        }
    }
}
