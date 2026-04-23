package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.database.ChatHistoryDB
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.llm.ToolDefinition
import org.json.JSONArray
import org.json.JSONObject

/**
 * DelegateToAgentPlugin — Agent-to-Agent delegation.
 * 
 * Allows EMMA (supervisor) to delegate tasks to specialized agents
 * created by the user in La Forja. The delegated agent runs with
 * its own system prompt and model, and returns a text result.
 * 
 * ANTI-RECURSION: Delegated agents do NOT get access to this plugin,
 * preventing infinite A→B→A loops.
 * 
 * SAFETY: Disabled for local providers (Gemma) since running two
 * engines simultaneously would double RAM usage and crash.
 */
class DelegateToAgentPlugin(private val context: Context) : EmmaPlugin {

    private val TAG = "DelegateToAgent"
    override val id = "delegate_to_agent"

    override fun getToolDefinition() = ToolDefinition(
        name = id,
        description = "Delega una tarea a otro agente especializado creado por el usuario. " +
            "Usa esto cuando necesites la experiencia de un experto específico para completar parte de tu trabajo. " +
            "Ejemplo: 'delega al agente Redactor que escriba un resumen ejecutivo'. " +
            "El agente ejecuta con su propia inteligencia y devuelve el resultado como texto.",
        parameters = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("agent_name", JSONObject().apply {
                    put("type", "string")
                    put("description", "Nombre del agente a invocar (tal como aparece en La Forja)")
                })
                put("task", JSONObject().apply {
                    put("type", "string")
                    put("description", "La tarea específica que el agente debe realizar")
                })
            })
            put("required", JSONArray().put("agent_name").put("task"))
        }
    )

    override suspend fun execute(args: Map<String, Any>): String {
        val agentName = args["agent_name"] as? String
            ?: return "ERROR_TOOL_FAILED: Falta el nombre del agente."
        val task = args["task"] as? String
            ?: return "ERROR_TOOL_FAILED: Falta la tarea para el agente."

        return try {
            Log.i(TAG, "Delegando tarea a agente '$agentName': ${task.take(100)}")

            // 1. Check if we're on a local provider (block A2A)
            val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
            val currentProvider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
            if (currentProvider == "local" || currentProvider.startsWith("gemma")) {
                return "ERROR_TOOL_FAILED: La delegación entre agentes no está disponible en modo local (Gemma). " +
                    "Se necesita un proveedor en la nube. Realiza la tarea tú mismo."
            }

            // 2. Find the agent in Room DB
            val db = ChatHistoryDB.getDatabase(context)
            val allAgents = db.chatHistoryDao().getAllAgents()
            val agent = allAgents.find { 
                it.name.equals(agentName, ignoreCase = true) 
            } ?: run {
                val available = allAgents.joinToString(", ") { it.name }
                return "ERROR_TOOL_FAILED: Agente '$agentName' no encontrado. " +
                    "Agentes disponibles: $available. Informa al usuario."
            }

            Log.i(TAG, "Agente encontrado: ${agent.name} (${agent.fallbackModel})")

            // 3. Parse the agent's model config
            val modelParts = agent.fallbackModel.split(":", limit = 2)
            val providerId = if (modelParts.isNotEmpty() && modelParts[0] != "koog-engine") modelParts[0] else null
            val modelId = if (modelParts.size > 1) modelParts[1] else null

            // 4. Create a temporary engine WITHOUT DelegateToAgentPlugin (anti-recursion)
            val delegateEngine = EmmaEngine(context)
            delegateEngine.initialize()
            // Remove this plugin from the delegate to prevent recursion
            delegateEngine.plugins.remove(id)

            // 5. Set the agent's custom system prompt
            delegateEngine.clearMemoryAndHistory(agent.systemPrompt)

            // 6. Execute the task with the agent's model
            val result = delegateEngine.processUserMessage(
                message = task,
                forcedProvider = providerId,
                forcedModel = modelId
            )

            // 7. Compress result if too long (protect parent context window)
            val finalResult = if (result.length > 3000) {
                result.take(3000) + "\n\n[... resultado del agente '${agent.name}' comprimido a 3000 caracteres]"
            } else result

            Log.i(TAG, "Agente '${agent.name}' completó tarea (${finalResult.length} chars)")
            "Resultado del agente '${agent.name}':\n$finalResult"

        } catch (e: Exception) {
            Log.e(TAG, "Error delegando a agente '$agentName'", e)
            "ERROR_TOOL_FAILED: Error delegando tarea al agente '$agentName': ${e.message}"
        }
    }
}
