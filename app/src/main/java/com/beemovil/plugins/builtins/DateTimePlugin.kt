package com.beemovil.plugins.builtins

import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateTimePlugin : EmmaPlugin {
    override val id: String = "get_current_datetime"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Obtiene la fecha y hora actual del sistema. Usa esto si el usuario te pregunta qué hora es o la fecha de hoy.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val currentDateAndTime = sdf.format(Date())
        return "La fecha y hora actual es: $currentDateAndTime"
    }
}
