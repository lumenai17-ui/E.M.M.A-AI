package com.beemovil.a2a

import org.json.JSONArray
import org.json.JSONObject

/**
 * A2A Protocol — Google's Agent-to-Agent standard.
 *
 * This implements the core data types from the A2A specification,
 * allowing Bee-Movil to communicate with any A2A-compatible agent.
 *
 * Spec: https://google.github.io/A2A/
 *
 * Key concepts:
 * - Agent Card: describes an agent's capabilities (like a business card)
 * - Task: a unit of work sent from one agent to another
 * - Artifact: a piece of output produced by a task
 */

/**
 * Agent Card — describes a remote agent's capabilities.
 * Published at /.well-known/agent.json on the agent's server.
 */
data class AgentCard(
    val name: String,
    val description: String,
    val url: String,                        // Base URL of the agent
    val version: String = "1.0",
    val capabilities: List<String> = emptyList(),  // e.g. ["text", "file", "vision"]
    val skills: List<AgentSkillInfo> = emptyList(),
    val provider: String = "",              // Organization name
    val icon: String = "🤖"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put("url", url)
        put("version", version)
        put("capabilities", JSONArray(capabilities))
        put("skills", JSONArray().apply {
            skills.forEach { put(it.toJson()) }
        })
        put("provider", provider)
        put("icon", icon)
    }

    companion object {
        fun fromJson(json: JSONObject): AgentCard {
            return AgentCard(
                name = json.optString("name", "Unknown Agent"),
                description = json.optString("description", ""),
                url = json.optString("url", ""),
                version = json.optString("version", "1.0"),
                capabilities = json.optJSONArray("capabilities")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                skills = json.optJSONArray("skills")?.let { arr ->
                    (0 until arr.length()).map { AgentSkillInfo.fromJson(arr.getJSONObject(it)) }
                } ?: emptyList(),
                provider = json.optString("provider", ""),
                icon = json.optString("icon", "🤖")
            )
        }
    }
}

data class AgentSkillInfo(
    val name: String,
    val description: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
    }

    companion object {
        fun fromJson(json: JSONObject): AgentSkillInfo = AgentSkillInfo(
            name = json.optString("name", ""),
            description = json.optString("description", "")
        )
    }
}

/**
 * A2A Task — a unit of work between agents.
 */
data class A2ATask(
    val id: String = "task_${System.currentTimeMillis()}",
    val status: TaskStatus = TaskStatus.SUBMITTED,
    val message: String = "",
    val result: String = "",
    val artifacts: List<A2AArtifact> = emptyList(),
    val error: String = "",
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("status", status.name.lowercase())
        put("message", message)
        put("result", result)
        put("artifacts", JSONArray().apply {
            artifacts.forEach { put(it.toJson()) }
        })
        if (error.isNotBlank()) put("error", error)
        if (metadata.isNotEmpty()) {
            put("metadata", JSONObject().apply {
                metadata.forEach { (k, v) -> put(k, v) }
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): A2ATask = A2ATask(
            id = json.optString("id", ""),
            status = try {
                TaskStatus.valueOf(json.optString("status", "submitted").uppercase())
            } catch (_: Exception) { TaskStatus.SUBMITTED },
            message = json.optString("message", ""),
            result = json.optString("result", ""),
            artifacts = json.optJSONArray("artifacts")?.let { arr ->
                (0 until arr.length()).map { A2AArtifact.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
            error = json.optString("error", ""),
            metadata = json.optJSONObject("metadata")?.let { meta ->
                meta.keys().asSequence().associateWith { meta.getString(it) }
            } ?: emptyMap()
        )
    }
}

enum class TaskStatus {
    SUBMITTED,   // Task received
    WORKING,     // Agent is processing
    COMPLETED,   // Done successfully
    FAILED,      // Error occurred
    CANCELLED    // Cancelled by requester
}

/**
 * A2A Artifact — output produced by a task.
 */
data class A2AArtifact(
    val type: String,       // "text", "file", "image"
    val content: String,    // The content or file path
    val mimeType: String = "text/plain",
    val name: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("content", content)
        put("mimeType", mimeType)
        if (name.isNotBlank()) put("name", name)
    }

    companion object {
        fun fromJson(json: JSONObject): A2AArtifact = A2AArtifact(
            type = json.optString("type", "text"),
            content = json.optString("content", ""),
            mimeType = json.optString("mimeType", "text/plain"),
            name = json.optString("name", "")
        )
    }
}
