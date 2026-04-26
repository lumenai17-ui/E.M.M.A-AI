package com.beemovil.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.beemovil.R
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.database.ChatHistoryDB
import com.beemovil.database.ChatMessageEntity
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * EmmaTaskService — ForegroundService that keeps LLM requests alive
 * when the user backgrounds the app.
 *
 * Architecture:
 *   1. ChatViewModel sends message details via Intent extras
 *   2. Service starts foreground with progress notification
 *   3. EmmaEngine processes the message in a service-scoped coroutine
 *   4. Result is saved to Room DB + posted to LiveData for live UI update
 *   5. Notification updates to show completion + service auto-stops
 *
 * The user can freely leave the app — the request completes regardless.
 */
class EmmaTaskService : Service() {

    companion object {
        private const val TAG = "EmmaTaskService"
        private const val CHANNEL_ID = "emma_task_channel"
        private const val NOTIFICATION_ID = 3001

        const val ACTION_PROCESS = "ACTION_PROCESS"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val ACTION_KEEPALIVE = "ACTION_KEEPALIVE"
        const val ACTION_DONE = "ACTION_DONE"

        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        const val EXTRA_THREAD_ID = "EXTRA_THREAD_ID"
        const val EXTRA_AGENT_ID = "EXTRA_AGENT_ID"
        const val EXTRA_PROVIDER = "EXTRA_PROVIDER"
        const val EXTRA_MODEL = "EXTRA_MODEL"
        const val EXTRA_SYSTEM_PROMPT = "EXTRA_SYSTEM_PROMPT"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"

        /**
         * LiveData that the ViewModel observes.
         * When a task completes, the result is posted here.
         * If the ViewModel is alive, it picks it up immediately.
         * If not, the result is already in Room DB.
         */
        val taskResult = MutableLiveData<TaskResult>()

        /**
         * Observable progress text for the ViewModel to show inline.
         */
        val taskProgress = MutableLiveData<TaskProgress>()
    }

