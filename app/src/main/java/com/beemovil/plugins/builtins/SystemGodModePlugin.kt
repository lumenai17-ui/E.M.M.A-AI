package com.beemovil.plugins.builtins

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SystemGodModePlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "os_god_mode_operations"
    private val TAG = "SystemGodModePlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Herramienta maestra de control físico del entorno de Android. Úsala para: 'Ponme una Alarma', 'Avísame en X minutos', 'Prende la Linterna', 'Sube/baja el brillo', 'Cambia el volumen'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("set_alarm").put("set_timer").put("toggle_flashlight").put("set_brightness").put("set_volume").put("toggle_wifi"))
                        put("description", "Operación requerida. set_alarm (alarma específica), set_timer (temporizador en reversa), toggle_flashlight (linterna), set_brightness (brillo pantalla), set_volume (volumen de media), toggle_wifi (abrir panel de wifi).")
                    })
                    put("alarm_hour", JSONObject().apply {
                        put("type", "integer")
                        put("description", "(Solo para set_alarm) La hora en formato 24 hrs (0-23).")
                    })
                    put("alarm_minute", JSONObject().apply {
                        put("type", "integer")
                        put("description", "(Solo para set_alarm) Los minutos (0-59).")
                    })
                    put("timer_minutes", JSONObject().apply {
                        put("type", "integer")
                        put("description", "(Solo para set_timer) Cuántos minutos en el futuro poner el cronómetro. (Ej: 'avísame en 50 min' -> 50).")
                    })
                    put("flashlight_on", JSONObject().apply {
                        put("type", "boolean")
                        put("description", "(Solo para toggle_flashlight) 'true' para encenderla, 'false' para apagarla.")
                    })
                    put("brightness_level", JSONObject().apply {
                        put("type", "integer")
                        put("description", "(Solo para set_brightness) Nivel de brillo de 0 a 100.")
                    })
                    put("volume_level", JSONObject().apply {
                        put("type", "integer")
                        put("description", "(Solo para set_volume) Nivel de volumen de 0 a 100.")
                    })
                })
                put("required", JSONArray().put("operation"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val operation = args["operation"] as? String ?: return "Falta 'operation'."

        return withContext(Dispatchers.Main) { // Intents and CameraManager usually prefer Main thread or are fast enough
            try {
                when (operation) {
                    "set_alarm" -> {
                        val hour = (args["alarm_hour"] as? Number)?.toInt() ?: return@withContext "Falta la hora."
                        val min = (args["alarm_minute"] as? Number)?.toInt() ?: 0
                        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_HOUR, hour)
                            putExtra(AlarmClock.EXTRA_MINUTES, min)
                            putExtra(AlarmClock.EXTRA_MESSAGE, "Programado por E.M.M.A. AI")
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Abrir la UI para que el usuario corrobore
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        "La directiva fue enviada al servicio Reloj Android exitosamente para crear una alarma a las $hour:$min."
                    }
                    "set_timer" -> {
                        val timerMinutes = (args["timer_minutes"] as? Number)?.toInt() ?: return@withContext "No pasaste los minutos del Timer."
                        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                            putExtra(AlarmClock.EXTRA_LENGTH, timerMinutes * 60) // En segundos
                            putExtra(AlarmClock.EXTRA_MESSAGE, "Timer E.M.M.A. AI")
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        "Se le inyectó la orden de Temporizador a Android por $timerMinutes minutos."
                    }
                    "toggle_flashlight" -> {
                        val turnOn = args["flashlight_on"] as? Boolean ?: true
                        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return@withContext "El hardware del dispositivo no reporta una cámara válida."
                        cameraManager.setTorchMode(cameraId, turnOn)
                        "Linterna de poder físico " + (if (turnOn) "ENCENDIDA." else "APAGADA.")
                    }
                    "set_brightness" -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.System.canWrite(context)) {
                            return@withContext "TOOL_CALL::request_permission::SYSTEM_SETTINGS"
                        }
                        val level = (args["brightness_level"] as? Number)?.toInt() ?: 50
                        // Convert 0-100 to 0-255
                        val sysLevel = (level.coerceIn(0, 100) * 255) / 100
                        android.provider.Settings.System.putInt(
                            context.contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        )
                        android.provider.Settings.System.putInt(
                            context.contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS,
                            sysLevel
                        )
                        "Brillo de pantalla ajustado al $level%."
                    }
                    "set_volume" -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.System.canWrite(context)) {
                            return@withContext "TOOL_CALL::request_permission::SYSTEM_SETTINGS"
                        }
                        val level = (args["volume_level"] as? Number)?.toInt() ?: 50
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        val sysVolume = (level.coerceIn(0, 100) * maxVolume) / 100
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, sysVolume, android.media.AudioManager.FLAG_SHOW_UI)
                        "Volumen multimedia ajustado al $level%."
                    }
                    "toggle_wifi" -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val panelIntent = Intent(android.provider.Settings.Panel.ACTION_WIFI)
                            panelIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(panelIntent)
                            "El panel de WiFi se ha desplegado para que el usuario pueda interactuar con la red."
                        } else {
                            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                            val newState = if (wifiManager.isWifiEnabled) false else true
                            wifiManager.isWifiEnabled = newState
                            "WiFi físico ha sido " + (if (newState) "ENCENDIDO." else "APAGADO.")
                        }
                    }
                    else -> "Operación desconocida: $operation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "God Mode Exception", e)
                "El subsistema blindado de Android escudó la operación. Error: ${e.message}"
            }
        }
    }
}
