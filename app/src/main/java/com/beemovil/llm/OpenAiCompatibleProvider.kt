package com.beemovil.llm

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
        val responseBody = response.body?.string() ?: throw Exception("Empty response body")

        Log.d(TAG, "[$providerName] Response: ${response.code}")

        val json = JSONObject(responseBody)

        if (!response.isSuccessful) {
            val error = json.optJSONObject("error")?.optString("message") ?: responseBody.take(200)
            throw Exception("$providerName ${response.code}: $error")
        }

        return parseResponse(json)
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
            ModelOption("llama3.3", "Llama 3.3 70B"),
            ModelOption("qwen3", "Qwen 3 32B"),
            ModelOption("qwen3:235b", "Qwen 3 235B (Large)"),
            ModelOption("deepseek-r1", "DeepSeek R1 (Reasoning)"),
            ModelOption("gemma3:27b", "Gemma 3 27B"),
            ModelOption("mistral", "Mistral 7B"),
            ModelOption("command-r-plus", "Command R+ 104B"),
            ModelOption("llama3.1:405b", "Llama 3.1 405B (Massive)"),
            ModelOption("phi4", "Phi-4 14B"),
            ModelOption("glm-5:cloud", "GLM-5 Cloud")
        )
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
            else -> OpenAiCompatibleProvider(
                apiKey = apiKey,
                model = model,
                baseUrl = "https://openrouter.ai/api/v1/chat/completions",
                providerName = "Custom"
            )
        }
    }
}
