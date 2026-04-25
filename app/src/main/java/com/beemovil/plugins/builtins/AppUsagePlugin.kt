package com.beemovil.plugins.builtins

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * AppUsagePlugin — Project Autonomía Phase S4
 *
 * Screen time reports: which apps were used, for how long.
 * All operations 🟢 GREEN (read-only).
 * Requires PACKAGE_USAGE_STATS permission (granted via Settings).
 */
class AppUsagePlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "emma_app_usage"
    private val TAG = "AppUsagePlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Reportes de uso del teléfono. Usa cuando digan '¿cuánto usé el teléfono?', '¿qué apps usé hoy?', 'dame mi screen time'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("today").put("week").put("top_apps"))
                    })
                })
                put("required", JSONArray().put("operation"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val operation = args["operation"] as? String ?: return "Falta 'operation'."

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return "UsageStatsManager no disponible en este dispositivo."

        // Check if permission is granted
        val cal = Calendar.getInstance()
        cal.add(Calendar.HOUR, -1)
        val testStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
        if (testStats.isNullOrEmpty()) {
            return "⚠️ E.M.M.A. no tiene permiso de 'Acceso de uso'. El usuario debe ir a Settings → Apps → Acceso especial → Acceso de uso y habilitar E.M.M.A."
        }

        return withContext(Dispatchers.IO) {
            when (operation) {
                "today" -> usageReport(usm, 1)
                "week" -> usageReport(usm, 7)
                "top_apps" -> topApps(usm)
                else -> "Operación desconocida: $operation"
            }
        }
    }

    private fun usageReport(usm: UsageStatsManager, days: Int): String {
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val start = cal.timeInMillis

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?.filter { it.totalTimeInForeground > 60_000 } // Min 1 minute
            ?.sortedByDescending { it.totalTimeInForeground }
            ?: return "No hay datos de uso disponibles."

        val pm = context.packageManager
        val totalMs = stats.sumOf { it.totalTimeInForeground }
        val period = if (days == 1) "HOY" else "ÚLTIMOS $days DÍAS"

        return buildString {
            appendLine("═══ USO DEL TELÉFONO — $period ═══")
            appendLine("⏱️ Tiempo total: ${formatDuration(totalMs)}")
            appendLine()
            stats.take(15).forEach { stat ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                } catch (_: Exception) { stat.packageName.substringAfterLast(".") }
                val time = formatDuration(stat.totalTimeInForeground)
                val pct = if (totalMs > 0) (stat.totalTimeInForeground * 100 / totalMs) else 0
                appendLine("  📱 $label: $time ($pct%)")
            }
            if (stats.size > 15) appendLine("  ... y ${stats.size - 15} apps más")
        }
    }

    private fun topApps(usm: UsageStatsManager): String {
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val start = cal.timeInMillis

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, start, end)
            ?.filter { it.totalTimeInForeground > 60_000 }
            ?.sortedByDescending { it.totalTimeInForeground }
            ?.take(10)
            ?: return "No hay datos."

        val pm = context.packageManager
        return buildString {
            appendLine("═══ TOP 10 APPS (última semana) ═══")
            stats.forEachIndexed { i, stat ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                } catch (_: Exception) { stat.packageName.substringAfterLast(".") }
                appendLine("  ${i + 1}. $label — ${formatDuration(stat.totalTimeInForeground)}")
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val hours = ms / 3_600_000
        val mins = (ms % 3_600_000) / 60_000
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }
}
