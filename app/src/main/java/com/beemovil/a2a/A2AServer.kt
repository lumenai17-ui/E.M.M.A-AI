package com.beemovil.a2a

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A2AServer — Lightweight HTTP server that exposes Bee-Movil as an A2A agent.
 *
 * This allows external agents to send tasks TO Bee-Movil on the local network.
 *
 * Endpoints:
 * - GET  /.well-known/agent.json  → Agent Card (capabilities)
 * - POST /tasks                    → Submit a new task
 * - GET  /tasks/{id}               → Get task status/result
 * - GET  /status                   → Server health check
 *
 * Runs on port 8765 by default (configurable).
 * Only accessible on local network (WiFi).
 */
class A2AServer(
    private val port: Int = 8765,
    private val onTaskReceived: (A2ATask) -> A2ATask  // Process task synchronously
) {
    companion object {
        private const val TAG = "A2AServer"
    }

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(2)
    private val tasks = ConcurrentHashMap<String, A2ATask>()
    @Volatile private var running = false

    /** Bee-Movil's Agent Card */
    private val agentCard = AgentCard(
        name = "Bee-Movil",
        description = "Asistente AI con 36 skills nativos corriendo en un teléfono Android. " +
            "Puede buscar en internet, generar PDFs, crear landing pages, " +
            "enviar emails, tomar fotos, controlar dispositivos y más.",
        url = "",  // Set dynamically when server starts
        version = "4.2.7",
        capabilities = listOf("text", "file", "image", "vision", "voice", "tools"),
        skills = listOf(
            AgentSkillInfo("web_search", "Busca en internet"),
            AgentSkillInfo("generate_pdf", "Genera documentos PDF"),
            AgentSkillInfo("generate_html", "Crea páginas HTML"),
            AgentSkillInfo("email", "Enviar correos"),
            AgentSkillInfo("calendar", "Gestión de calendario"),
            AgentSkillInfo("camera", "Tomar fotos"),
            AgentSkillInfo("browser_agent", "Navegar y extraer datos web"),
            AgentSkillInfo("file_manager", "Gestión de archivos"),
            AgentSkillInfo("weather", "Consultar clima"),
            AgentSkillInfo("contacts", "Acceso a contactos"),
            AgentSkillInfo("run_code", "Ejecutar JavaScript"),
            AgentSkillInfo("delegate_to_agent", "Delegar a agentes especializados")
        ),
        provider = "Bee-Movil",
        icon = "🐝"
    )

    fun start() {
        if (running) {
            Log.w(TAG, "Server already running")
            return
        }

        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                running = true
                Log.i(TAG, "A2A Server started on port $port")

                while (running) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor.execute { handleConnection(client) }
                    } catch (e: Exception) {
                        if (running) Log.e(TAG, "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server start failed: ${e.message}", e)
            }
        }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        Log.i(TAG, "A2A Server stopped")
    }

    fun isRunning() = running
    fun getPort() = port

    private fun handleConnection(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)

            // Parse HTTP request line
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
                line = reader.readLine()
            }

            // Read body if present
            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                reader.read(chars, 0, contentLength)
                String(chars)
            } else ""

            Log.d(TAG, "$method $path (${body.length} bytes)")

            // Route request
            val (statusCode, responseBody) = route(method, path, body)

            // Send response
            val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
            writer.print("HTTP/1.1 $statusCode\r\n")
            writer.print("Content-Type: application/json; charset=utf-8\r\n")
            writer.print("Content-Length: ${responseBytes.size}\r\n")
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            writer.print("Access-Control-Allow-Headers: Content-Type\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()
            client.getOutputStream().write(responseBytes)
            client.getOutputStream().flush()

        } catch (e: Exception) {
            Log.e(TAG, "Connection handling error: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun route(method: String, path: String, body: String): Pair<String, String> {
        // CORS preflight
        if (method == "OPTIONS") {
            return "200 OK" to "{}"
        }

        return when {
            // Agent Card discovery
            path == "/.well-known/agent.json" || path == "/agent.json" -> {
                "200 OK" to agentCard.toJson().toString(2)
            }

            // Health check
            path == "/status" -> {
                "200 OK" to JSONObject().apply {
                    put("status", "ok")
                    put("agent", "Bee-Movil")
                    put("version", "4.2.7")
                    put("tasks_processed", tasks.size)
                }.toString(2)
            }

            // Submit new task
            method == "POST" && path == "/tasks" -> {
                handleNewTask(body)
            }

            // Get task status
            method == "GET" && path.startsWith("/tasks/") -> {
                val taskId = path.removePrefix("/tasks/")
                handleGetTask(taskId)
            }

            else -> {
                "404 Not Found" to JSONObject().apply {
                    put("error", "Not found: $path")
                    put("endpoints", JSONArray().apply {
                        put("GET /.well-known/agent.json")
                        put("POST /tasks")
                        put("GET /tasks/{id}")
                        put("GET /status")
                    })
                }.toString(2)
            }
        }
    }

    private fun handleNewTask(body: String): Pair<String, String> {
        return try {
            val json = JSONObject(body)
            val message = json.optString("message", "")
            val taskId = json.optString("task_id", "task_${System.currentTimeMillis()}")

            if (message.isBlank()) {
                return "400 Bad Request" to JSONObject()
                    .put("error", "Missing 'message' field").toString()
            }

            var task = A2ATask(
                id = taskId,
                message = message,
                status = TaskStatus.WORKING
            )
            tasks[taskId] = task

            // Process task (this calls the agent)
            try {
                task = onTaskReceived(task)
                tasks[taskId] = task
            } catch (e: Exception) {
                task = task.copy(
                    status = TaskStatus.FAILED,
                    error = e.message ?: "Processing error"
                )
                tasks[taskId] = task
            }

            "200 OK" to task.toJson().toString(2)

        } catch (e: Exception) {
            "400 Bad Request" to JSONObject()
                .put("error", "Invalid request: ${e.message}").toString()
        }
    }

    private fun handleGetTask(taskId: String): Pair<String, String> {
        val task = tasks[taskId]
            ?: return "404 Not Found" to JSONObject()
                .put("error", "Task '$taskId' not found").toString()

        return "200 OK" to task.toJson().toString(2)
    }
}
