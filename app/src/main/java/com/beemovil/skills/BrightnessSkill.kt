package com.beemovil.skills

import android.content.Context
import android.provider.Settings
import org.json.JSONObject

/**
 * BrightnessSkill — control screen brightness.
 */
class BrightnessSkill(private val context: Context) : BeeSkill {
    override val name = "brightness"
    override val description = "Control screen brightness. Actions: 'get' (current brightness), 'set' (requires 'level' 0-100), 'auto_on', 'auto_off'"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["get","set","auto_on","auto_off"]},
            "level":{"type":"integer","description":"Brightness level 0-100"}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "get")

        return when (action) {
            "get" -> {
                try {
                    val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                    val percent = (brightness * 100) / 255
                    val autoMode = try {
                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
                    } catch (_: Exception) { 0 }

                    JSONObject()
                        .put("brightness", percent)
                        .put("raw", brightness)
                        .put("auto_brightness", autoMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                } catch (e: Exception) {
                    JSONObject().put("error", "Cannot read brightness: ${e.message}")
                }
            }
            "set" -> {
                val level = params.optInt("level", 50)
                try {
                    if (!Settings.System.canWrite(context)) {
                        return JSONObject().put("error", "La app necesita permiso para modificar configuración del sistema. Ve a Configuración → Apps → Bee-Movil → Modificar ajustes del sistema")
                    }
                    val value = (level * 255) / 100
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
                    JSONObject()
                        .put("success", true)
                        .put("brightness", level)
                        .put("message", "🔆 Brillo: $level%")
                } catch (e: Exception) {
                    JSONObject().put("error", "Cannot set brightness: ${e.message}")
                }
            }
            "auto_on" -> {
                try {
                    if (!Settings.System.canWrite(context)) {
                        return JSONObject().put("error", "Necesita permiso de escritura de sistema")
                    }
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                    JSONObject().put("success", true).put("message", "🔆 Brillo automático activado")
                } catch (e: Exception) {
                    JSONObject().put("error", "Error: ${e.message}")
                }
            }
            "auto_off" -> {
                try {
                    if (!Settings.System.canWrite(context)) {
                        return JSONObject().put("error", "Necesita permiso de escritura de sistema")
                    }
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    JSONObject().put("success", true).put("message", "🔆 Brillo manual activado")
                } catch (e: Exception) {
                    JSONObject().put("error", "Error: ${e.message}")
                }
            }
            else -> JSONObject().put("error", "Action not supported")
        }
    }
}
