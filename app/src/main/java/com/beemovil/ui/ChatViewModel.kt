package com.beemovil.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.service.EmmaTaskService
import com.beemovil.voice.DeepgramVoiceManager
import kotlinx.coroutines.launch
import android.util.Log
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
    val model: String = "koog-engine",
    val avatarUri: String? = null
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


    // Track active task IDs so we know which results belong to us
    private val pendingTaskIds = mutableSetOf<Long>()

    private val taskResultObserver = Observer<EmmaTaskService.TaskResult> { result ->
        if (result.threadId != activeThreadId.value) return@Observer
        if (result.taskId !in pendingTaskIds) return@Observer
        pendingTaskIds.remove(result.taskId)
        handleServiceResult(result)
    }

    private val taskProgressObserver = Observer<EmmaTaskService.TaskProgress> { progress ->
        if (progress.threadId == activeThreadId.value) {
            swarmInsight.value = progress.progressText
        }
    }

    init {
        // Inicializamos los oídos y la boca local
        voiceManager.initialize()

        // Observe service results on the main thread
        EmmaTaskService.taskResult.observeForever(taskResultObserver)
        EmmaTaskService.taskProgress.observeForever(taskProgressObserver)
        
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

    fun forgeAgent(name: String, icon: String, prompt: String, model: String, avatarUri: String? = null) {
        viewModelScope.launch {
            val validIcon = if (icon.isBlank()) "🤖" else icon
            val newAgent = com.beemovil.database.AgentConfigEntity(
                agentId = "agent_${System.currentTimeMillis()}",
                name = name,
                icon = validIcon,
                systemPrompt = prompt,
                fallbackModel = model,
                avatarUri = avatarUri
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
            // Parse fallbackModel: only override provider/model if agent has a specific one
            if (agent.fallbackModel.startsWith("hermes-a2a")) {
                currentProvider.value = agent.fallbackModel
                currentModel.value = ""
            } else if (agent.fallbackModel != "koog-engine" && agent.fallbackModel.contains(":")) {
                val parts = agent.fallbackModel.split(":", limit = 2)
                currentProvider.value = parts[0]
                currentModel.value = parts[1]
            }
            // else: "koog-engine" → keep global settings
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
            insightText = "Generating Neural Environment Inference..."
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
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE HH:mm", java.util.Locale.getDefault())
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

            // Read language preference for AI prompt
            val appLang = prefs.getString("app_language", "auto") ?: "auto"
            val isEnglish = appLang == "en" || (appLang == "auto" && java.util.Locale.getDefault().language == "en")

            // Petición silenciosa a la IA (Prompt de Función Estricto para evadir alucinaciones charlatanas)
            val systemMatrixPrompt = if (isEnglish) {
                "SYSTEM INSTRUCTION: You are not an assistant. Do not greet or explain. Generate the report. Telemetry data received: Battery $battery%, Network $net, Time $sysTime, Weather $weatherRaw, Recent events: $recentMemory$ghostInjection. MANDATORY FORMAT (Max 2 lines): Line 1: Environment insight. Line 2: '👉 Suggested action:' followed by instruction."
            } else {
                "INSTRUCCIÓN DE SISTEMA: No eres un asistente. No saludes ni expliques. Genera el reporte. Datos de telemetría recibidos: Bateria $battery%, Red $net, Hora $sysTime, Clima $weatherRaw, Sucesos recientes: $recentMemory$ghostInjection. FORMATO OBLIGATORIO (Max 2 líneas): Línea 1: Insight de entorno. Línea 2: '👉 Acción sugerida:' seguido de instrucción."
            }
            
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
            // C-05 fix: buscar en TODOS los threads, no en uno hardcodeado
            val results = chatHistoryDB.chatHistoryDao().searchAllHistory(query)
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
            
            // Parse fallbackModel: "provider:modelId", "hermes-a2a|url|token", or "koog-engine" (global)
            if (agent.fallbackModel.startsWith("hermes-a2a")) {
                currentProvider.value = agent.fallbackModel
                currentModel.value = ""
            } else if (agent.fallbackModel != "koog-engine" && agent.fallbackModel.contains(":")) {
                val parts = agent.fallbackModel.split(":", limit = 2)
                currentProvider.value = parts[0]
                currentModel.value = parts[1]
            }
            // else: "koog-engine" → keep current global provider/model untouched
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
            // Bug 2 fix: full state reset when returning to main Emma chat
            viewModelScope.launch {
                activeThreadId.value = "main"
                activeAgentId.value = "emma"
                activeAgentName.value = "E.M.M.A."
                activeSystemPrompt.value = ""
                activeAgentConfig.value = null
                
                val appPrefs = getApplication<Application>().getSharedPreferences("beemovil_session", Context.MODE_PRIVATE)
                appPrefs.edit().putString("last_active_thread", "main").apply()
                
                // Restore global provider/model from prefs
                val globalPrefs = getApplication<Application>().getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                currentProvider.value = globalPrefs.getString("selected_provider", "openrouter") ?: "openrouter"
                currentModel.value = globalPrefs.getString("selected_model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini"
                
                // Reload main thread messages
                val pastMessages = chatHistoryDB.chatHistoryDao().getHistory("main")
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
        val context = getApplication<Application>()
        
        // Use the agent's own model if configured, otherwise fall back to global settings
        val agentConfig = activeAgentConfig.value
        val capturedProvider: String
        val capturedModel: String
        val agentModel = agentConfig?.fallbackModel ?: ""
        if (agentModel.isNotBlank() && agentModel != "koog-engine" && agentModel.contains(":")) {
            // Agent has a specific model like "openrouter:google/gemini-2.5-flash"
            val parts = agentModel.split(":", limit = 2)
            capturedProvider = parts[0]
            capturedModel = parts[1]
        } else if (agentModel.startsWith("hermes-a2a")) {
            capturedProvider = agentModel
            capturedModel = ""
        } else {
            // "koog-engine" or blank → use global user settings
            capturedProvider = currentProvider.value
            capturedModel = currentModel.value
        }
        
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
            // U-06 fix: Guardar mensaje del usuario ANTES de llamar al LLM
            // Si la app se cierra o el LLM falla, al menos el mensaje del usuario NO se pierde
            chatHistoryDB.chatHistoryDao().insertMessage(com.beemovil.database.ChatMessageEntity(
                threadId = capturedThreadId,
                senderId = "user",
                timestamp = System.currentTimeMillis(),
                role = "user",
                content = text,
                metadataJson = metaJson
            ))
        }

        // Delegate to ForegroundService — survives app backgrounding
        val taskId = System.currentTimeMillis()
        pendingTaskIds.add(taskId)
        swarmInsight.value = ""

        val serviceIntent = Intent(context, EmmaTaskService::class.java).apply {
            action = EmmaTaskService.ACTION_PROCESS
            putExtra(EmmaTaskService.EXTRA_MESSAGE, enrichedText)
            putExtra(EmmaTaskService.EXTRA_THREAD_ID, capturedThreadId)
            putExtra(EmmaTaskService.EXTRA_AGENT_ID, capturedAgentId)
            putExtra(EmmaTaskService.EXTRA_PROVIDER, capturedProvider)
            putExtra(EmmaTaskService.EXTRA_MODEL, capturedModel)
            putExtra(EmmaTaskService.EXTRA_TASK_ID, taskId)
            val agentPrompt = activeAgentConfig.value?.systemPrompt
            if (!agentPrompt.isNullOrBlank()) {
                putExtra(EmmaTaskService.EXTRA_SYSTEM_PROMPT, agentPrompt)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i("ChatViewModel", "Task $taskId delegated to EmmaTaskService")
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to start service, falling back to in-process", e)
            pendingTaskIds.remove(taskId)
            // Fallback: execute in-process if service can't start
            executeInProcess(enrichedText, capturedProvider, capturedModel, capturedThreadId, capturedAgentId)
        }
    }
    
    fun sendMessageWithAttachments(text: String, attachments: List<Any>) {
        // Pasa el primer attachment como fileUri si es un String (URI)
        val uri = attachments.filterIsInstance<String>().firstOrNull()
            ?: attachments.filterIsInstance<android.net.Uri>().firstOrNull()?.toString()
        sendMessage(text, uri)
    }
    
    /**
     * Handle results coming from EmmaTaskService via LiveData.
     * This runs on the main thread — safe to update UI.
     */
    private fun handleServiceResult(result: EmmaTaskService.TaskResult) {
        isLoading.value = false
        swarmInsight.value = ""

        if (result.response.isBlank()) return

        when {
            result.response.startsWith("TOOL_CALL::open_browser::") -> {
                val urlToOpen = result.response.removePrefix("TOOL_CALL::open_browser::")
                browserUrl.value = urlToOpen
                showBrowser.value = true
                val feedback = "Aquí tienes la navegación interactiva que pediste."
                messages.add(ChatUiMessage(feedback, false))
                if (!isMuted.value) {
                    voiceManager.speak(feedback, language = Locale.getDefault().language)
                }
            }
            result.isFileGenerated && result.filePath != null -> {
                val generatedName = result.fileName ?: java.io.File(result.filePath).name
                val extension = generatedName.substringAfterLast('.', "").lowercase()
                val isImage = extension in listOf("png", "jpg", "jpeg", "webp", "gif")
                val mimeType = result.mimeType ?: "application/octet-stream"
                val feedback = if (isImage) {
                    "🎨 ¡Listo! He generado tu imagen '$generatedName'. La puedes ver aquí abajo y también está guardada en Downloads/EMMA/."
                } else {
                    "He generado tu documento '$generatedName' y lo he archivado aquí en la conversación."
                }
                messages.add(ChatUiMessage(
                    feedback, false,
                    filePaths = listOf(result.filePath),
                    attachmentNames = listOf(generatedName),
                    attachmentMimeTypes = listOf(mimeType)
                ))
                if (!isMuted.value) {
                    voiceManager.speak(feedback, language = Locale.getDefault().language)
                }
            }
            else -> {
                messages.add(ChatUiMessage(result.response, false))
                if (!isMuted.value) {
                    voiceManager.speak(result.response, language = Locale.getDefault().language, onError = { err ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(getApplication(), "Avisos de Voz: $err", android.widget.Toast.LENGTH_LONG).show()
                        }
                    })
                }
            }
        }
        refreshHistoryCount()
    }

    /**
     * Fallback: execute in-process if the ForegroundService can't start.
     * This is the old behavior — tied to viewModelScope (dies with Activity).
     */
    private fun executeInProcess(
        message: String,
        provider: String,
        model: String,
        threadId: String,
        agentId: String
    ) {
        viewModelScope.launch {
            swarmInsight.value = ""
            val response = try {
                engine.processUserMessage(message, provider, model) { progress ->
                    swarmInsight.value = progress
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "In-process fallback error: ${e.message}", e)
                "⚠️ Error interno: ${e.message?.take(200) ?: "desconocido"}"
            }
            swarmInsight.value = ""
            isLoading.value = false

            if (response.isBlank()) return@launch

            // Simplified handler for fallback mode
            handleServiceResult(EmmaTaskService.TaskResult(
                taskId = 0,
                threadId = threadId,
                agentId = agentId,
                response = response,
                isFileGenerated = response.startsWith("TOOL_CALL::file_generated::"),
                filePath = if (response.startsWith("TOOL_CALL::file_generated::")) response.removePrefix("TOOL_CALL::file_generated::") else null
            ))

            // Save to Room (service does this normally, but we need to in fallback)
            if (!response.startsWith("TOOL_CALL::file_generated::")) {
                chatHistoryDB.chatHistoryDao().insertMessage(com.beemovil.database.ChatMessageEntity(
                    threadId = threadId,
                    senderId = agentId,
                    timestamp = System.currentTimeMillis(),
                    role = "assistant",
                    content = response
                ))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Remove observers to prevent leaks
        EmmaTaskService.taskResult.removeObserver(taskResultObserver)
        EmmaTaskService.taskProgress.removeObserver(taskProgressObserver)
    }

    fun prefillAgentChat(agentId: String, prompt: String) {
        pendingPrompt.value = prompt
    }

    fun navigateToConversations() { currentScreen.value = "conversations" }
    // C-07 fix: Conectar al motor real de refresh
    fun forceRefreshInsight() { refreshLiveDashboard() }
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
