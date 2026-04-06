package com.beemovil.skills

import android.util.Log
import com.beemovil.agent.AgentConfig
import com.beemovil.agent.BeeAgent
import org.json.JSONArray
import org.json.JSONObject

/**
 * DelegateSkill — Allows the main agent to delegate tasks to other agents.
 *
 * This is what turns Bee-Movil from a single assistant into an agent orchestrator.
 * The main agent can call this skill like any other tool, passing the target agent
 * ID and the task to perform. The target agent runs synchronously, uses all its
 * own skills, and returns the result back to the main agent.
 *
 * Example flow:
 * 1. User: "Make me a quote for 100 shirts at $15"
 * 2. Main agent calls: delegate_to_agent(agent_id="ventas", task="Quote for 100 shirts at $15")
 * 3. DelegateSkill creates a temporary Ventas agent, calls chat()
 * 4. Ventas agent uses generate_pdf, returns result
 * 5. Main agent gets JSON result back and presents it to user
 */
class DelegateSkill(
    private val agentResolver: (String) -> BeeAgent?,
    private val getAvailableAgents: () -> List<AgentConfig>,
    private val onDelegation: ((agentId: String, task: String) -> Unit)? = null
) : BeeSkill {

    companion object {
        private const val TAG = "DelegateSkill"
        private const val TIMEOUT_MS = 90_000L // 90 second timeout for delegated agent
    }

    override val name = "delegate_to_agent"

    override val description = """Delega una tarea a otro agente especializado de Bee-Movil.
Usa esto cuando la tarea requiere un especialista:
- "ventas" → cotizaciones, documentos comerciales, PDFs profesionales
- "agenda" → calendario, eventos, recordatorios, morning brief
- "creativo" → imágenes, diseño, contenido visual
- También puedes delegar a agentes custom creados por el usuario.
El agente delegado ejecutará la tarea completa y devolverá el resultado."""

    override val parametersSchema = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("agent_id", JSONObject().apply {
                put("type", "string")
                put("description", "ID del agente destino (ventas, agenda, creativo, o ID de agente custom)")
            })
            put("task", JSONObject().apply {
                put("type", "string")
                put("description", "Descripción clara y completa de lo que debe hacer el agente")
            })
            put("context", JSONObject().apply {
                put("type", "string")
                put("description", "Información adicional relevante para el agente (datos del usuario, preferencias, etc)")
            })
        })
        put("required", JSONArray().apply { put("agent_id"); put("task") })
    }

    override fun execute(params: JSONObject): JSONObject {
        val agentId = params.optString("agent_id", "").trim()
        val task = params.optString("task", "").trim()
        val context = params.optString("context", "").trim()

        if (agentId.isBlank()) {
            return errorResult("Falta agent_id. Agentes disponibles: ${getAgentList()}")
        }
        if (task.isBlank()) {
            return errorResult("Falta task. Describe qué debe hacer el agente.")
        }

        Log.i(TAG, "Delegating to '$agentId': ${task.take(80)}")

        // Notify UI about delegation
        onDelegation?.invoke(agentId, task)

        // Resolve the target agent
        val agent = try {
            agentResolver(agentId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve agent '$agentId': ${e.message}")
            return errorResult("Error creando agente '$agentId': ${e.message}")
        }

        if (agent == null) {
            return errorResult(
                "Agente '$agentId' no encontrado. Disponibles: ${getAgentList()}"
            )
        }

        // Build the full task message (task + optional context)
        val fullMessage = buildString {
            append(task)
            if (context.isNotBlank()) {
                append("\n\n[Contexto del agente principal]: $context")
            }
        }

        // Execute the delegated agent synchronously with timeout
        return try {
            val startTime = System.currentTimeMillis()

            // Run agent.chat() — this is already synchronous and runs tool loops
            val response = agent.chat(fullMessage)

            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Delegation to '$agentId' completed in ${elapsed}ms")

            // Build result
            JSONObject().apply {
                put("success", true)
                put("agent_id", agentId)
                put("agent_name", agent.config.name)
                put("agent_icon", agent.config.icon)
                put("result", response.text)
                put("is_error", response.isError)
                put("elapsed_ms", elapsed)

                // Include tools the delegated agent used
                if (response.toolExecutions.isNotEmpty()) {
                    put("tools_used", JSONArray().apply {
                        response.toolExecutions.forEach { exec ->
                            put(exec.skillName)
                        }
                    })

                    // Extract file paths from delegated agent's tool results
                    val files = JSONArray()
                    response.toolExecutions.forEach { exec ->
                        arrayOf("path", "file_path", "filepath", "filePath", "output_path").forEach { key ->
                            if (exec.result.has(key)) {
                                files.put(exec.result.getString(key))
                            }
                        }
                    }
                    if (files.length() > 0) {
                        put("files", files)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delegation to '$agentId' failed: ${e.message}", e)
            errorResult("El agente '$agentId' falló: ${e.message?.take(200)}")
        }
    }

    private fun getAgentList(): String {
        return try {
            getAvailableAgents().joinToString(", ") { "${it.icon}${it.id}" }
        } catch (_: Exception) {
            "ventas, agenda, creativo"
        }
    }

    private fun errorResult(message: String): JSONObject {
        return JSONObject().apply {
            put("success", false)
            put("error", message)
        }
    }
}
