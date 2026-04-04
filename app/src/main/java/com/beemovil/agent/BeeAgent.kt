package com.beemovil.agent

import android.util.Log
import com.beemovil.llm.*
import com.beemovil.skills.BeeSkill
import org.json.JSONObject

/**
 * BeeAgent — SYNCHRONOUS agentic loop. No coroutines.
 * Must be called from a background thread.
 */
class BeeAgent(
    val config: AgentConfig,
    private val llm: LlmProvider,
    private val skills: Map<String, BeeSkill>
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
     * SYNCHRONOUS chat — no suspend, no coroutines.
     */
    fun chat(userMessage: String): AgentResponse {
        messages.add(ChatMessage(role = "user", content = userMessage))
        Log.i(TAG, "[${config.id}] User: ${userMessage.take(80)}")

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
