package com.beemovil.voice

import android.content.Context
import android.util.Log
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.security.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * GeminiLiveBackend — Voice Intelligence Phase V6
 *
 * Conversation backend that calls Google's Gemini API directly.
 * Uses Google AI Studio API key (free tier: 15 RPM gemini-2.0-flash).
 *
 * Pipeline:
 * - STT: Handled by ConversationEngine (native or Deepgram)
 * - LLM: Gemini 2.0 Flash (or 1.5 Pro) via Google AI REST endpoint
 * - TTS: Handled by ConversationEngine (native, Deepgram, or ElevenLabs)
 *
 * Why not WebSocket?
 * - Google's Multimodal Live API requires OAuth + special permissions
 * - REST endpoint is simpler, more reliable, and works with free API keys
 * - We get the same model quality — just without real-time audio streaming
 *
 * Endpoint: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
 * Key: google_ai_key (from AI Studio: https://aistudio.google.com/apikey)
 *
 * Conversation history is maintained in-memory for multi-turn context.
 */
class GeminiLiveBackend(
    private val context: Context,
    private val voiceManager: DeepgramVoiceManager,
    private val engine: EmmaEngine
) : ConversationBackend {

    companion object {
        private const val TAG = "GeminiLiveBackend"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val DEFAULT_MODEL = "gemini-2.0-flash"
        private const val MAX_HISTORY_TURNS = 20 // Keep last N turns for context
    }

    override val id = "gemini_live"
    override val displayName = "Gemini Live (Google AI)"
    override val requiresInternet = true
    override val requiredKeys = listOf("google_ai_key")

    private var config: ConversationConfig? = null
    private var isActive = false
    private var apiKey = ""
    private var model = DEFAULT_MODEL

    // Multi-turn conversation history (Gemini format)
    private val conversationHistory = mutableListOf<GeminiMessage>()

    // System instruction (set once per session)
    private var systemInstruction: String = ""

    override fun isAvailable(): Boolean {
        val prefs = SecurePrefs.get(context)
        val key = prefs.getString("google_ai_key", "") ?: ""
        return key.isNotBlank()
    }

    override suspend fun startSession(config: ConversationConfig) {
        this.config = config
        isActive = true

        val prefs = SecurePrefs.get(context)
        apiKey = prefs.getString("google_ai_key", "") ?: ""

        // Allow user to pick model via config (e.g. "gemini-2.0-flash" or "gemini-1.5-pro")
        model = if (config.llmModel.contains("gemini")) {
            config.llmModel.substringAfterLast("/") // Handle "google/gemini-2.0-flash" format
        } else {
            DEFAULT_MODEL
        }

        // Build system instruction
        val userName = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
            .getString("user_display_name", "Usuario") ?: "Usuario"

        systemInstruction = config.systemPrompt.ifBlank {
            buildString {
                append("You are E.M.M.A., an advanced AI assistant. ")
                append("You are speaking with $userName in a live voice conversation. ")
                append("Keep your responses concise and conversational — this is voice, not text. ")
                append("Respond in the same language the user speaks. ")
                append("Be helpful, intelligent, and natural. ")
                append("Avoid markdown formatting, bullet points, or code blocks — use plain speech. ")
                append("If the user speaks Spanish, respond in Spanish. If English, respond in English.")
            }
        }

        conversationHistory.clear()
        Log.i(TAG, "Session started (model=$model, key=${apiKey.take(8)}...)")
    }

    override suspend fun processTranscript(transcript: String): String {
        if (!isActive || apiKey.isBlank()) return ""

        // Add user message to history
        conversationHistory.add(GeminiMessage("user", transcript))

        // Trim history if too long
        while (conversationHistory.size > MAX_HISTORY_TURNS * 2) {
            conversationHistory.removeAt(0)
        }

        return try {
            val response = withContext(Dispatchers.IO) {
                callGeminiAPI(transcript)
            }
            // Add assistant response to history
            conversationHistory.add(GeminiMessage("model", response))
            response
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API error: ${e.message}", e)
            // Fall back to EmmaEngine on error
            try {
                val cfg = config ?: return "Error: no config"
                val fallback = engine.processUserMessage(
                    transcript,
                    "openrouter",
                    "openai/gpt-4o-mini",
                    threadId = cfg.threadId,
                    senderId = cfg.agentId
                )
                conversationHistory.add(GeminiMessage("model", fallback))
                fallback
            } catch (fallbackError: Exception) {
                "Lo siento, hubo un error al procesar tu mensaje."
            }
        }
    }

    override fun stopSession() {
        isActive = false
        conversationHistory.clear()
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        config = null
        Log.i(TAG, "Session stopped")
    }

    override fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    override fun isSpeaking(): Boolean = false

    // --- Internal: Gemini API Call ---

    private fun callGeminiAPI(transcript: String): String {
        val url = URL("$BASE_URL/$model:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.doOutput = true

            // Build request body
            val requestBody = buildRequestBody()

            val fullUrl = "$BASE_URL/$model:generateContent?key=${apiKey.take(8)}..."
            Log.i(TAG, "Calling Gemini: $fullUrl (${conversationHistory.size} messages)")
            Log.d(TAG, "Request body: ${requestBody.toString().take(500)}")

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorStream = connection.errorStream
                val errorBody = errorStream?.let {
                    BufferedReader(InputStreamReader(it)).readText()
                } ?: "Unknown error"
                Log.e(TAG, "Gemini API error $responseCode: ${errorBody.take(500)}")

                // Parse specific error types
                return when {
                    responseCode == 429 -> {
                        Log.w(TAG, "Rate limited (429) — will fall back to OpenRouter")
                        throw Exception("Gemini rate limit (429). Usando respaldo.")
                    }
                    responseCode == 403 -> "La API key de Google AI no tiene permisos. Verifica en aistudio.google.com."
                    responseCode == 400 -> {
                        // Could be safety, invalid key, or bad request
                        Log.e(TAG, "Bad request (400): $errorBody")
                        when {
                            errorBody.contains("SAFETY") -> "No puedo responder a eso por políticas de seguridad."
                            errorBody.contains("API_KEY_INVALID") -> {
                                throw Exception("API key inválida. Verifica tu key de Google AI Studio.")
                            }
                            else -> throw Exception("Gemini 400: ${errorBody.take(150)}")
                        }
                    }
                    responseCode == 404 -> {
                        Log.e(TAG, "Model not found (404) — model=$model")
                        throw Exception("Modelo '$model' no encontrado. Verifica en Google AI Studio.")
                    }
                    else -> throw Exception("HTTP $responseCode: ${errorBody.take(200)}")
                }
            }

            val responseBody = connection.inputStream.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            }

            Log.d(TAG, "Gemini response (${responseBody.length} chars): ${responseBody.take(300)}")
            return parseGeminiResponse(responseBody)

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "No internet: ${e.message}")
            throw Exception("Sin conexión a internet")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout: ${e.message}")
            throw Exception("Tiempo de espera agotado con Gemini")
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequestBody(): JSONObject {
        val body = JSONObject()

        // System instruction
        if (systemInstruction.isNotBlank()) {
            body.put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemInstruction))
                })
            })
        }

        // Conversation contents (multi-turn)
        val contents = JSONArray()
        for (msg in conversationHistory) {
            contents.put(JSONObject().apply {
                put("role", msg.role)
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", msg.content))
                })
            })
        }
        body.put("contents", contents)

        // Generation config — optimized for voice conversation
        body.put("generationConfig", JSONObject().apply {
            put("temperature", 0.7)
            put("topP", 0.9)
            put("topK", 40)
            put("maxOutputTokens", 500) // Keep responses concise for voice
            put("candidateCount", 1)
        })

        // Safety settings — relaxed for natural conversation
        body.put("safetySettings", JSONArray().apply {
            listOf(
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT"
            ).forEach { category ->
                put(JSONObject().apply {
                    put("category", category)
                    put("threshold", "BLOCK_ONLY_HIGH")
                })
            }
        })

        return body
    }

    private fun parseGeminiResponse(responseBody: String): String {
        val json = JSONObject(responseBody)

        // Check for candidates
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            // Check if blocked by safety
            val promptFeedback = json.optJSONObject("promptFeedback")
            val blockReason = promptFeedback?.optString("blockReason")
            if (blockReason != null) {
                Log.w(TAG, "Response blocked: $blockReason")
                return "No puedo responder a eso — contenido filtrado."
            }
            return "No obtuve respuesta de Gemini."
        }

        val candidate = candidates.getJSONObject(0)

        // Check finish reason
        val finishReason = candidate.optString("finishReason", "STOP")
        if (finishReason == "SAFETY") {
            return "No puedo responder a eso por políticas de seguridad."
        }

        // Extract text from parts
        val content = candidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            return "Respuesta vacía de Gemini."
        }

        val text = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val partText = part.optString("text", "")
            if (partText.isNotBlank()) {
                text.append(partText)
            }
        }

        return text.toString().trim().ifBlank { "..." }
    }

    // --- Internal Data ---

    private data class GeminiMessage(
        val role: String, // "user" or "model"
        val content: String
    )
}
