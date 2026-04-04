package com.beemovil.telegram

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.beemovil.MainActivity
import com.beemovil.R
import com.beemovil.agent.AgentConfig
import com.beemovil.agent.BeeAgent
import com.beemovil.agent.DefaultAgents
import com.beemovil.llm.LlmFactory
import com.beemovil.memory.BeeMemoryDB
import com.beemovil.skills.BeeSkill
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
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

                setupAgent(provider, model, apiKey)
                startBot()
            }
        }
        return START_STICKY
    }

    private fun setupAgent(providerType: String, model: String, apiKey: String) {
        val llmProvider = LlmFactory.createProvider(providerType, apiKey, model)
        val skills = (applicationContext as? MainActivity)?.let { emptyMap<String, BeeSkill>() }
            ?: emptyMap()

        // Use shared prefs to get skills reference
        val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
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

        // Get skills from the app's skill registry
        val skillMap = BeeMovilSkillRegistry.getSkills(this)
        agent = BeeAgent(config, llmProvider, skillMap, memoryDB)
        Log.d(TAG, "Agent ready: $providerType/$model with ${skillMap.size} skills")
    }

    private fun startBot() {
        shouldRun.set(true)
        isRunning.set(true)

        val notification = buildNotification("🐝 Telegram Bot activo — esperando mensajes...")
        startForeground(NOTIFICATION_ID, notification)

        pollingThread = Thread {
            Log.i(TAG, "Polling started for bot token: ${botToken.take(10)}...")

            // Get bot info first
            val botName = getBotInfo()
            if (botName != null) {
                updateNotification("🐝 @$botName — activo y escuchando")
                Log.i(TAG, "Bot: @$botName")
            }

            while (shouldRun.get()) {
                try {
                    pollUpdates()
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                    if (shouldRun.get()) Thread.sleep(5000)
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Bot stopped")
    }

    private fun pollUpdates() {
        val url = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=30&allowed_updates=[\"message\"]"
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return
        val json = JSONObject(body)

        if (!json.optBoolean("ok", false)) {
            Log.e(TAG, "Telegram API error: $body")
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
            val chatId = message.optJSONObject("chat")?.optLong("chat_id", 0) ?: 0
            val fromUser = message.optJSONObject("from")?.optString("first_name", "User") ?: "User"

            if (text.isBlank() || chatId == 0L) continue

            Log.d(TAG, "Message from $fromUser: $text")
            handleMessage(chatId, text, fromUser)
        }
    }

    private fun handleMessage(chatId: Long, text: String, fromUser: String) {
        // Send typing action
        sendChatAction(chatId, "typing")

        Thread {
            try {
                val response = agent?.chat(text)
                val reply = response?.text ?: "Lo siento, no pude procesar tu mensaje."

                sendMessage(chatId, reply)
                messagesHandled++
                updateNotification("🐝 Telegram Bot — $messagesHandled mensajes procesados")
                Log.d(TAG, "Replied to $fromUser: ${reply.take(100)}...")
            } catch (e: Exception) {
                Log.e(TAG, "Handler error: ${e.message}")
                sendMessage(chatId, "❌ Error procesando mensaje: ${e.message}")
            }
        }.start()
    }

    private fun sendMessage(chatId: Long, text: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val jsonBody = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text)
            put("parse_mode", "Markdown")
        }
        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
            // Retry without markdown
            try {
                val plainBody = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                }.toString().toRequestBody("application/json".toMediaType())
                client.newCall(Request.Builder().url(url).post(plainBody).build()).execute().close()
            } catch (_: Exception) {}
        }
    }

    private fun sendChatAction(chatId: Long, action: String) {
        val url = "https://api.telegram.org/bot$botToken/sendChatAction"
        val body = JSONObject().apply {
            put("chat_id", chatId)
            put("action", action)
        }.toString().toRequestBody("application/json".toMediaType())

        try {
            client.newCall(Request.Builder().url(url).post(body).build()).execute().close()
        } catch (_: Exception) {}
    }

    private fun getBotInfo(): String? {
        return try {
            val url = "https://api.telegram.org/bot$botToken/getMe"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JSONObject(response.body?.string() ?: "")
            json.optJSONObject("result")?.optString("username")
        } catch (_: Exception) { null }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telegram Bot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Bee-Movil Telegram Bot Service"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, TelegramBotService::class.java).apply {
            action = ACTION_STOP
        }
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        stopBot()
        super.onDestroy()
    }
}
