package com.beemovil.llm.local

import android.content.Context
import android.util.Log
import com.beemovil.llm.*
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.*
import kotlinx.coroutines.flow.MutableStateFlow

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
            
        // Emit state for UI Loading Dialog
        val engineLoadingState = MutableStateFlow(false)
        
        // Prevent multiple C++ init threads simultaneously
        private val isNativeInitRunning = java.util.concurrent.atomic.AtomicBoolean(false)

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
            engineLoadingState.value = false
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
        engineLoadingState.value = true
        val startTime = System.currentTimeMillis()

        try {
            // Run initialization on a separate thread with timeout
            val executor = Executors.newSingleThreadExecutor()
            val future: Future<Engine> = executor.submit(Callable {
                if (isNativeInitRunning.getAndSet(true)) {
                    throw RuntimeException("Ya hay una sesión intentando cargar el modelo local. Espera un momento.")
                }
                try {
                    val config = EngineConfig(modelPath = modelPath)
                    val engine = Engine(config)
                    engine.initialize()
                    engine
                } finally {
                    isNativeInitRunning.set(false)
                }
            })

            val engine = try {
                future.get(INIT_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                throw RuntimeException("[TIMER] El modelo tardó más de ${INIT_TIMEOUT_SEC}s en cargar. Tu dispositivo puede no tener suficiente RAM.")
            } finally {
                executor.shutdown()
            }

            sharedEngine = engine
            currentModelPath = modelPath

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Engine initialized in ${elapsed}ms")
            isInitializing = false
            engineLoadingState.value = false

            return engine

        } catch (e: OutOfMemoryError) {
            isInitializing = false
            engineLoadingState.value = false
            releaseEngine()
            System.gc()
            throw RuntimeException("[DEV] Sin memoria RAM suficiente para este modelo. Cierra otras apps e intenta de nuevo, o usa un modelo en la nube.")
        } catch (e: Exception) {
            isInitializing = false
            engineLoadingState.value = false
            releaseEngine()
            throw e
        }
    }

    override fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        // Check if any message contains images (vision mode)
        val hasImages = messages.any { !it.images.isNullOrEmpty() }

        val responseText = try {
            val engine = getEngine()

            // Run inference with timeout
            val executor = Executors.newSingleThreadExecutor()
            val future = executor.submit(Callable {
                val conversation = engine.createConversation()
                try {
                    if (hasImages) {
                        // 22-G: MULTIMODAL — try multiple approaches for image+text
                        val lastImageMsg = messages.lastOrNull { !it.images.isNullOrEmpty() }
                        // 22-G: MULTIMODAL — Use the official LiteRT-LM Content API
                        val textPrompt = buildPrompt(messages, tools)
                        val contents = buildMultimodalContent(messages, tools)

                        val response: Message = try {
                            // Pass the properly built image and text contents
                            val msg = Message.of(contents) 
                            conversation.sendMessage(msg)
                        } catch (e: Exception) {
                            Log.w(TAG, "Multimodal Content API failed: ${e.message}", e)
                            conversation.sendMessage(textPrompt)
                        }
                        extractText(response)
                    } else {
                        // Text-only mode (original)
                        val prompt = buildPrompt(messages, tools)
                        Log.d(TAG, "Text prompt length: ${prompt.length} chars")
                        val response: Message = conversation.sendMessage(prompt)
                        extractText(response)
                    }
                } finally {
                    try { conversation.close() } catch (_: Exception) {}
                }
            })

            try {
                future.get(INFERENCE_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                "[TIMER] El modelo tardó demasiado en responder. Intenta con un mensaje más corto."
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            } finally {
                executor.shutdown()
            }

        } catch (e: OutOfMemoryError) {
            releaseEngine()
            System.gc()
            "[DEV] Sin memoria. Cierra otras apps e intenta de nuevo."
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            "[WARN] Error en modelo local: ${e.message?.take(200)}. El proceso ha sido cancelado con seguridad para proteger el dispositivo."
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

    /**
     * 22-G: Build multimodal content (text + image) for vision inference.
     * Decodes base64 images from ChatMessage.images and creates Content objects.
     */
    private fun buildMultimodalContent(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): List<Content> {
        val contents = mutableListOf<Content>()

        // System prompt (as text)
        val systemMsg = messages.firstOrNull { it.role == "system" }
        if (systemMsg != null) {
            val compact = (systemMsg.content ?: "")
                .substringBefore("## Tus 3")
                .take(600)
                .trim()
            contents.add(Content.Text("[System] $compact"))
        }

        // Process messages for images and text
        messages.filter { it.role != "system" }.forEach { msg ->
            // Add text content
            val text = msg.content ?: ""
            if (text.isNotBlank()) {
                contents.add(Content.Text(text))
            }

            // Add images (decode from base64 → resize → Content.ImageBytes)
            msg.images?.forEach { b64 ->
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        // Resize if too large (max 512px for edge models)
                        val maxDim = 512
                        val resized = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                            val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                            Bitmap.createScaledBitmap(
                                bitmap,
                                (bitmap.width * scale).toInt(),
                                (bitmap.height * scale).toInt(),
                                true
                            )
                        } else bitmap

                        // Convert resized bitmap to JPEG bytes for LiteRT-LM
                        val baos = ByteArrayOutputStream()
                        resized.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                        val imageBytes = baos.toByteArray()

                        contents.add(Content.ImageBytes(imageBytes))
                        Log.i(TAG, "Added image: ${resized.width}x${resized.height} (${imageBytes.size} bytes)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode image: ${e.message}")
                }
            }
        }

        if (contents.isEmpty()) {
            contents.add(Content.Text("Describe what you see."))
        }

        return contents
    }

    private fun buildPrompt(messages: List<ChatMessage>, tools: List<ToolDefinition>): String {
        val sb = StringBuilder()

        // LOCAL MODELS: Compact tool list (names + short description only).
        // E2B = 4096 tokens, E4B = 8192 tokens. Full schemas (~3000 tokens) don't fit.
        // Instead of removing tools, we give the model a compact summary.
        if (tools.isNotEmpty()) {
            sb.appendLine("You have these tools available (call them using <tool_call>{\"name\":\"NAME\",\"arguments\":{...}}</tool_call>):")
            // Only list name + short desc, NO parameter schemas
            tools.forEach { tool ->
                val shortDesc = tool.description.take(60).substringBefore('\n')
                sb.appendLine("- ${tool.name}: $shortDesc")
            }
            sb.appendLine()
        }

        // Add conversation messages in Gemma chat format
        messages.forEach { msg ->
            when (msg.role) {
                "system" -> {
                    // Compact system prompt — keep personality but trim verbose parts
                    val compact = (msg.content ?: "")
                        .substringBefore("## Tus 3") // Cut before the huge tool list
                        .take(800) // Enough for custom agent personality
                        .trim()
                    sb.appendLine("<start_of_turn>user")
                    sb.appendLine("[System] $compact")
                    sb.appendLine("<end_of_turn>")
                }
                "user" -> {
                    sb.appendLine("<start_of_turn>user")
                    sb.appendLine(msg.content ?: "")
                    sb.appendLine("<end_of_turn>")
                }
                "assistant" -> {
                    sb.appendLine("<start_of_turn>model")
                    sb.appendLine(msg.content ?: "")
                    sb.appendLine("<end_of_turn>")
                }
                "tool" -> {
                    sb.appendLine("<start_of_turn>user")
                    sb.appendLine("[Tool Result] ${msg.content?.take(500) ?: ""}")
                    sb.appendLine("<end_of_turn>")
                }
            }
        }

        sb.appendLine("<start_of_turn>model")

        // Safety check: if prompt is still too long, truncate middle messages
        val prompt = sb.toString()
        if (prompt.length > 24000) { // ~6000 tokens — fits E4B (8192 tokens) with room for response
            Log.w(TAG, "Prompt too long (${prompt.length} chars), truncating")
            return prompt.take(8000) + "\n...[contexto truncado]...\n" + prompt.takeLast(8000)
        }

        return prompt
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
