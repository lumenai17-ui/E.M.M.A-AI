package com.beemovil.plugins.builtins

import android.content.Context
import android.media.AudioManager
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import org.json.JSONObject

class VolumePlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "set_volume_level"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Silencia o ajusta el volumen multimedia del sistema operativo Android.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("percentage", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Nivel de volumen del 0 al 100.")
                    })
                })
                put("required", org.json.JSONArray().put("percentage"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val rawPercent = args["percentage"]
        val percent = when(rawPercent) {
            is Int -> rawPercent
            is Double -> rawPercent.toInt()
            is String -> rawPercent.toIntOrNull() ?: 50
            else -> 50
        }
        
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val mappedVolume = (percent / 100.0 * maxVolume).toInt().coerceIn(0, maxVolume)
            
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mappedVolume, 0)
            "Volumen global del teléfono ajustado físicamente al \$percent%."
        } catch (e: Exception) {
            "Error al mutear/cambiar el volumen: \${e.message}"
        }
    }
}
