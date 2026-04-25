package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import androidx.work.*
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.plugins.SecurityGate
import com.beemovil.service.EmmaSchedulerWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * SchedulerPlugin — Project Autonomía Phase S4
 *
 * E.M.M.A. can schedule recurring tasks: morning briefings,
 * weekly reports, storage cleanup alerts.
 * Create/delete = 🟡 YELLOW, list = 🟢 GREEN.
 */
class SchedulerPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "emma_scheduler"
    private val TAG = "SchedulerPlugin"

    companion object {
        private const val MAX_SCHEDULES = 5
    }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Programador de tareas recurrentes. Usa cuando digan 'dame un briefing cada mañana', 'reporte semanal', 'avísame si me quedo sin espacio', 'qué tareas tengo programadas'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray()
                            .put("list_schedules")
                            .put("create_schedule")
                            .put("delete_schedule")
                        )
                    })
                    put("task_type", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray()
                            .put("morning_briefing")
                            .put("weekly_report")
                            .put("storage_cleanup")
                        )
                        put("description", "Tipo de tarea: morning_briefing (diario 7AM), weekly_report (viernes 5PM), storage_cleanup (cada 3 días).")
                    })
                    put("schedule_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "(delete_schedule) ID de la tarea a eliminar.")
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
                    "list_schedules" -> listSchedules()
                    "create_schedule" -> createSchedule(args)
                    "delete_schedule" -> deleteSchedule(args)
                    else -> "Operación desconocida: $operation"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scheduler error", e)
                "Error: ${e.message}"
            }
        }
    }

    private fun listSchedules(): String {
        val wm = WorkManager.getInstance(context)
        val prefs = context.getSharedPreferences("emma_schedules", Context.MODE_PRIVATE)
        val schedules = prefs.all

        if (schedules.isEmpty()) {
            return "No hay tareas programadas. Puedes crear: morning_briefing, weekly_report, storage_cleanup."
        }

        return buildString {
            appendLine("═══ TAREAS PROGRAMADAS (${schedules.size}) ═══")
            schedules.forEach { (key, value) ->
                val info = value as? String ?: "?"
                appendLine("  ⏰ $key: $info")
            }
        }
    }

    private suspend fun createSchedule(args: Map<String, Any>): String {
        val taskType = args["task_type"] as? String ?: return "Falta 'task_type'."

        val prefs = context.getSharedPreferences("emma_schedules", Context.MODE_PRIVATE)
        if (prefs.all.size >= MAX_SCHEDULES) {
            return "Límite de $MAX_SCHEDULES tareas programadas alcanzado. Elimina una primero."
        }

        if (prefs.contains(taskType)) {
            return "Ya existe una tarea '$taskType'. Elimínala primero si quieres recrearla."
        }

        val (interval, unit, label) = when (taskType) {
            "morning_briefing" -> Triple(24L, TimeUnit.HOURS, "Briefing matutino diario")
            "weekly_report" -> Triple(7L, TimeUnit.DAYS, "Reporte semanal")
            "storage_cleanup" -> Triple(3L, TimeUnit.DAYS, "Verificación de espacio cada 3 días")
            else -> return "Tipo no reconocido: $taskType"
        }

        val op = SecurityGate.yellow(id, "create_schedule", "Crear tarea recurrente: $label")
        if (!SecurityGate.evaluate(op)) return "Cancelado."

        val data = Data.Builder()
            .putString(EmmaSchedulerWorker.KEY_TASK_TYPE, taskType)
            .putString(EmmaSchedulerWorker.KEY_TASK_LABEL, label)
            .build()

        val request = PeriodicWorkRequestBuilder<EmmaSchedulerWorker>(interval, unit)
            .setInputData(data)
            .addTag("emma_$taskType")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "emma_$taskType",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

        prefs.edit().putString(taskType, "$label (cada $interval ${unit.name.lowercase()})").apply()
        Log.i(TAG, "Schedule created: $taskType")

        return "Tarea programada ✅: $label\n  Frecuencia: cada $interval ${unit.name.lowercase()}\n  ID: $taskType"
    }

    private suspend fun deleteSchedule(args: Map<String, Any>): String {
        val scheduleId = args["schedule_id"] as? String
            ?: args["task_type"] as? String
            ?: return "Falta 'schedule_id' o 'task_type'."

        val op = SecurityGate.yellow(id, "delete_schedule", "Eliminar tarea: $scheduleId")
        if (!SecurityGate.evaluate(op)) return "Cancelado."

        WorkManager.getInstance(context).cancelUniqueWork("emma_$scheduleId")
        context.getSharedPreferences("emma_schedules", Context.MODE_PRIVATE)
            .edit().remove(scheduleId).apply()

        Log.i(TAG, "Schedule deleted: $scheduleId")
        return "Tarea eliminada ✅: $scheduleId"
    }
}
