package com.beemovil.telegram

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.beemovil.MainActivity
import com.beemovil.agent.AgentConfig
import com.beemovil.agent.BeeAgent
import com.beemovil.llm.LlmFactory
import com.beemovil.memory.BeeMemoryDB
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TelegramBotService — Foreground service that polls Telegram Bot API.
 * Reports status back to UI via static callbacks.
 */
class TelegramBotService : Service() {

    companion object {
        private const val TAG = "TelegramBot"
        private const val CHANNEL_ID = "bee_telegram_bot"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.beemovil.telegram.START"
        const val ACTION_STOP = "com.beemovil.telegram.STOP"
        const val EXTRA_BOT_TOKEN = "bot_token"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_MODEL = "model"
        const val EXTRA_API_KEY = "api_key"
        const val PREF_ALLOWED_CHATS = "telegram_allowed_chats"
        const val PREF_ALLOW_ALL = "telegram_allow_all"

        // Status callback for UI
        var onStatusChange: ((status: String, botName: String, msgCount: Int) -> Unit)? = null
        var isRunning = AtomicBoolean(false)
    }

    private var botToken = ""
    private var lastUpdateId = 0L
    private var pollingThread: Thread? = null
    private val shouldRun = AtomicBoolean(false)
    private var agent: BeeAgent? = null
    private var messagesHandled = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var allowedChatIds = mutableSetOf<Long>()
    private var allowAll = false
    private var ownerSet = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(35, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBot()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                botToken = intent.getStringExtra(EXTRA_BOT_TOKEN) ?: ""
                val provider = intent.getStringExtra(EXTRA_PROVIDER) ?: "openrouter"
                val model = intent.getStringExtra(EXTRA_MODEL) ?: "qwen/qwen3.6-plus:free"
                val apiKey = intent.getStringExtra(EXTRA_API_KEY) ?: ""

                if (botToken.isBlank()) {
                    showToast("❌ Token de bot vacío")
                    reportStatus("error", "", 0)
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (apiKey.isBlank()) {
                    showToast("❌ API key vacía, configura primero tu proveedor")
                    reportStatus("error", "", 0)
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Start foreground IMMEDIATELY
                startForeground(NOTIFICATION_ID, buildNotification("🐝 Conectando a Telegram..."))
                reportStatus("connecting", "", 0)
                showToast("🐝 Conectando a Telegram...")
                loadAllowedChats()

                // Setup async to not block
                Thread {
                    try {
                        setupAgent(provider, model, apiKey)

                        // Test connection FIRST
                        val botName = getBotInfo()
                        if (botName != null) {
                            mainHandler.post {
                                showToast("✅ Conectado: @$botName")
                            }
                            updateNotification("🐝 @$botName — activo y escuchando")
                            reportStatus("online", botName, 0)
                            startPolling()
                        } else {
                            mainHandler.post {
                                showToast("❌ Token inválido. Verifica en @BotFather")
                            }
                            reportStatus("error", "", 0)
                            updateNotification("❌ Token inválido")
                            // Stop after 3 seconds
                            Thread.sleep(3000)
                            mainHandler.post { stopBot() }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Setup failed: ${e.message}", e)
                        mainHandler.post {
                            showToast("❌ Error: ${e.message}")
                        }
                        reportStatus("error", "", 0)
                        mainHandler.post { stopBot() }
                    }
                }.start()
            }
        }
        return START_STICKY
    }

    private fun setupAgent(providerType: String, model: String, apiKey: String) {
        val llmProvider = LlmFactory.createProvider(providerType, apiKey, model)
        val memoryDB = try { BeeMemoryDB(this) } catch (_: Exception) { null }

        val config = AgentConfig(
            id = "telegram",
            name = "Bee Telegram",
            icon = "🤖",
            description = "Agente Bee-Movil vía Telegram",
            systemPrompt = """
                Eres Bee-Movil 🐝 respondiendo vía Telegram.
                Eres el asistente personal del dueño de este teléfono.
                Responde de forma amigable, concisa y útil.
                Puedes usar herramientas como: calculator, datetime, weather, web_search, memory.
                No puedes controlar el teléfono directamente desde Telegram.
                Responde en español por defecto.
            """.trimIndent(),
            enabledTools = setOf("calculator", "datetime", "weather", "web_search", "memory", "battery_saver"),
            model = model
        )

        val skillMap = BeeMovilSkillRegistry.getSkills(this)
        agent = BeeAgent(config, llmProvider, skillMap, memoryDB)
        Log.d(TAG, "Agent ready: $providerType/$model with ${skillMap.size} skills")
    }

    private fun startPolling() {
        shouldRun.set(true)
        isRunning.set(true)

        pollingThread = Thread {
            Log.i(TAG, "Polling started")
            while (shouldRun.get()) {
                try {
                    pollUpdates()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                    if (shouldRun.get()) {
                        try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
                    }
                }
            }
            Log.i(TAG, "Polling stopped")
        }.apply {
            name = "TelegramPolling"
            isDaemon = true
            start()
        }
    }

    private fun stopBot() {
        shouldRun.set(false)
        isRunning.set(false)
        pollingThread?.interrupt()
        pollingThread = null
        reportStatus("offline", "", 0)
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
        Log.i(TAG, "Bot stopped")
    }

    private fun pollUpdates() {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=30&allowed_updates=[\"message\"]"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()

        val json = JSONObject(body)
        if (!json.optBoolean("ok", false)) {
            Log.e(TAG, "API error: $body")
            Thread.sleep(5000)
            return
        }

        val results = json.optJSONArray("result") ?: return

        for (i in 0 until results.length()) {
            val update = results.getJSONObject(i)
            lastUpdateId = update.getLong("update_id")

            val message = update.optJSONObject("message") ?: continue
            val text = message.optString("text", "")
            val chat = message.optJSONObject("chat")

            // Telegram API: chat.id is the correct field (not chat_id)
            val chatId = chat?.getLong("id") ?: 0L
            val fromUser = message.optJSONObject("from")?.optString("first_name", "User") ?: "User"

            Log.d(TAG, "Update ${update.getLong("update_id")}: chat=$chat, chatId=$chatId, text='$text', from=$fromUser")

            if (text.isBlank() || chatId == 0L) {
                Log.d(TAG, "Skipping: text blank=${text.isBlank()}, chatId=$chatId")
                continue
            }

            // Guardrail: check allowlist
            if (!isAllowed(chatId, fromUser)) {
                Log.w(TAG, "Blocked message from $fromUser ($chatId) - not in allowlist")
                sendMessage(chatId, "🔒 No tienes acceso a este bot. Contacta al dueño.")
                continue
            }

            Log.i(TAG, "Processing message from $fromUser ($chatId): $text")
            handleMessage(chatId, text, fromUser)
        }
    }

    /**
     * Allowlist guardrail. First user is auto-added as owner.
     */
    private fun isAllowed(chatId: Long, userName: String): Boolean {
        if (allowAll) return true
        if (allowedChatIds.contains(chatId)) return true

        // First user to message becomes the owner (auto-pair)
        if (!ownerSet && allowedChatIds.isEmpty()) {
            allowedChatIds.add(chatId)
            ownerSet = true
            saveAllowedChats()
            Log.i(TAG, "Owner auto-paired: $userName ($chatId)")
            mainHandler.post { showToast("👤 Dueño registrado: $userName") }
            return true
        }
        return false
    }

    private fun saveAllowedChats() {
        val prefs = getSharedPreferences("bee_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_ALLOWED_CHATS, allowedChatIds.joinToString(",")).apply()
    }

    private fun loadAllowedChats() {
        val prefs = getSharedPreferences("bee_prefs", Context.MODE_PRIVATE)
        allowAll = prefs.getBoolean(PREF_ALLOW_ALL, false)
        val saved = prefs.getString(PREF_ALLOWED_CHATS, "") ?: ""
        allowedChatIds.clear()
        if (saved.isNotBlank()) {
            saved.split(",").forEach { id ->
                try { allowedChatIds.add(id.trim().toLong()) } catch (_: Exception) {}
            }
            ownerSet = true
        }
        Log.d(TAG, "Allowlist loaded: $allowedChatIds, allowAll=$allowAll")
    }

    private fun handleMessage(chatId: Long, text: String, fromUser: String) {
        sendChatAction(chatId, "typing")
        Thread {
            try {
                val resp = agent?.chat(text)
                val reply = resp?.text ?: "Lo siento, no pude procesar tu mensaje."
                sendMessage(chatId, reply)
                messagesHandled++
                reportStatus("online", "", messagesHandled)
                updateNotification("🐝 $messagesHandled mensajes procesados")
            } catch (e: Exception) {
                Log.e(TAG, "Handle error: ${e.message}", e)
                sendMessage(chatId, "❌ Error: ${e.message?.take(200)}")
            }
        }.start()
    }

    private fun sendMessage(chatId: Long, text: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        // Try markdown, fallback plain
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId); put("text", text); put("parse_mode", "Markdown")
            }.toString().toRequestBody("application/json".toMediaType())
            val r = client.newCall(Request.Builder().url(url).post(body).build()).execute()
            val rb = r.body?.string() ?: ""; r.close()
            if (JSONObject(rb).optBoolean("ok", false)) return
        } catch (_: Exception) {}
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId); put("text", text)
            }.toString().toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url(url).post(body).build()).execute().close()
        } catch (e: Exception) { Log.e(TAG, "Send failed: ${e.message}") }
    }

    private fun sendChatAction(chatId: Long, action: String) {
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId); put("action", action)
            }.toString().toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url("https://api.telegram.org/bot$botToken/sendChatAction").post(body).build()).execute().close()
        } catch (_: Exception) {}
    }

    private fun getBotInfo(): String? {
        return try {
            val r = client.newCall(Request.Builder().url("https://api.telegram.org/bot$botToken/getMe").build()).execute()
            val b = r.body?.string() ?: ""; r.close()
            Log.d(TAG, "getMe response: $b")
            val json = JSONObject(b)
            if (json.optBoolean("ok", false)) {
                json.optJSONObject("result")?.optString("username")
            } else {
                Log.e(TAG, "getMe failed: $b")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMe error: ${e.message}")
            null
        }
    }

    private fun reportStatus(status: String, botName: String, msgCount: Int) {
        mainHandler.post {
            onStatusChange?.invoke(status, botName, msgCount)
        }
    }

    private fun showToast(msg: String) {
        try {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Telegram Bot", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Bee-Movil Telegram Bot"; setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopPi = PendingIntent.getService(this, 1,
            Intent(this, TelegramBotService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bee-Movil Telegram")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_delete, "Detener", stopPi)
            .setOngoing(true).build()
    }

    private fun updateNotification(text: String) {
        try { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text)) }
        catch (_: Exception) {}
    }

    override fun onDestroy() { stopBot(); super.onDestroy() }
}
