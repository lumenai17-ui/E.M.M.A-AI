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
import androidx.core.content.edit
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
        const val PREF_ALLOWED_USERS = "telegram_allowed_users"

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
        val messageId: Long,
        val chatType: String,       // "private", "group", "supergroup", "channel"
        val fromUserId: Long        // Telegram user ID (always present, unlike username)
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
                messageId = message.optLong("message_id", 0),
                chatType = chat.optString("type", "private"),
                fromUserId = from?.optLong("id", 0L) ?: 0L
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
        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
    }

    /**
     * Send a file as a Telegram document using multipart upload.
     * Supports images (sends as photo if small enough) and documents.
     */
    private fun sendDocument(chatId: Long, file: java.io.File, replyToMessageId: Long? = null) {
        val extension = file.name.substringAfterLast('.', "").lowercase()
        val isImage = extension in listOf("png", "jpg", "jpeg", "webp", "gif")
        val mimeType = when (extension) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "html" -> "text/html"
            else -> "application/octet-stream"
        }

        // Use sendPhoto for images (better Telegram UX), sendDocument for others
        val endpoint = if (isImage && file.length() < 10 * 1024 * 1024) "sendPhoto" else "sendDocument"
        val fieldName = if (endpoint == "sendPhoto") "photo" else "document"

        val multipartBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart(fieldName, file.name,
                okhttp3.RequestBody.create(mimeType.toMediaType(), file))
            .addFormDataPart("caption", "📎 ${file.name}")
            .apply {
                if (replyToMessageId != null) {
                    addFormDataPart("reply_to_message_id", replyToMessageId.toString())
                }
            }
            .build()

        val request = Request.Builder()
            .url("${TELEGRAM_API}$botToken/$endpoint")
            .post(multipartBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val respBody = response.body?.string()
        response.close()
        
        if (!response.isSuccessful) {
            Log.e(TAG, "sendDocument failed: $respBody")
            // Fallback: if sendPhoto fails, try sendDocument
            if (endpoint == "sendPhoto") {
                sendDocumentFallback(chatId, file, replyToMessageId)
            } else {
                throw Exception("Failed to send document: ${response.code}")
            }
        } else {
            Log.i(TAG, "📤 File sent to Telegram: ${file.name}")
        }
    }

    private fun sendDocumentFallback(chatId: Long, file: java.io.File, replyToMessageId: Long?) {
        val multipartBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart("document", file.name,
                okhttp3.RequestBody.create("application/octet-stream".toMediaType(), file))
            .addFormDataPart("caption", "📎 ${file.name}")
            .apply {
                if (replyToMessageId != null) {
                    addFormDataPart("reply_to_message_id", replyToMessageId.toString())
                }
            }
            .build()

        val request = Request.Builder()
            .url("${TELEGRAM_API}$botToken/sendDocument")
            .post(multipartBody)
            .build()

        httpClient.newCall(request).execute().close()
    }

    // ═══════════════════════════════════════
    // MESSAGE PROCESSING
    // ═══════════════════════════════════════

    private suspend fun processUpdate(update: TelegramUpdate) {
        if (update.text.isBlank()) return

        // Security: Check if sender is authorized
        if (!isAuthorized(update)) {
            Log.w(TAG, "Unauthorized message from @${update.fromUsername} userId=${update.fromUserId} (chat ${update.chatId} ${update.chatType})")
            sendMessage(update.chatId, "⛔ No autorizado (ID: ${update.fromUserId}). Contacta al administrador del bot.")
            return
        }

        Log.i(TAG, "📩 Mensaje de @${update.fromUsername}: ${update.text.take(50)}...")
        messageCount++
        updateStatus("Procesando...", botUsername, messageCount)
        updateNotification("@$botUsername — Procesando mensaje #$messageCount")

        // Show typing indicator
        sendTypingAction(update.chatId)

        try {
            // Route through EmmaEngine with timeout to prevent hanging
            val response = withTimeoutOrNull(120_000L) { // 2 min max
                engine.processUserMessage(update.text, provider, model)
            } ?: run {
                sendMessage(update.chatId, "⏰ La operación tardó demasiado. Intenta con algo más simple.", update.messageId)
                updateStatus("Activo", botUsername, messageCount)
                return
            }

            // Handle file generation — send file as Telegram document
            if (response.startsWith("TOOL_CALL::file_generated::")) {
                val filePath = response.removePrefix("TOOL_CALL::file_generated::")
                val file = java.io.File(filePath)
                if (file.exists()) {
                    try {
                        sendDocument(update.chatId, file, update.messageId)
                        updateStatus("Activo", botUsername, messageCount)
                        updateNotification("@$botUsername — $messageCount mensajes procesados")
                        return
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending document: ${e.message}")
                        sendMessage(update.chatId, "✅ Archivo generado (${file.name}) pero no pude enviarlo por Telegram. Está disponible en la app.", update.messageId)
                        updateStatus("Activo", botUsername, messageCount)
                        return
                    }
                } else {
                    sendMessage(update.chatId, "⚠️ Archivo generado pero no encontrado en: ${file.name}", update.messageId)
                    updateStatus("Activo", botUsername, messageCount)
                    return
                }
            }

            // Handle browser (can't open from Telegram)
            if (response.startsWith("TOOL_CALL::open_browser::")) {
                val url = response.removePrefix("TOOL_CALL::open_browser::")
                sendMessage(update.chatId, "🌐 Enlace: $url\n\n_(Abre la app de E.M.M.A. para navegación interactiva)_", update.messageId)
                updateStatus("Activo", botUsername, messageCount)
                return
            }

            // Normal text response
            val cleanResponse = when {
                response.startsWith("TOOL_CALL::") -> {
                    "✅ Acción ejecutada desde la app de E.M.M.A."
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

    /**
     * 3-Layer Authorization System:
     *  Layer 1: First-Contact Rule — if no owner AND no allowlist, first user auto-registers as owner
     *  Layer 2: Owner username match — normalized comparison with auto-registration
     *  Layer 3: Allowlist — chatId (groups+private) and userId checks
     */
    private fun isAuthorized(update: TelegramUpdate): Boolean {
        val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val allowedChatStr = prefs.getString(PREF_ALLOWED_CHATS, "") ?: ""
        val allowedChatIds = if (allowedChatStr.isNotBlank()) {
            allowedChatStr.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
        } else emptySet()

        val allowedUserStr = prefs.getString(PREF_ALLOWED_USERS, "") ?: ""
        val allowedUserIds = if (allowedUserStr.isNotBlank()) {
            allowedUserStr.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
        } else emptySet()

        Log.d(TAG, "Auth check: owner='$ownerUsername' sender='@${update.fromUsername}' userId=${update.fromUserId} chatId=${update.chatId} type=${update.chatType} allowedChats=${allowedChatIds.size} allowedUsers=${allowedUserIds.size}")

        // ── Layer 1: First-Contact Rule ──
        // If no owner AND no one in any allowlist, the first user that sends /start auto-registers
        if (ownerUsername.isBlank() && allowedChatIds.isEmpty() && allowedUserIds.isEmpty()) {
            Log.i(TAG, "First-contact: auto-registering owner @${update.fromUsername} (userId=${update.fromUserId}, chatId=${update.chatId})")
            val securePrefs = SecurePrefs.get(this)
            if (update.fromUsername.isNotBlank()) {
                securePrefs.edit().putString("telegram_owner_username", update.fromUsername.removePrefix("@").trim().lowercase()).apply()
                ownerUsername = update.fromUsername.removePrefix("@").trim().lowercase()
            }
            autoRegisterChatId(update.chatId)
            autoRegisterUserId(update.fromUserId)
            return true
        }

        // ── Layer 2: Owner username match ──
        if (ownerUsername.isNotBlank()) {
            val normalizedOwner = ownerUsername.removePrefix("@").trim().lowercase()
            val normalizedSender = update.fromUsername.removePrefix("@").trim().lowercase()
            if (normalizedSender.isNotBlank() && normalizedSender == normalizedOwner) {
                autoRegisterChatId(update.chatId)
                autoRegisterUserId(update.fromUserId)
                return true
            }
        }

        // ── Layer 3: Allowlist (chat IDs + user IDs) ──
        if (update.chatId in allowedChatIds) {
            Log.d(TAG, "Auth OK: chatId=${update.chatId} in chat allowlist")
            return true
        }
        if (update.fromUserId in allowedUserIds) {
            Log.d(TAG, "Auth OK: userId=${update.fromUserId} in user allowlist")
            // Authorized user in a new group → auto-register the group too
            autoRegisterChatId(update.chatId)
            return true
        }

        Log.w(TAG, "Auth DENIED: @${update.fromUsername} userId=${update.fromUserId} chatId=${update.chatId} (${update.chatType}) not in any allowlist")
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

    private fun autoRegisterUserId(userId: Long) {
        if (userId == 0L) return
        val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val existing = prefs.getString(PREF_ALLOWED_USERS, "")?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }?.toMutableSet() ?: mutableSetOf()
        if (userId !in existing) {
            existing.add(userId)
            prefs.edit().putString(PREF_ALLOWED_USERS, existing.joinToString(",")).apply()
            Log.i(TAG, "Auto-registered userId=$userId for future authorization")
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
        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
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
