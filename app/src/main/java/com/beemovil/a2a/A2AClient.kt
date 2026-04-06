package com.beemovil.a2a

import android.util.Log
import com.beemovil.network.BeeHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * A2AClient — Sends tasks to remote A2A-compatible agents.
 *
 * Usage:
 * 1. Discover agent via Agent Card (GET /.well-known/agent.json)
 * 2. Send task (POST /tasks)
 * 3. Poll for result (GET /tasks/{id})
 *
 * This client works with any A2A-compatible server, including:
 * - BEE-Dashboard agents
 * - OpenClaw agents
 * - Any Google A2A compliant agent
 */
object A2AClient {
    private const val TAG = "A2AClient"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Discover a remote agent by fetching its Agent Card.
     * Tries /.well-known/agent.json first, then /agent.json as fallback.
     */
    fun discoverAgent(baseUrl: String): AgentCard? {
        val cleanUrl = baseUrl.trimEnd('/')

        // Try standard well-known path first
        val urls = listOf(
            "$cleanUrl/.well-known/agent.json",
            "$cleanUrl/agent.json"
        )

        for (url in urls) {
            try {
                Log.d(TAG, "Discovering agent at: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "BeeMovil/4.2.7 A2A-Client")
                    .build()

                val response = BeeHttpClient.default.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    response.close()
                    val card = AgentCard.fromJson(JSONObject(body))
                    Log.i(TAG, "Discovered agent: ${card.name} at $cleanUrl")
                    return card.copy(url = cleanUrl) // Ensure URL is set
                }
                response.close()
            } catch (e: Exception) {
                Log.d(TAG, "Discovery failed at $url: ${e.message}")
            }
        }

        Log.w(TAG, "No agent found at $baseUrl")
        return null
    }

    /**
     * Send a task to a remote agent.
     * Returns the task with initial status (usually SUBMITTED or WORKING).
     */
    fun sendTask(agentUrl: String, message: String, metadata: Map<String, String> = emptyMap()): A2ATask {
        val cleanUrl = agentUrl.trimEnd('/')
        val task = A2ATask(
            message = message,
            status = TaskStatus.SUBMITTED,
            metadata = metadata
        )

        val requestBody = JSONObject().apply {
            put("message", message)
            put("task_id", task.id)
            if (metadata.isNotEmpty()) {
                put("metadata", JSONObject().apply {
                    metadata.forEach { (k, v) -> put(k, v) }
                })
            }
        }

        Log.i(TAG, "Sending task to $cleanUrl: ${message.take(80)}")

        val request = Request.Builder()
            .url("$cleanUrl/tasks")
            .header("User-Agent", "BeeMovil/4.2.7 A2A-Client")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_TYPE))
            .build()

        val response = BeeHttpClient.llm.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()

        if (!response.isSuccessful) {
            Log.e(TAG, "Task send failed: HTTP ${response.code}")
            return task.copy(
                status = TaskStatus.FAILED,
                error = "HTTP ${response.code}: $body"
            )
        }

        return try {
            val result = JSONObject(body)
            A2ATask.fromJson(result)
        } catch (e: Exception) {
            // Simple text response — treat as completed
            task.copy(status = TaskStatus.COMPLETED, result = body)
        }
    }

    /**
     * Poll for task status/result.
     */
    fun getTaskStatus(agentUrl: String, taskId: String): A2ATask? {
        val cleanUrl = agentUrl.trimEnd('/')

        try {
            val request = Request.Builder()
                .url("$cleanUrl/tasks/$taskId")
                .header("User-Agent", "BeeMovil/4.2.7 A2A-Client")
                .build()

            val response = BeeHttpClient.default.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) return null

            return A2ATask.fromJson(JSONObject(body))
        } catch (e: Exception) {
            Log.e(TAG, "Task status poll failed: ${e.message}")
            return null
        }
    }

    /**
     * Send a task and wait for completion (polling).
     * Blocks the calling thread. Use from background thread only.
     */
    fun sendAndWait(
        agentUrl: String,
        message: String,
        maxWaitMs: Long = 120_000,
        pollIntervalMs: Long = 2_000,
        metadata: Map<String, String> = emptyMap()
    ): A2ATask {
        val task = sendTask(agentUrl, message, metadata)

        // If already completed or failed, return immediately
        if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.FAILED) {
            return task
        }

        // Poll for completion
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(pollIntervalMs)

            val updated = getTaskStatus(agentUrl, task.id)
            if (updated != null) {
                when (updated.status) {
                    TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED -> return updated
                    else -> Log.d(TAG, "Task ${task.id} still ${updated.status}")
                }
            }
        }

        // Timeout
        return task.copy(
            status = TaskStatus.FAILED,
            error = "Timeout: el agente remoto no respondió en ${maxWaitMs / 1000}s"
        )
    }
}
