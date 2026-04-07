package com.beemovil.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.beemovil.agent.AgentConfig
import com.beemovil.agent.BeeAgent
import com.beemovil.agent.DefaultAgents
import com.beemovil.security.SecurePrefs
import com.beemovil.llm.ChatMessage
import com.beemovil.llm.LlmFactory
import com.beemovil.llm.OllamaCloudProvider
import com.beemovil.memory.BeeMemoryDB
import com.beemovil.memory.ChatHistoryDB
import com.beemovil.skills.BeeSkill
import com.beemovil.files.AttachedFile
import com.beemovil.files.FileType
import java.io.ByteArrayOutputStream
import java.io.File

data class ChatUiMessage(
    val text: String,
    val isUser: Boolean,
    val agentIcon: String = "BEE",
    val isError: Boolean = false,
    val isLoading: Boolean = false,
    val toolsUsed: List<String> = emptyList(),
    val filePaths: List<String> = emptyList(),
    val attachmentNames: List<String> = emptyList()  // Display names of attached files
)

class ChatViewModel : ViewModel() {

    companion object {
        private const val TAG = "ChatVM"
    }

    val messages = mutableStateListOf<ChatUiMessage>()
    val isLoading = mutableStateOf(false)
    val currentAgentConfig = mutableStateOf(DefaultAgents.MAIN)
    val availableAgents = mutableStateListOf<AgentConfig>().apply { addAll(DefaultAgents.ALL) }

    // Provider configuration
    val currentProvider = mutableStateOf("openrouter")
    val currentModel = mutableStateOf("qwen/qwen3.6-plus:free")

    // Navigation state
    val currentScreen = mutableStateOf("conversations") // "conversations" | "chat" | "settings"

    // Email navigation state
    val selectedEmailUid = mutableStateOf(0L)
    val replyTo = mutableStateOf<String?>(null)
    val replySubject = mutableStateOf<String?>(null)

    private var agents = mutableMapOf<String, BeeAgent>()
    private var skills = mapOf<String, BeeSkill>()
    fun getSkills(): Map<String, BeeSkill> = skills
    private var apiKeys = mutableMapOf<String, String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    // Memory & History
    var memoryDB: BeeMemoryDB? = null
        private set
    var chatHistoryDB: ChatHistoryDB? = null
        private set

    // Telegram bot state (observable by Compose)
    val telegramBotStatus = mutableStateOf("offline") // "offline" | "connecting" | "online" | "error"
    val telegramBotName = mutableStateOf("")
    val telegramBotMessages = mutableStateOf(0)

    // Voice input
    var voiceManager: com.beemovil.skills.VoiceInputManager? = null
    var deepgramVoiceManager: com.beemovil.voice.DeepgramVoiceManager? = null
    val isRecording = mutableStateOf(false)

    // Dashboard AI insight (dynamic, context-aware)
    val dashboardInsight = mutableStateOf("")
    val dashboardInsightLoading = mutableStateOf(false)

    // Conversation attachments — persist across entire session
    val conversationAttachments = mutableStateListOf<AttachedFile>()

    fun toggleVoiceInput(onText: (String) -> Unit) {
        val vm = voiceManager ?: return
        if (isRecording.value) {
            vm.stopListening()
            isRecording.value = false
        } else {
            vm.startListening(
                onPartialResult = { partial -> mainHandler.post { onText(partial) } },
                onListeningState = { state -> mainHandler.post { isRecording.value = state } },
                onErrorCallback = { error ->
                    mainHandler.post {
                        isRecording.value = false
                        messages.add(ChatUiMessage("$error", isUser = false, agentIcon = "ERR", isError = true))
                    }
                },
                onFinalResult = { text ->
                    mainHandler.post {
                        isRecording.value = false
                        onText(text)
                        sendMessage(text)
                    }
                }
            )
        }
    }

    fun initialize(skillMap: Map<String, BeeSkill>, openRouterKey: String, ollamaKey: String = "",
                   memory: BeeMemoryDB? = null, chatHistory: ChatHistoryDB? = null,
                   context: Context? = null) {
        this.skills = skillMap
        this.memoryDB = memory
        this.chatHistoryDB = chatHistory
        this.appContext = context?.applicationContext
        if (openRouterKey.isNotBlank()) apiKeys["openrouter"] = openRouterKey
        if (ollamaKey.isNotBlank()) apiKeys["ollama"] = ollamaKey
    }

