package com.beemovil.llm

import org.json.JSONArray
import org.json.JSONObject

data class ToolCall(
    val id: String,
    val name: String,
    val params: JSONObject
)

data class LlmResponse(
    val text: String?,
    val toolCalls: List<ToolCall>,
    val raw: JSONObject
) {
    val hasToolCalls get() = toolCalls.isNotEmpty()
}

data class ChatMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("role", role)
        when (role) {
            "tool" -> {
                json.put("content", content ?: "")
                json.put("tool_call_id", toolCallId ?: "")
            }
            "assistant" -> {
                json.put("content", content ?: JSONObject.NULL)
                if (toolCalls != null && toolCalls.isNotEmpty()) {
                    val calls = JSONArray()
                    toolCalls.forEach { tc ->
                        calls.put(JSONObject().apply {
                            put("id", tc.id)
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", tc.name)
                                put("arguments", tc.params.toString())
                            })
                        })
                    }
                    json.put("tool_calls", calls)
                }
            }
            else -> json.put("content", content ?: "")
        }
        return json
    }
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JSONObject
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", parameters)
        })
    }
}

/**
 * SYNCHRONOUS interface — no coroutines, no suspend.
 * Called from a background Thread directly.
 */
interface LlmProvider {
    val name: String
    fun complete(messages: List<ChatMessage>, tools: List<ToolDefinition>): LlmResponse
}
