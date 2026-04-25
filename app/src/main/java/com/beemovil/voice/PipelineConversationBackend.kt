package com.beemovil.voice

import android.content.Context
import android.util.Log
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.security.SecurePrefs

/**
 * PipelineConversationBackend — Voice Intelligence Phase V2
 *
 * Full pipeline: Deepgram STT → any LLM (OpenRouter) → ElevenLabs/Deepgram/Native TTS.
 * Maximum flexibility: user chooses STT provider, LLM model, and TTS voice independently.
 *
 * Benefits over Offline:
 * - Better accuracy (Deepgram Nova-3)
 * - Future: live transcription via WebSocket streaming
 * - Premium voices via ElevenLabs
 */
class PipelineConversationBackend(
    private val context: Context,
    private val voiceManager: DeepgramVoiceManager,
    private val engine: EmmaEngine
) : ConversationBackend {

    companion object {
        private const val TAG = "PipelineBackend"
    }

    override val id = "pipeline"
    override val displayName = "Pipeline (Deepgram + LLM + TTS)"
    override val requiresInternet = true
    override val requiredKeys = listOf("deepgram_api_key", "openrouter_api_key")

    private var config: ConversationConfig? = null
    private var isActive = false

    override fun isAvailable(): Boolean {
        val prefs = SecurePrefs.get(context)
        val dgKey = prefs.getString("deepgram_api_key", "") ?: ""
        val orKey = prefs.getString("openrouter_api_key", "") ?: ""
        return dgKey.isNotBlank() && orKey.isNotBlank()
    }

    override suspend fun startSession(config: ConversationConfig) {
        this.config = config
        isActive = true
        Log.i(TAG, "Session started (Deepgram STT → ${config.llmProvider}:${config.llmModel} → TTS)")
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

    override fun isSpeaking(): Boolean = false
}
