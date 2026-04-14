package com.beemovil.telegram

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
import com.beemovil.R
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.llm.LlmFactory
import com.beemovil.security.SecurePrefs
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TelegramBotService — Foreground service that polls the Telegram Bot API
 * for incoming messages, routes them through EmmaEngine, and sends
 * the AI response back to the Telegram chat.
 *
 * Architecture:
 *   1. User provides Bot Token from @BotFather
 *   2. Service starts as Foreground with persistent notification
 *   3. Long-polling loop: getUpdates(offset, timeout=30)
 *   4. Each message → EmmaEngine.processUserMessage()
 *   5. Response → sendMessage() back to Telegram
 *
 * Security: Only responds to the owner's username (if configured)
 *           or to chat IDs in the allowed list.
 */
class TelegramBotService : Service() {

    companion object {
        private const val TAG = "TelegramBot"
        private const val CHANNEL_ID = "emma_telegram_bot"
        private const val NOTIFICATION_ID = 2001
        private const val TELEGRAM_API = "https://api.telegram.org/bot"

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_BOT_TOKEN = "EXTRA_BOT_TOKEN"
        const val EXTRA_PROVIDER = "EXTRA_PROVIDER"
        const val EXTRA_MODEL = "EXTRA_MODEL"
        const val EXTRA_API_KEY = "EXTRA_API_KEY"
        const val PREF_ALLOWED_CHATS = "telegram_allowed_chats"

        /** Callback for SettingsScreen to receive status updates */
        var onStatusChange: ((status: String, botName: String, messageCount: Int) -> Unit)? = null
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var botToken = ""
    private var provider = "openrouter"
    private var model = ""
    private var apiKey = ""
    private var ownerUsername = ""
    private var botUsername = ""
    private var messageCount = 0
    private var lastUpdateId = 0L

    private lateinit var engine: EmmaEngine

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS) // > polling timeout (30s)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // ═══════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        engine = EmmaEngine(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                botToken = intent.getStringExtra(EXTRA_BOT_TOKEN) ?: ""
                provider = intent.getStringExtra(EXTRA_PROVIDER) ?: "openrouter"
                model = intent.getStringExtra(EXTRA_MODEL) ?: ""
                apiKey = intent.getStringExtra(EXTRA_API_KEY) ?: ""

                // Load owner username for security filtering
                val securePrefs = SecurePrefs.get(this)
                ownerUsername = securePrefs.getString("telegram_owner_username", "")
                    ?.removePrefix("@")?.lowercase() ?: ""

