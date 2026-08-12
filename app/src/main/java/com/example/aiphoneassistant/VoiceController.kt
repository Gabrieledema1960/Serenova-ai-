package com.example.aiphoneassistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Reliable one-shot voice capture.
 *
 * Error 11 is ERROR_SERVER_DISCONNECTED, not a "bad microphone" error. The
 * recognizer must be destroyed and recreated before another startListening().
 * This controller also avoids overlapping sessions and retries transient
 * recognizer failures with a short backoff.
 */
class VoiceController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var attempt = 0
    private var delivered = false

    fun start() {
        main.post {
            attempt = 0
            delivered = false
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
                Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    if (delivered) return
                    delivered = true
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    destroyRecognizer()
                    if (!text.isNullOrBlank()) {
                        onResult(text)
                    } else {
                        retryOrReport(SpeechRecognizer.ERROR_NO_MATCH)
                    }
                }

                override fun onError(error: Int) {
                    listening = false
                    if (delivered) return
                    destroyRecognizer()
                    retryOrReport(error)
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-NG")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-NG")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                // Do not force offline recognition. If the device has no local
                // English model, that setting is a common cause of failures.
            }
            recognizer?.startListening(intent)
        } catch (t: Throwable) {
            listening = false
            destroyRecognizer()
            retryOrReport(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    private fun retryOrReport(error: Int) {
        val transient = error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT

        // Never make the user press Listen repeatedly. Give the recognizer up
        // to three clean sessions before reporting an error.
        if (transient && attempt < 3) {
            val delay = 450L * (attempt + 1)
            attempt++
            main.postDelayed({ startInternal() }, delay)
            return
        }

        val message = when (error) {
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                "The speech service disconnected. I restarted it; please try speaking again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "The speech service was busy. I restarted it for you."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "I couldn't reach the speech service. Check your connection and try again."
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "I didn't catch that. Speak a little closer to the phone and try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "I don't have microphone permission yet. Please allow microphone access."
            else -> "Voice recognition couldn't start (code $error). I reset the listener."
        }
        onError(message)
    }

    private fun destroyRecognizer() {
        try { recognizer?.cancel() } catch (_: Throwable) {}
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
        listening = false
    }

    fun stop() {
        main.post { destroyRecognizer() }
    }

    fun destroy() {
        main.post { destroyRecognizer(); main.removeCallbacksAndMessages(null) }
    }
}
