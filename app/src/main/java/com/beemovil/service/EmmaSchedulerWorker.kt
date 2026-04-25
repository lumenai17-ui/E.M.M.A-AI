package com.beemovil.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beemovil.security.SecurePrefs

/**
 * EmmaSchedulerWorker — Project Autonomía Phase S4
 *
 * WorkManager worker that executes scheduled E.M.M.A. tasks.
 * Each task is identified by a tag and has associated data.
 */
class EmmaSchedulerWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "EmmaSchedulerWorker"
        const val KEY_TASK_TYPE = "task_type"
        const val KEY_TASK_LABEL = "task_label"
    }

    override suspend fun doWork(): Result {
        val taskType = inputData.getString(KEY_TASK_TYPE) ?: return Result.failure()
        val taskLabel = inputData.getString(KEY_TASK_LABEL) ?: "Tarea programada"

        Log.i(TAG, "Ejecutando tarea programada: $taskType ($taskLabel)")

        return try {
            when (taskType) {
                "morning_briefing" -> executeMorningBriefing()
                "weekly_report" -> executeWeeklyReport()
                "storage_cleanup" -> executeStorageCheck()
                else -> {
                    Log.w(TAG, "Tipo de tarea desconocido: $taskType")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en tarea programada: $taskType", e)
            Result.retry()
        }
    }

    private suspend fun executeMorningBriefing(): Result {
        // Build a notification with today's summary
        val prefs = appContext.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val builder = StringBuilder("Buenos días. ")

        // Calendar events count
        try {
            val cal = java.util.Calendar.getInstance()
            val startOfDay = cal.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
            }.timeInMillis
            val endOfDay = cal.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
            }.timeInMillis

            val cursor = appContext.contentResolver.query(
                android.provider.CalendarContract.Events.CONTENT_URI,
                arrayOf(android.provider.CalendarContract.Events.TITLE),
                "${android.provider.CalendarContract.Events.DTSTART} >= ? AND ${android.provider.CalendarContract.Events.DTSTART} <= ?",
                arrayOf(startOfDay.toString(), endOfDay.toString()),
                null
            )
            val eventCount = cursor?.count ?: 0
            cursor?.close()
            if (eventCount > 0) builder.append("Tienes $eventCount eventos hoy. ")
        } catch (_: Exception) {}

        // Battery
        try {
            val scanner = com.beemovil.telemetry.DeviceScanner(appContext)
            val state = scanner.getCurrentDeviceState()
            builder.append("Batería: ${state.batteryPercent}%. ")
        } catch (_: Exception) {}

        sendNotification("☀️ Briefing Matutino", builder.toString())
        return Result.success()
    }

    private suspend fun executeWeeklyReport(): Result {
        sendNotification("📊 Reporte Semanal", "Tu reporte semanal está listo. Abre E.M.M.A. para verlo.")
        return Result.success()
    }

    private suspend fun executeStorageCheck(): Result {
        val stats = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path)
        val freeGB = stats.availableBytes / (1024 * 1024 * 1024)
        if (freeGB < 2) {
            sendNotification("💾 Espacio Bajo", "Solo quedan ${freeGB}GB libres. Pídeme que limpie archivos.")
        }
        return Result.success()
    }

    private fun sendNotification(title: String, body: String) {
        try {
            val channelId = "emma_scheduler"
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(channelId, "E.M.M.A. Scheduler", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                nm.createNotificationChannel(channel)
            }

            val notification = android.app.Notification.Builder(appContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build()

            nm.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "Notification failed", e)
        }
    }
}
