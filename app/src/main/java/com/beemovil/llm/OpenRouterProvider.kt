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
 * SYNCHRONOUS OpenRouter provider.
 *
 * For FREE models: embeds tools in the system prompt (text-based tool calling).
 * For PAID models: uses native OpenAI function calling API.
 *
 * This mirrors how OpenClaw handles tool calling with any model.
 */
class OpenRouterProvider(
    private val apiKey: String,
    private val model: String = "qwen/qwen3.6-plus:free"
) : LlmProvider {

    override val name = "OpenRouter ($model)"

    companion object {
        private const val TAG = "OpenRouter"
        private const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
        private val JSON_MEDIA = "application/json".toMediaType()

        // Regex to find tool calls in text responses
        private val TOOL_CALL_REGEX = Regex(
            """<tool_call>\s*\{[^}]*"name"\s*:\s*"([^"]+)"[^}]*"arguments"\s*:\s*(\{[^}]*\})[^}]*\}\s*</tool_call>""",
            RegexOption.DOT_MATCHES_ALL
        )
        // Simpler fallback regex
        private val TOOL_CALL_SIMPLE = Regex(
            """<tool_call>\s*(\{.+?\})\s*</tool_call>""",
            RegexOption.DOT_MATCHES_ALL
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val isFreeModel get() = model.contains(":free")

    /**
     * Build text-based tool instructions for models that don't support function calling.
     * This is how OpenClaw handles it.
     */
    fun buildToolPrompt(tools: List<ToolDefinition>): String {
        if (tools.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("\n\n## Herramientas disponibles")
        sb.appendLine("Tienes acceso a las siguientes herramientas. Para usarlas, responde EXACTAMENTE con este formato:")
        sb.appendLine()
        sb.appendLine("<tool_call>")
        sb.appendLine("""{"name": "nombre_herramienta", "arguments": {"param": "valor"}}""")
        sb.appendLine("</tool_call>")
        sb.appendLine()
        sb.appendLine("Puedes usar múltiples herramientas en una respuesta. Después de usar una herramienta, recibirás el resultado y podrás responder al usuario.")
        sb.appendLine()
        sb.appendLine("### Herramientas:")
        tools.forEach { tool ->
            sb.appendLine("- **${tool.name}**: ${tool.description}")
            sb.appendLine("  Parámetros: ${tool.parameters}")
        }
        sb.appendLine()
        sb.appendLine("IMPORTANTE: Si no necesitas una herramienta, responde normalmente en texto sin usar <tool_call>.")
        return sb.toString()
    }

    /**
     * Parse tool calls from text response (for free models).
     */
    fun parseToolCallsFromText(text: String): Pair<String, List<ToolCall>> {
        val toolCalls = mutableListOf<ToolCall>()
        var cleanText = text

        // Try simple regex first
        val matches = TOOL_CALL_SIMPLE.findAll(text).toList()

        for ((index, match) in matches.withIndex()) {
            try {
                val jsonStr = match.groupValues[1]
                val json = JSONObject(jsonStr)
                val name = json.getString("name")
                val args = json.optJSONObject("arguments") ?: JSONObject()

                toolCalls.add(ToolCall(
                    id = "call_text_$index",
                    name = name,
                    params = args
                ))

                // Remove the tool call from visible text
                cleanText = cleanText.replace(match.value, "")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool call: ${e.message}")
            }
        }

        return Pair(cleanText.trim(), toolCalls)
    }

    override fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        // For free models: skip tools entirely (rate limit is token-based)
        val effectiveMessages = messages

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                effectiveMessages.forEach { put(it.toJson()) }
            })
            // Only use native function calling for paid models
            if (tools.isNotEmpty() && !isFreeModel) {
                put("tools", JSONArray().apply {
                    tools.forEach { put(it.toJson()) }
                })
            }
            put("max_tokens", 4096)
        }

        Log.d(TAG, "POST to OpenRouter: model=$model, msgs=${effectiveMessages.size}, tools=${tools.size}, free=$isFreeModel")

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://beemovil.app")
            .addHeader("X-Title", "Bee-Movil")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response body")

        Log.d(TAG, "Response code: ${response.code}, body length: ${responseBody.length}")

        val json = JSONObject(responseBody)

        if (!response.isSuccessful) {
            val error = json.optJSONObject("error")?.optString("message") ?: responseBody.take(200)
            throw Exception("OpenRouter ${response.code}: $error")
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

        // Check for native tool calls first (paid models)
        val nativeToolCalls = message.optJSONArray("tool_calls")
        if (nativeToolCalls != null && nativeToolCalls.length() > 0) {
            val toolCalls = mutableListOf<ToolCall>()
            for (i in 0 until nativeToolCalls.length()) {
                val tc = nativeToolCalls.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                toolCalls.add(ToolCall(
                    id = tc.getString("id"),
                    name = fn.getString("name"),
                    params = try { JSONObject(fn.getString("arguments")) } catch (_: Exception) { JSONObject() }
                ))
            }
            Log.d(TAG, "Native tool calls: ${toolCalls.size}")
            return LlmResponse(
                text = if (content.isBlank() && toolCalls.isNotEmpty()) null else content,
                toolCalls = toolCalls,
                raw = json
            )
        }

        // For free models: parse tool calls from text
        if (isFreeModel && content.contains("<tool_call>")) {
            val (cleanText, textToolCalls) = parseToolCallsFromText(content)
            Log.d(TAG, "Text-based tool calls: ${textToolCalls.size}")
            return LlmResponse(
                text = if (cleanText.isBlank() && textToolCalls.isNotEmpty()) null else cleanText,
                toolCalls = textToolCalls,
                raw = json
            )
        }

        Log.d(TAG, "Plain text response: ${content.take(50)}")
        return LlmResponse(text = content, toolCalls = emptyList(), raw = json)
    }
}
