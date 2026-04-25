package com.beemovil.voice

import android.content.Context
import android.util.Log
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.security.SecurePrefs

/**
 * OfflineConversationBackend — Voice Intelligence Phase V2
 *
 * Uses Android native STT (SpeechRecognizer) + LLM via existing engine + Android native TTS.
 * Works without Deepgram/ElevenLabs API keys.
 * STT and TTS are local, only the LLM call needs internet.
 *
 * Limitations:
 * - No live transcription (text appears after full utterance)
 * - Restart-loop beep between turns
 * - Lower accuracy than Deepgram for Spanish
 */
class OfflineConversationBackend(
    private val context: Context,
    private val voiceManager: DeepgramVoiceManager,
    private val engine: EmmaEngine
) : ConversationBackend {

    companion object {
        private const val TAG = "OfflineBackend"
    }

    override val id = "offline"
    override val displayName = "Offline (Nativo)"
    override val requiresInternet = true // LLM still needs internet
    override val requiredKeys = listOf<String>() // No API keys needed for STT/TTS

    private var config: ConversationConfig? = null
    private var isActive = false

    override fun isAvailable(): Boolean = true // Always available

    override suspend fun startSession(config: ConversationConfig) {
        this.config = config
        isActive = true
        Log.i(TAG, "Session started (native STT + native TTS)")
    }

    override suspend fun processTranscript(transcript: String): String {
        if (!isActive) return ""
        val cfg = config ?: return ""

        return try {
            val response = engine.processUserMessage(
                transcript,
                cfg.llmProvider,
                cfg.llmModel,
                threadId = cfg.threadId,
                senderId = cfg.agentId
            )
            response
        } catch (e: Exception) {
            Log.e(TAG, "LLM error: ${e.message}")
            "Error procesando tu mensaje: ${e.message?.take(100)}"
        }
    }

    override fun stopSession() {
        isActive = false
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        config = null
        Log.i(TAG, "Session stopped")
    }

    override fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    override fun isSpeaking(): Boolean = false // Native TTS reports through DeepgramVoiceManager
}
