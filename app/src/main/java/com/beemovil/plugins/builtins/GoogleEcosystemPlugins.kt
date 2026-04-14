package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.google.GoogleAuthManager
import com.beemovil.google.GoogleCalendarService
import com.beemovil.google.GoogleGmailService
import com.beemovil.google.GoogleTasksService
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sprint 4: Google Ecosystem Plugins — gives E.M.M.A. access to
 * Gmail, Calendar, and Tasks via tool calling.
 *
 * Each plugin checks for Google auth and the required scope before executing.
 */

// ═══════════════════════════════════════
// GMAIL PLUGIN
// ═══════════════════════════════════════

class GmailPlugin(private val context: Context) : EmmaPlugin {
    override val id = "google_gmail"
    private val TAG = "GmailPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Lee la bandeja de entrada de Gmail del usuario, busca correos, o envía emails. Úsalo cuando el usuario pregunte por sus emails, pida revisar su bandeja, o quiera enviar un correo desde su cuenta de Google.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("inbox").put("unread_count").put("search").put("send"))
                        put("description", "Acción: 'inbox' para ver bandeja, 'unread_count' para contar no leídos, 'search' para buscar, 'send' para enviar.")
                    })
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Query de búsqueda (solo para action='search'). Ej: 'from:jefe@empresa.com' o 'asunto factura'.")
                    })
                    put("to", JSONObject().apply {
                        put("type", "string")
                        put("description", "Email del destinatario (solo para action='send').")
                    })
                    put("subject", JSONObject().apply {
                        put("type", "string")
                        put("description", "Asunto del correo (solo para action='send').")
                    })
                    put("body", JSONObject().apply {
                        put("type", "string")
                        put("description", "Cuerpo del correo (solo para action='send').")
                    })
                })
                put("required", JSONArray().put("action"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val auth = GoogleAuthManager(context)
        val token = auth.getAccessToken() ?: return "❌ No estás conectado a Google. Ve a Settings → Google para iniciar sesión."
        val gmail = GoogleGmailService(token)
        val action = args["action"] as? String ?: "inbox"

        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "inbox" -> gmail.formatInboxForLlm(gmail.listInbox(15))
                    "unread_count" -> {
                        val count = gmail.getUnreadCount()
                        "Tienes $count correos sin leer en tu bandeja."
                    }
                    "search" -> {
                        val query = args["query"] as? String ?: "in:inbox"
                        gmail.formatInboxForLlm(gmail.searchEmails(query, 10))
                    }
                    "send" -> {
                        val to = args["to"] as? String ?: return@withContext "Falta el destinatario."
                        val subject = args["subject"] as? String ?: "Mensaje de E.M.M.A."
                        val body = args["body"] as? String ?: ""
                        val msgId = gmail.sendEmail(to, subject, body)
                        if (msgId != null) "✅ Email enviado exitosamente a $to"
                        else "❌ Error enviando el email."
                    }
                    else -> "Acción '$action' no reconocida."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gmail plugin error: ${e.message}", e)
                "❌ Error con Gmail: ${e.message}"
            }
        }
    }
}

// ═══════════════════════════════════════
// CALENDAR PLUGIN
// ═══════════════════════════════════════

