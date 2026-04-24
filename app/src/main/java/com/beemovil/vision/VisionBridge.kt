package com.beemovil.vision

import android.content.Context
import android.util.Log
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * VisionBridge — Phase V7: La Inteligencia
 *
 * Connects voice intents from LiveVision to E.M.M.A.'s 27 existing plugins.
 * "Send this to Maria on WhatsApp" → WhatsAppAutomatorPlugin
 * "Make a PDF of the meeting" → PremiumPdfPlugin
 * "Turn on the flashlight" → FlashlightPlugin
 */
class VisionBridge(
    private val context: Context,
    private val plugins: Map<String, EmmaPlugin>
) {

    companion object {
        private const val TAG = "VisionBridge"
    }

    /**
     * Execute a vision-triggered action via the appropriate plugin.
     * Returns a human-readable result for TTS narration.
     */
    suspend fun execute(
        actionType: ActionType,
        params: Map<String, String>,
        sessionLog: String = "",
        lastResult: String = ""
    ): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Executing action: $actionType with ${params.size} params")

        try {
            when (actionType) {
                ActionType.SEND_WHATSAPP -> {
                    val plugin = plugins["send_whatsapp_message"]
                        ?: return@withContext "WhatsApp no disponible"
                    val contact = params["contact"] ?: ""
                    val message = params["message"] ?: lastResult.take(200)
                    plugin.execute(mapOf(
                        "phone_number" to contact,
                        "generated_message" to message
                    ))
                }

                ActionType.SEND_EMAIL -> {
                    val plugin = plugins["compose_email_intent"]
                        ?: return@withContext "Email no disponible"
                    plugin.execute(mapOf(
                        "to" to (params["to"] ?: ""),
                        "subject" to (params["subject"] ?: "Desde E.M.M.A. Vision"),
                        "body" to (params["body"] ?: sessionLog.take(500))
                    ))
                }

                ActionType.GENERATE_PDF -> {
                    val plugin = plugins["generate_premium_pdf"]
                        ?: return@withContext "PDF no disponible"
                    val content = sessionLog.ifBlank { lastResult }
                    val htmlBody = buildString {
                        append("<h1>📋 Sesión E.M.M.A. Vision</h1>")
                        append("<p style='color:#666'>Generado automáticamente</p>")
                        append("<hr>")
                        content.split("\n").forEach { line ->
                            if (line.startsWith("Frame #") || line.startsWith("[")) {
                                append("<p><b>$line</b></p>")
                            } else if (line.isNotBlank()) {
                                append("<p>$line</p>")
                            }
                        }
                    }
                    plugin.execute(mapOf(
                        "title" to (params["title"] ?: "Notas de Sesión Vision"),
                        "body_html" to htmlBody
                    ))
                }

                ActionType.CREATE_EVENT -> {
                    val plugin = plugins["calendar_os_operations"]
                        ?: return@withContext "Calendario no disponible"
                    plugin.execute(mapOf(
                        "action" to "create",
                        "title" to (params["title"] ?: "Evento desde Vision"),
                        "description" to (params["description"] ?: lastResult.take(100)),
                        "date" to (params["date"] ?: ""),
                        "time" to (params["time"] ?: "")
                    ))
                }

                ActionType.TOGGLE_FLASHLIGHT -> {
                    val plugin = plugins["flashlight_control"]
                        ?: return@withContext "Linterna no disponible"
                    plugin.execute(mapOf("action" to "toggle"))
                }

                ActionType.WEB_SEARCH -> {
                    val plugin = plugins["web_search"]
                        ?: return@withContext "Búsqueda no disponible"
                    val query = params["query"] ?: lastResult.take(50)
                    val result = plugin.execute(mapOf("query" to query))
                    "Encontré: ${result.take(200)}"
                }

                ActionType.SAVE_PHOTO -> {
                    // Handled by VisionRecorder directly in LiveVisionScreen
                    "Foto guardada ✅"
                }

                ActionType.SET_ALARM -> {
                    val plugin = plugins["os_god_mode_operations"]
                        ?: return@withContext "Alarma no disponible"
                    plugin.execute(mapOf(
                        "action" to "set_alarm",
                        "time" to (params["time"] ?: "10")
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bridge action failed: ${e.message}")
            "Error ejecutando acción: ${e.message?.take(60)}"
        }
    }

    /**
     * Parse a voice command into an ActionType + params.
     */
    fun parseVoiceAction(text: String): Pair<ActionType, Map<String, String>>? {
        val lower = text.lowercase().trim()

        // WhatsApp
        if (lower.contains("whatsapp") || lower.contains("manda") || lower.contains("envía")) {
            val contactMatch = Regex("(?:a|para)\\s+(\\w+(?:\\s+\\w+)?)").find(lower)
            return ActionType.SEND_WHATSAPP to mapOf(
                "contact" to (contactMatch?.groupValues?.getOrNull(1) ?: ""),
                "message" to text
            )
        }

        // PDF / Report
        if (lower.contains("pdf") || lower.contains("reporte") || lower.contains("resumen") ||
            lower.contains("notas de la reunión")) {
            return ActionType.GENERATE_PDF to mapOf(
                "title" to "Sesión Vision"
            )
        }

        // Calendar
        if (lower.contains("agenda") || lower.contains("cita") || lower.contains("evento") ||
            lower.contains("reunión")) {
            val timeMatch = Regex("(?:a las|mañana|hoy)\\s*(.+)").find(lower)
            return ActionType.CREATE_EVENT to mapOf(
                "title" to text.take(50),
                "time" to (timeMatch?.groupValues?.getOrNull(1) ?: "")
            )
        }

        // Flashlight
        if (lower.contains("linterna") || lower.contains("flashlight") || lower.contains("luz")) {
            return ActionType.TOGGLE_FLASHLIGHT to emptyMap()
        }

        // Web search
        if (lower.contains("busca") || lower.contains("search") || lower.contains("investiga")) {
            val query = lower.substringAfter("busca").substringAfter("search")
                .substringAfter("investiga").trim()
            return ActionType.WEB_SEARCH to mapOf("query" to query)
        }

        // Save photo
        if (lower.contains("guarda") && (lower.contains("foto") || lower.contains("imagen"))) {
            return ActionType.SAVE_PHOTO to emptyMap()
        }

        // Alarm
        if (lower.contains("alarma") || lower.contains("timer") || lower.contains("temporizador")) {
            val minutesMatch = Regex("(\\d+)\\s*(?:minuto|min)").find(lower)
            return ActionType.SET_ALARM to mapOf(
                "time" to (minutesMatch?.groupValues?.getOrNull(1) ?: "10")
            )
        }

        // Email
        if (lower.contains("correo") || lower.contains("email") || lower.contains("mail")) {
            return ActionType.SEND_EMAIL to mapOf(
                "subject" to "Desde E.M.M.A. Vision",
                "body" to text
            )
        }

        return null // Not an action command
    }

    enum class ActionType {
        SEND_WHATSAPP, SEND_EMAIL, GENERATE_PDF, CREATE_EVENT,
        TOGGLE_FLASHLIGHT, WEB_SEARCH, SAVE_PHOTO, SET_ALARM
    }
}
