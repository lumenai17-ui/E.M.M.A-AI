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
    val attachmentNames: List<String> = emptyList()
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
    private val voiceManager = DeepgramVoiceManager(application)
    private val envScanner = com.beemovil.core.EnvironmentScanner(application)

    val dynamicDashboardState = mutableStateOf(DashboardMatrixState())

    init {
        // Inicializamos los oídos y la boca local
        voiceManager.initialize()
        
        viewModelScope.launch {
            engine.initialize()
            dashboardInsight.value = "Koog Engine Cargado: Ollama conectando..."
            
            // Cargar Historial al Iniciar del Hilo Principal
            val history = chatHistoryDB.chatHistoryDao().getHistory("main")
            
            // 1. Restaurar Visor Visual
            history.forEach {
                // Filtramos "system" ni "tool" en la UI, solo user/assistant
                if (it.role == "user" || it.role == "assistant") {
                    var filePaths = emptyList<String>()
                    if (!it.metadataJson.isNullOrBlank()) {
                        try {
                            val json = org.json.JSONObject(it.metadataJson)
                            if (json.has("file_path")) {
                                filePaths = listOf(json.getString("file_path"))
                            }
                        } catch (e: Exception) {}
                    }
                    messages.add(ChatUiMessage(text = it.content, isUser = it.role == "user", filePaths = filePaths))
                }
            }
            
            // 2. Re-hidratar Corteza Neuronal (God Mode Persistencia)
            engine.loadPersistedContext(history)

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

    val messages = mutableStateListOf<ChatUiMessage>()
    val searchResults = mutableStateListOf<ChatUiMessage>()
    val isSearchMode = mutableStateOf(false)
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
                var aiInsight = engine.processUserMessage(systemMatrixPrompt).replace("SISTEMA CORE:", "").trim()
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
    val currentScreen = mutableStateOf("chat")
    val browserUrl = mutableStateOf("https://www.google.com")
    val showBrowser = mutableStateOf(false)
    val browserAgentStatusText = mutableStateOf("Desconectado")
    
    val telegramBotStatus = mutableStateOf("offline")
    val telegramBotName = mutableStateOf("")
    val telegramBotMessages = mutableStateOf(0)
    
    // UI states
    val dashboardInsight = mutableStateOf("Sistema base reiniciado. Esperando motor Koog...")
    val dashboardInsightLoading = mutableStateOf(false)
    val currentProvider = mutableStateOf("koog")
    
    val pendingPrompt = mutableStateOf("")

    val currentModel = mutableStateOf("llama3")

    val activeThreadId = mutableStateOf("main")
    val activeAgentId = mutableStateOf("emma")
    val activeAgentName = mutableStateOf("E.M.M.A.")
    val activeSystemPrompt = mutableStateOf("")

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
            
            val newThreadId = "thread_${agent.agentId}_primary"
            activeThreadId.value = newThreadId
            
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
                if (!msg.metadataJson.isNullOrBlank()) {
                    try {
                        val json = org.json.JSONObject(msg.metadataJson)
                        if (json.has("file_path")) {
                            filePaths = listOf(json.getString("file_path"))
                        }
                    } catch (e: Exception) {}
                }
                messages.add(ChatUiMessage(text = msg.content, isUser = msg.role == "user", filePaths = filePaths))
            }
            
            engine.loadPersistedContext(pastMessages, agent.systemPrompt)
            
            currentProvider.value = agent.fallbackModel
            currentScreen.value = "chat"
        }
    }

    fun openThread(thread: com.beemovil.database.ChatThreadEntity) {
        viewModelScope.launch {
            activeThreadId.value = thread.threadId
            
            val pastMessages = chatHistoryDB.chatHistoryDao().getHistory(thread.threadId)
            messages.clear()
            pastMessages.forEach { msg ->
                var filePaths = emptyList<String>()
                if (!msg.metadataJson.isNullOrBlank()) {
                    try {
                        val json = org.json.JSONObject(msg.metadataJson)
                        if (json.has("file_path")) {
                            filePaths = listOf(json.getString("file_path"))
                        }
                    } catch (e: Exception) {}
                }
                messages.add(ChatUiMessage(text = msg.content, isUser = msg.role == "user", filePaths = filePaths))
            }
            
            engine.loadPersistedContext(pastMessages)
            currentScreen.value = "chat"
        }
    }
    // ------------------------------------

    fun switchProvider(preset: String, model: String) {
        currentProvider.value = preset
        currentModel.value = model
        dashboardInsight.value = "Motor conmutador a $preset: $model cargado."
    }

    fun updateApiKey(provider: String, key: String) {
        // Dummy implementation for SettingsScreen compatibility
    }

    val memoryDB: com.beemovil.memory.BeeMemoryDB? = null
    val chatHistoryDB = com.beemovil.database.ChatHistoryDB.getDatabase(application)
    
    fun refreshHistoryCount() {
        // Enjambre no usa raw count (Reservado para Swarm Dashboard)
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            chatHistoryDB.chatHistoryDao().clearAll()
            messages.clear()
            engine.clearMemoryAndHistory()
        }
    }

    // Hardware states
    val isRecording = mutableStateOf(false)
    val isMuted = mutableStateOf(false)

    // STUBS de Integración Fuerte (A rellenar en Fase 3)
    fun sendMessage(text: String, fileUri: String? = null) {
        if (text.isBlank() && fileUri == null) return
        
        val displayPaths = if (fileUri != null) listOf(fileUri) else emptyList()
        messages.add(ChatUiMessage(text, true, filePaths = displayPaths))
        isLoading.value = true
        
        val metaJson = if (fileUri != null) {
            org.json.JSONObject().apply { put("file_path", fileUri) }.toString()
        } else null
        
        viewModelScope.launch {
            val response = engine.processUserMessage(text)
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
            } else {
                messages.add(ChatUiMessage(response, false))
                
                // Hacer que Emma hable en voz alta leyendo el System Default Locale
                if (!isMuted.value) {
                    voiceManager.speak(response, language = Locale.getDefault().language)
                }
            }
            
            // Insertar asíncronamente en Room DB el mensaje del usuario y la respuesta
            chatHistoryDB.chatHistoryDao().insertMessage(com.beemovil.database.ChatMessageEntity(
                threadId = activeThreadId.value,
                senderId = "user",
                timestamp = System.currentTimeMillis() - 100, // usuario antes
                role = "user",
                content = text,
                metadataJson = metaJson
            ))
            
            chatHistoryDB.chatHistoryDao().insertMessage(com.beemovil.database.ChatMessageEntity(
                threadId = activeThreadId.value,
                senderId = activeAgentId.value,
                timestamp = System.currentTimeMillis(),
                role = "assistant",
                content = response
            ))
            refreshHistoryCount()
        }
    }
    
    fun sendMessageWithAttachments(text: String, attachments: List<Any>) {
        sendMessage(text)
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
