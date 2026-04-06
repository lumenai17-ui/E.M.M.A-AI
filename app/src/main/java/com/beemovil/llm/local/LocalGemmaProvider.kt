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

/**
 * LocalGemmaProvider — On-device LLM inference using Gemma 4 via LiteRT-LM.
 *
 * Uses the official Google AI Edge LiteRT-LM SDK (replacement for deprecated MediaPipe GenAI).
 * Runs entirely offline on the device CPU/GPU — no internet needed.
 *
 * API:
 * - Engine(EngineConfig(modelPath)) → engine.initialize() → loads model
 * - engine.createConversation() → conversation session
 * - conversation.sendMessage("prompt") → Message (with contents and toolCalls)
 * - Message.contents.contents → List<Content>, where Content.Text has .text property
 */
class LocalGemmaProvider(
    private val modelPath: String,
    private val modelName: String = "Gemma 4 Local"
) : LlmProvider {

    override val name = "Local ($modelName)"

    companion object {
        private const val TAG = "LocalGemma"
        private var sharedEngine: Engine? = null
        private var sharedConversation: Conversation? = null
        private var currentModelPath: String? = null
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
                sharedConversation?.close()
            } catch (_: Exception) {}
            try {
                sharedEngine?.close()
            } catch (_: Exception) {}
            sharedConversation = null
            sharedEngine = null
            currentModelPath = null
            Log.i(TAG, "Engine released")
        }
    }

    /**
     * Get or create the LiteRT-LM engine.
     * Lazily initialized, shared across calls. Thread-safe.
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
        val startTime = System.currentTimeMillis()

        val config = EngineConfig(modelPath = modelPath)
        val engine = Engine(config)
        engine.initialize()

        sharedEngine = engine
        currentModelPath = modelPath

        val elapsed = System.currentTimeMillis() - startTime
        Log.i(TAG, "Engine initialized in ${elapsed}ms")

        return engine
    }

    override fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        // Build the prompt with tool definitions injected as text
        val prompt = buildPrompt(messages, tools)
        Log.d(TAG, "Prompt length: ${prompt.length} chars")

        val responseText = try {
            val engine = getEngine()

            // Create a fresh conversation for each request to avoid context pollution.
            // We build the full conversation history in the prompt.
            val conversation = engine.createConversation()
            try {
                val response: Message = conversation.sendMessage(prompt)
                extractText(response)
            } finally {
                conversation.close()
            }
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
                    conversation.close()
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Retry also failed: ${e2.message}", e2)
                "⚠️ Error en modelo local: ${e.message?.take(150)}"
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
