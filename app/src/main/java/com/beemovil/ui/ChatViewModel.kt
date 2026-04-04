package com.beemovil.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.beemovil.agent.AgentConfig
import com.beemovil.agent.BeeAgent
import com.beemovil.agent.DefaultAgents
import com.beemovil.llm.LlmFactory
import com.beemovil.memory.BeeMemoryDB
import com.beemovil.skills.BeeSkill

data class ChatUiMessage(
    val text: String,
    val isUser: Boolean,
    val agentIcon: String = "🐝",
    val isError: Boolean = false,
    val isLoading: Boolean = false,
    val toolsUsed: List<String> = emptyList()
)

class ChatViewModel : ViewModel() {

    companion object {
        private const val TAG = "ChatVM"
    }

    val messages = mutableStateListOf<ChatUiMessage>()
    val isLoading = mutableStateOf(false)
    val currentAgentConfig = mutableStateOf(DefaultAgents.MAIN)
    val availableAgents = DefaultAgents.ALL

    // Provider configuration
    val currentProvider = mutableStateOf("openrouter")
    val currentModel = mutableStateOf("qwen/qwen3.6-plus:free")

    private var agents = mutableMapOf<String, BeeAgent>()
    private var skills = mapOf<String, BeeSkill>()
    private var apiKeys = mutableMapOf<String, String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Memory system
    var memoryDB: BeeMemoryDB? = null
        private set

    // Voice input
    var voiceManager: com.beemovil.skills.VoiceInputManager? = null
    val isRecording = mutableStateOf(false)

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
                        messages.add(ChatUiMessage("🎙️ $error", isUser = false, agentIcon = "⚠️", isError = true))
                    }
                },
                onFinalResult = { text ->
                    mainHandler.post {
                        isRecording.value = false
                        onText(text)
                        // Auto-send voice input
                        sendMessage(text)
                    }
                }
            )
        }
    }

    fun initialize(skillMap: Map<String, BeeSkill>, openRouterKey: String, ollamaKey: String = "", memory: BeeMemoryDB? = null) {
        this.skills = skillMap
        this.memoryDB = memory
        if (openRouterKey.isNotBlank()) apiKeys["openrouter"] = openRouterKey
        if (ollamaKey.isNotBlank()) apiKeys["ollama"] = ollamaKey

        if (messages.isEmpty()) {
            val skillCount = skillMap.size
            val memCount = memory?.getMemoryCount() ?: 0
            val memInfo = if (memCount > 0) "\n🧠 **$memCount memorias** almacenadas" else ""

            messages.add(ChatUiMessage(
                text = "¡Hola! Soy Bee-Movil 🐝\n\n" +
                        "Tengo **$skillCount skills nativos** listos.\n" +
                        "Proveedor: **${getProviderDisplayName()}**" +
                        memInfo + "\n\n¿En qué te puedo ayudar?",
                isUser = false, agentIcon = "🐝"
            ))
        }
    }

    fun hasApiKey(): Boolean = apiKeys[currentProvider.value]?.isNotBlank() == true

    fun getApiKey(provider: String): String = apiKeys[provider] ?: ""

    fun updateApiKey(provider: String, key: String) {
        apiKeys[provider] = key
        agents.clear()
    }

    fun switchProvider(provider: String, model: String) {
        currentProvider.value = provider
        currentModel.value = model
        agents.clear()
        messages.add(ChatUiMessage(
            text = "🔄 Cambiando a **${getProviderDisplayName()}**\nModelo: `$model`",
            isUser = false, agentIcon = "⚙️"
        ))
    }

    private fun getProviderDisplayName(): String {
        return when (currentProvider.value) {
            "ollama" -> "Ollama Cloud"
            else -> "OpenRouter"
        }
    }

    private fun getOrCreateAgent(config: AgentConfig): BeeAgent {
        val key = "${config.id}_${currentProvider.value}_${currentModel.value}"
        return agents.getOrPut(key) {
            val apiKey = apiKeys[currentProvider.value] ?: ""
            val provider = LlmFactory.createProvider(
                providerType = currentProvider.value,
                apiKey = apiKey,
                model = currentModel.value
            )
            Log.d(TAG, "Created agent: ${config.id} on ${currentProvider.value}/${currentModel.value}")
            BeeAgent(config, provider, skills, memoryDB)
        }
    }

    fun switchAgent(config: AgentConfig) {
        currentAgentConfig.value = config
        messages.add(ChatUiMessage(
            text = "Cambiando a ${config.name} ${config.icon}",
            isUser = false, agentIcon = config.icon
        ))
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isLoading.value) return

        val config = currentAgentConfig.value
        messages.add(ChatUiMessage(text = text, isUser = true))

        val apiKey = apiKeys[currentProvider.value] ?: ""
        if (apiKey.isBlank()) {
            messages.add(ChatUiMessage(
                text = "⚠️ Configura tu API key para **${getProviderDisplayName()}** primero (⚙️)",
                isUser = false, agentIcon = "⚠️", isError = true
            ))
            return
        }

        isLoading.value = true

        Thread {
            var responseText: String
            var isError = false
            var toolNames = emptyList<String>()

            try {
                val agent = getOrCreateAgent(config)
                val response = agent.chat(text)
                responseText = response.text
                isError = response.isError
                toolNames = response.toolExecutions.map { it.skillName }
            } catch (e: Throwable) {
                Log.e(TAG, "Chat error: ${e.message}", e)
                responseText = "❌ ${e.javaClass.simpleName}: ${e.message}"
                isError = true
            }

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
        val memCount = memoryDB?.getMemoryCount() ?: 0
        messages.add(ChatUiMessage(
            text = "Chat limpiado ${config.icon}\n🧠 $memCount memorias persisten",
            isUser = false, agentIcon = config.icon
        ))
    }
}
