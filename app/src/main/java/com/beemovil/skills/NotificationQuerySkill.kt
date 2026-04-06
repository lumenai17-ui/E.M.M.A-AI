package com.beemovil.skills

import com.beemovil.memory.NotificationLogDB
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * NotificationQuerySkill — Lets the agent query the notification log from chat.
 *
 * Usage: "Que notificaciones recibi de WhatsApp hoy?"
 * The agent calls this skill to search the notification database.
 */
class NotificationQuerySkill(
    private val notifDB: NotificationLogDB
) : BeeSkill {
    override val name = "notification_query"
    override val description = "Consulta el historial de notificaciones capturadas del dispositivo. Tipos de query: 'recent' (ultimas), 'today' (hoy), 'app' (por app), 'stats' (resumen general)"
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "Tipo de consulta: 'recent', 'today', 'app', 'stats'"
                },
                "app_filter": {
                    "type": "string",
                    "description": "Nombre parcial de la app a filtrar (ej: 'whatsapp', 'gmail')"
                },
                "limit": {
                    "type": "integer",
                    "description": "Maximo de resultados (default 10)"
                }
            },
            "required": ["query"]
        }
    """)

    override fun execute(params: JSONObject): JSONObject {
        val query = params.optString("query", "recent").lowercase()
        val appFilter = params.optString("app_filter", "").ifBlank { null }
        val limit = params.optInt("limit", 10)

        val result = try {
            when {
                query.contains("stats") || query.contains("resumen") -> getStats()
                query.contains("today") || query.contains("hoy") -> getToday(appFilter, limit)
                query.contains("app") || appFilter != null -> getByApp(appFilter ?: "", limit)
                else -> getRecent(limit)
            }
        } catch (e: Exception) {
            "Error consultando notificaciones: ${e.message}"
        }

        return JSONObject().put("result", result)
    }

    private fun getRecent(limit: Int): String {
        val entries = notifDB.getRecent(limit)
        if (entries.isEmpty()) return "No hay notificaciones capturadas aun."

        val sb = StringBuilder("Ultimas $limit notificaciones:\n\n")
        entries.forEach { e ->
            val time = SimpleDateFormat("HH:mm", Locale("es")).format(Date(e.timestamp))
            sb.appendLine("[$time] ${e.appName}: ${e.title} - ${e.text.take(80)}")
        }
        return sb.toString()
    }

    private fun getToday(appFilter: String?, limit: Int): String {
        val today = notifDB.getTodayNotifications()
        val filtered = if (appFilter != null) {
            today.filter { it.appName.contains(appFilter, ignoreCase = true) ||
                    it.packageName.contains(appFilter, ignoreCase = true) }
        } else today

        if (filtered.isEmpty()) {
            return if (appFilter != null) "No hay notificaciones de '$appFilter' hoy."
            else "No hay notificaciones capturadas hoy."
        }

        val sb = StringBuilder("Notificaciones de hoy")
        if (appFilter != null) sb.append(" (filtro: $appFilter)")
        sb.appendLine(": ${filtered.size} total\n")

        filtered.take(limit).forEach { e ->
            val time = SimpleDateFormat("HH:mm", Locale("es")).format(Date(e.timestamp))
            sb.appendLine("[$time] ${e.appName}: ${e.title}")
            if (e.text.isNotBlank()) sb.appendLine("  > ${e.text.take(100)}")
        }
        return sb.toString()
    }

    private fun getByApp(appName: String, limit: Int): String {
        val allApps = notifDB.getAppStats(50)
        val match = allApps.find {
            it.appName.contains(appName, ignoreCase = true) ||
            it.packageName.contains(appName, ignoreCase = true)
        }

        if (match == null) {
            val available = allApps.joinToString(", ") { it.appName }
            return "No se encontro la app '$appName'. Apps disponibles: $available"
        }

        val entries = notifDB.getByApp(match.packageName, limit)
        val sb = StringBuilder("Notificaciones de ${match.appName} (${entries.size} mostradas):\n\n")
        entries.forEach { e ->
            val date = SimpleDateFormat("dd/MM HH:mm", Locale("es")).format(Date(e.timestamp))
            sb.appendLine("[$date] ${e.title}")
            if (e.text.isNotBlank()) sb.appendLine("  > ${e.text.take(100)}")
        }
        return sb.toString()
    }

    private fun getStats(): String {
        val stats = notifDB.getAppStats(10)
        val todayCount = notifDB.getTodayCount()
        val totalCount = notifDB.getCount()

        if (stats.isEmpty()) return "No hay datos de notificaciones aun."

        val sb = StringBuilder("Resumen de notificaciones:\n\n")
        sb.appendLine("Total capturadas: $totalCount")
        sb.appendLine("Hoy: $todayCount")
        sb.appendLine("\nTop apps:")
        stats.forEachIndexed { i, s ->
            sb.appendLine("${i + 1}. ${s.appName}: ${s.count} notificaciones")
        }
        return sb.toString()
    }
}
