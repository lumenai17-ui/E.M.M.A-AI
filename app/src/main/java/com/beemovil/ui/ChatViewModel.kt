package com.beemovil.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.content.Context
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.voice.DeepgramVoiceManager
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatUiMessage(
    val text: String,
    val isUser: Boolean,
    val agentIcon: String = "🧠",
    val isError: Boolean = false,
    val isLoading: Boolean = false,
    val toolsUsed: List<String> = emptyList(),
    val filePaths: List<String> = emptyList(),
    val attachmentNames: List<String> = emptyList(),
    val attachmentMimeTypes: List<String> = emptyList()
)

data class AgentConfigStub(
    val id: String = "EMMA",
    val name: String = "E.M.M.A. Ai",
    val icon: String = "🧠",
    val description: String = "Sistema central guiado por Koog",
    val systemPrompt: String = "",
    val model: String = "koog-engine"
)

data class DashboardMatrixState(
    val greetingName: String = "Arquitecto",
    val isMatrixLoading: Boolean = true,
    val capsuleLocation: String = "Cargando radar...",
    val capsuleWeather: String = "Calculando biomas...",
    val insightHeaderTop: String = "Midiendo sistema...",
    val insightHeaderBottom: String = "Calibrando biomas...",
    val insightText: String = "Iniciando cognición de entorno. Escaneando la red y calibrando fotones. Dame un milisegundo..."
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = EmmaEngine(application)
    // Motor aislado para el Dashboard — nunca contamina el contexto del chat del usuario
    private val dashboardEngine = EmmaEngine(application)
    private val voiceManager = DeepgramVoiceManager(application)
    private val envScanner = com.beemovil.core.EnvironmentScanner(application)

    val dynamicDashboardState = mutableStateOf(DashboardMatrixState())

    init {
        // Inicializamos los oídos y la boca local
        voiceManager.initialize()
        
        viewModelScope.launch {
            engine.initialize()
            dashboardEngine.initialize() // Engine aislado para telemetría
            dashboardInsight.value = "Koog Engine Cargado: Ollama conectando..."
            
            // Cargar Historial al Iniciar del Hilo Principal
            val prefs = application.getSharedPreferences("beemovil_session", Context.MODE_PRIVATE)
            val lastThread = prefs.getString("last_active_thread", "main") ?: "main"
            
            // UI-09: Validar que el thread existe antes de intentar cargarlo
            val allExistingThreads = chatHistoryDB.chatHistoryDao().getAllThreads()
            val validThread = if (allExistingThreads.any { it.threadId == lastThread }) lastThread else "main"
            activeThreadId.value = validThread
            
            val history = chatHistoryDB.chatHistoryDao().getHistory(validThread)
            
            val allTh = chatHistoryDB.chatHistoryDao().getAllThreads()
            val th = allTh.find { it.threadId == lastThread }
            if (th != null) {
                activeAgentName.value = th.title.replace("Chat con ", "")
            }
            
            // 1. Restaurar Visor Visual
            history.forEach {
                if (it.role == "user" || it.role == "assistant") {
                    var filePaths = emptyList<String>()
                    var fileNames = emptyList<String>()
                    var mimeTypes = emptyList<String>()
                    if (!it.metadataJson.isNullOrBlank()) {
                        try {
                            val json = org.json.JSONObject(it.metadataJson)
                            if (json.has("file_path")) {
                                filePaths = listOf(json.getString("file_path"))
                                fileNames = listOf(json.optString("file_name", "Adjunto"))
                                mimeTypes = listOf(json.optString("mime_type", ""))
                            }
                        } catch (e: Exception) {}
                    }
                    messages.add(ChatUiMessage(text = it.content, isUser = it.role == "user", filePaths = filePaths, attachmentNames = fileNames, attachmentMimeTypes = mimeTypes))
                }
            }
            
            // 2. Re-hidratar Corteza Neuronal (God Mode Persistencia)
            engine.loadPersistedContext(history)

            // Garantizar que el thread 'main' exista en Room
            val allExisting = chatHistoryDB.chatHistoryDao().getAllThreads()
            if (allExisting.none { it.threadId == "main" }) {
                chatHistoryDB.chatHistoryDao().createThread(com.beemovil.database.ChatThreadEntity(
                    threadId = "main",
                    title = "Chat con E.M.M.A.",
                    type = "SINGLE",
                    lastUpdateMillis = System.currentTimeMillis()
                ))
            }

            // Iniciar Motor de Novedad Dinámica
            refreshLiveDashboard()
            
            // Cargar datos del Enjambre
            refreshSwarmData()
        }
    }
    
    val allAgents = mutableStateListOf<com.beemovil.database.AgentConfigEntity>()
    val allThreads = mutableStateListOf<com.beemovil.database.ChatThreadEntity>()

    fun refreshSwarmData() {
        viewModelScope.launch {
            var dbAgents = chatHistoryDB.chatHistoryDao().getAllAgents()
            if (dbAgents.isEmpty()) {
                val defaultAgents = listOf(
                    com.beemovil.database.AgentConfigEntity("emma_chat", "E.M.M.A. Chat", "🧠", "Eres E.M.M.A. Coordinadora Central.", "openrouter:openai/gpt-4o-mini"),
                    com.beemovil.database.AgentConfigEntity("live_vision", "Live Vision", "👁️", "Eres un analizador de contexto visual en tiempo real.", "openrouter:openai/gpt-4-vision-preview"),
                    com.beemovil.database.AgentConfigEntity("deep_voice", "Deep Voice", "🎙️", "Especialista en flujos de voz y síntesis neuronal.", "koog-engine")
                )
                defaultAgents.forEach { chatHistoryDB.chatHistoryDao().insertAgent(it) }
                dbAgents = chatHistoryDB.chatHistoryDao().getAllAgents()
            }
            
            allAgents.clear()
            allAgents.addAll(dbAgents)
            
            allThreads.clear()
            allThreads.addAll(chatHistoryDB.chatHistoryDao().getAllThreads())
        }
    }

    fun forgeAgent(name: String, icon: String, prompt: String, model: String) {
        viewModelScope.launch {
            val validIcon = if (icon.isBlank()) "🤖" else icon
            val newAgent = com.beemovil.database.AgentConfigEntity(
                agentId = "agent_${System.currentTimeMillis()}",
                name = name,
                icon = validIcon,
                systemPrompt = prompt,
                fallbackModel = model
            )
            chatHistoryDB.chatHistoryDao().insertAgent(newAgent)
            
            // Refrescar para la pantalla
            refreshSwarmData()
        }
    }

    fun updateAgentConfig(agent: com.beemovil.database.AgentConfigEntity) {
        viewModelScope.launch {
            chatHistoryDB.chatHistoryDao().insertAgent(agent)
            activeAgentConfig.value = agent
            activeAgentName.value = agent.name
            activeSystemPrompt.value = agent.systemPrompt
            // UI-06 fix: parse fallbackModel correctamente igual que openAgentChat
            if (agent.fallbackModel.startsWith("hermes-a2a")) {
                currentProvider.value = agent.fallbackModel
                currentModel.value = ""
            } else {
                val parts = agent.fallbackModel.split(":", limit = 2)
                currentProvider.value = if (parts.isNotEmpty()) parts[0] else "openrouter"
                currentModel.value = if (parts.size > 1) parts[1] else agent.fallbackModel
            }
            refreshSwarmData()
        }
    }

    val messages = mutableStateListOf<ChatUiMessage>()
    val searchResults = mutableStateListOf<ChatUiMessage>()
    val isSearchMode = mutableStateOf(false)
    val swarmInsight = mutableStateOf("")
    val searchQuery = mutableStateOf("")

    fun refreshLiveDashboard() {
        dynamicDashboardState.value = dynamicDashboardState.value.copy(
            isMatrixLoading = true,
            insightText = "Generando Inferencia Neural del Entorno..."
        )
        viewModelScope.launch {
            // Leer de sensores fisicos en background
            val battery = envScanner.getBatteryLevel()
            val net = envScanner.getNetworkStatus()
            val batteryStatus = if (battery > 20) "🔋 Energía $battery%" else "⚠️ Batería Crítica $battery%"
            val netStatus = if(net.contains("WIFI", true)) "📡 Wi-Fi" else "📶 Datos Conectados"
            val netBatCombined = "$batteryStatus   |   $netStatus"
            
            // Intentar obtener GPS real
            val realLocation = envScanner.getCurrentLocation()
            val lat = realLocation?.first ?: 19.4326
            val lon = realLocation?.second ?: -99.1332
            
            val locationRaw = envScanner.getSemanticLocation(lat, lon)
            val weatherRaw = envScanner.fetchWeather(lat, lon)

            // Insight Headers Libres de Doble Emoji
            val insightTop = "$batteryStatus   |   $netStatus"
            val insightBottom = "$locationRaw   |   $weatherRaw" // locationRaw/weatherRaw ya traen emoji de DeviceScanner

            // Capa 3: Reloj Local Temporal
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE HH:mm", java.util.Locale("es", "ES"))
            val sysTime = java.time.LocalDateTime.now().format(formatter)

            // Capa 4: Memoria de Hilo Principal
            val historyLog = chatHistoryDB.chatHistoryDao().getHistory("main").takeLast(4)
            val recentMemory = historyLog.joinToString("; ") { "${if(it.role=="user") "Usuario" else "Emma"} dijo: ${it.content.take(60)}" }.ifBlank { "Sin contexto reciente." }

            // Extracción de prefs y Ghost Memory
            val prefs = getApplication<Application>().getSharedPreferences("beemovil", Context.MODE_PRIVATE)
            val validName = prefs.getString("user_display_name", "Arquitecto").takeIf { !it.isNullOrBlank() } ?: "Arquitecto"
            
            val ghostMemory = prefs.getString("GHOST_MEMORY", "") ?: ""
            if (ghostMemory.isNotBlank()) {
                prefs.edit().remove("GHOST_MEMORY").apply()
            }
            val ghostInjection = if (ghostMemory.isNotBlank()) " | SUCESO FANTASMA: $ghostMemory" else ""

            dynamicDashboardState.value = dynamicDashboardState.value.copy(
                greetingName = validName,
                capsuleLocation = locationRaw, // Radar lo agarra directo
                capsuleWeather = weatherRaw,   // Termómetro lo agarra directo
                insightHeaderTop = insightTop,
                insightHeaderBottom = insightBottom
            )

            // Petición silenciosa a la IA (Prompt de Función Estricto para evadir alucinaciones charlatanas)
            val systemMatrixPrompt = "INSTRUCCIÓN DE SISTEMA: No eres un asistente. No saludes ni expliques. Genera el reporte. Datos de telemetría recibidos: Bateria $battery%, Red $net, Hora $sysTime, Clima $weatherRaw, Sucesos recientes: $recentMemory$ghostInjection. FORMATO OBLIGATORIO (Max 2 líneas): Línea 1: Insight de entorno. Línea 2: '👉 Acción sugerida:' seguido de instrucción."
            
            try {
                var aiInsight = dashboardEngine.processUserMessage(systemMatrixPrompt).replace("SISTEMA CORE:", "").trim()
                if (aiInsight.length > 250) {
                    aiInsight = aiInsight.take(250) + "..."
                }
                dynamicDashboardState.value = dynamicDashboardState.value.copy(
                    isMatrixLoading = false,
                    insightText = aiInsight
                )
            } catch (e: Exception) {
                dynamicDashboardState.value = dynamicDashboardState.value.copy(
                    isMatrixLoading = false,
                    insightText = "Modo Seguro: Matrix desconectada. Dispositivos estabilizados."
                )
            }
        }
    }

    fun performSearch(query: String) {
        searchQuery.value = query
        if (query.isBlank()) {
            searchResults.clear()
            return
        }
        viewModelScope.launch {
            val results = chatHistoryDB.chatHistoryDao().searchHistory("default_session", query)
            searchResults.clear()
            results.forEach {
                searchResults.add(ChatUiMessage(text = it.content, isUser = it.role == "user"))
            }
        }
    }

    val isLoading = mutableStateOf(false)
    val currentAgentConfig = mutableStateOf(AgentConfigStub())
    val availableAgents = mutableStateListOf<AgentConfigStub>().apply { add(AgentConfigStub()) }

    // Navigation and status
    // UI-08: Boot siempre en Dashboard. Share Intent lo sobreescribe en MainActivity.
    val currentScreen = mutableStateOf("dashboard")
    val browserUrl = mutableStateOf("https://www.google.com")
    val showBrowser = mutableStateOf(false)
    val browserAgentStatusText = mutableStateOf("Desconectado")
    
    val telegramBotStatus = mutableStateOf("offline")
    val telegramBotName = mutableStateOf("")
    val telegramBotMessages = mutableStateOf(0)
    
    // UI states
    val dashboardInsight = mutableStateOf("Sistema base reiniciado. Esperando motor Koog...")
    val dashboardInsightLoading = mutableStateOf(false)
    // S-01/S-02/S-04: Provider y modelo restaurados de SharedPrefs al inicializar
    private val _bootPrefs = getApplication<Application>().getSharedPreferences("beemovil", Context.MODE_PRIVATE)
    val currentProvider = mutableStateOf(_bootPrefs.getString("selected_provider", "openrouter") ?: "openrouter")
    
    val pendingPrompt = mutableStateOf("")

    val currentModel = mutableStateOf(_bootPrefs.getString("selected_model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini")

    val activeThreadId = mutableStateOf("main")
    val activeAgentId = mutableStateOf("emma")
    val activeAgentName = mutableStateOf("E.M.M.A.")
    val activeSystemPrompt = mutableStateOf("")
    val activeAgentConfig = mutableStateOf<com.beemovil.database.AgentConfigEntity?>(null)

    // --- HERMES TUNNEL Y A2A ROUTING ---
    val isHermesConnected = mutableStateOf(false)
    val showHermesDialog = mutableStateOf(false)

    fun connectHermes(url: String, token: String) {
        val app = getApplication<Application>()
        val intent = android.content.Intent(app, com.beemovil.tunnel.TunnelService::class.java).apply {
            action = "START"
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
        com.beemovil.tunnel.HermesTunnelManager.startTunnel(app, url, token) { connected ->
            isHermesConnected.value = connected
        }
        showHermesDialog.value = false
    }

    fun disconnectHermes() {
        val app = getApplication<Application>()
        val intent = android.content.Intent(app, com.beemovil.tunnel.TunnelService::class.java).apply {
            action = "STOP"
        }
        app.startService(intent)
        isHermesConnected.value = false
    }


    fun openAgentChat(agent: com.beemovil.database.AgentConfigEntity) {
        viewModelScope.launch {
            activeAgentId.value = agent.agentId
            activeAgentName.value = agent.name
            activeSystemPrompt.value = agent.systemPrompt
            activeAgentConfig.value = agent
            
            val newThreadId = "thread_${agent.agentId}_primary"
            activeThreadId.value = newThreadId
            
            val appPrefs = getApplication<Application>().getSharedPreferences("beemovil_session", Context.MODE_PRIVATE)
            appPrefs.edit().putString("last_active_thread", newThreadId).apply()
            
            val threads = chatHistoryDB.chatHistoryDao().getAllThreads()
            if (threads.none { it.threadId == newThreadId }) {
                chatHistoryDB.chatHistoryDao().createThread(com.beemovil.database.ChatThreadEntity(
                    threadId = newThreadId,
                    title = "Chat con ${agent.name}",
                    type = "SINGLE",
                    lastUpdateMillis = System.currentTimeMillis()
                ))
                refreshSwarmData()
            }
            
            val pastMessages = chatHistoryDB.chatHistoryDao().getHistory(newThreadId)
            messages.clear()
            pastMessages.forEach { msg ->
                var filePaths = emptyList<String>()
                var fileNames = emptyList<String>()
                var mimeTypes = emptyList<String>()
                if (!msg.metadataJson.isNullOrBlank()) {
                    try {
                        val json = org.json.JSONObject(msg.metadataJson)
                        if (json.has("file_path")) {
                            filePaths = listOf(json.getString("file_path"))
                            fileNames = listOf(json.optString("file_name", "Adjunto"))
                            mimeTypes = listOf(json.optString("mime_type", ""))
                        }
                    } catch (e: Exception) {}
                }
                messages.add(ChatUiMessage(text = msg.content, isUser = msg.role == "user", filePaths = filePaths, attachmentNames = fileNames, attachmentMimeTypes = mimeTypes))
            }
            
            engine.loadPersistedContext(pastMessages, agent.systemPrompt)
            
            // Parse fallbackModel correctamente (formato "provider:modelId" o "hermes-a2a|url|token")
            if (agent.fallbackModel.startsWith("hermes-a2a")) {
                currentProvider.value = agent.fallbackModel  // pass through completo para hermes
                currentModel.value = ""
            } else {
                val parts = agent.fallbackModel.split(":", limit = 2)
                currentProvider.value = if (parts.isNotEmpty()) parts[0] else "openrouter"
                currentModel.value = if (parts.size > 1) parts[1] else agent.fallbackModel
            }
            currentScreen.value = "chat"
        }
    }

    fun openThread(thread: com.beemovil.database.ChatThreadEntity) {
        viewModelScope.launch {
            activeThreadId.value = thread.threadId
            activeAgentName.value = thread.title.replace("Chat con ", "")
            
            // Derive agent from thread
            val prefix = "thread_"
            val agentSuffix = "_primary"
            if (thread.threadId.startsWith(prefix) && thread.threadId.endsWith(agentSuffix)) {
                val derivedAgentId = thread.threadId.substring(prefix.length, thread.threadId.length - agentSuffix.length)
                val agents = chatHistoryDB.chatHistoryDao().getAllAgents()
                activeAgentConfig.value = agents.find { it.agentId == derivedAgentId }
            } else {
                activeAgentConfig.value = null
            }

            val appPrefs = getApplication<Application>().getSharedPreferences("beemovil_session", Context.MODE_PRIVATE)
            appPrefs.edit().putString("last_active_thread", thread.threadId).apply()
            
            val pastMessages = chatHistoryDB.chatHistoryDao().getHistory(thread.threadId)
            messages.clear()
            pastMessages.forEach { msg ->
                var filePaths = emptyList<String>()
                var fileNames = emptyList<String>()
                var mimeTypes = emptyList<String>()
                if (!msg.metadataJson.isNullOrBlank()) {
                    try {
                        val json = org.json.JSONObject(msg.metadataJson)
                        if (json.has("file_path")) {
                            filePaths = listOf(json.getString("file_path"))
                            fileNames = listOf(json.optString("file_name", "Adjunto"))
                            mimeTypes = listOf(json.optString("mime_type", ""))
                        }
                    } catch (e: Exception) {}
                }
                messages.add(ChatUiMessage(text = msg.content, isUser = msg.role == "user", filePaths = filePaths, attachmentNames = fileNames, attachmentMimeTypes = mimeTypes))
            }
            
            engine.loadPersistedContext(pastMessages)
            currentScreen.value = "chat"
        }
    }
    // ------------------------------------

    fun navigateToThread(threadId: String) {
        if (threadId == "main") {
            currentScreen.value = "chat"
        } else {
            viewModelScope.launch {
                val thread = chatHistoryDB.chatHistoryDao().getAllThreads()
                    .find { it.threadId == threadId }
                if (thread != null) openThread(thread)
                else currentScreen.value = "chat"
            }
        }
    }

    fun switchProvider(preset: String, model: String) {
        currentProvider.value = preset
        currentModel.value = model
        dashboardInsight.value = "Motor conmutador a $preset: $model cargado."
    }

    // S-03: Actualizar key en SecurePrefs y refrescar el engine context
    fun updateApiKey(provider: String, key: String) {
        val securePrefs = com.beemovil.security.SecurePrefs.get(getApplication())
        val prefKey = when(provider) {
            "openrouter" -> "openrouter_api_key"
            "ollama" -> "ollama_api_key"
            else -> "openrouter_api_key"
        }
        securePrefs.edit().putString(prefKey, key).apply()
    }

    val chatHistoryDB = com.beemovil.database.ChatHistoryDB.getDatabase(application)
    
    fun refreshHistoryCount() {
        // Enjambre no usa raw count (Reservado para Swarm Dashboard)
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            chatHistoryDB.chatHistoryDao().clearAll()
            // UI-14: Limpiar también threads (mantener solo 'main') 
            chatHistoryDB.chatHistoryDao().clearAllThreads()
            messages.clear()
            allThreads.clear()
            engine.clearMemoryAndHistory()
            activeThreadId.value = "main"
            activeAgentName.value = "E.M.M.A. Ai"
            activeAgentConfig.value = null
            // Re-crear thread main
            chatHistoryDB.chatHistoryDao().createThread(com.beemovil.database.ChatThreadEntity(
                threadId = "main",
                title = "Chat con E.M.M.A.",
                type = "SINGLE",
                lastUpdateMillis = System.currentTimeMillis()
            ))
            refreshSwarmData()
        }
    }

    // Hardware states
    val isRecording = mutableStateOf(false)
    val isMuted = mutableStateOf(false)

    // STUBS de Integración Fuerte (A rellenar en Fase 3)
    /**
     * FILE-02: Copia un content:// URI al almacenamiento privado de la app
     * para que persista después de reiniciar.
     */
    private fun copyToLocalStorage(context: android.content.Context, uri: android.net.Uri): Pair<String, String?> {
        val attachDir = java.io.File(context.filesDir, "attachments")
        if (!attachDir.exists()) attachDir.mkdirs()
        
        // Obtener nombre original
        var fileName = "file_${System.currentTimeMillis()}"
        var mimeType: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) {
                    fileName = cursor.getString(nameIdx) ?: fileName
                }
            }
            mimeType = context.contentResolver.getType(uri)
        } catch (_: Exception) {}
        
        val localFile = java.io.File(attachDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            localFile.outputStream().use { output -> input.copyTo(output) }
        }
        return Pair(localFile.absolutePath, mimeType)
    }

    /**
     * FILE-01: Lee el contenido de texto de un archivo para inyectarlo al prompt del LLM.
     */
    private fun extractTextContent(context: android.content.Context, filePath: String, mimeType: String?): String? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists() || file.length() > 500_000) return null // Max 500KB de texto
            
            val isTextBased = mimeType?.let {
                it.startsWith("text/") || it.contains("json") || it.contains("xml") || 
                it.contains("csv") || it.contains("javascript")
            } ?: filePath.let {
                it.endsWith(".txt") || it.endsWith(".md") || it.endsWith(".csv") || 
                it.endsWith(".json") || it.endsWith(".xml") || it.endsWith(".html")
            }
            
            if (isTextBased) {
                val content = file.readText().take(8000) // Max 8K chars para no volar el contexto
                content
            } else null // PDFs, imágenes, etc. requieren procesamiento especial
        } catch (_: Exception) { null }
    }

    fun sendMessage(text: String, fileUri: String? = null) {
        if (text.isBlank() && fileUri == null) return
        
        val capturedAgentId = activeAgentId.value
        val capturedThreadId = activeThreadId.value
        val capturedProvider = currentProvider.value
        val capturedModel = currentModel.value
        val context = getApplication<Application>()
        
        // FILE-02: Copiar archivo localmente si es content:// URI
        var localPath: String? = null
        var fileMimeType: String? = null
        var originalFileName: String? = null
        if (fileUri != null) {
            try {
                val uri = android.net.Uri.parse(fileUri)
                if (fileUri.startsWith("content://")) {
                    val (path, mime) = copyToLocalStorage(context, uri)
                    localPath = path
                    fileMimeType = mime
                    originalFileName = java.io.File(path).name
                } else {
                    localPath = fileUri
                    originalFileName = java.io.File(fileUri).name
                }
            } catch (e: Exception) {
                localPath = fileUri // fallback al URI original
            }
        }
        
        val displayPaths = if (localPath != null) listOf(localPath) else emptyList()
        messages.add(ChatUiMessage(text, true, filePaths = displayPaths))
        isLoading.value = true
        
        val metaJson = if (localPath != null) {
            org.json.JSONObject().apply {
                put("file_path", localPath)
                if (originalFileName != null) put("file_name", originalFileName)
                if (fileMimeType != null) put("mime_type", fileMimeType)
            }.toString()
        } else null
        
        // FILE-01: Inyectar contenido del archivo al prompt si es texto legible
        val enrichedText = if (localPath != null) {
            val fileContent = extractTextContent(context, localPath, fileMimeType)
            if (fileContent != null) {
                "$text\n\n[Contenido del archivo adjunto '$originalFileName':]\n$fileContent"
            } else {
                val label = originalFileName ?: "archivo"
                "$text\n\n[Archivo adjunto: $label (tipo: ${fileMimeType ?: "desconocido"}) — contenido binario no extraíble]"
            }
        } else text
        
        viewModelScope.launch {
            swarmInsight.value = ""
            val response = engine.processUserMessage(enrichedText, capturedProvider, capturedModel) { progress ->
                swarmInsight.value = progress
            }
            swarmInsight.value = ""
            isLoading.value = false
            
            if (response.startsWith("TOOL_CALL::open_browser::")) {
                val urlToOpen = response.removePrefix("TOOL_CALL::open_browser::")
                browserUrl.value = urlToOpen
                showBrowser.value = true
                
                val feedback = "Aquí tienes la navegación interactiva que pediste."
                messages.add(ChatUiMessage(feedback, false))
                if (!isMuted.value) {
                    voiceManager.speak(feedback, language = Locale.getDefault().language)
                }
            } else if (response.startsWith("TOOL_CALL::file_generated::")) {
                val filePath = response.removePrefix("TOOL_CALL::file_generated::")
                val generatedName = java.io.File(filePath).name
                val feedback = "He generado tu documento '$generatedName' y lo he archivado aquí en la conversación."
                
                messages.add(ChatUiMessage(feedback, false, filePaths = listOf(filePath)))
                
                if (!isMuted.value) {
                    voiceManager.speak(feedback, language = Locale.getDefault().language)
                }
                
                // FILE-07 fix: usar IDs capturados, no los mutables
                val aiMetaJson = org.json.JSONObject().apply {
                    put("file_path", filePath)
                    put("file_name", generatedName)
                }.toString()
                chatHistoryDB.chatHistoryDao().insertMessage(com.beemovil.database.ChatMessageEntity(
                    threadId = capturedThreadId,
                    senderId = capturedAgentId,
                    timestamp = System.currentTimeMillis(),
                    role = "assistant",
                    content = feedback,
                    metadataJson = aiMetaJson
                ))
            } else {
                messages.add(ChatUiMessage(response, false))
                
                // Hacer que Emma hable en voz alta leyendo el System Default Locale
                if (!isMuted.value) {
                    voiceManager.speak(response, language = Locale.getDefault().language, onError = { err ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(getApplication(), "Avisos de Voz: $err", android.widget.Toast.LENGTH_LONG).show()
                        }
                    })
                }
            }
            
            // Insertar asíncronamente en Room DB el mensaje del usuario y la respuesta
            chatHistoryDB.chatHistoryDao().insertMessage(com.beemovil.database.ChatMessageEntity(
                threadId = capturedThreadId,
                senderId = "user",
                timestamp = System.currentTimeMillis() - 100,
                role = "user",
                content = text,
                metadataJson = metaJson
            ))
            
            if (!response.startsWith("TOOL_CALL::file_generated::")) {
                chatHistoryDB.chatHistoryDao().insertMessage(com.beemovil.database.ChatMessageEntity(
                    threadId = capturedThreadId,
                    senderId = capturedAgentId,
                    timestamp = System.currentTimeMillis(),
                    role = "assistant",
                    content = response
                ))
            }
            refreshHistoryCount()
        }
    }
    
    fun sendMessageWithAttachments(text: String, attachments: List<Any>) {
        // Pasa el primer attachment como fileUri si es un String (URI)
        val uri = attachments.filterIsInstance<String>().firstOrNull()
            ?: attachments.filterIsInstance<android.net.Uri>().firstOrNull()?.toString()
        sendMessage(text, uri)
    }
    
    fun prefillAgentChat(agentId: String, prompt: String) {
        pendingPrompt.value = prompt
    }

    fun navigateToConversations() { currentScreen.value = "conversations" }
    fun forceRefreshInsight() {}
    fun toggleVoiceInput(onText: (String) -> Unit) {
        if (isRecording.value) {
            isRecording.value = false
            voiceManager.stopListening()
        } else {
            isRecording.value = true
            voiceManager.startListening(
                language = Locale.getDefault().toLanguageTag(),
                onResult = { text ->
                    isRecording.value = false
                    onText(text) // Este onText se enviará a sendMessage en el Screen
                },
                onError = {
                    isRecording.value = false
                }
            )
        }
    }
    fun analyzeImageInChat(context: Context, imagePath: String, prompt: String = "") {
        sendMessage("[Imagen adjunta] $prompt")
    }
    
    fun getProviderDisplayName(): String = "E.M.M.A. Core (Koog)"
}
