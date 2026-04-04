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
import com.beemovil.llm.OpenRouterProvider
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

    private var agents = mutableMapOf<String, BeeAgent>()
    private var skills = mapOf<String, BeeSkill>()
    private var apiKey = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(skillMap: Map<String, BeeSkill>, openRouterApiKey: String) {
        this.skills = skillMap
        this.apiKey = openRouterApiKey
        if (messages.isEmpty()) {
            messages.add(ChatUiMessage(
                text = "¡Hola! Soy Bee-Movil 🐝\n\n¿En qué te puedo ayudar?",
                isUser = false, agentIcon = "🐝"
            ))
        }
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    fun updateApiKey(newKey: String) {
        this.apiKey = newKey
        agents.clear()
    }

    private fun getOrCreateAgent(config: AgentConfig): BeeAgent {
        return agents.getOrPut(config.id) {
            Log.d(TAG, "Creating agent: ${config.id} with model: ${config.model}")
            val provider = OpenRouterProvider(apiKey, config.model)
            BeeAgent(config, provider, skills)
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
        Log.d(TAG, "sendMessage: $text")

        val config = currentAgentConfig.value
        messages.add(ChatUiMessage(text = text, isUser = true))

        if (apiKey.isBlank()) {
            messages.add(ChatUiMessage(
                text = "⚠️ Configura tu API key primero (⚙️)",
                isUser = false, agentIcon = "⚠️", isError = true
            ))
            return
        }

        isLoading.value = true

        // Simple background thread — NO coroutines, NO runBlocking
        Thread {
            Log.d(TAG, "Thread started")
            var responseText: String
            var isError = false
            var toolNames = emptyList<String>()

            try {
                val agent = getOrCreateAgent(config)
                Log.d(TAG, "Agent ready, calling chat()")
                val response = agent.chat(text)  // SYNCHRONOUS call
                Log.d(TAG, "Chat returned: ${response.text.take(50)}")
                responseText = response.text
                isError = response.isError
                toolNames = response.toolExecutions.map { it.skillName }
            } catch (e: Throwable) {
                Log.e(TAG, "CRASH in thread: ${e.javaClass.name}: ${e.message}", e)
                responseText = "❌ ${e.javaClass.simpleName}: ${e.message}"
                isError = true
            }

            // Post result to main thread
            val finalText = responseText
            val finalError = isError
            val finalTools = toolNames
            mainHandler.post {
                Log.d(TAG, "Updating UI")
                isLoading.value = false
                messages.add(ChatUiMessage(
                    text = finalText,
                    isUser = false, agentIcon = config.icon,
                    isError = finalError, toolsUsed = finalTools
                ))
            }
        }.start()
    }

    fun clearChat() {
        val config = currentAgentConfig.value
        try { agents[config.id]?.clearMemory() } catch (_: Throwable) {}
        messages.clear()
        messages.add(ChatUiMessage(
            text = "Chat limpiado ${config.icon}",
            isUser = false, agentIcon = config.icon
        ))
    }
}
