package com.beemovil.llm

import android.util.Log
import com.beemovil.network.BeeHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Generic OpenAI-compatible provider.
 * Works with: OpenRouter, Ollama Cloud, any OpenAI-compatible API.
 */
class OpenAiCompatibleProvider(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
    private val providerName: String = "LLM",
    private val supportsTools: Boolean = true
) : LlmProvider {

    override val name = "$providerName ($model)"

    companion object {
        private const val TAG = "LlmProvider"
        private val JSON_MEDIA = "application/json".toMediaType()
    }

    private val client = BeeHttpClient.llm

    override fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                messages.forEach { put(it.toJson()) }
            })
            if (tools.isNotEmpty() && supportsTools) {
                put("tools", JSONArray().apply {
                    tools.forEach { put(it.toJson()) }
                })
            }
            put("max_tokens", 4096)
        }

        Log.d(TAG, "[$providerName] POST: model=$model, msgs=${messages.size}, tools=${if (supportsTools) tools.size else 0}")

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(request).execute()
        try {
            val responseBody = response.body?.string() ?: throw Exception("Empty response body")

            Log.d(TAG, "[$providerName] Response: ${response.code}")

            val json = JSONObject(responseBody)

            if (!response.isSuccessful) {
                val error = json.optJSONObject("error")?.optString("message") ?: responseBody.take(200)
                throw Exception("$providerName ${response.code}: $error")
            }

            return parseResponse(json)
        } finally {
            response.close()
        }
    }

    private fun parseResponse(json: JSONObject): LlmResponse {
        val choices = json.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            return LlmResponse(text = "No response", toolCalls = emptyList(), raw = json)
        }

        val message = choices.getJSONObject(0).getJSONObject("message")
        val content = message.optString("content", "")
        val toolCallsJson = message.optJSONArray("tool_calls")

        val toolCalls = mutableListOf<ToolCall>()
        if (toolCallsJson != null) {
            for (i in 0 until toolCallsJson.length()) {
                val tc = toolCallsJson.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                toolCalls.add(ToolCall(
                    id = tc.optString("id", "call_$i"),
                    name = fn.getString("name"),
                    params = try { JSONObject(fn.getString("arguments")) } catch (_: Exception) { JSONObject() }
                ))
            }
        }

        return LlmResponse(
            text = if (content.isBlank() && toolCalls.isNotEmpty()) null else content,
            toolCalls = toolCalls,
            raw = json
        )
    }
}

/**
 * Factory for creating LLM providers based on provider type.
 */
object LlmFactory {

    data class ProviderConfig(
        val id: String,
        val name: String,
        val baseUrl: String,
        val apiKey: String,
        val models: List<ModelOption>,
        val supportsTools: Boolean = true
    )

    data class ModelOption(
        val id: String,
        val name: String,
        val free: Boolean = false
    )

    // Predefined providers
    val OPENROUTER = ProviderConfig(
        id = "openrouter",
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1/chat/completions",
        apiKey = "", // set by user
        models = listOf(
            ModelOption("qwen/qwen3.6-plus:free", "Qwen 3.6+ (Free)", free = true),
            ModelOption("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (Free)", free = true),
            ModelOption("google/gemini-2.5-flash", "Gemini 2.5 Flash"),
            ModelOption("openai/gpt-4o", "GPT-4o"),
            ModelOption("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet")
        )
    )

    val OLLAMA_CLOUD = ProviderConfig(
        id = "ollama",
        name = "Ollama Cloud",
        baseUrl = "https://ollama.com/api/chat",
        apiKey = "",
        supportsTools = true,
        models = listOf(
            ModelOption("gemma4:31b-cloud", "Gemma 4 31B (👁️ Vision + Chat)"),
            ModelOption("glm4:32b", "GLM-4 32B"),
            ModelOption("llama3.3", "Llama 3.3 70B"),
            ModelOption("qwen3", "Qwen 3 32B"),
            ModelOption("qwen3:235b", "Qwen 3 235B (Large)"),
            ModelOption("deepseek-r1", "DeepSeek R1 (Reasoning)"),
            ModelOption("gemma3:27b", "Gemma 3 27B"),
            ModelOption("mistral", "Mistral 7B"),
            ModelOption("command-r-plus", "Command R+ 104B"),
            ModelOption("phi4", "Phi-4 14B"),
            // Vision models (for camera/image analysis)
            ModelOption("llava", "LLaVA 7B (👁️ Vision)"),
            ModelOption("llama3.2-vision", "Llama 3.2 Vision 11B (👁️)"),
            ModelOption("bakllava", "BakLLaVA 7B (👁️ Vision)"),
            ModelOption("moondream", "Moondream 2 (👁️ Vision)")
        )
    )

    val LOCAL = ProviderConfig(
        id = "local",
        name = "📱 Local (Sin Internet)",
        baseUrl = "",
        apiKey = "",
        supportsTools = true,
        models = listOf(
            ModelOption("gemma4-e2b", "⚡ Gemma 4 E2B (Rápido, ~1.4GB)"),
            ModelOption("gemma4-e4b", "🧠 Gemma 4 E4B (Inteligente, ~2.6GB)")
        )
    )

    // Vision models list for CameraScreen
    val VISION_MODELS = listOf(
        ModelOption("gemma4:31b-cloud", "Gemma 4 31B (Recomendado)"),
        ModelOption("gemma3:27b", "Gemma 3 27B"),
        ModelOption("gemma3:12b", "Gemma 3 12B (Ligero)"),
        ModelOption("llava", "LLaVA 7B"),
        ModelOption("llama3.2-vision", "Llama 3.2 Vision 11B"),
        ModelOption("bakllava", "BakLLaVA 7B"),
        ModelOption("moondream", "Moondream 2 (Ultra-ligero)")
    )

    fun createProvider(
        providerType: String,
        apiKey: String,
        model: String
    ): LlmProvider {
        val isFree = model.contains(":free")

        return when (providerType) {
            "openrouter" -> OpenAiCompatibleProvider(
                apiKey = apiKey,
                model = model,
                baseUrl = OPENROUTER.baseUrl,
                providerName = "OpenRouter",
                supportsTools = !isFree
            )
            "ollama" -> OllamaCloudProvider(
                apiKey = apiKey,
                model = model
            )
            "local" -> {
                val modelPath = com.beemovil.llm.local.LocalModelManager.getModelPath(model)
                    ?: throw IllegalStateException("Modelo local '$model' no descargado. Ve a Settings → Modelo Local para descargarlo.")
                com.beemovil.llm.local.LocalGemmaProvider(
                    modelPath = modelPath,
                    modelName = LOCAL.models.find { it.id == model }?.name ?: model
                )
            }
            else -> OpenAiCompatibleProvider(
                apiKey = apiKey,
                model = model,
                baseUrl = "https://openrouter.ai/api/v1/chat/completions",
                providerName = "Custom"
            )
        }
    }
}

