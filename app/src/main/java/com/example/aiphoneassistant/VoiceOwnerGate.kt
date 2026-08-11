package com.example.aiphoneassistant

import android.content.Context
import java.security.MessageDigest

/**
 * Voice-only owner gate.
 *
 * Android SpeechRecognizer provides speech-to-text, not speaker identity. This gate therefore
 * protects commands with a private spoken owner phrase. It is intentionally NOT described as
 * biometric speaker verification.
 */
class VoiceOwnerGate(context: Context) {
    private val prefs = context.getSharedPreferences("voice_owner_gate", Context.MODE_PRIVATE)

    fun isEnrolled(): Boolean = !prefs.getString("phrase_hash", null).isNullOrBlank()

    fun enroll(phrase: String) {
        val normalized = normalize(phrase)
        require(normalized.length >= 6) { "Owner phrase must be at least 6 characters." }
        prefs.edit().putString("phrase_hash", hash(normalized)).commit()
    }

    fun clear() { prefs.edit().remove("phrase_hash").commit() }

    fun accepts(spoken: String): Boolean {
        val saved = prefs.getString("phrase_hash", null) ?: return false
        return hash(normalize(spoken)).contains(saved) || hash(normalize(spoken)) == saved
    }

    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
