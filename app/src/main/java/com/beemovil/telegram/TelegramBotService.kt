package com.beemovil.telegram

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
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
 * TelegramBotService — Foreground service that polls Telegram Bot API
 * and responds using BeeAgent. Runs entirely on the phone, no VPS needed.
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

        var isRunning = AtomicBoolean(false)
        var messagesHandled = 0
            private set
    }

    private var botToken = ""
    private var lastUpdateId = 0L
    private var pollingThread: Thread? = null
    private val shouldRun = AtomicBoolean(false)
    private var agent: BeeAgent? = null

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

                if (botToken.isBlank() || apiKey.isBlank()) {
                    Log.e(TAG, "Missing bot token or API key")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Must call startForeground IMMEDIATELY before any async work
                val notification = buildNotification("🐝 Telegram Bot iniciando...")
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground failed: ${e.message}")
                    stopSelf()
                    return START_NOT_STICKY
                }

                try {
                    setupAgent(provider, model, apiKey)
                    startPolling()
                } catch (e: Exception) {
                    Log.e(TAG, "Setup failed: ${e.message}", e)
                    stopBot()
                }
            }
        }
        return START_STICKY
    }

    private fun setupAgent(providerType: String, model: String, apiKey: String) {
        val llmProvider = LlmFactory.createProvider(providerType, apiKey, model)
        val memoryDB = try { BeeMemoryDB(this) } catch (e: Exception) { null }

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
                No puedes controlar el teléfono directamente desde Telegram (no flashlight, no camera, etc).
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

            // Get bot info
            val botName = getBotInfo()
            if (botName != null) {
                updateNotification("🐝 @$botName activo")
                Log.i(TAG, "Bot: @$botName")
            } else {
                updateNotification("🐝 Bot activo (verificando token...)")
            }

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
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
        Log.i(TAG, "Bot stopped")
    }

    private fun pollUpdates() {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=30&allowed_updates=[\"message\"]"
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return
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
            val updateId = update.getLong("update_id")
            lastUpdateId = updateId

            val message = update.optJSONObject("message") ?: continue
            val text = message.optString("text", "")
            val chat = message.optJSONObject("chat")
            val chatId = chat?.optLong("chat_id") ?: chat?.optLong("id") ?: 0L
            val fromUser = message.optJSONObject("from")?.optString("first_name", "User") ?: "User"

            if (text.isBlank() || chatId == 0L) continue

            Log.d(TAG, "From $fromUser ($chatId): $text")
            handleMessage(chatId, text, fromUser)
        }
    }

    private fun handleMessage(chatId: Long, text: String, fromUser: String) {
        sendChatAction(chatId, "typing")

        Thread {
            try {
                val response = agent?.chat(text)
                val reply = response?.text ?: "Lo siento, no pude procesar tu mensaje."

                sendMessage(chatId, reply)
                messagesHandled++
                updateNotification("🐝 Telegram Bot — $messagesHandled msgs")
            } catch (e: Exception) {
                Log.e(TAG, "Handle error: ${e.message}", e)
                sendMessage(chatId, "❌ Error: ${e.message?.take(200)}")
            }
        }.start()
    }

    private fun sendMessage(chatId: Long, text: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        // Try with Markdown first, fallback to plain
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "Markdown")
            }.toString().toRequestBody("application/json".toMediaType())
            val resp = client.newCall(Request.Builder().url(url).post(body).build()).execute()
            val respBody = resp.body?.string() ?: ""
            resp.close()
            if (JSONObject(respBody).optBoolean("ok", false)) return
        } catch (_: Exception) {}

        // Fallback: plain text
        try {
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }.toString().toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url(url).post(body).build()).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
        }
    }

    private fun sendChatAction(chatId: Long, action: String) {
        try {
            val url = "https://api.telegram.org/bot$botToken/sendChatAction"
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("action", action)
            }.toString().toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url(url).post(body).build()).execute().close()
        } catch (_: Exception) {}
    }

    private fun getBotInfo(): String? {
        return try {
            val resp = client.newCall(
                Request.Builder().url("https://api.telegram.org/bot$botToken/getMe").build()
            ).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            JSONObject(body).optJSONObject("result")?.optString("username")
        } catch (_: Exception) { null }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Telegram Bot", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Bee-Movil Telegram Bot"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val stopIntent = Intent(this, TelegramBotService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bee-Movil Telegram")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_delete, "Detener", stopPi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopBot()
        super.onDestroy()
    }
}
