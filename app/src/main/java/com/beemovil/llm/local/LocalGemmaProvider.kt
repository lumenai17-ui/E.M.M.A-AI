package com.beemovil.llm.local

import android.content.Context
import android.util.Log
import com.beemovil.llm.*
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

/**
 * LocalGemmaProvider — On-device LLM inference using Gemma 4 via MediaPipe.
 *
 * Implements the same synchronous LlmProvider interface as the cloud providers.
 * Runs entirely offline on the device CPU/GPU — no internet needed.
 *
 * Tool calling is supported via text-based format injection (same approach as
 * OpenRouterProvider uses for free models).
 */
class LocalGemmaProvider(
    private val modelPath: String,
    private val modelName: String = "Gemma 4 Local"
) : LlmProvider {

    override val name = "Local ($modelName)"

    companion object {
        private const val TAG = "LocalGemma"
        private var sharedInference: LlmInference? = null
        private var currentModelPath: String? = null
        // Store application context for MediaPipe initialization
        var appContext: Context? = null

        // Regex to extract tool calls from text output
        private val TOOL_CALL_REGEX = Regex(
            """<tool_call>\s*\{[^}]*"name"\s*:\s*"([^"]+)"[^}]*"arguments"\s*:\s*(\{[^}]*\})[^}]*\}\s*</tool_call>""",
            RegexOption.DOT_MATCHES_ALL
        )
        private val TOOL_CALL_SIMPLE = Regex(
            """<tool_call>\s*(\{.+?\})\s*</tool_call>""",
            RegexOption.DOT_MATCHES_ALL
        )

        /** Release shared engine resources */
        fun releaseEngine() {
            try {
                sharedInference?.close()
            } catch (_: Exception) {}
            sharedInference = null
            currentModelPath = null
        }
    }

    /**
     * Get or create the LlmInference engine.
     * Lazily initialized, shared across calls. Thread-safe.
     */
    @Synchronized
    private fun getEngine(): LlmInference {
        // Reuse if same model
        if (sharedInference != null && currentModelPath == modelPath) {
            return sharedInference!!
        }

        // Close old engine if different model
        releaseEngine()

        val ctx = appContext
            ?: throw IllegalStateException("LocalGemmaProvider.appContext not set. Call LocalGemmaProvider.appContext = applicationContext first.")

        Log.i(TAG, "Initializing MediaPipe LLM from: $modelPath")
        val startTime = System.currentTimeMillis()

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(2048)
            .build()

        sharedInference = LlmInference.createFromOptions(ctx, options)
        currentModelPath = modelPath

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Engine initialized in ${elapsed}ms")

        return sharedInference!!
    }

    override fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        // Build the prompt with tool definitions injected as text
        val prompt = buildPrompt(messages, tools)
        Log.d(TAG, "Prompt length: ${prompt.length} chars")

        val responseText = try {
            val engine = getEngine()
            engine.generateResponse(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            // Try fallback
            runOllamaFallback(prompt)
        }

        Log.d(TAG, "Response: ${responseText.take(200)}")

        // Parse for tool calls
        val toolCalls = parseToolCalls(responseText)
        val cleanText = if (toolCalls.isNotEmpty()) {
            responseText
                .replace(TOOL_CALL_REGEX, "")
                .replace(TOOL_CALL_SIMPLE, "")
                .trim()
                .ifBlank { null }
        } else {
            responseText
        }

        return LlmResponse(
            text = cleanText,
            toolCalls = toolCalls,
            raw = JSONObject().put("local_response", responseText)
        )
    }

    /**
     * Fallback: Use local Ollama server if MediaPipe fails or model isn't loaded.
     */
    private fun runOllamaFallback(prompt: String): String {
        try {
            val body = JSONObject().apply {
                put("model", "gemma4:2b")
                put("prompt", prompt)
                put("stream", false)
            }

            val request = okhttp3.Request.Builder()
                .url("http://localhost:11434/api/generate")
                .post(
                    okhttp3.RequestBody.create(
                        "application/json; charset=utf-8".toMediaTypeOrNull(),
                        body.toString()
                    )
                )
                .build()

            val response = com.beemovil.network.BeeHttpClient.llm.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            response.close()

            val json = JSONObject(responseBody)
            return json.optString("response", "")
        } catch (e: Exception) {
            return "⚠️ Modelo local no disponible. Descarga el modelo en Settings → Proveedor AI → 📱 Local."
        }
    }
    private fun buildPrompt(messages: List<ChatMessage>, tools: List<ToolDefinition>): String {
        val sb = StringBuilder()

        // If tools available, inject tool definitions
        if (tools.isNotEmpty()) {
            sb.appendLine("You are a helpful AI assistant with access to the following tools:")
            sb.appendLine()
            tools.forEach { tool ->
                sb.appendLine("- ${tool.name}: ${tool.description}")
                sb.appendLine("  Parameters: ${tool.parameters}")
            }
            sb.appendLine()
            sb.appendLine("To use a tool, respond with: <tool_call>{\"name\": \"tool_name\", \"arguments\": {\"param\": \"value\"}}</tool_call>")
            sb.appendLine("You can use multiple tools. After tool results are provided, give a natural language summary.")
            sb.appendLine()
        }

        // Add conversation messages in Gemma chat format
        messages.forEach { msg ->
            when (msg.role) {
                "system" -> {
                    sb.appendLine("<start_of_turn>user")
                    sb.appendLine("[System Instructions] ${msg.content}")
                    sb.appendLine("<end_of_turn>")
                }
                "user" -> {
                    sb.appendLine("<start_of_turn>user")
                    sb.appendLine(msg.content)
                    sb.appendLine("<end_of_turn>")
                }
                "assistant" -> {
                    sb.appendLine("<start_of_turn>model")
                    sb.appendLine(msg.content)
                    sb.appendLine("<end_of_turn>")
                }
                "tool" -> {
                    sb.appendLine("<start_of_turn>user")
                    sb.appendLine("[Tool Result] ${msg.content}")
                    sb.appendLine("<end_of_turn>")
                }
            }
        }

        sb.appendLine("<start_of_turn>model")
        return sb.toString()
    }

    private fun parseToolCalls(text: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()

        // Try structured regex first
        TOOL_CALL_REGEX.findAll(text).forEach { match ->
            try {
                val name = match.groupValues[1]
                val args = JSONObject(match.groupValues[2])
                calls.add(ToolCall(
                    id = "local_${System.currentTimeMillis()}_${calls.size}",
                    name = name,
                    params = args
                ))
            } catch (_: Exception) {}
        }

        // Try simpler regex if none found
        if (calls.isEmpty()) {
            TOOL_CALL_SIMPLE.findAll(text).forEach { match ->
                try {
                    val json = JSONObject(match.groupValues[1])
                    val name = json.optString("name", "")
                    val args = json.optJSONObject("arguments") ?: JSONObject()
                    if (name.isNotBlank()) {
                        calls.add(ToolCall(
                            id = "local_${System.currentTimeMillis()}_${calls.size}",
                            name = name,
                            params = args
                        ))
                    }
                } catch (_: Exception) {}
            }
        }

        return calls
    }
}
