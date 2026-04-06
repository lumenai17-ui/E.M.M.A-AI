package com.beemovil.skills

import android.util.Log
import com.beemovil.a2a.A2AClient
import com.beemovil.a2a.AgentCard
import com.beemovil.a2a.TaskStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * RemoteAgentSkill — Allows Bee-Movil to send tasks to external A2A agents.
 *
 * This is the bridge between the local agent and the outside world.
 * The LLM can call this skill to delegate tasks to remote agents
 * (running on VPS, cloud, or another device).
 *
 * Usage from chat:
 * "Envía esta tarea al agente remoto: analiza este dataset"
 * → call_remote_agent(agent_url="https://my-agent.com", task="analiza este dataset")
 */
class RemoteAgentSkill(
    private val getRegisteredAgents: () -> List<AgentCard>
) : BeeSkill {

    companion object {
        private const val TAG = "RemoteAgentSkill"
    }

    override val name = "call_remote_agent"

    override val description = """Envía una tarea a un agente remoto externo usando el protocolo A2A.
Puedes enviar tareas a agentes que corren en servidores externos (VPS, cloud).
Necesitas la URL del agente o su nombre si está registrado en Settings.
El agente procesará la tarea y devolverá el resultado."""

    override val parametersSchema = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("agent_url", JSONObject().apply {
                put("type", "string")
                put("description", "URL del agente remoto (ej: https://mi-agente.com) o nombre del agente registrado")
            })
            put("task", JSONObject().apply {
                put("type", "string")
                put("description", "Descripción de la tarea que debe realizar el agente remoto")
            })
            put("action", JSONObject().apply {
                put("type", "string")
                put("description", "Acción: 'send' (enviar tarea), 'discover' (ver capacidades), 'list' (ver agentes registrados)")
            })
        })
        put("required", JSONArray().apply { put("action") })
    }

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "send")

        return when (action) {
            "list" -> listRegisteredAgents()
            "discover" -> discoverAgent(params)
            "send" -> sendTask(params)
            else -> JSONObject().put("error", "Acción desconocida: $action. Usa: send, discover, list")
        }
    }

    private fun listRegisteredAgents(): JSONObject {
        val agents = try { getRegisteredAgents() } catch (_: Exception) { emptyList() }
        return JSONObject().apply {
            put("success", true)
            put("count", agents.size)
            put("agents", JSONArray().apply {
                agents.forEach { agent ->
                    put(JSONObject().apply {
                        put("name", agent.name)
                        put("url", agent.url)
                        put("description", agent.description)
                        put("icon", agent.icon)
                        put("capabilities", JSONArray(agent.capabilities))
                    })
                }
            })
            if (agents.isEmpty()) {
                put("hint", "No hay agentes registrados. Ve a Settings → Agentes Remotos para agregar uno.")
            }
        }
    }

    private fun discoverAgent(params: JSONObject): JSONObject {
        val url = resolveAgentUrl(params.optString("agent_url", ""))
            ?: return JSONObject().put("error", "Falta agent_url o nombre del agente")

        Log.i(TAG, "Discovering agent at: $url")

        val card = A2AClient.discoverAgent(url)
            ?: return JSONObject().apply {
                put("error", "No se encontró un agente A2A en $url")
                put("hint", "Verifica que el servidor esté corriendo y tenga /.well-known/agent.json")
            }

        return JSONObject().apply {
            put("success", true)
            put("agent", card.toJson())
        }
    }

    private fun sendTask(params: JSONObject): JSONObject {
        val url = resolveAgentUrl(params.optString("agent_url", ""))
            ?: return JSONObject().put("error", "Falta agent_url. Usa 'list' para ver agentes registrados.")

        val task = params.optString("task", "")
        if (task.isBlank()) {
            return JSONObject().put("error", "Falta task. Describe qué debe hacer el agente.")
        }

        Log.i(TAG, "Sending task to $url: ${task.take(80)}")

        return try {
            val result = A2AClient.sendAndWait(
                agentUrl = url,
                message = task,
                maxWaitMs = 90_000 // 90 second timeout
            )

            JSONObject().apply {
                put("success", result.status == TaskStatus.COMPLETED)
                put("task_id", result.id)
                put("status", result.status.name.lowercase())
                put("result", result.result)
                if (result.error.isNotBlank()) put("error", result.error)
                if (result.artifacts.isNotEmpty()) {
                    put("artifacts", JSONArray().apply {
                        result.artifacts.forEach { put(it.toJson()) }
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote task failed: ${e.message}", e)
            JSONObject().apply {
                put("success", false)
                put("error", "Error enviando tarea: ${e.message?.take(200)}")
            }
        }
    }

    /**
     * Resolve agent URL — if user passes a name, look it up in registered agents.
     */
    private fun resolveAgentUrl(input: String): String? {
        if (input.isBlank()) return null

        // If it looks like a URL, use directly
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return input
        }

        // Try to find by name in registered agents
        val agents = try { getRegisteredAgents() } catch (_: Exception) { emptyList() }
        val found = agents.find {
            it.name.equals(input, ignoreCase = true) ||
            it.url.contains(input, ignoreCase = true)
        }

        return found?.url
    }
}
