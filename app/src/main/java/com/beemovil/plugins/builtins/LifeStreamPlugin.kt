package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.lifestream.LifeStreamDB
import com.beemovil.lifestream.LifeStreamManager
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LifeStreamPlugin — LifeStream Intelligence Query Tool
 *
 * Allows Emma to query the user's LifeStream data:
 * - Recent notifications (WhatsApp, Telegram, Gmail, etc.)
 * - Location history (GPS snapshots)
 * - Daily stats (steps, battery, connectivity)
 * - Full-text search across all signals
 * - LifeStream overview/summary
 *
 * All operations are 🟢 GREEN (read-only, no confirmation needed).
 */
class LifeStreamPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "lifestream_query"
    private val TAG = "LifeStreamPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = """Tu sensor de vida del usuario. Consulta notificaciones, ubicación, 
                pasos, batería y más. Úsala cuando el usuario pregunte:
                '¿quién me escribió?', '¿tengo mensajes?', '¿dónde estuve?',
                '¿cuántos pasos llevo?', '¿cómo está mi batería?', 
                '¿qué notificaciones tengo?', '¿me llegó algo de X?'.
                También útil para contexto proactivo sobre la vida del usuario.
                SIEMPRE consulta este plugin para preguntas sobre mensajes, ubicación o sensores.""".trimIndent(),
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray()
                            .put("get_notifications")
                            .put("get_location")
                            .put("get_daily_stats")
                            .put("search")
                            .put("overview")
                        )
                        put("description", """Operación a realizar:
                            - get_notifications: Últimas notificaciones (WhatsApp, Telegram, Gmail, etc.)
                            - get_location: Historial de ubicación reciente (GPS)
                            - get_daily_stats: Estadísticas del día (pasos, batería, conectividad)
                            - search: Buscar en todo el LifeStream por palabra clave
                            - overview: Resumen general del LifeStream""".trimIndent())
                    })
                    put("source", JSONObject().apply {
                        put("type", "string")
                        put("description", """Para get_notifications: filtrar por fuente. 
                            Opciones: 'whatsapp', 'telegram', 'gmail', 'instagram', 'messenger', 
                            'calendar', 'sms', 'slack', 'teams'. 
                            Dejar vacío para todas las notificaciones.""".trimIndent())
                    })
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Para search: término de búsqueda (nombre, tema, palabra clave)")
                    })
                    put("limit", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Máximo número de resultados (default: 10, max: 30)")
                    })
                })
                put("required", JSONArray().put("operation"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val operation = args["operation"] as? String ?: return "Falta 'operation'."

        if (!LifeStreamManager.isEnabled(context)) {
            return "⚠️ LifeStream está desactivado. El usuario debe activarlo en Settings → LifeStream."
        }

        return withContext(Dispatchers.IO) {
            try {
                when (operation) {
                    "get_notifications" -> {
                        val source = args["source"] as? String
                        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 30) ?: 10
                        getNotifications(source, limit)
                    }
                    "get_location" -> {
                        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5
                        getLocation(limit)
                    }
                    "get_daily_stats" -> getDailyStats()
                    "search" -> {
                        val query = args["query"] as? String ?: return@withContext "Falta 'query' para buscar."
                        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 30) ?: 10
                        searchLifeStream(query, limit)
                    }
                    "overview" -> getOverview()
                    else -> "Operación desconocida: $operation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Execute failed: ${e.message}", e)
                "Error consultando LifeStream: ${e.message}"
            }
        }
    }

    // ═══════════════════════════════════════
    // OPERATIONS
    // ═══════════════════════════════════════

    private suspend fun getNotifications(source: String?, limit: Int): String {
        val dao = LifeStreamDB.getDatabase(context).lifeStreamDao()

        val entries = if (source != null) {
            dao.getNotificationsFrom(source, limit)
        } else {
            dao.getByCategory("notification", limit)
        }

        if (entries.isEmpty()) {
            return if (source != null) {
                "No hay notificaciones recientes de $source."
            } else {
                "No hay notificaciones recientes capturadas. " +
                "Verifica que LifeStream esté activo y el permiso de notificaciones esté habilitado."
            }
        }

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sdfDate = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()

        return buildString {
            appendLine("📱 Notificaciones${if (source != null) " de $source" else ""} (${entries.size}):")
            appendLine()
            entries.forEach { entry ->
                val age = now - entry.timestamp
                val timeStr = if (age < 86_400_000) {
                    "hace ${formatAge(age)}"
                } else {
                    sdfDate.format(Date(entry.timestamp))
                }
                val icon = sourceIcon(entry.source)
                val importance = "★".repeat(entry.importance.coerceIn(0, 3))
                appendLine("$icon ${entry.source} • $timeStr $importance")
                appendLine("   ${entry.title}: ${entry.content.take(120)}")
                appendLine()
            }
        }
    }

    private suspend fun getLocation(limit: Int): String {
        val dao = LifeStreamDB.getDatabase(context).lifeStreamDao()
        val entries = dao.getByCategory("location", limit)

        if (entries.isEmpty()) {
            return "No hay datos de ubicación recientes. GPS puede estar desactivado o sin permiso."
        }

        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sdfDate = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()

        return buildString {
            appendLine("📍 Historial de ubicación (${entries.size} puntos):")
            appendLine()
            entries.forEach { entry ->
                val age = now - entry.timestamp
                val timeStr = if (age < 86_400_000) {
                    "hace ${formatAge(age)}"
                } else {
                    sdfDate.format(Date(entry.timestamp))
                }
                appendLine("📍 $timeStr — ${entry.content.ifBlank { entry.title }}")

                // Parse lat/lng from metadata
                try {
                    val meta = JSONObject(entry.metadata)
                    val lat = meta.optDouble("lat", 0.0)
                    val lng = meta.optDouble("lng", 0.0)
                    val acc = meta.optDouble("accuracy", 0.0)
                    if (lat != 0.0) {
                        appendLine("   Coords: %.5f, %.5f (±%.0fm)".format(lat, lng, acc))
                    }
                } catch (_: Exception) {}
                appendLine()
            }
        }
    }

    private suspend fun getDailyStats(): String {
        val dao = LifeStreamDB.getDatabase(context).lifeStreamDao()
        val last24h = System.currentTimeMillis() - (24 * 3_600_000L)

        // Get latest of each sensor type
        val steps = dao.getBySource("steps", 1).firstOrNull()
        val battery = dao.getBySource("battery", 1).firstOrNull()
        val connectivity = dao.getBySource("connectivity", 1).firstOrNull()
        val location = dao.getByCategory("location", 1).firstOrNull()
        val notifCount = dao.getByCategory("notification", 100).count { it.timestamp > last24h }

        return buildString {
            appendLine("📊 Estadísticas del día:")
            appendLine()

            // Steps
            if (steps != null) {
                appendLine("🚶 Pasos: ${steps.content}")
                try {
                    val meta = JSONObject(steps.metadata)
                    val daily = meta.optInt("daily", 0)
                    val progress = (daily * 100) / 10_000 // Assume 10k goal
                    appendLine("   Meta diaria: $progress% de 10,000")
                } catch (_: Exception) {}
            } else {
                appendLine("🚶 Pasos: Sin datos (sensor no disponible)")
            }
            appendLine()

            // Battery
            if (battery != null) {
                appendLine("🔋 Batería: ${battery.content}")
            } else {
                appendLine("🔋 Batería: Sin datos")
            }
            appendLine()

            // Connectivity
            if (connectivity != null) {
                appendLine("🌐 Conexión: ${connectivity.content}")
            } else {
                appendLine("🌐 Conexión: Sin datos")
            }
            appendLine()

            // Location
            if (location != null) {
                val age = formatAge(System.currentTimeMillis() - location.timestamp)
                appendLine("📍 Ubicación: ${location.content.ifBlank { location.title }} (hace $age)")
            } else {
                appendLine("📍 Ubicación: Sin datos de GPS")
            }
            appendLine()

            // Notifications summary
            appendLine("📱 Notificaciones hoy: $notifCount")
        }
    }

    private suspend fun searchLifeStream(query: String, limit: Int): String {
        val dao = LifeStreamDB.getDatabase(context).lifeStreamDao()
        val results = dao.search(query, limit)

        if (results.isEmpty()) {
            return "No se encontraron resultados para \"$query\" en el LifeStream."
        }

        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        return buildString {
            appendLine("🔍 Búsqueda: \"$query\" (${results.size} resultados):")
            appendLine()
            results.forEach { entry ->
                val age = now - entry.timestamp
                val timeStr = if (age < 86_400_000) "hace ${formatAge(age)}" else sdf.format(Date(entry.timestamp))
                val icon = when (entry.category) {
                    "notification" -> sourceIcon(entry.source)
                    "location" -> "📍"
                    "sensor" -> "📊"
                    else -> "⚙️"
                }
                appendLine("$icon [${entry.category}/${entry.source}] $timeStr")
                appendLine("   ${entry.title}: ${entry.content.take(150)}")
                appendLine()
            }
        }
    }

    private suspend fun getOverview(): String {
        val stats = LifeStreamManager.getStats(context)

        return buildString {
            appendLine("🌊 LifeStream — Resumen")
            appendLine()
            appendLine("📊 Total: ${stats.totalEntries} señales")
            appendLine("🔴 Sin leer: ${stats.unreadCount}")
            appendLine("📅 Hoy: ${stats.todayCount}")
            appendLine()

            if (stats.sourceBreakdown.isNotEmpty()) {
                appendLine("Por fuente (últimas 24h):")
                stats.sourceBreakdown.forEach { stat ->
                    appendLine("  ${sourceIcon(stat.source)} ${stat.source}: ${stat.count}")
                }
                appendLine()
            }

            if (stats.categoryBreakdown.isNotEmpty()) {
                appendLine("Por categoría (últimas 24h):")
                stats.categoryBreakdown.forEach { stat ->
                    val icon = when (stat.category) {
                        "notification" -> "📱"
                        "location" -> "📍"
                        "sensor" -> "📊"
                        "system" -> "⚙️"
                        else -> "❓"
                    }
                    appendLine("  $icon ${stat.category}: ${stat.count}")
                }
            }

            val hasAccess = LifeStreamManager.hasNotificationAccess(context)
            appendLine()
            appendLine("Estado: ${if (hasAccess) "✅ Notificaciones activas" else "⚠️ Sin acceso a notificaciones"}")
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    private fun formatAge(ms: Long): String {
        val minutes = ms / 60_000
        val hours = ms / 3_600_000
        return when {
            minutes < 1 -> "ahora"
            minutes < 60 -> "${minutes}min"
            hours < 24 -> "${hours}h ${minutes % 60}min"
            else -> "${hours / 24}d ${hours % 24}h"
        }
    }

    private fun sourceIcon(source: String): String = when (source) {
        "whatsapp", "whatsapp_business" -> "💬"
        "telegram" -> "✈️"
        "instagram" -> "📸"
        "gmail" -> "📧"
        "calendar" -> "📅"
        "messenger" -> "💭"
        "sms" -> "📱"
        "phone" -> "📞"
        "slack" -> "💼"
        "teams" -> "👥"
        "maps" -> "🗺️"
        "gps" -> "📍"
        "steps" -> "🚶"
        "battery" -> "🔋"
        "connectivity" -> "🌐"
        "spotify" -> "🎵"
        "linkedin" -> "💼"
        else -> "🔔"
    }
}
