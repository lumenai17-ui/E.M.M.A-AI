package com.beemovil.plugins.builtins

import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.plugins.SecurityGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * SystemControlPlugin — Project Autonomía Phase S3
 *
 * Brightness, DND, wallpaper, share. All 🟡 YELLOW.
 */
class SystemControlPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "emma_system_control"
    private val TAG = "SystemControlPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Control avanzado del sistema: brillo, No Molestar, wallpaper. Usa cuando digan 'sube el brillo', 'no me molesten', 'ponme un wallpaper'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray()
                            .put("get_brightness")
                            .put("set_brightness")
                            .put("get_dnd_status")
                            .put("toggle_dnd")
                            .put("set_wallpaper")
                        )
                    })
                    put("brightness_level", JSONObject().apply {
                        put("type", "integer")
                        put("description", "(set_brightness) Nivel 0-255. 0=oscuro, 128=medio, 255=máximo.")
                    })
                    put("dnd_enabled", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "(toggle_dnd) true=activar No Molestar, false=desactivar.")
                    })
                    put("image_url", JSONObject().apply {
                        put("type", "string")
                        put("description", "(set_wallpaper) URL de la imagen para wallpaper. Puedes usar Pollinations: https://image.pollinations.ai/prompt/DESCRIPCION?width=1080&height=1920&nologo=true")
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
                    "get_brightness" -> getBrightness()
                    "set_brightness" -> setBrightness(args)
                    "get_dnd_status" -> getDndStatus()
                    "toggle_dnd" -> toggleDnd(args)
                    "set_wallpaper" -> setWallpaper(args)
                    else -> "Operación desconocida: $operation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "SystemControl error", e)
                "Error: ${e.message}"
            }
        }
    }

    private fun getBrightness(): String {
        return try {
            val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            val pct = (brightness * 100 / 255)
            "Brillo actual: $brightness/255 ($pct%)"
        } catch (e: Exception) {
            "No se pudo leer el brillo: ${e.message}"
        }
    }

    private suspend fun setBrightness(args: Map<String, Any>): String {
        val level = (args["brightness_level"] as? Number)?.toInt() ?: return "Falta 'brightness_level' (0-255)."
        val clamped = level.coerceIn(0, 255)

        if (!Settings.System.canWrite(context)) {
            return "⚠️ E.M.M.A. no tiene permiso para modificar ajustes del sistema. El usuario debe ir a Settings → Apps → E.M.M.A. → 'Modify system settings' y habilitarlo."
        }

        val op = SecurityGate.yellow(id, "set_brightness", "Cambiar brillo a $clamped/255 (${clamped * 100 / 255}%)")
        if (!SecurityGate.evaluate(op)) return "Cancelado."

        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, clamped)
        return "Brillo cambiado a $clamped/255 (${clamped * 100 / 255}%) ✅"
    }

    private fun getDndStatus(): String {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val filter = nm.currentInterruptionFilter
        val status = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "Desactivado (todas las notificaciones pasan)"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Solo prioritarias"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "Silencio total"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Solo alarmas"
            else -> "Desconocido ($filter)"
        }
        return "No Molestar: $status"
    }

    private suspend fun toggleDnd(args: Map<String, Any>): String {
        val enabled = args["dnd_enabled"] as? Boolean ?: return "Falta 'dnd_enabled'."
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!nm.isNotificationPolicyAccessGranted) {
            return "⚠️ E.M.M.A. no tiene acceso a Do Not Disturb. El usuario debe ir a Settings → Apps → Acceso especial → No molestar y habilitar E.M.M.A."
        }

        val op = SecurityGate.yellow(id, "toggle_dnd", if (enabled) "Activar No Molestar" else "Desactivar No Molestar")
        if (!SecurityGate.evaluate(op)) return "Cancelado."

        nm.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return "No Molestar ${if (enabled) "activado ✅" else "desactivado ✅"}"
    }

    private suspend fun setWallpaper(args: Map<String, Any>): String {
        val imageUrl = args["image_url"] as? String ?: return "Falta 'image_url'."

        val op = SecurityGate.yellow(id, "set_wallpaper", "Cambiar wallpaper desde URL")
        if (!SecurityGate.evaluate(op)) return "Cancelado."

        return try {
            val stream = URL(imageUrl).openStream()
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()

            if (bitmap == null) return "No se pudo decodificar la imagen desde la URL."

            withContext(Dispatchers.Main) {
                val wm = WallpaperManager.getInstance(context)
                wm.setBitmap(bitmap)
                bitmap.recycle()
            }
            "Wallpaper actualizado ✅"
        } catch (e: Exception) {
            Log.e(TAG, "Wallpaper error", e)
            "Error al cambiar wallpaper: ${e.message}"
        }
    }
}