class GoogleCalendarPlugin(private val context: Context) : EmmaPlugin {
    override val id = "google_calendar"
    private val TAG = "CalendarPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Lee los eventos del Google Calendar del usuario o crea nuevos eventos. Úsalo cuando pregunte '¿qué tengo mañana?', 'agenda una reunión', o 'crea un evento'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("list").put("create").put("search"))
                        put("description", "Acción: 'list' para ver próximos eventos, 'create' para crear, 'search' para buscar.")
                    })
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Título del evento (solo para action='create').")
                    })
                    put("description", JSONObject().apply {
                        put("type", "string")
                        put("description", "Descripción del evento (solo para action='create').")
                    })
                    put("location", JSONObject().apply {
                        put("type", "string")
                        put("description", "Ubicación del evento (solo para action='create').")
                    })
                    put("start_time_millis", JSONObject().apply {
                        put("type", "number")
                        put("description", "Timestamp Unix en milisegundos del inicio (solo para action='create').")
                    })
                    put("end_time_millis", JSONObject().apply {
                        put("type", "number")
                        put("description", "Timestamp Unix en milisegundos del fin (solo para action='create').")
                    })
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Texto de búsqueda (solo para action='search').")
                    })
                })
                put("required", JSONArray().put("action"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val auth = GoogleAuthManager(context)
        val token = auth.getAccessToken() ?: return "❌ No estás conectado a Google. Ve a Settings → Google para iniciar sesión."
        val calendar = GoogleCalendarService(token)
        val action = args["action"] as? String ?: "list"

        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "list" -> calendar.formatEventsForLlm(calendar.listUpcomingEvents(15))
                    "search" -> {
                        val query = args["query"] as? String ?: ""
                        calendar.formatEventsForLlm(calendar.searchEvents(query))
                    }
                    "create" -> {
                        val title = args["title"] as? String ?: return@withContext "Falta el título del evento."
                        val desc = args["description"] as? String ?: ""
                        val loc = args["location"] as? String ?: ""
                        val startMs = (args["start_time_millis"] as? Number)?.toLong()
                            ?: return@withContext "Falta la hora de inicio."
                        val endMs = (args["end_time_millis"] as? Number)?.toLong()
                            ?: (startMs + 3600_000) // Default 1 hour
                        
                        val eventId = calendar.createEvent(title, desc, loc, startMs, endMs)
                        if (eventId != null) "✅ Evento '$title' creado exitosamente en tu calendario."
                        else "❌ Error creando el evento."
                    }
                    else -> "Acción '$action' no reconocida."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Calendar plugin error: ${e.message}", e)
                "❌ Error con Calendar: ${e.message}"
            }
        }
    }
}

// ═══════════════════════════════════════
// TASKS PLUGIN
// ═══════════════════════════════════════

class GoogleTasksPlugin(private val context: Context) : EmmaPlugin {
    override val id = "google_tasks"
    private val TAG = "TasksPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Lee las tareas de Google Tasks del usuario o crea nuevas. Úsalo cuando pregunte 'mis pendientes', 'qué tengo que hacer', o 'agrega una tarea'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("list").put("create").put("complete"))
                        put("description", "Acción: 'list' para ver tareas, 'create' para crear, 'complete' para marcar como completada.")
                    })
                    put("title", JSONObject().apply {
                        put("type", "string")
                        put("description", "Título de la tarea (solo para action='create').")
                    })
                    put("notes", JSONObject().apply {
                        put("type", "string")
                        put("description", "Notas adicionales (solo para action='create').")
                    })
                    put("task_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "ID de la tarea (solo para action='complete').")
                    })
                })
                put("required", JSONArray().put("action"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val auth = GoogleAuthManager(context)
        val token = auth.getAccessToken() ?: return "❌ No estás conectado a Google. Ve a Settings → Google para iniciar sesión."
        val tasks = GoogleTasksService(token)
        val action = args["action"] as? String ?: "list"

        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "list" -> tasks.formatTasksForLlm(tasks.listTasks())
                    "create" -> {
                        val title = args["title"] as? String ?: return@withContext "Falta el título de la tarea."
                        val notes = args["notes"] as? String ?: ""
                        val taskId = tasks.createTask(title, notes)
                        if (taskId != null) "✅ Tarea '$title' creada exitosamente."
                        else "❌ Error creando la tarea."
                    }
                    "complete" -> {
                        val taskId = args["task_id"] as? String ?: return@withContext "Falta el ID de la tarea."
                        val ok = tasks.completeTask(taskId)
                        if (ok) "✅ Tarea marcada como completada."
                        else "❌ Error completando la tarea."
                    }
                    else -> "Acción '$action' no reconocida."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tasks plugin error: ${e.message}", e)
                "❌ Error con Tasks: ${e.message}"
            }
        }
    }
}