    fun hasApiKey(): Boolean {
        if (currentProvider.value == "local") return true  // Local models don't need API key
        return apiKeys[currentProvider.value]?.isNotBlank() == true
    }

    fun getApiKey(provider: String): String = apiKeys[provider] ?: ""

    fun getProviderDisplayName(): String = when (currentProvider.value) {
        "openrouter" -> "OpenRouter"
        "ollama" -> "Ollama Cloud"
        "local" -> "Local (Gemma 4)"
        else -> currentProvider.value
    }

    /**
     * Generate a contextual AI insight for the dashboard.
     * ALWAYS uses LLM (cloud or local) — no hardcoded phrases.
     * Includes: time, soul profile, memories, calendar, insight type rotation.
     */
    fun generateDashboardInsight() {
        if (dashboardInsightLoading.value) return
        dashboardInsightLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val dayOfWeek = java.text.SimpleDateFormat("EEEE", java.util.Locale("es")).format(java.util.Date())
                val timeContext = when {
                    hour < 6 -> "Es de madrugada ($dayOfWeek)"
                    hour < 12 -> "Es la manana del $dayOfWeek"
                    hour < 18 -> "Es la tarde del $dayOfWeek"
                    else -> "Es la noche del $dayOfWeek"
                }

                // Soul profile for personalization
                val soulContext = try {
                    memoryDB?.getSoulSummary()?.take(200) ?: ""
                } catch (_: Exception) { "" }

                // Recent memories
                val recentMemories = try {
                    memoryDB?.getAllMemories()?.takeLast(5)?.joinToString("; ") { it.content.take(60) } ?: ""
                } catch (_: Exception) { "" }

                // Calendar
                val calendarContext = try {
                    val calSkill = skills["read_calendar"]
                    if (calSkill != null) {
                        val result = calSkill.execute(org.json.JSONObject().apply { put("days", 1) }).toString()
                        if (result.length < 200) result else result.take(200)
                    } else ""
                } catch (_: Exception) { "" }

                // Rotate insight type for variety
                val insightTypes = listOf(
                    "un dato curioso o interesante sobre tecnologia, ciencia o historia",
                    "un tip de productividad practico",
                    "una frase motivacional original (no cliche)",
                    "un recordatorio util basado en el contexto del usuario",
                    "una pregunta inteligente que haga pensar al usuario",
                    "un saludo creativo y personalizado"
                )
                val insightType = insightTypes[hour % insightTypes.size]

                val prompt = buildString {
                    append("Eres Bee-Movil, un asistente AI personal e inteligente. ")
                    append("Genera UNA frase corta (max 15 palabras) para el dashboard. ")
                    append("$timeContext. ")
                    if (soulContext.isNotBlank()) append("$soulContext ")
                    if (recentMemories.isNotBlank()) append("Contexto reciente: $recentMemories. ")
                    if (calendarContext.isNotBlank()) append("Agenda hoy: $calendarContext. ")
                    append("Tipo de frase: $insightType. ")
                    append("Solo la frase, nada mas. Sin comillas. En espanol.")
                }

                // Always try LLM (cloud or local — both available)
                val provider = currentProvider.value
                val key = apiKeys[provider] ?: ""

                val llmProvider = LlmFactory.createProvider(provider, key, currentModel.value)
                val response = llmProvider.complete(
                    listOf(ChatMessage("user", prompt)),
                    emptyList()
                )
                val text = response.text?.trim()?.take(80) ?: ""
                if (text.isNotBlank()) {
                    mainHandler.post { dashboardInsight.value = text }
                } else {
                    mainHandler.post { dashboardInsight.value = "Listo para lo que necesites" }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Dashboard insight failed: ${e.message}")
                // Only fallback if LLM truly unavailable (no model downloaded, etc.)
                mainHandler.post { dashboardInsight.value = "Conectando inteligencia..." }
            } finally {
                mainHandler.post { dashboardInsightLoading.value = false }
            }
        }
    }

    /** Force refresh the dashboard insight (tap-to-refresh). */
    fun forceRefreshInsight() {
        dashboardInsight.value = ""
        dashboardInsightLoading.value = false
        generateDashboardInsight()
    }

    fun updateApiKey(provider: String, key: String) {
        apiKeys[provider] = key
        agents.clear()
    }

    fun switchProvider(provider: String, model: String) {
        currentProvider.value = provider
        currentModel.value = model
        agents.clear()
    }

    /**
     * Resolve an agent by ID for delegation.
     * Creates a FRESH agent instance (not cached) so delegated agents
     * have clean conversation history.
     */
    fun resolveAgent(agentId: String): com.beemovil.agent.BeeAgent? {
        val config = availableAgents.find { it.id == agentId } ?: return null
        // Use the agent's own model if configured, else inherit global
        val resolvedConfig = if (config.model.isBlank()) {
            config.copy(model = currentModel.value)
        } else config
        return try {
            getOrCreateAgent(resolvedConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve agent '${agentId}': ${e.message}")
            null
        }
    }

    /** Get all agent configs (for DelegateSkill to list available agents) */
    fun getAvailableAgentConfigs(): List<com.beemovil.agent.AgentConfig> {
        return availableAgents.toList()
    }

    /**
     * Open a chat with a specific agent. Loads history from DB.
     */
    fun openAgentChat(agentId: String) {
        val config = availableAgents.find { it.id == agentId } ?: DefaultAgents.MAIN
        currentAgentConfig.value = config

        // Invalidate cached agent so it gets recreated with current systemPrompt
        val cacheKey = "${config.id}_${currentProvider.value}_${currentModel.value}"
        agents.remove(cacheKey)

        // Clear current UI messages
        messages.clear()

        // Load history from DB
        chatHistoryDB?.let { db ->
            val history = db.getMessages(agentId)
            if (history.isNotEmpty()) {
                history.forEach { msg ->
                    val tools = try {
                        val arr = org.json.JSONArray(msg.toolsUsed)
                        (0 until arr.length()).map { arr.getString(it) }
                    } catch (_: Exception) { emptyList() }

                    messages.add(ChatUiMessage(
                        text = msg.text,
                        isUser = msg.isUser,
                        agentIcon = msg.agentIcon,
                        isError = msg.isError,
                        toolsUsed = tools
                    ))
                }
            } else {
                // First time — show welcome
                val skillCount = skills.size
                val memCount = memoryDB?.getMemoryCount() ?: 0
                val memInfo = if (memCount > 0) "\n**$memCount memorias** almacenadas" else ""
                val welcome = ChatUiMessage(
                    text = "¡Hola! Soy ${config.name} ${config.icon}\n\n" +
                            "Tengo **$skillCount skills nativos** listos.\n" +
                            "Proveedor: **${getProviderDisplayName()}**" +
                            memInfo + "\n\n¿En qué te puedo ayudar?",
                    isUser = false, agentIcon = config.icon
                )
                messages.add(welcome)
                db.saveMessage(agentId, welcome.text, false, config.icon)
            }
        }

        currentScreen.value = "chat"
    }

    /**
     * Go back to conversations list.
     */
    fun navigateToConversations() {
        currentScreen.value = "conversations"
    }

    // Pending prompt text — prefilled in the chat input but NOT auto-sent.
    val pendingPrompt = mutableStateOf("")

    /**
     * Open a chat and PREFILL the prompt in the text field (editable, NOT auto-sent).
     * The user can review/modify and tap Send themselves.
     */
    fun prefillAgentChat(agentId: String, prompt: String) {
        openAgentChat(agentId)
        pendingPrompt.value = prompt
    }

    /**
     * Open a chat and immediately send a message (for quick actions / shortcuts).
     */
    fun openAgentChatWithPrompt(agentId: String, prompt: String) {
        openAgentChat(agentId)
        // Auto-send after a small delay to let UI settle
        mainHandler.postDelayed({ sendMessage(prompt) }, 300)
    }

    /**
     * Reload custom agents from database.
     */
    fun reloadCustomAgents(db: com.beemovil.agent.CustomAgentDB) {
        availableAgents.clear()
        availableAgents.addAll(DefaultAgents.ALL)
        val customs = db.getAllAgents()
        customs.forEach { custom ->
            // If model is blank, inherit global model
            val resolved = if (custom.model.isBlank()) {
                custom.copy(model = currentModel.value)
            } else custom
            availableAgents.add(resolved)
        }
    }

    private fun getOrCreateAgent(config: AgentConfig): BeeAgent {
        val key = "${config.id}_${currentProvider.value}_${currentModel.value}"
        return agents.getOrPut(key) {
            val apiKey = apiKeys[currentProvider.value] ?: ""
            try {
                val provider = LlmFactory.createProvider(
                    providerType = currentProvider.value,
                    apiKey = apiKey,
                    model = currentModel.value
                )
                Log.d(TAG, "Created agent: ${config.id} on ${currentProvider.value}/${currentModel.value}")
                BeeAgent(config, provider, skills, memoryDB, appContext = appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create provider: ${e.message}")
                // Throw with user-friendly message
                throw Exception(when {
                    e.message?.contains("no descargado") == true ->
                        "Modelo local no descargado. Ve a Settings para descargarlo."
                    e.message?.contains("appContext") == true ->
                        "Error de inicializacion. Reinicia la app."
                    else -> "Error de proveedor: ${e.message}"
                })
            }
        }
    }

    fun switchAgent(config: AgentConfig) {
        openAgentChat(config.id)
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isLoading.value) return

        val config = currentAgentConfig.value
        messages.add(ChatUiMessage(text = text, isUser = true))

        // Save to DB
        chatHistoryDB?.saveMessage(config.id, text, true, config.icon)

        val apiKey = apiKeys[currentProvider.value] ?: ""
        if (apiKey.isBlank() && currentProvider.value != "local") {
            val errorMsg = "Configura tu API key para **${getProviderDisplayName()}** primero"
            messages.add(ChatUiMessage(text = errorMsg, isUser = false, agentIcon = "ERR", isError = true))
            chatHistoryDB?.saveMessage(config.id, errorMsg, false, "ERR", true)
            return
        }

        isLoading.value = true

        Thread {
            var responseText: String
            var isError = false
            var toolNames = emptyList<String>()
            var detectedFiles = emptyList<String>()

            try {
                // Show loading message for local models (engine.initialize can take 15-60s)
                if (currentProvider.value == "local") {
                    mainHandler.post {
                        if (messages.lastOrNull()?.text?.startsWith("Cargando") != true) {
                            messages.add(ChatUiMessage(
                                text = "Cargando modelo local en memoria (~15-30s la primera vez)...",
                                isUser = false, agentIcon = "WAIT"
                            ))
                        }
                    }
                }

                val agent = getOrCreateAgent(config)

                // Remove loading message if we added one
                if (currentProvider.value == "local") {
                    mainHandler.post {
                        messages.removeAll { it.text.startsWith("Cargando modelo") }
                    }
                }

                val response = agent.chat(text)
                responseText = response.text
                isError = response.isError
                toolNames = response.toolExecutions.map { it.skillName }

                // PRIMARY: Extract file paths from tool execution results
                val toolFiles = mutableSetOf<String>()
                response.toolExecutions.forEach { exec ->
                    val result = exec.result
                    // Check common keys where skills return file paths
                    arrayOf("path", "file_path", "filepath", "filePath", "output_path", "local_path", "image_path").forEach { key ->
                        if (result.has(key)) {
                            toolFiles.add(result.getString(key))
                        }
                    }
                }
                // FALLBACK: Also search text for paths
                toolFiles.addAll(extractFilePaths(responseText))
                detectedFiles = toolFiles.toList()

            } catch (e: Throwable) {
                Log.e(TAG, "Chat error: ${e.message}", e)
                responseText = "Error: ${e.javaClass.simpleName}: ${e.message}"
                isError = true
            }

            // Save response to DB
            chatHistoryDB?.saveMessage(config.id, responseText, false, config.icon, isError, toolNames)

            mainHandler.post {
                isLoading.value = false
                messages.add(ChatUiMessage(
                    text = responseText,
                    isUser = false, agentIcon = config.icon,
                    isError = isError, toolsUsed = toolNames,
                    filePaths = detectedFiles
                ))
            }
        }.start()
    }

    /**
     * Extract file paths from agent response text (fallback).
     */
    private fun extractFilePaths(text: String): List<String> {
        val pattern = Regex("""(/storage/emulated/0/[^\s\n"')`,]+\.\w{2,5})""")
        val files = mutableSetOf<String>()
        pattern.findAll(text).forEach { match ->
            files.add(match.groupValues[1])
        }
        return files.toList()
    }

    /**
     * Send a message with file attachments.
     * Attachments are processed by AttachmentManager and their context is injected
     * into the agent's system prompt so the LLM can reference them across the conversation.
     *
     * For images: sent as vision (base64) in first message, then as text description after
     * For documents: full extracted text injected into system prompt
     */
    fun sendMessageWithAttachments(text: String, pendingAttachments: List<AttachedFile>) {
        if (isLoading.value) return

        // Add new attachments to conversation-level context
        pendingAttachments.forEach { att ->
            if (conversationAttachments.none { it.id == att.id }) {
                conversationAttachments.add(att)
            }
        }

        val config = currentAgentConfig.value
        val attachNames = pendingAttachments.map { it.name }
        val imageAttachments = pendingAttachments.filter { it.type == FileType.IMAGE && it.base64 != null }
        val textAttachments = pendingAttachments.filter { it.type != FileType.IMAGE || it.base64 == null }

        // Build display text with attachment info
        val userDisplayText = buildString {
            if (attachNames.isNotEmpty()) {
                append("📎 ")
                append(attachNames.joinToString(", "))
                append("\n")
            }
            append(text.ifBlank { "Analiza los archivos adjuntos" })
        }

        messages.add(ChatUiMessage(
            text = userDisplayText, isUser = true,
            attachmentNames = attachNames,
            filePaths = pendingAttachments.mapNotNull { it.filePath }
        ))
        chatHistoryDB?.saveMessage(config.id, userDisplayText, true, config.icon)

        val apiKey = apiKeys[currentProvider.value] ?: ""
        if (apiKey.isBlank() && currentProvider.value != "local") {
            val errorMsg = "Configura tu API key para **${getProviderDisplayName()}** primero"
            messages.add(ChatUiMessage(text = errorMsg, isUser = false, agentIcon = "ERR", isError = true))
            return
        }

        isLoading.value = true

        Thread {
            var responseText: String
            var isError = false
            var toolNames = emptyList<String>()
            var detectedFiles = emptyList<String>()

            try {
                val agent = getOrCreateAgent(config)

                // Inject ALL conversation attachments into system prompt
                val attachmentContext = buildAttachmentContext()
                if (attachmentContext.isNotBlank()) {
                    agent.injectAttachmentContext(attachmentContext)
                }

                // If there are images, use chatWithImage for the first one
                if (imageAttachments.isNotEmpty()) {
                    val imgBase64 = imageAttachments.first().base64!!
                    val fullPrompt = buildString {
                        append(text.ifBlank { "Analiza los archivos adjuntos" })
                        textAttachments.forEach { att ->
                            append("\n\n${att.contextChunk}")
                        }
                    }
                    val response = agent.chatWithImage(fullPrompt, imgBase64)
                    responseText = response.text
                    isError = response.isError
                    toolNames = response.toolExecutions.map { it.skillName }
                } else {
                    // Text-only attachments: include context in the message itself
                    val fullPrompt = buildString {
                        append(text.ifBlank { "Analiza los archivos adjuntos" })
                        textAttachments.forEach { att ->
                            append("\n\n${att.contextChunk}")
                        }
                    }
                    val response = agent.chat(fullPrompt)
                    responseText = response.text
                    isError = response.isError
                    toolNames = response.toolExecutions.map { it.skillName }

                    val toolFiles = mutableSetOf<String>()
                    response.toolExecutions.forEach { exec ->
                        arrayOf("path", "file_path", "filepath", "filePath", "output_path", "local_path", "image_path").forEach { key ->
                            if (exec.result.has(key)) toolFiles.add(exec.result.getString(key))
                        }
                    }
                    toolFiles.addAll(extractFilePaths(responseText))
                    detectedFiles = toolFiles.toList()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Chat+attachment error: ${e.message}", e)
                responseText = "Error: ${e.javaClass.simpleName}: ${e.message}"
                isError = true
            }

            chatHistoryDB?.saveMessage(config.id, responseText, false, config.icon, isError, toolNames)

            mainHandler.post {
                isLoading.value = false
                messages.add(ChatUiMessage(
                    text = responseText,
                    isUser = false, agentIcon = config.icon,
                    isError = isError, toolsUsed = toolNames,
                    filePaths = detectedFiles
                ))
            }
        }.start()
    }

    /**
     * Build attachment context string for system prompt injection.
     * All conversation attachments are summarized so the agent always knows what files are available.
     */
    private fun buildAttachmentContext(): String {
        if (conversationAttachments.isEmpty()) return ""
        return buildString {
            appendLine("\n## Archivos adjuntos en esta conversacion:")
            conversationAttachments.forEachIndexed { i, att ->
                appendLine("${i + 1}. [${att.type.name}] ${att.name} (${att.preview})")
                // For non-image files, include full content in context
                if (att.type != FileType.IMAGE && att.contextChunk.length < 50_000) {
                    appendLine(att.contextChunk)
                }
            }
        }
    }

    /**
     * Analyze an image using the current agent's vision capability.
     * Falls back to Ollama Cloud vision model if the current provider doesn't support images.
     * Images are now part of the conversation history for multi-turn context.
     */
    fun analyzeImageInChat(context: Context, imagePath: String, prompt: String = "Describe detalladamente lo que ves en esta imagen.") {
        val config = currentAgentConfig.value
        isLoading.value = true

        // Add user message with image
        messages.add(ChatUiMessage(
            text = prompt,
            isUser = true,
            filePaths = listOf(imagePath)
        ))
        chatHistoryDB?.saveMessage(config.id, "[IMG] $prompt", true, config.icon)

        Thread {
            try {
                // Read and encode image
                val bitmap = BitmapFactory.decodeFile(imagePath) ?: throw Exception("No se pudo leer la imagen")
                if (bitmap.width == 0 || bitmap.height == 0) throw Exception("Imagen corrupta o vacia")
                val scale = minOf(800f / bitmap.width, 800f / bitmap.height, 1f)
                val resized = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                val baos = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                // Try to use the current agent (preserves conversation context)
                val agent = getOrCreateAgent(config)
                val response = agent.chatWithImage(prompt, base64)

                mainHandler.post {
                    isLoading.value = false
                    val files = response.toolExecutions.mapNotNull { exec ->
                        arrayOf("path", "file_path", "filepath").firstNotNullOfOrNull { key ->
                            if (exec.result.has(key)) exec.result.getString(key) else null
                        }
                    }
                    messages.add(ChatUiMessage(
                        text = response.text,
                        isUser = false, agentIcon = config.icon,
                        isError = response.isError,
                        toolsUsed = response.toolExecutions.map { it.skillName },
                        filePaths = files
                    ))
                    chatHistoryDB?.saveMessage(config.id, response.text, false, config.icon,
                        response.isError, response.toolExecutions.map { it.skillName })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vision error: ${e.message}", e)

                // Fallback: try Ollama Cloud vision model if current provider failed
                if (currentProvider.value != "ollama") {
                    try {
                        val ollamaKey = SecurePrefs.get(context).getString("ollama_api_key", "") ?: ""
                        if (ollamaKey.isNotBlank()) {
                            val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                            val visionModel = prefs.getString("vision_model", "gemma4:31b-cloud") ?: "gemma4:31b-cloud"
                            val bitmap = BitmapFactory.decodeFile(imagePath)
                            val scale = minOf(800f / bitmap.width, 800f / bitmap.height, 1f)
                            val resized = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                            val baos = ByteArrayOutputStream()
                            resized.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                            val visionProvider = OllamaCloudProvider(apiKey = ollamaKey, model = visionModel)
                            val visionMessages = listOf(ChatMessage(role = "user", content = prompt, images = listOf(base64)))
                            val response = visionProvider.complete(visionMessages, emptyList())

                            mainHandler.post {
                                isLoading.value = false
                                messages.add(ChatUiMessage(
                                    text = response.text ?: "Sin respuesta",
                                    isUser = false, agentIcon = "VIS",
                                    toolsUsed = listOf("vision:$visionModel")
                                ))
                            }
                            return@Thread
                        }
                    } catch (fallbackErr: Exception) {
                        Log.e(TAG, "Vision fallback also failed: ${fallbackErr.message}")
                    }
                }

                mainHandler.post {
                    isLoading.value = false
                    messages.add(ChatUiMessage(
                        text = "Error de vision: ${e.message}",
                        isUser = false, agentIcon = config.icon, isError = true
                    ))
                }
            }
        }.start()
    }

    /**
     * Synchronous message send for voice chat — call from background thread only.
     * Returns the agent's response text directly.
     */
    fun sendMessageSync(text: String): String {
        if (text.isBlank()) return "No text"

        val config = currentAgentConfig.value
        val apiKey = apiKeys[currentProvider.value] ?: ""
        if (apiKey.isBlank() && currentProvider.value != "local") return "Configura tu API key primero"

        // Add to UI on main thread
        mainHandler.post {
            messages.add(ChatUiMessage(text = text, isUser = true))
            chatHistoryDB?.saveMessage(config.id, text, true, config.icon)
        }

        return try {
            val agent = getOrCreateAgent(config)
            val response = agent.chat(text)

            mainHandler.post {
                chatHistoryDB?.saveMessage(config.id, response.text, false, config.icon,
                    response.isError, response.toolExecutions.map { it.skillName })
                messages.add(ChatUiMessage(
                    text = response.text, isUser = false, agentIcon = config.icon,
                    isError = response.isError,
                    toolsUsed = response.toolExecutions.map { it.skillName }
                ))
            }
            response.text
        } catch (e: Throwable) {
            val err = "Error: ${e.message}"
            mainHandler.post {
                messages.add(ChatUiMessage(text = err, isUser = false, agentIcon = "ERR", isError = true))
            }
            err
        }
    }

    /**
     * Send message with an image (vision). Image is base64 encoded.
     */
    fun sendMessageWithImage(text: String, imageBase64: String) {
        if (text.isBlank() || isLoading.value) return

        val config = currentAgentConfig.value
        messages.add(ChatUiMessage(text = "[IMG] $text", isUser = true))
        chatHistoryDB?.saveMessage(config.id, "[IMG] $text", true, config.icon)

        val apiKey = apiKeys[currentProvider.value] ?: ""
        if (apiKey.isBlank()) {
            val errorMsg = "Configura tu API key primero"
            messages.add(ChatUiMessage(text = errorMsg, isUser = false, agentIcon = "ERR", isError = true))
            return
        }

        isLoading.value = true

        Thread {
            var responseText: String
            var isError = false
            var toolNames = emptyList<String>()

            try {
                val agent = getOrCreateAgent(config)
                val response = agent.chatWithImage(text, imageBase64)
                responseText = response.text
                isError = response.isError
                toolNames = response.toolExecutions.map { it.skillName }
            } catch (e: Throwable) {
                Log.e(TAG, "Vision error: ${e.message}", e)
                responseText = "Error: ${e.javaClass.simpleName}: ${e.message}"
                isError = true
            }

            chatHistoryDB?.saveMessage(config.id, responseText, false, config.icon, isError, toolNames)

            mainHandler.post {
                isLoading.value = false
                messages.add(ChatUiMessage(
                    text = responseText,
                    isUser = false, agentIcon = config.icon,
                    isError = isError, toolsUsed = toolNames
                ))
            }
        }.start()
    }

    fun clearChat() {
        val config = currentAgentConfig.value
        agents.values.forEach { try { it.clearMemory() } catch (_: Throwable) {} }
        messages.clear()
        chatHistoryDB?.clearAgent(config.id)
        val memCount = memoryDB?.getMemoryCount() ?: 0
        val clearMsg = ChatUiMessage(
            text = "Chat limpiado\n$memCount memorias persisten",
            isUser = false, agentIcon = config.icon
        )
        messages.add(clearMsg)
        chatHistoryDB?.saveMessage(config.id, clearMsg.text, false, config.icon)
    }
}
