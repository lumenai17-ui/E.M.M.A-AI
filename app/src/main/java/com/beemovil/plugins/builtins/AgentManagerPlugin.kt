package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.database.AgentConfigEntity
import com.beemovil.database.ChatHistoryDB
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.plugins.SecurityGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * AgentManagerPlugin — Project Autonomía Phase S2
 *
 * E.M.M.A. can create, edit, clone, and delete agents from chat.
 * "Créame un agente nutriólogo" → creates a new agent in the database.
 *
 * Security: create/edit = 🟡 YELLOW, delete = 🔴 RED
 */
class AgentManagerPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "emma_agent_manager"
    private val TAG = "AgentManagerPlugin"
    private val db by lazy { ChatHistoryDB.getDatabase(context) }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = """Gestiona los agentes especializados de E.M.M.A. Úsala cuando el usuario diga:
                'créame un agente', 'quiero un asistente de cocina', 'edita el agente X',
                'borra el agente Y', 'clona el agente Z', 'qué agentes tengo'.
                Los agentes son personalidades con instrucciones y modelo específico.""".trimIndent(),
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray()
                            .put("list")
                            .put("create")
                            .put("edit")
                            .put("clone")
                            .put("delete")
                        )
                        put("description", """Operación:
                            - list: Listar todos los agentes
                            - create: Crear un agente nuevo
                            - edit: Editar un agente existente
                            - clone: Duplicar un agente con nuevo nombre
                            - delete: Eliminar un agente""".trimIndent())
                    })
                    put("agent_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para edit/clone/delete) ID del agente a modificar.")
                    })
                    put("name", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para create/edit/clone) Nombre del agente.")
                    })
                    put("icon", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para create/edit) Emoji que represente al agente (ej: 🧑‍🍳, 💼, 🏋️).")
                    })
                    put("system_prompt", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para create/edit) Instrucciones del sistema que definen la personalidad y expertise del agente. Sé detallado.")
                    })
                    put("model", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Para create/edit) Modelo LLM preferido en formato 'provider:model_id' (ej: 'openrouter:openai/gpt-4o-mini'). Dejar vacío para usar el default.")
                    })
                })
                put("required", JSONArray().put("operation"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val operation = args["operation"] as? String ?: return "Falta 'operation'."

        return withContext(Dispatchers.IO) {
            try {
                when (operation) {
                    "list" -> listAgents()
                    "create" -> createAgent(args)
                    "edit" -> editAgent(args)
                    "clone" -> cloneAgent(args)
                    "delete" -> deleteAgent(args)
                    else -> "Operación desconocida: $operation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "AgentManager error", e)
                "Error en gestión de agentes: ${e.message}"
            }
        }
    }

    private suspend fun listAgents(): String {
        val agents = db.chatHistoryDao().getAllAgents()
        if (agents.isEmpty()) {
            return "No hay agentes personalizados. Solo está E.M.M.A. principal.\nPuedes crear uno con: 'Créame un agente de [especialidad]'"
        }

        return buildString {
            appendLine("═══ AGENTES (${agents.size}) ═══")
            agents.forEach { agent ->
                appendLine()
                appendLine("${agent.icon} ${agent.name}")
                appendLine("  ID: ${agent.agentId}")
                appendLine("  Modelo: ${agent.fallbackModel.ifBlank { "Default" }}")
                appendLine("  Prompt: ${agent.systemPrompt.take(120)}...")
            }
        }
    }

    private suspend fun createAgent(args: Map<String, Any>): String {
        val name = args["name"] as? String ?: return "Falta el nombre del agente."
        val icon = args["icon"] as? String ?: "🤖"
        val prompt = args["system_prompt"] as? String ?: return "Falta el system_prompt del agente."
        val model = args["model"] as? String ?: ""

        // SecurityGate: YELLOW
        val op = SecurityGate.yellow(id, "create", "Crear agente: $icon $name")
        if (!SecurityGate.evaluate(op)) {
            return "Creación cancelada por el usuario."
        }

        val agentId = "agent_${UUID.randomUUID().toString().take(8)}"
        val entity = AgentConfigEntity(
            agentId = agentId,
            name = name,
            icon = icon,
            systemPrompt = prompt,
            fallbackModel = model
        )

        db.chatHistoryDao().insertAgent(entity)
        Log.i(TAG, "Agent created: $agentId ($name)")

        return "Agente creado ✅\n  ${icon} ${name}\n  ID: $agentId\n  Modelo: ${model.ifBlank { "Default" }}\n  Prompt: ${prompt.take(100)}..."
    }

    private suspend fun editAgent(args: Map<String, Any>): String {
        val agentId = args["agent_id"] as? String ?: return "Falta agent_id del agente a editar."
        val existing = db.chatHistoryDao().getAgent(agentId)
            ?: return "No se encontró agente con ID: $agentId"

        // SecurityGate: YELLOW
        val op = SecurityGate.yellow(id, "edit", "Editar agente: ${existing.icon} ${existing.name}")
        if (!SecurityGate.evaluate(op)) {
            return "Edición cancelada."
        }

        val updated = existing.copy(
            name = args["name"] as? String ?: existing.name,
            icon = args["icon"] as? String ?: existing.icon,
            systemPrompt = args["system_prompt"] as? String ?: existing.systemPrompt,
            fallbackModel = args["model"] as? String ?: existing.fallbackModel
        )

        db.chatHistoryDao().insertAgent(updated) // REPLACE on conflict
        Log.i(TAG, "Agent updated: $agentId")

        return "Agente actualizado ✅\n  ${updated.icon} ${updated.name}\n  Modelo: ${updated.fallbackModel.ifBlank { "Default" }}"
    }

    private suspend fun cloneAgent(args: Map<String, Any>): String {
        val agentId = args["agent_id"] as? String ?: return "Falta agent_id del agente a clonar."
        val existing = db.chatHistoryDao().getAgent(agentId)
            ?: return "No se encontró agente con ID: $agentId"

        val newName = args["name"] as? String ?: "${existing.name} (copia)"

        // SecurityGate: YELLOW
        val op = SecurityGate.yellow(id, "clone", "Clonar agente: ${existing.icon} ${existing.name} → $newName")
        if (!SecurityGate.evaluate(op)) {
            return "Clonación cancelada."
        }

        val newId = "agent_${UUID.randomUUID().toString().take(8)}"
        val clone = existing.copy(agentId = newId, name = newName)
        db.chatHistoryDao().insertAgent(clone)
        Log.i(TAG, "Agent cloned: $agentId → $newId")

        return "Agente clonado ✅\n  ${clone.icon} ${clone.name}\n  ID: $newId (original: $agentId)"
    }

    private suspend fun deleteAgent(args: Map<String, Any>): String {
        val agentId = args["agent_id"] as? String ?: return "Falta agent_id del agente a eliminar."
        val existing = db.chatHistoryDao().getAgent(agentId)
            ?: return "No se encontró agente con ID: $agentId"

        // SecurityGate: RED — destructive
        val op = SecurityGate.red(id, "delete", "Eliminar agente permanentemente: ${existing.icon} ${existing.name}")
        if (!SecurityGate.evaluate(op)) {
            return "Eliminación cancelada."
        }

        db.chatHistoryDao().deleteAgent(agentId)
        Log.i(TAG, "Agent deleted: $agentId (${existing.name})")

        return "Agente eliminado ✅: ${existing.icon} ${existing.name}"
    }
}
