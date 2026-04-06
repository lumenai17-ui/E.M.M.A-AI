package com.beemovil.llm.local

import android.content.Context
import android.util.Log
import com.beemovil.llm.*
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import org.json.JSONObject
import java.util.concurrent.*

/**
 * LocalGemmaProvider — On-device LLM inference using Gemma 4 via LiteRT-LM.
 *
 * Uses the official Google AI Edge LiteRT-LM SDK (replacement for deprecated MediaPipe GenAI).
 * Runs entirely offline on the device CPU/GPU — no internet needed.
 *
 * Safety features:
 * - Engine initialization timeout (90s max)
 * - Inference timeout (120s max)
 * - OOM protection with catch + release
 * - Automatic retry with engine recreation
 */
class LocalGemmaProvider(
    private val modelPath: String,
    private val modelName: String = "Gemma 4 Local"
) : LlmProvider {

    override val name = "Local ($modelName)"

    companion object {
        private const val TAG = "LocalGemma"
        private const val INIT_TIMEOUT_SEC = 90L      // 90s max for model loading
        private const val INFERENCE_TIMEOUT_SEC = 120L // 120s max for response generation

        private var sharedEngine: Engine? = null
        private var currentModelPath: String? = null
        @Volatile var isInitializing = false
            private set

        // Store application context for initialization
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
                sharedEngine?.close()
            } catch (_: Exception) {}
            sharedEngine = null
            currentModelPath = null
            isInitializing = false
            Log.i(TAG, "Engine released")
        }
    }

    /**
     * Get or create the LiteRT-LM engine with timeout protection.
     * Engine.initialize() loads 2.6GB+ into RAM and can take 15-60s.
     */
    @Synchronized
    private fun getEngine(): Engine {
        // Reuse if same model
        if (sharedEngine != null && currentModelPath == modelPath) {
            return sharedEngine!!
        }

        // Close old engine if different model
        releaseEngine()

        Log.i(TAG, "Initializing LiteRT-LM engine from: $modelPath")
        isInitializing = true
        val startTime = System.currentTimeMillis()

        try {
            // Run initialization on a separate thread with timeout
            val executor = Executors.newSingleThreadExecutor()
            val future: Future<Engine> = executor.submit(Callable {
                val config = EngineConfig(modelPath = modelPath)
                val engine = Engine(config)
                engine.initialize()
                engine
            })

            val engine = try {
                future.get(INIT_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                throw RuntimeException("⏱️ El modelo tardó más de ${INIT_TIMEOUT_SEC}s en cargar. Tu dispositivo puede no tener suficiente RAM.")
            } finally {
                executor.shutdown()
            }

            sharedEngine = engine
            currentModelPath = modelPath

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Engine initialized in ${elapsed}ms")
            isInitializing = false

            return engine

        } catch (e: OutOfMemoryError) {
            isInitializing = false
            releaseEngine()
            System.gc()
            throw RuntimeException("📱 Sin memoria RAM suficiente para este modelo. Cierra otras apps e intenta de nuevo, o usa un modelo en la nube.")
        } catch (e: Exception) {
            isInitializing = false
            releaseEngine()
            throw e
        }
    }

    override fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        // Build the prompt with tool definitions injected as text
        val prompt = buildPrompt(messages, tools)
        Log.d(TAG, "Prompt length: ${prompt.length} chars")

        val responseText = try {
            val engine = getEngine()

            // Run inference with timeout
            val executor = Executors.newSingleThreadExecutor()
            val future = executor.submit(Callable {
                val conversation = engine.createConversation()
                try {
                    val response: Message = conversation.sendMessage(prompt)
                    extractText(response)
                } finally {
                    try { conversation.close() } catch (_: Exception) {}
                }
            })

            try {
                future.get(INFERENCE_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                "⏱️ El modelo tardó demasiado en responder. Intenta con un mensaje más corto."
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            } finally {
                executor.shutdown()
            }

        } catch (e: OutOfMemoryError) {
            releaseEngine()
            System.gc()
            "📱 Sin memoria. Cierra otras apps e intenta de nuevo."
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)

            // Try to recover by releasing and retrying once
            try {
                releaseEngine()
                val engine = getEngine()
                val conversation = engine.createConversation()
                try {
                    val response = conversation.sendMessage(prompt)
                    extractText(response)
                } finally {
                    try { conversation.close() } catch (_: Exception) {}
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Retry also failed: ${e2.message}", e2)
                "⚠️ Error en modelo local: ${e.message?.take(200)}"
            }
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
     * Extract text from a LiteRT-LM Message.
     * Message.contents → Contents → List<Content> → filter Content.Text → join .text
     */
    private fun extractText(message: Message): String {
        return try {
            val contents = message.contents?.contents ?: emptyList()
            contents.filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .ifBlank {
                    // Fallback: try toString on the message
                    message.toString()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction error: ${e.message}")
            message.toString()
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
