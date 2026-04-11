package com.beemovil.plugins.builtins

import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.memory.BeeMemoryDB
import org.json.JSONObject

class MemoryPlugin(private val memoryDB: BeeMemoryDB) : EmmaPlugin {
    override val id: String = "save_memory"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Memoriza un hecho importante sobre el usuario (ej: su nombre, gusto, profesión). Usa esta herramienta para recordar datos valiosos a largo plazo.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("fact", JSONObject().apply {
                        put("type", "string")
                        put("description", "El hecho o memoria a guardar (ej: 'El usuario se llama Juan', 'Al usuario le gusta el color azul').")
                    })
                })
                put("required", org.json.JSONArray().put("fact"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val fact = args["fact"]?.toString() ?: return "Error: No fact provided"
        memoryDB.saveMemory(fact)
        return "Hecho memorizado de forma exitosa: '\$fact'"
    }
}
