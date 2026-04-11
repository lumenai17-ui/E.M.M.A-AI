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
    val capsuleNetBattery: String = "Midiendo sistema...",
    val capsuleWeather: String = "Calculando biomas...",
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
            history.forEach {
                messages.add(ChatUiMessage(text = it.content, isUser = it.role == "user"))
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
            val netBatCombined = if (battery > 20) "🔋 Energía $battery% | 📡 $net" else "⚠️ Batería Crítica $battery% | 📡 $net"
            
            // Simular GPS por defecto si no tenemos lat/long activos del Engine
            val locationSemantic = envScanner.getSemanticLocation(19.4326, -99.1332)
            val weather = envScanner.fetchWeather(19.4326, -99.1332)

            dynamicDashboardState.value = dynamicDashboardState.value.copy(
                capsuleNetBattery = netBatCombined,
                capsuleLocation = locationSemantic,
                capsuleWeather = weather
            )

            // Petición silenciosa a la IA (Prompt ciego Administrativo)
            val systemMatrixPrompt = "SYS_PROMPT: Eres el motor central. Los sensores leen: $netBatCombined, $locationSemantic, $weather. Redacta en 2 frases cortas (max 25 palabras) un 'Insight' o recomendación futurista y útil de bienvenida para el usuario (Arquitecto)."
            try {
                val aiInsight = engine.processUserMessage(systemMatrixPrompt).replace("SYS_PROMPT:", "").trim()
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
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        messages.add(ChatUiMessage(text, true))
        isLoading.value = true
        
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
                threadId = "main",
                senderId = "user",
                timestamp = System.currentTimeMillis() - 100, // usuario antes
                role = "user",
                content = text
            ))
            
            chatHistoryDB.chatHistoryDao().insertMessage(com.beemovil.database.ChatMessageEntity(
                threadId = "main",
                senderId = "emma", // asistente por defecto
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