    data class TaskResult(
        val taskId: Long,
        val threadId: String,
        val agentId: String,
        val response: String,
        val isFileGenerated: Boolean = false,
        val filePath: String? = null,
        val fileName: String? = null,
        val mimeType: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class TaskProgress(
        val taskId: Long,
        val threadId: String,
        val progressText: String
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTaskId: Long = 0

    private lateinit var engine: EmmaEngine
    private lateinit var chatHistoryDB: ChatHistoryDB

    // ═══════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        engine = EmmaEngine(applicationContext)
        chatHistoryDB = ChatHistoryDB.getDatabase(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PROCESS -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                val threadId = intent.getStringExtra(EXTRA_THREAD_ID) ?: "main"
                val agentId = intent.getStringExtra(EXTRA_AGENT_ID) ?: "emma"
                val provider = intent.getStringExtra(EXTRA_PROVIDER) ?: "openrouter"
                val model = intent.getStringExtra(EXTRA_MODEL) ?: "openai/gpt-4o-mini"
                val systemPrompt = intent.getStringExtra(EXTRA_SYSTEM_PROMPT)
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, System.currentTimeMillis())

                if (message.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                currentTaskId = taskId
                startForeground(NOTIFICATION_ID, buildProgressNotification("Procesando tu solicitud..."))
                acquireWakeLock()
                processMessage(taskId, message, threadId, agentId, provider, model, systemPrompt)
            }
            ACTION_CANCEL -> {
                Log.i(TAG, "Task cancelled by user")
                currentJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_KEEPALIVE -> {
                Log.i(TAG, "Keep-alive mode: notification + WakeLock only")
                startForeground(NOTIFICATION_ID, buildProgressNotification("E.M.M.A. está procesando tu solicitud..."))
                acquireWakeLock()
            }
            ACTION_DONE -> {
                Log.i(TAG, "Task done, stopping service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                releaseWakeLock()
                stopSelf()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        currentJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ═══════════════════════════════════════
    // CORE PROCESSING
    // ═══════════════════════════════════════

    private fun processMessage(
        taskId: Long,
        message: String,
        threadId: String,
        agentId: String,
        provider: String,
        model: String,
        systemPrompt: String?
    ) {
        currentJob = scope.launch {
            try {
                // Step 1: Initialize engine and load conversation context from Room
                engine.initialize()
                val history = chatHistoryDB.chatHistoryDao().getHistory(threadId)
                engine.loadPersistedContext(history, systemPrompt)

                Log.i(TAG, "[$taskId] Processing: '${message.take(60)}...' on $provider:$model")

                // Step 2: Execute LLM request (this is what survives backgrounding)
                val response = engine.processUserMessage(
                    message, provider, model,
                    onProgress = { progress ->
                        // Update notification with progress
                        updateNotification(progress)
                        // Notify ViewModel if alive
                        taskProgress.postValue(TaskProgress(taskId, threadId, progress))
                    },
                    threadId = threadId,
                    senderId = agentId
                )

                // Step 3: Handle result
                if (response.isBlank()) {
                    Log.w(TAG, "[$taskId] Empty response, skipping")
                    cleanupAndStop()
                    return@launch
                }

                when {
                    response.startsWith("TOOL_CALL::file_generated::") -> {
                        handleFileResult(taskId, threadId, agentId, response)
                    }
                    response.startsWith("TOOL_CALL::open_browser::") -> {
                        handleBrowserResult(taskId, threadId, agentId, response)
                    }
                    else -> {
                        handleTextResult(taskId, threadId, agentId, response)
                    }
                }

            } catch (e: CancellationException) {
                Log.w(TAG, "[$taskId] Task cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "[$taskId] Task failed", e)
                val errorMsg = "⚠️ Error procesando tu mensaje: ${e.message?.take(100)}"

                // Save error to Room so user sees it when they return
                saveToRoom(threadId, agentId, errorMsg)

                // Post to LiveData
                taskResult.postValue(TaskResult(
                    taskId = taskId,
                    threadId = threadId,
                    agentId = agentId,
                    response = errorMsg
                ))

                showCompletionNotification("❌ Error al procesar", errorMsg.take(80))
            } finally {
                cleanupAndStop()
            }
        }
    }

    // ═══════════════════════════════════════
    // RESULT HANDLERS
    // ═══════════════════════════════════════

    private suspend fun handleFileResult(taskId: Long, threadId: String, agentId: String, response: String) {
        val filePath = response.removePrefix("TOOL_CALL::file_generated::")
        val file = java.io.File(filePath)
        val generatedName = file.name
        val extension = generatedName.substringAfterLast('.', "").lowercase()
        val isImage = extension in listOf("png", "jpg", "jpeg", "webp", "gif")
        val isVideo = extension in listOf("mp4", "webm")
        val isAudio = extension in listOf("mp3", "wav", "ogg")
        val mimeType = when (extension) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "html" -> "text/html"
            else -> "application/octet-stream"
        }

        val feedback = when {
            isImage -> "🎨 ¡Listo! He generado tu imagen '$generatedName'. Está guardada en Downloads/EMMA/."
            isVideo -> "🎬 ¡Listo! Tu video '$generatedName' está listo en Downloads/EMMA/."
            isAudio -> "🎵 ¡Listo! Tu audio '$generatedName' está listo en Downloads/EMMA/."
            else -> "📄 He generado tu documento '$generatedName'."
        }

        // Save to Room
        val metaJson = JSONObject().apply {
            put("file_path", filePath)
            put("file_name", generatedName)
            put("mime_type", mimeType)
        }.toString()

        chatHistoryDB.chatHistoryDao().insertMessage(ChatMessageEntity(
            threadId = threadId,
            senderId = agentId,
            timestamp = System.currentTimeMillis(),
            role = "assistant",
            content = feedback,
            metadataJson = metaJson
        ))

        // Post to LiveData
        taskResult.postValue(TaskResult(
            taskId = taskId,
            threadId = threadId,
            agentId = agentId,
            response = response, // Keep the TOOL_CALL:: prefix for ViewModel to handle
            isFileGenerated = true,
            filePath = filePath,
            fileName = generatedName,
            mimeType = mimeType
        ))

        val emoji = when {
            isImage -> "🎨"
            isVideo -> "🎬"
            isAudio -> "🎵"
            else -> "📄"
        }
        showCompletionNotification("$emoji $generatedName listo", feedback)
    }

    private suspend fun handleBrowserResult(taskId: Long, threadId: String, agentId: String, response: String) {
        val url = response.removePrefix("TOOL_CALL::open_browser::")
        val feedback = "🌐 Navegación lista: $url — Abre la app para verla."

        saveToRoom(threadId, agentId, feedback)

        taskResult.postValue(TaskResult(
            taskId = taskId,
            threadId = threadId,
            agentId = agentId,
            response = response
        ))

        showCompletionNotification("🌐 Navegación lista", url)
    }

    private suspend fun handleTextResult(taskId: Long, threadId: String, agentId: String, response: String) {
        // Save to Room
        saveToRoom(threadId, agentId, response)

        // Post to LiveData
        taskResult.postValue(TaskResult(
            taskId = taskId,
            threadId = threadId,
            agentId = agentId,
            response = response
        ))

        // Show completion notification with preview
        val preview = response.take(100).replace("\n", " ")
        showCompletionNotification("✅ E.M.M.A. respondió", preview)
    }

    private suspend fun saveToRoom(threadId: String, agentId: String, content: String) {
        chatHistoryDB.chatHistoryDao().insertMessage(ChatMessageEntity(
            threadId = threadId,
            senderId = agentId,
            timestamp = System.currentTimeMillis(),
            role = "assistant",
            content = content
        ))
    }

    // ═══════════════════════════════════════
    // NOTIFICATIONS
    // ═══════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "E.M.M.A. Tareas IA",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de tareas de IA en progreso"
                setShowBadge(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildProgressNotification(text: String): Notification {
        val cancelIntent = Intent(this, EmmaTaskService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openPending = getLaunchPendingIntent()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🧠 E.M.M.A. procesando...")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(0, 0, true) // Indeterminate progress bar
            .setContentIntent(openPending)
            .addAction(Notification.Action.Builder(
                null, "Cancelar", cancelPending
            ).build())
            .build()
    }

    private fun showCompletionNotification(title: String, text: String) {
        val openPending = getLaunchPendingIntent()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .setTimeoutAfter(60_000) // Auto-dismiss after 60s
            .build()

        try {
            val nm = getSystemService(NotificationManager::class.java)
            // Use a different ID so it doesn't conflict with the foreground notification
            nm.notify(NOTIFICATION_ID + 1, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show completion notification: ${e.message}")
        }
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildProgressNotification(text))
        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
    }

    private fun getLaunchPendingIntent(): PendingIntent? {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        return if (openIntent != null) PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) else null
    }

    // ═══════════════════════════════════════
    // LIFECYCLE HELPERS
    // ═══════════════════════════════════════

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "emma:task_processing"
        ).apply { acquire(5 * 60 * 1000L) } // 5 min max per task
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun cleanupAndStop() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
