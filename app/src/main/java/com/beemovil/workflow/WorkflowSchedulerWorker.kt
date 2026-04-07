package com.beemovil.workflow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.beemovil.R
import com.beemovil.agent.*
import com.beemovil.llm.LlmFactory
import com.beemovil.llm.ModelRegistry
import com.beemovil.llm.ChatMessage
import com.beemovil.security.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkflowSchedulerWorker — Executes a scheduled workflow in the background.
 *
 * Uses WorkManager's CoroutineWorker for:
 * - Foreground service notification ("Ejecutando workflow...")
 * - Automatic retry on failure
 * - Network constraints (if WiFi required)
 *
 * On completion:
 * - Saves result to WorkflowHistoryDB
 * - Updates CustomWorkflowDB run count
 * - Shows notification with result preview
 */
class WorkflowSchedulerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WFSchedulerWorker"
        private const val CHANNEL_ID = "bee_workflow_scheduler"
        private const val CHANNEL_NAME = "Workflows Programados"
        private const val NOTIFICATION_ID_PROGRESS = 8001
        private const val NOTIFICATION_ID_RESULT = 8002
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workflowId = inputData.getString("workflow_id")
        if (workflowId.isNullOrBlank()) {
            Log.e(TAG, "No workflow_id provided")
            return@withContext Result.failure()
        }

        Log.i(TAG, "Starting scheduled execution for: $workflowId")

        // Show foreground notification
        setForeground(createForegroundInfo("Ejecutando workflow..."))

        val customDB = CustomWorkflowDB(applicationContext)
        val historyDB = WorkflowHistoryDB(applicationContext)
        val record = customDB.getWorkflow(workflowId)

        if (record == null) {
            Log.e(TAG, "Workflow not found: $workflowId")
            return@withContext Result.failure()
        }

        val workflow = record.toWorkflow()

        try {
            // Resolve provider and API key
            val prefs = applicationContext.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
            val provider = prefs.getString("current_provider", "ollama_cloud") ?: "ollama_cloud"
            val securePrefs = SecurePrefs.get(applicationContext)
            val apiKey = when (provider) {
                "openrouter" -> securePrefs.getString("openrouter_api_key", "") ?: ""
                else -> ""
            }

            // Execute steps sequentially
            val stepResults = mutableListOf<String>()
            var currentInput = "(ejecución automática programada)"
            var finalOutput = ""
            var allSuccess = true

            for ((index, step) in workflow.steps.withIndex()) {
                setForeground(createForegroundInfo(
                    "Ejecutando: ${step.label} (${index + 1}/${workflow.steps.size})"
                ))

                try {
                    val prompt = step.prompt.replace("{input}", currentInput)
                    val stepProvider = if (!step.modelOverride.isNullOrBlank()) {
                        val modelInfo = ModelRegistry.findModel(step.modelOverride)
                        modelInfo?.provider ?: provider
                    } else provider

                    val stepModel = step.modelOverride ?: ""

                    val llmProvider = LlmFactory.createProvider(
                        providerType = stepProvider,
                        model = stepModel,
                        apiKey = apiKey
                    )

                    val messages = listOf(
                        ChatMessage(role = "system", content = "Ejecutas un paso de workflow automático. Sé conciso."),
                        ChatMessage(role = "user", content = prompt)
                    )
                    val response = llmProvider.complete(messages, emptyList())
                    val result = response.text ?: "Sin respuesta"
                    stepResults.add("Paso ${index + 1} (${step.label}): OK")
                    currentInput = result
                    finalOutput = result

                } catch (e: Exception) {
                    Log.e(TAG, "Step ${index + 1} failed: ${e.message}", e)
                    stepResults.add("Paso ${index + 1} (${step.label}): ERROR - ${e.message}")
                    allSuccess = false
                    // Continue with next step (skip behavior for scheduled workflows)
                    currentInput = "(paso anterior falló: ${e.message})"
                }
            }

            // Save to history
            val status = if (allSuccess) "completed" else "partial"
            val runId = historyDB.startRun(
                workflowId = workflowId,
                workflowName = workflow.name,
                workflowIcon = workflow.icon,
                userInput = "(programado)"
            )
            historyDB.completeRun(runId, status, finalOutput, 0)

            // Update run count
            customDB.markRun(workflowId)

            // Save result to file
            try {
                val dir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOCUMENTS
                    ), "BeeMovil/generated"
                )
                dir.mkdirs()
                val file = java.io.File(dir, "workflow_${workflow.name.replace(" ", "_")}_${System.currentTimeMillis()}.txt")
                file.writeText(finalOutput)
                Log.i(TAG, "Result saved to: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not save result file: ${e.message}")
            }

            // Show result notification
            showResultNotification(
                title = if (allSuccess) "✅ ${workflow.name}" else "⚠️ ${workflow.name}",
                body = finalOutput.take(200)
            )

            Log.i(TAG, "Workflow completed: $workflowId (${if (allSuccess) "success" else "partial"})")
            return@withContext Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Workflow execution failed: ${e.message}", e)
            showResultNotification(
                title = "❌ ${workflow.name}",
                body = "Error: ${e.message}"
            )
            return@withContext Result.retry()
        }
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Bee-Movil Workflow")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return ForegroundInfo(NOTIFICATION_ID_PROGRESS, notification)
    }

    private fun showResultNotification(title: String, body: String) {
        createNotificationChannel()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        notificationManager.notify(
            NOTIFICATION_ID_RESULT + System.currentTimeMillis().toInt() % 1000,
            notification
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones de workflows programados"
            }
            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
