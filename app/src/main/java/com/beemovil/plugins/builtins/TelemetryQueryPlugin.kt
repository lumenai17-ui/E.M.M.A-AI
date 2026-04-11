package com.beemovil.plugins.builtins

import android.content.Context
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.telemetry.TelemetryDatabase
import com.beemovil.telemetry.DeviceScanner
import org.json.JSONObject

class TelemetryQueryPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "scan_telemetry"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Escaner omnisciente. Lee todos los sensores físicos crudos del teléfono, variables ambientales, batería, conexión WIFI e I/O en tiempo real.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        return try {
            val scanner = DeviceScanner(context)
            val state = scanner.getCurrentDeviceState()
            
            """
            TELEMETRIA FÍSICA EN TIEMPO REAL:
            - Batería: \${state.batteryPercent}% (Temperatura Nucleo: \${state.batteryTemp}°C)
            - Modo de Red: \${state.wifiSSID} | Operadora: \${state.telephonyOperator}
            - Geoposición (GPS/Red): Lat \${state.latitude ?: "N/A"}, Lon \${state.longitude ?: "N/A"}
            - Sensibilidad Lumínica: \${if (state.ambientLux >= 0f) state.ambientLux.toString() else "N/A"} lux
            - Pasos Totales del Acelerómetro: \${if (state.totalSteps >= 0f) state.totalSteps.toString() else "N/A"}
            - Estado Habilitado del Parlante: \${state.volumeLevel}/Max
            """.trimIndent()
            
        } catch (e: Exception) {
            "Aviso: Parcialmente falló la decodificación de HW: \${e.message}"
        }
    }
}
