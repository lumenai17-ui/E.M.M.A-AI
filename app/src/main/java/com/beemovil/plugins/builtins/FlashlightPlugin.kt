package com.beemovil.plugins.builtins

import android.content.Context
import android.hardware.camera2.CameraManager
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import org.json.JSONObject

class FlashlightPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "toggle_flashlight"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Enciende o apaga la linterna física (LED de la cámara) del dispositivo. Usa true para encender y false para apagar.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("turn_on", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "true para encender, false para apagar.")
                    })
                })
                put("required", org.json.JSONArray().put("turn_on"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val turnOn = args["turn_on"] as? Boolean ?: return "Error: no se especificó turn_on como booleano."
        
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0] // Asumimos que la lente 0 tiene flash
            cameraManager.setTorchMode(cameraId, turnOn)
            if (turnOn) "Linterna encendida físicamente." else "Linterna apagada físicamente."
        } catch (e: Exception) {
            "Error al intentar controlar la linterna física: \${e.message}"
        }
    }
}
