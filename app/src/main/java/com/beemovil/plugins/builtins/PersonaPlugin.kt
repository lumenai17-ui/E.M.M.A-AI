package com.beemovil.plugins.builtins

import com.beemovil.llm.ToolDefinition
import com.beemovil.memory.PersonaManager
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PersonaPlugin(private val personaManager: PersonaManager) : EmmaPlugin {

    override val id = "manage_persona"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Herramienta maestra para leer y modificar tu identidad (Soul), tu relación con el usuario (Heart) y el perfil del usuario (User Profile). " +
                    "Úsala para recordar activamente gustos del usuario, proyectos, hitos importantes o para consultar tus propias directivas. " +
                    "La memoria está estructurada en 3 archivos JSON: soul (tus reglas), heart (hitos compartidos), user (datos del usuario).",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("read_soul", "read_heart", "read_user", "update_user_fact", "add_heart_milestone")))
                        put("description", "Acción a realizar. Usa read_* para consultar. Usa update_user_fact para aprender algo del usuario. Usa add_heart_milestone para registrar un momento importante compartido.")
                    })
                    put("category", JSONObject().apply {
                        put("type", "string")
                        put("description", "SOLO para update_user_fact. Categoría lógica del dato (ej: 'preferences', 'contacts', 'projects', 'known_facts').")
                    })
                    put("key", JSONObject().apply {
                        put("type", "string")
                        put("description", "SOLO para update_user_fact. Llave específica (ej: 'favorite_drink', 'lumen_number').")
                    })
                    put("value", JSONObject().apply {
                        put("type", "string")
                        put("description", "SOLO para update_user_fact. El valor a guardar (ej: 'Café negro sin azúcar').")
                    })
                    put("milestone", JSONObject().apply {
                        put("type", "string")
                        put("description", "SOLO para add_heart_milestone. Descripción del hito importante a recordar.")
                    })
                })
                put("required", JSONArray(listOf("action")))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val action = args["action"] as? String ?: return@withContext "ERROR: 'action' requerida."

        try {
            when (action) {
                "read_soul" -> "SOUL_JSON:\n" + personaManager.readSoul()
                "read_heart" -> "HEART_JSON:\n" + personaManager.readHeart()
                "read_user" -> "USER_PROFILE_JSON:\n" + personaManager.readUserProfile()
                
                "update_user_fact" -> {
                    val cat = args["category"] as? String ?: return@withContext "ERROR: 'category' requerida."
                    val key = args["key"] as? String ?: return@withContext "ERROR: 'key' requerida."
                    val value = args["value"] as? String ?: return@withContext "ERROR: 'value' requerida."
                    
                    val success = personaManager.updateUserFact(cat, key, value)
                    if (success) "SUCCESS: El perfil del usuario fue actualizado correctamente. ($cat -> $key: $value)"
                    else "ERROR: Fallo al escribir en el JSON del usuario."
                }
                
                "add_heart_milestone" -> {
                    val ms = args["milestone"] as? String ?: return@withContext "ERROR: 'milestone' requerido."
                    val success = personaManager.addHeartMilestone(ms)
                    if (success) "SUCCESS: Hito emocional agregado a heart.json."
                    else "ERROR: Fallo al escribir en heart.json."
                }
                
                else -> "ERROR: Acción desconocida."
            }
        } catch (e: Exception) {
            "ERROR: Fallo en PersonaPlugin -> ${e.message}"
        }
    }
}
