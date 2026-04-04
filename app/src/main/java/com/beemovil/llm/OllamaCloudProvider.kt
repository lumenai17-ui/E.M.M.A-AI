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
 * Ollama Cloud provider — uses native Ollama API format.
 *
 * API: POST https://ollama.com/api/chat
 * Auth: Bearer token
 * Tool calling: SUPPORTED natively
 *
 * Response format differs from OpenAI:
 * - message.content (string) instead of choices[0].message.content
 * - message.tool_calls[].function.arguments is JSONObject (not string)
 */
class OllamaCloudProvider(
    private val apiKey: String,
    private val model: String = "llama3.3"
) : LlmProvider {

    override val name = "Ollama Cloud ($model)"

    companion object {
        private const val TAG = "OllamaCloud"
        private const val BASE_URL = "https://ollama.com/api/chat"
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
            put("stream", false) // Non-streaming for simplicity
            put("messages", buildMessages(messages))
            if (tools.isNotEmpty()) {
                put("tools", buildTools(tools))
            }
        }

        Log.d(TAG, "POST to Ollama Cloud: model=$model, msgs=${messages.size}, tools=${tools.size}")

        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response body")

        Log.d(TAG, "Response code: ${response.code}, length: ${responseBody.length}")

        if (!response.isSuccessful) {
            val json = try { JSONObject(responseBody) } catch (_: Exception) { null }
            val error = json?.optString("error") ?: responseBody.take(200)
            throw Exception("Ollama Cloud ${response.code}: $error")
        }

        val json = JSONObject(responseBody)
        return parseResponse(json)
    }

    private fun buildMessages(messages: List<ChatMessage>): JSONArray {
        return JSONArray().apply {
            messages.forEach { msg ->
                put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content ?: "")
                    // For tool results, add tool_name
                    if (msg.role == "tool" && msg.toolCallId != null) {
                        // Ollama uses tool_name instead of tool_call_id
                    }
                    // For assistant messages with tool calls
                    if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
                        put("tool_calls", JSONArray().apply {
                            msg.toolCalls.forEach { tc ->
                                put(JSONObject().apply {
                                    put("function", JSONObject().apply {
                                        put("name", tc.name)
                                        put("arguments", tc.params) // Ollama: JSONObject, not string!
                                    })
                                })
                            }
                        })
                    }
                })
            }
        }
    }

    private fun buildTools(tools: List<ToolDefinition>): JSONArray {
        return JSONArray().apply {
            tools.forEach { tool ->
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    })
                })
            }
        }
    }

    private fun parseResponse(json: JSONObject): LlmResponse {
        val message = json.optJSONObject("message")
            ?: return LlmResponse(text = "No response from model", toolCalls = emptyList(), raw = json)

        val content = message.optString("content", "")
        val toolCallsJson = message.optJSONArray("tool_calls")

        val toolCalls = mutableListOf<ToolCall>()
        if (toolCallsJson != null) {
            for (i in 0 until toolCallsJson.length()) {
                val tc = toolCallsJson.getJSONObject(i)
                val fn = tc.getJSONObject("function")
                // Ollama returns arguments as JSONObject, not string
                val args = fn.optJSONObject("arguments") ?: try {
                    JSONObject(fn.optString("arguments", "{}"))
                } catch (_: Exception) { JSONObject() }

                toolCalls.add(ToolCall(
                    id = "ollama_call_$i",
                    name = fn.getString("name"),
                    params = args
                ))
            }
        }

        Log.d(TAG, "Parsed: content=${content.take(50)}, tools=${toolCalls.size}")
        return LlmResponse(
            text = if (content.isBlank() && toolCalls.isNotEmpty()) null else content,
            toolCalls = toolCalls,
            raw = json
        )
    }
}
