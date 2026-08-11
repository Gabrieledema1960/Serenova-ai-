package com.example.aiphoneassistant

import android.content.Context

/**
 * Voice-only owner gate.
 * Android SpeechRecognizer provides speech-to-text, not speaker identity. This gate therefore
 * requires a private spoken owner phrase before a command is accepted. It is NOT biometric
 * speaker verification.
 */
class VoiceOwnerGate(context: Context) {
    private val prefs = context.getSharedPreferences("voice_owner_gate", Context.MODE_PRIVATE)

    fun isEnrolled(): Boolean = !prefs.getString("owner_phrase", null).isNullOrBlank()

    fun enroll(phrase: String) {
        val normalized = normalize(phrase)
        require(normalized.length >= 6) { "Owner phrase must be at least 6 characters." }
        prefs.edit().putString("owner_phrase", normalized).commit()
    }

    fun clear() { prefs.edit().remove("owner_phrase").commit() }

    fun accepts(spoken: String): Boolean {
        val phrase = prefs.getString("owner_phrase", null) ?: return false
        val text = normalize(spoken)
        return text == phrase || text.startsWith("$phrase ")
    }

    fun stripPhrase(spoken: String): String {
        val phrase = prefs.getString("owner_phrase", null) ?: return spoken.trim()
        val text = normalize(spoken)
        return if (text.startsWith("$phrase ")) text.removePrefix("$phrase ").trim() else text
    }

    private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
}
