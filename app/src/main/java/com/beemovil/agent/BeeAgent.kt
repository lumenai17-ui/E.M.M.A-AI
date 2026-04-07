package com.beemovil.agent

import android.util.Log
import com.beemovil.llm.*
import com.beemovil.memory.BeeMemoryDB
import com.beemovil.skills.BeeSkill
import org.json.JSONObject

/**
 * BeeAgent — SYNCHRONOUS agentic loop with RAG memory.
 * Must be called from a background thread.
 *
 * RAG Flow:
 * 1. User sends message
 * 2. Agent retrieves relevant memories from local DB
 * 3. Memories injected into system prompt as context
 * 4. LLM responds with awareness of past interactions
 * 5. Conversation saved to local DB
 */
class BeeAgent(
    val config: AgentConfig,
    private val llm: LlmProvider,
    private val skills: Map<String, BeeSkill>,
    private val memoryDB: BeeMemoryDB? = null,
    private val sessionId: String = "session_${System.currentTimeMillis()}"
) {
    companion object {
        private const val TAG = "BeeAgent"
    }

    private val messages = mutableListOf<ChatMessage>()

    init {
        messages.add(ChatMessage(role = "system", content = config.systemPrompt))
    }

    private fun getToolDefinitions(): List<ToolDefinition> {
        val allowAll = config.enabledTools.contains("*")
        return skills.filter { (name, _) ->
            allowAll || config.enabledTools.contains(name)
        }.map { (_, skill) ->
            ToolDefinition(
                name = skill.name,
                description = skill.description,
                parameters = skill.parametersSchema
            )
        }
    }

    /**
     * SYNCHRONOUS chat with RAG memory injection.
     */
    fun chat(userMessage: String): AgentResponse {
        // ── RAG: Inject relevant memories into context ──
        if (memoryDB != null) {
            val ragContext = memoryDB.buildRagContext(userMessage)
            if (ragContext.isNotBlank()) {
                // Update system prompt with RAG context
                val enhancedPrompt = config.systemPrompt + "\n\n" + ragContext
                messages[0] = ChatMessage(role = "system", content = enhancedPrompt)
                Log.d(TAG, "[${config.id}] RAG context injected (${ragContext.length} chars)")
            }
        }

        messages.add(ChatMessage(role = "user", content = userMessage))
        trimHistory() // Prevent unbounded growth
        Log.i(TAG, "[${config.id}] User: ${userMessage.take(80)}")

        return processChat(userMessage)
    }

    /**
     * Inject file attachment context into the system prompt.
     * Called before chat() when the conversation has attachments.
     */
    fun injectAttachmentContext(attachmentContext: String) {
        val currentSystem = messages.firstOrNull()?.content ?: config.systemPrompt
        // Remove old attachment context if present, add new
        val base = currentSystem.substringBefore("\n## Archivos adjuntos en esta conversacion:")
        messages[0] = ChatMessage(role = "system", content = base + attachmentContext)
        Log.d(TAG, "[${config.id}] Attachment context injected (${attachmentContext.length} chars)")
    }

    /**
     * SYNCHRONOUS chat with image (vision). Image is base64 encoded.
     */
    fun chatWithImage(userMessage: String, imageBase64: String): AgentResponse {
        // RAG still applies
        if (memoryDB != null) {
            val ragContext = memoryDB.buildRagContext(userMessage)
            if (ragContext.isNotBlank()) {
                val enhancedPrompt = config.systemPrompt + "\n\n" + ragContext
                messages[0] = ChatMessage(role = "system", content = enhancedPrompt)
            }
        }

        messages.add(ChatMessage(
            role = "user",
            content = userMessage,
            images = listOf(imageBase64)
        ))
        Log.i(TAG, "[${config.id}] User (vision): ${userMessage.take(80)} + image")

        return processChat(userMessage)
    }

    private fun processChat(userMessage: String): AgentResponse {

        // Save user message to DB
        memoryDB?.saveMessage(sessionId, "user", userMessage, config.id)

        val toolResults = mutableListOf<ToolExecution>()

        repeat(config.maxToolLoops) { iteration ->
            try {
                Log.d(TAG, "[${config.id}] LLM call #$iteration")
                val response = llm.complete(messages, getToolDefinitions())

                if (response.hasToolCalls) {
                    messages.add(ChatMessage(
                        role = "assistant",
                        content = response.text,
                        toolCalls = response.toolCalls
                    ))
                    for (toolCall in response.toolCalls) {
                        Log.i(TAG, "[${config.id}] Tool: ${toolCall.name}")
                        val result = executeSkill(toolCall.name, toolCall.params)
                        toolResults.add(ToolExecution(toolCall.name, toolCall.params, result))
                        messages.add(ChatMessage(
                            role = "tool",
                            content = result.toString(),
                            toolCallId = toolCall.id
                        ))
                    }
                } else {
                    val text = response.text ?: "..."
                    messages.add(ChatMessage(role = "assistant", content = text))

                    // Save assistant response to DB
                    memoryDB?.saveMessage(sessionId, "assistant", text, config.id)

                    Log.i(TAG, "[${config.id}] Response: ${text.take(80)}")
                    return AgentResponse(text = text, toolExecutions = toolResults, agentId = config.id)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "[${config.id}] Error: ${e.message}", e)
                val errorMsg = "Error: ${e.message ?: e.javaClass.simpleName}"
                messages.add(ChatMessage(role = "assistant", content = errorMsg))
                return AgentResponse(text = errorMsg, toolExecutions = toolResults, agentId = config.id, isError = true)
            }
        }

        return AgentResponse(
            text = "Límite de ${config.maxToolLoops} acciones alcanzado.",
            toolExecutions = toolResults, agentId = config.id
        )
    }

    private fun executeSkill(name: String, params: JSONObject): JSONObject {
        val skill = skills[name]
            ?: return JSONObject().put("error", "Skill '$name' not found")
        return try {
            skill.execute(params)
        } catch (e: Throwable) {
            JSONObject().put("error", e.message ?: "Unknown error")
        }
    }

    fun clearMemory() {
        val systemMsg = messages.first()
        messages.clear()
        messages.add(systemMsg)
    }

    /**
     * Trim message history to prevent unbounded memory growth.
     * Keeps system prompt + last N messages.
     */
    private fun trimHistory() {
        val maxMessages = 40 // system + 20 exchanges
        if (messages.size > maxMessages) {
            val systemMsg = messages.first()
            val recent = messages.takeLast(maxMessages - 1)
            messages.clear()
            messages.add(systemMsg)
            messages.addAll(recent)
            Log.d(TAG, "[${config.id}] Trimmed history to $maxMessages messages")
        }
    }
}

data class AgentResponse(
    val text: String,
    val toolExecutions: List<ToolExecution> = emptyList(),
    val agentId: String,
    val isError: Boolean = false
)

data class ToolExecution(
    val skillName: String,
    val params: JSONObject,
    val result: JSONObject
)