                if (botToken.isBlank()) {
                    Log.e(TAG, "No bot token provided!")
                    updateStatus("Error: Sin token", "", 0)
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForeground(NOTIFICATION_ID, buildNotification("Conectando..."))
                acquireWakeLock()
                startPolling()
            }
            ACTION_STOP -> {
                stopPolling()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ═══════════════════════════════════════
    // POLLING LOOP
    // ═══════════════════════════════════════

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            // Step 0: Verify bot token and get bot info
            val botInfo = getBotInfo()
            if (botInfo == null) {
                updateStatus("Error: Token inválido", "", 0)
                withContext(Dispatchers.Main) { stopSelf() }
                return@launch
            }
            botUsername = botInfo
            updateStatus("Activo", botUsername, 0)
            updateNotification("@$botUsername — Escuchando mensajes")
            Log.i(TAG, "Bot conectado: @$botUsername | Owner: @$ownerUsername")

            // Step 1: Initialize engine
            engine.initialize()

            // Step 2: Long-polling loop
            while (isActive) {
                try {
                    val updates = getUpdates(lastUpdateId + 1, timeout = 30)
                    for (update in updates) {
                        lastUpdateId = update.updateId
                        processUpdate(update)
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                    updateStatus("Reconectando...", botUsername, messageCount)
                    delay(5000) // Wait before retry
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        updateStatus("Detenido", botUsername, messageCount)
    }

    // ═══════════════════════════════════════
    // TELEGRAM API CALLS
    // ═══════════════════════════════════════

    private fun getBotInfo(): String? {
        return try {
            val request = Request.Builder()
                .url("${TELEGRAM_API}$botToken/getMe")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            if (json.optBoolean("ok", false)) {
                json.getJSONObject("result").optString("username", "bot")
            } else {
                Log.e(TAG, "getMe failed: $body")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMe error: ${e.message}")
            null
        }
    }

    data class TelegramUpdate(
        val updateId: Long,
        val chatId: Long,
        val text: String,
        val fromUsername: String,
        val firstName: String,
        val messageId: Long
    )

    private fun getUpdates(offset: Long, timeout: Int): List<TelegramUpdate> {
        val url = "${TELEGRAM_API}$botToken/getUpdates?offset=$offset&timeout=$timeout&allowed_updates=[\"message\"]"
        val request = Request.Builder().url(url).get().build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        val json = JSONObject(body)

        if (!json.optBoolean("ok", false)) return emptyList()

        val results = json.optJSONArray("result") ?: return emptyList()
        val updates = mutableListOf<TelegramUpdate>()

        for (i in 0 until results.length()) {
            val update = results.getJSONObject(i)
            val message = update.optJSONObject("message") ?: continue
            val chat = message.optJSONObject("chat") ?: continue
            val from = message.optJSONObject("from")

            updates.add(TelegramUpdate(
                updateId = update.getLong("update_id"),
                chatId = chat.getLong("id"),
                text = message.optString("text", ""),
                fromUsername = from?.optString("username", "")?.lowercase() ?: "",
                firstName = from?.optString("first_name", "Usuario") ?: "Usuario",
                messageId = message.optLong("message_id", 0)
            ))
        }
        return updates
    }

    private fun sendMessage(chatId: Long, text: String, replyToMessageId: Long? = null) {
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "Markdown")
                if (replyToMessageId != null) {
                    put("reply_to_message_id", replyToMessageId)
                }
            }

            val request = Request.Builder()
                .url("${TELEGRAM_API}$botToken/sendMessage")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error: ${e.message}")
        }
    }

    private fun sendTypingAction(chatId: Long) {
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("action", "typing")
            }
            val request = Request.Builder()
                .url("${TELEGRAM_API}$botToken/sendChatAction")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════
    // MESSAGE PROCESSING
    // ═══════════════════════════════════════

    private suspend fun processUpdate(update: TelegramUpdate) {
        if (update.text.isBlank()) return

        // Security: Check if sender is authorized
        if (!isAuthorized(update.fromUsername, update.chatId)) {
            Log.w(TAG, "Unauthorized message from @${update.fromUsername} (chat ${update.chatId})")
            sendMessage(update.chatId, "⛔ No autorizado. Contacta al administrador del bot.")
            return
        }

        Log.i(TAG, "📩 Mensaje de @${update.fromUsername}: ${update.text.take(50)}...")
        messageCount++
        updateStatus("Procesando...", botUsername, messageCount)
        updateNotification("@$botUsername — Procesando mensaje #$messageCount")

        // Show typing indicator
        sendTypingAction(update.chatId)

        try {
            // Route through EmmaEngine
            val response = engine.processUserMessage(update.text, provider, model)

            // Filter out internal tool call signals
            val cleanResponse = when {
                response.startsWith("TOOL_CALL::") -> {
                    val action = response.substringBefore("::", "").substringAfter("::")
                    "✅ Acción ejecutada: $action (disponible en la app de E.M.M.A.)"
                }
                response.length > 4000 -> response.take(4000) + "\n\n... _(respuesta truncada)_"
                else -> response
            }

            sendMessage(update.chatId, cleanResponse, update.messageId)
            updateStatus("Activo", botUsername, messageCount)
            updateNotification("@$botUsername — $messageCount mensajes procesados")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing: ${e.message}")
            sendMessage(update.chatId, "❌ Error interno: ${e.message?.take(100)}")
            updateStatus("Activo", botUsername, messageCount)
        }
    }

    private fun isAuthorized(username: String, chatId: Long): Boolean {
        // If no owner is set, allow everyone (open mode)
        if (ownerUsername.isBlank()) {
            Log.d(TAG, "Auth: Open mode (no owner set). Allowing chatId=$chatId")
            return true
        }
        
        // Normalize both usernames for robust comparison
        val normalizedOwner = ownerUsername.removePrefix("@").trim().lowercase()
        val normalizedSender = username.removePrefix("@").trim().lowercase()
        
        Log.d(TAG, "Auth check: owner='$normalizedOwner' vs sender='$normalizedSender' chatId=$chatId")
        
        // Check owner username (normalized)
        if (normalizedSender.isNotBlank() && normalizedSender == normalizedOwner) {
            // Auto-register this chat ID for future messages (even if username changes)
            autoRegisterChatId(chatId)
            return true
        }
        
        // Check allowed chat IDs (covers cases where username is empty or changed)
        val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val allowedStr = prefs.getString(PREF_ALLOWED_CHATS, "") ?: ""
        if (allowedStr.isNotBlank()) {
            val allowedIds = allowedStr.split(",").mapNotNull { it.trim().toLongOrNull() }
            if (chatId in allowedIds) {
                Log.d(TAG, "Auth: Allowed by chat ID $chatId")
                return true
            }
        }
        
        // If sender has no username (Telegram allows this), auto-authorize 
        // the first message and save the chat ID for future auth
        if (normalizedSender.isBlank()) {
            Log.i(TAG, "Auth: Sender has no username. Auto-authorizing chatId=$chatId")
            autoRegisterChatId(chatId)
            return true
        }
        
        Log.w(TAG, "Auth DENIED: '$normalizedSender' != '$normalizedOwner' and chatId=$chatId not in allowlist")
        return false
    }
    
    private fun autoRegisterChatId(chatId: Long) {
        val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val allowedStr = prefs.getString(PREF_ALLOWED_CHATS, "") ?: ""
        val existingIds = allowedStr.split(",").mapNotNull { it.trim().toLongOrNull() }.toMutableSet()
        if (chatId !in existingIds) {
            existingIds.add(chatId)
            prefs.edit().putString(PREF_ALLOWED_CHATS, existingIds.joinToString(",")).apply()
            Log.i(TAG, "Auto-registered chatId=$chatId for future authorization")
        }
    }

    // ═══════════════════════════════════════
    // NOTIFICATIONS
    // ═══════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "E.M.M.A. Telegram Bot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Bot de Telegram conectado a E.M.M.A."
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, TelegramBotService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPending = if (openIntent != null) PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) else null

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🤖 E.M.M.A. Telegram Bot")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(Notification.Action.Builder(
                null, "Detener", stopPending
            ).build())
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════
    // WAKE LOCK
    // ═══════════════════════════════════════

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "emma:telegram_bot"
        ).apply { acquire(60 * 60 * 1000L) } // 1 hour max
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ═══════════════════════════════════════
    // STATUS CALLBACK
    // ═══════════════════════════════════════

    private fun updateStatus(status: String, name: String, count: Int) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onStatusChange?.invoke(status, name, count)
        }
    }
}
