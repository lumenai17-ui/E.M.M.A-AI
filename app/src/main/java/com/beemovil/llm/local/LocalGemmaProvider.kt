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
import java.util.concurrent.locks.ReentrantLock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * LocalGemmaProvider — On-device LLM inference using Gemma 4 via LiteRT-LM.
 *
 * Uses the official Google AI Edge LiteRT-LM SDK (replacement for deprecated MediaPipe GenAI).
 * Runs entirely offline on the device CPU/GPU — no internet needed.
 *
 * Safety features (v5.7.2):
 * - ReentrantLock (no more @Synchronized deadlocks between init and inference threads)
 * - AtomicBoolean busy guard — concurrent calls fail fast instead of blocking
 * - Engine initialization timeout (90s max)
 * - Inference timeout (120s max)
 * - OOM protection with catch + release
 * - Aggressive prompt truncation for E2B (4096 tokens) / E4B (8192 tokens)
 * - Tool list cap (max 12 tools)
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
        private const val MAX_TOOLS_LOCAL = 12         // Max tools in compact list
        private const val SYSTEM_PROMPT_CAP = 400      // Chars for system prompt (saves ~300 tokens)
        private const val PROMPT_CAP_E2B = 8000        // ~2000 tokens — safe for E2B (4096 token window)
        private const val PROMPT_CAP_E4B = 16000       // ~4000 tokens — safe for E4B (8192 token window)

        private var sharedEngine: Engine? = null
        private var currentModelPath: String? = null
        @Volatile var isInitializing = false
            private set

        // Emit state for UI Loading Dialog
        val engineLoadingState = MutableStateFlow(false)

        // Prevent multiple C++ init threads simultaneously
        private val isNativeInitRunning = java.util.concurrent.atomic.AtomicBoolean(false)

        // BUG-L2 FIX: Inference busy guard — prevents concurrent inference deadlocks
        private val isBusy = java.util.concurrent.atomic.AtomicBoolean(false)

        // BUG-L1/L2 FIX: ReentrantLock replaces @Synchronized to allow tryLock()
        private val engineLock = ReentrantLock()

        // Store application context for initialization
        var appContext: Context? = null

        // BUG-L1 FIX: Singleton provider cache — prevents creating new instances per frame
        @Volatile
        private var cachedProvider: LocalGemmaProvider? = null
        @Volatile
        private var cachedModelPath: String? = null

        /**
         * Get or create a cached provider instance.
         * LiveVision and ChatViewModel should use this instead of creating new instances.
         */
        fun getOrCreate(modelPath: String, modelName: String = "Gemma 4 Local"): LocalGemmaProvider {
            val existing = cachedProvider
            if (existing != null && cachedModelPath == modelPath) {
                return existing
            }
            val provider = LocalGemmaProvider(modelPath, modelName)
            cachedProvider = provider
            cachedModelPath = modelPath
            return provider
        }

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
            engineLock.lock()
            try {
                try {
                    sharedEngine?.close()
                } catch (_: Exception) {}
                sharedEngine = null
                currentModelPath = null
                isInitializing = false
                engineLoadingState.value = false
                isBusy.set(false)
                Log.i(TAG, "Engine released")
            } finally {
                engineLock.unlock()
            }
        }

        /** Check if the engine is currently busy with inference */
        fun isEngineBusy(): Boolean = isBusy.get()
    }

    /**
     * Detect model size from path to adjust token limits.
     * E2B = 4096 tokens, E4B = 8192 tokens.
     */
    private fun isE4B(): Boolean = modelPath.contains("E4B", ignoreCase = true)

    private fun getPromptCap(): Int = if (isE4B()) PROMPT_CAP_E4B else PROMPT_CAP_E2B

    /**
     * Get or create the LiteRT-LM engine with timeout protection.
     * Engine.initialize() loads 2.6GB+ into RAM and can take 15-60s.
     *
     * BUG-L2 FIX: Uses ReentrantLock.tryLock() + timeout instead of @Synchronized
     * to prevent indefinite blocking.
     */
    private fun getEngine(): Engine {
        // Fast path: engine already exists and matches
        if (sharedEngine != null && currentModelPath == modelPath) {
            return sharedEngine!!
        }

        // Try to acquire lock with timeout — don't block forever
        val acquired = engineLock.tryLock(5, TimeUnit.SECONDS)
        if (!acquired) {
            throw RuntimeException("[BUSY] El motor local está siendo cargado por otro proceso. Espera unos segundos e intenta de nuevo.")
        }

        try {
            // Double-check after acquiring lock
            if (sharedEngine != null && currentModelPath == modelPath) {
                return sharedEngine!!
            }

            // Close old engine if different model
            try { sharedEngine?.close() } catch (_: Exception) {}
            sharedEngine = null
            currentModelPath = null

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
                try { sharedEngine?.close() } catch (_: Exception) {}
                sharedEngine = null
                currentModelPath = null
                System.gc()
                throw RuntimeException("[DEV] Sin memoria RAM suficiente para este modelo. Cierra otras apps e intenta de nuevo, o usa un modelo en la nube.")
            } catch (e: Exception) {
                isInitializing = false
                engineLoadingState.value = false
                try { sharedEngine?.close() } catch (_: Exception) {}
                sharedEngine = null
                currentModelPath = null
                throw e
            }
        } finally {
            engineLock.unlock()
        }
    }

    override fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse {
        // BUG-L2 FIX: Busy guard — fail fast if another inference is running
        if (!isBusy.compareAndSet(false, true)) {
            return LlmResponse(
                text = "[BUSY] El modelo local está procesando otra solicitud. Espera a que termine.",
                toolCalls = emptyList(),
                raw = JSONObject().put("error", "busy")
            )
        }

        try {
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
                            // MULTIMODAL — try multiple approaches for image+text
                            val textPrompt = buildPrompt(messages, tools)
                            val contents = buildMultimodalContent(messages, tools)

                            val response: Message = try {
                                val msg = Message.of(contents)
                                conversation.sendMessage(msg)
                            } catch (e: Exception) {
                                Log.w(TAG, "Multimodal Content API failed: ${e.message}", e)
                                conversation.sendMessage(textPrompt)
                            }
                            extractText(response)
                        } else {
                            // Text-only mode
                            val prompt = buildPrompt(messages, tools)
                            Log.d(TAG, "Text prompt length: ${prompt.length} chars (cap: ${getPromptCap()})")
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
        } finally {
            // BUG-L2 FIX: Always release busy guard
            isBusy.set(false)
        }
    }

    /**
     * Extract text from a LiteRT-LM Message.
     */
    private fun extractText(message: Message): String {
        return try {
            val contents = message.contents?.contents ?: emptyList()
            contents.filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .ifBlank {
                    message.toString()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction error: ${e.message}")
            message.toString()
        }
    }

    /**
     * Build multimodal content (text + image) for vision inference.
     */
    private fun buildMultimodalContent(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): List<Content> {
        val contents = mutableListOf<Content>()

        // System prompt — ultra compact for local models
        val systemMsg = messages.firstOrNull { it.role == "system" }
        if (systemMsg != null) {
            val compact = compactSystemPrompt(systemMsg.content ?: "")
            contents.add(Content.Text("[System] $compact"))
        }

        // Process messages for images and text
        messages.filter { it.role != "system" }.forEach { msg ->
            val text = msg.content ?: ""
            if (text.isNotBlank()) {
                contents.add(Content.Text(text.take(500)))
            }

            // Add images (decode from base64 → resize → Content.ImageBytes)
            msg.images?.forEach { b64 ->
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
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

    /**
     * BUG-L3 FIX: Ultra-compact system prompt for local models.
     * Strips tool lists, delegation rules, verbose sections.
     * Returns max SYSTEM_PROMPT_CAP chars of essential personality.
     */
    private fun compactSystemPrompt(raw: String): String {
        return raw
            .substringBefore("## Tus ")      // Cut tool list
            .substringBefore("## Reglas de")  // Cut delegation rules
            .substringBefore("## Reglas Gen") // Cut general rules
            .substringBefore("CORE:")         // Cut inline tool lists
            .substringBefore("INTELIGENCIA:") // Cut inline tool lists
            .substringBefore("IMPORTANTE:")   // Cut verbose instructions
            .replace(Regex("""\n{3,}"""), "\n\n") // Collapse blank lines
            .trim()
            .take(SYSTEM_PROMPT_CAP)
    }

    private fun buildPrompt(messages: List<ChatMessage>, tools: List<ToolDefinition>): String {
        val sb = StringBuilder()

        // BUG-L4 FIX: Limit tools to MAX_TOOLS_LOCAL, prioritize most useful
        if (tools.isNotEmpty()) {
            val limitedTools = if (tools.size > MAX_TOOLS_LOCAL) {
                // Prioritize: keep tools that are most commonly requested
                val priority = setOf(
                    "web_search", "weather", "calendar", "memory", "notify",
                    "generate_pdf", "generate_html", "email", "calculator",
                    "task_manager", "file_manager", "camera"
                )
                val prioritized = tools.filter { priority.contains(it.name) }
                val rest = tools.filter { !priority.contains(it.name) }
                (prioritized + rest).take(MAX_TOOLS_LOCAL)
            } else {
                tools
            }

            sb.appendLine("Available tools (use <tool_call>{\"name\":\"NAME\",\"arguments\":{...}}</tool_call>):")
            limitedTools.forEach { tool ->
                val shortDesc = tool.description.take(40).substringBefore('\n')
                sb.appendLine("- ${tool.name}: $shortDesc")
            }
            sb.appendLine()
        }

        // Add conversation messages in Gemma chat format
        messages.forEach { msg ->
            when (msg.role) {
                "system" -> {
                    // BUG-L3 FIX: Ultra compact system prompt
                    val compact = compactSystemPrompt(msg.content ?: "")
                    sb.appendLine("<start_of_turn>user")
                    sb.appendLine("[System] $compact")
                    sb.appendLine("<end_of_turn>")
                }
                "user" -> {
                    sb.appendLine("<start_of_turn>user")
                    // Cap user messages to prevent single huge messages from killing context
                    sb.appendLine((msg.content ?: "").take(1500))
                    sb.appendLine("<end_of_turn>")
                }
                "assistant" -> {
                    sb.appendLine("<start_of_turn>model")
                    sb.appendLine((msg.content ?: "").take(1000))
                    sb.appendLine("<end_of_turn>")
                }
                "tool" -> {
                    sb.appendLine("<start_of_turn>user")
                    sb.appendLine("[Tool Result] ${msg.content?.take(300) ?: ""}")
                    sb.appendLine("<end_of_turn>")
                }
            }
        }

        sb.appendLine("<start_of_turn>model")

        // BUG-L3/L4 FIX: Enforce total prompt cap based on model size
        val promptCap = getPromptCap()
        val prompt = sb.toString()
        if (prompt.length > promptCap) {
            Log.w(TAG, "Prompt too long (${prompt.length} chars, cap=$promptCap), truncating")
            // Keep system prompt (first ~600) + last part (most recent context)
            val keepStart = minOf(prompt.length, promptCap / 3)
            val keepEnd = minOf(prompt.length, promptCap * 2 / 3)
            return prompt.take(keepStart) + "\n...[truncado]...\n" + prompt.takeLast(keepEnd)
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
