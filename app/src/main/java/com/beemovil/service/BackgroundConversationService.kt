package com.beemovil.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.beemovil.BeeMovilApp
import com.beemovil.MainActivity
import com.beemovil.R
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.database.ChatHistoryDB
import com.beemovil.database.ChatMessageEntity
import com.beemovil.voice.DeepgramSTT
import com.beemovil.voice.DeepgramVoiceManager
import com.beemovil.voice.MicrophoneArbiter
import com.beemovil.security.SecurePrefs
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * BackgroundConversationService (BCS) — Voice Intelligence Phase V7.2
 *
 * Foreground service that enables full voice conversations with E.M.M.A.
 * even when the app is closed or the screen is locked.
 *
 * Architecture:
 * - Uses DeepgramSTT (AudioRecord-based) for continuous STT — works with screen off
 * - Uses EmmaEngine singleton for LLM + tool execution (40+ plugins)
 * - Uses TTS for spoken responses
 * - Persists all turns to ChatHistoryDB (same DB as in-app conversations)
 * - Shows a notification with Pause/Stop/Open controls
 *
 * Trigger: WakeWordService detects "Hello Emma" → starts this service
 * Lifecycle: Runs until user says "ya terminamos" or taps Stop in notification
 */
class BackgroundConversationService : Service() {

    companion object {
        private const val TAG = "BgConvService"
        private const val CHANNEL_ID = "emma_bg_conversation"
        private const val NOTIFICATION_ID = 4244

        const val ACTION_START = "com.beemovil.action.BG_CONV_START"
        const val ACTION_STOP = "com.beemovil.action.BG_CONV_STOP"
        const val ACTION_PAUSE = "com.beemovil.action.BG_CONV_PAUSE"
        const val ACTION_RESUME = "com.beemovil.action.BG_CONV_RESUME"

        @Volatile
        var isRunning = false
            private set
    }

    // --- State ---
    private enum class BCSState { IDLE, GREETING, LISTENING, PROCESSING, SPEAKING, PAUSED, STANDBY }
    private var state = BCSState.IDLE
    private var turnCount = 0
    private var sessionStartMs = 0L

    // --- Dependencies ---
    private var emmaEngine: EmmaEngine? = null
    private var deepgramSTT: DeepgramSTT? = null
    private var voiceManager: DeepgramVoiceManager? = null
    private var chatHistoryDB: ChatHistoryDB? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // --- Coroutine ---
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var conversationJob: Job? = null

    // --- Session ---
    private var threadId = ""
    private var lastResponseText = ""

    // --- Standby timer ---
    private var silenceStartMs = 0L
    private val STANDBY_TIMEOUT_MS = 60_000L  // 60s silence → standby
    private val AUTO_STOP_TIMEOUT_MS = 300_000L // 5min standby → stop

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize from app singleton
        val app = application as BeeMovilApp
        emmaEngine = app.emmaEngine
        chatHistoryDB = ChatHistoryDB.getDatabase(applicationContext)

        // Create voice manager for TTS
        voiceManager = DeepgramVoiceManager(applicationContext)

        // Create Deepgram STT instance
        val prefs = SecurePrefs.get(applicationContext)
        val dgKey = prefs.getString("deepgram_api_key", "") ?: ""
        if (dgKey.isNotBlank()) {
            deepgramSTT = DeepgramSTT(applicationContext)
        }

        Log.i(TAG, "Service created (Deepgram=${deepgramSTT != null})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startConversation()
            ACTION_STOP -> stopConversation()
            ACTION_PAUSE -> pauseConversation()
            ACTION_RESUME -> resumeConversation()
            else -> startConversation()
        }
        return START_STICKY
    }

    // ═══════════════════════════════════════════
    //  CONVERSATION LIFECYCLE
    // ═══════════════════════════════════════════

    private fun startConversation() {
        if (isRunning) {
            Log.w(TAG, "Already running — ignoring start")
            return
        }

        Log.i(TAG, "🎙️ Starting background conversation")
        isRunning = true
        sessionStartMs = System.currentTimeMillis()
        turnCount = 0

        // Generate thread ID for this session
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        threadId = "bg_voice_${sdf.format(Date())}"

        // Acquire partial wake lock (keeps CPU alive, screen can be off)
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "emma:bg_conversation"
            )
            wakeLock?.acquire(600_000) // 10 min max
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire failed: ${e.message}")
        }

        // Acquire mic
        MicrophoneArbiter.requestMic(MicrophoneArbiter.MicOwner.CONVERSATION, "BgConvService")

        // Show notification
        val notification = buildNotification("🎙️ Iniciando conversación...")
        startForeground(NOTIFICATION_ID, notification)

        // Initialize EmmaEngine if needed
        serviceScope.launch {
            try {
                if (emmaEngine?.plugins?.isEmpty() == true) {
                    emmaEngine?.initialize()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Engine init: ${e.message}")
            }

            // Greet user
            greetAndStartListening()
        }
    }

    private fun greetAndStartListening() {
        state = BCSState.GREETING

        val greetings = listOf(
            "Hola, te escucho.",
            "Aquí estoy, dime.",
            "Hola, ¿en qué te ayudo?",
            "Lista para escucharte."
        )
        val greeting = greetings.random()

        // Save greeting to history
        persistMessage(greeting, "assistant")

        updateNotification("🔊 $greeting")

        voiceManager?.speak(
            text = greeting,
            language = Locale.getDefault().language,
            onDone = {
                beginListening()
            },
            onError = { _ ->
                // Even if TTS fails, start listening
                beginListening()
            }
        )
    }

    // ═══════════════════════════════════════════
    //  LISTENING (DeepgramSTT with AudioRecord)
    // ═══════════════════════════════════════════

    private fun beginListening() {
        if (!isRunning || state == BCSState.PAUSED) return
        state = BCSState.LISTENING
        silenceStartMs = System.currentTimeMillis()
        updateNotification("🎤 Escuchando...")

        val prefs = SecurePrefs.get(applicationContext)
        val dgKey = prefs.getString("deepgram_api_key", "") ?: ""

        if (dgKey.isNotBlank() && deepgramSTT != null) {
            // Use Deepgram STT (AudioRecord-based, works with screen off)
            deepgramSTT?.startListening(
                apiKey = dgKey,
                language = Locale.getDefault().language.substringBefore("-"),
                onPartial = { partial ->
                    if (partial.isNotBlank()) {
                        silenceStartMs = System.currentTimeMillis() // Reset silence timer
                        updateNotification("🎤 \"$partial...\"")
                    }
                },
                onResult = { transcript ->
                    if (transcript.isNotBlank()) {
                        Log.i(TAG, "Transcript: ${transcript.take(80)}")
                        processTranscript(transcript)
                    } else {
                        checkStandbyTimeout()
                        beginListening() // Empty result, try again
                    }
                },
                onErrorCb = { error ->
                    Log.w(TAG, "STT error: $error — retrying in 1s")
                    serviceScope.launch {
                        delay(1000)
                        if (isRunning && state != BCSState.PAUSED) {
                            beginListening()
                        }
                    }
                },
                onState = null
            )
        } else {
            // No Deepgram key — use native SpeechRecognizer as fallback
            // Note: This won't work reliably with screen off
            Log.w(TAG, "No Deepgram API key — using native STT (limited background support)")
            voiceManager?.startListening(
                language = Locale.getDefault().toLanguageTag(),
                onResult = { transcript ->
                    if (transcript.isNotBlank()) {
                        processTranscript(transcript)
                    } else {
                        checkStandbyTimeout()
                        serviceScope.launch {
                            delay(500)
                            beginListening()
                        }
                    }
                },
                onError = { error ->
                    Log.w(TAG, "Native STT error: $error")
                    serviceScope.launch {
                        delay(1000)
                        if (isRunning) beginListening()
                    }
                },
                micOwner = MicrophoneArbiter.MicOwner.CONVERSATION,
                micTag = "BgConv-Turn$turnCount"
            )
        }
    }

    // ═══════════════════════════════════════════
    //  PROCESSING (EmmaEngine + Plugins)
    // ═══════════════════════════════════════════

    private fun processTranscript(transcript: String) {
        state = BCSState.PROCESSING
        turnCount++
        updateNotification("🧠 Procesando: \"${transcript.take(40)}...\"")

        // Check for stop commands
        val lower = transcript.lowercase().trim()
        if (lower in listOf("ya terminamos", "ya terminé", "terminar conversación",
                "para emma", "adiós emma", "bye emma", "stop")) {
            farewell()
            return
        }
        if (lower in listOf("pausa", "pause", "espera emma")) {
            pauseConversation()
            return
        }

        // Save user message
        persistMessage(transcript, "user")

        // Inject tool reminder for self-awareness
        val toolReminder = buildToolReminder()

        conversationJob = serviceScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val prefs = SecurePrefs.get(applicationContext)
                    val provider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
                    val model = prefs.getString("selected_model", "") ?: ""

                    // Prepend tool reminder to transcript
                    val enrichedMessage = "$toolReminder\n\nUsuario dice: $transcript"

                    emmaEngine?.processUserMessage(
                        message = enrichedMessage,
                        forcedProvider = provider,
                        forcedModel = model,
                        threadId = threadId,
                        senderId = "conversation"
                    ) ?: "No pude procesar tu mensaje"
                }

                if (response.isNotBlank()) {
                    lastResponseText = response
                    persistMessage(response, "assistant")
                    speakResponse(response)
                } else {
                    beginListening()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Processing cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Processing error: ${e.message}")
                val errorMsg = "Hubo un error procesando tu mensaje"
                speakResponse(errorMsg)
            }
        }
    }

    // ═══════════════════════════════════════════
    //  SPEAKING (TTS)
    // ═══════════════════════════════════════════

    private fun speakResponse(text: String) {
        state = BCSState.SPEAKING
        val displayText = text.take(60).replace("\n", " ")
        updateNotification("🔊 \"$displayText...\"")

        voiceManager?.speak(
            text = text,
            language = Locale.getDefault().language,
            onDone = {
                // THE LOOP: TTS done → listen again
                if (isRunning && state == BCSState.SPEAKING) {
                    beginListening()
                }
            },
            onError = { _ ->
                // Even if TTS fails, continue listening
                if (isRunning) {
                    beginListening()
                }
            }
        )
    }

    private fun farewell() {
        val farewells = listOf(
            "¡Hasta luego!",
            "¡Nos vemos!",
            "Ok, aquí estaré si me necesitas.",
            "¡Chao! Di Hello Emma cuando quieras."
        )
        val msg = farewells.random()
        persistMessage(msg, "assistant")
        updateNotification("👋 $msg")

        voiceManager?.speak(
            text = msg,
            language = Locale.getDefault().language,
            onDone = { stopConversation() },
            onError = { stopConversation() }
        )
    }

    // ═══════════════════════════════════════════
    //  STANDBY & TIMEOUT
    // ═══════════════════════════════════════════

    private fun checkStandbyTimeout() {
        val silenceDuration = System.currentTimeMillis() - silenceStartMs
        if (silenceDuration > AUTO_STOP_TIMEOUT_MS && state == BCSState.STANDBY) {
            Log.i(TAG, "Auto-stop: 5min standby timeout")
            farewell()
        } else if (silenceDuration > STANDBY_TIMEOUT_MS && state == BCSState.LISTENING) {
            Log.i(TAG, "Entering standby (60s silence)")
            state = BCSState.STANDBY
            updateNotification("💤 Emma en espera — habla para continuar")
        }
    }

    // ═══════════════════════════════════════════
    //  LIFECYCLE CONTROLS
    // ═══════════════════════════════════════════

    private fun pauseConversation() {
        state = BCSState.PAUSED
        deepgramSTT?.stopListening()
        voiceManager?.stopListening()
        voiceManager?.stopSpeaking()
        updateNotification("⏸️ Emma pausada — toca ▶️ para continuar")
    }

    private fun resumeConversation() {
        if (state == BCSState.PAUSED) {
            voiceManager?.speak(
                text = "Aquí sigo, dime.",
                language = Locale.getDefault().language,
                onDone = { beginListening() },
                onError = { beginListening() }
            )
        }
    }

    private fun stopConversation() {
        Log.i(TAG, "🛑 Stopping background conversation (turns=$turnCount)")
        isRunning = false
        state = BCSState.IDLE
        conversationJob?.cancel()

        deepgramSTT?.stopListening()
        voiceManager?.stopListening()
        voiceManager?.stopSpeaking()

        MicrophoneArbiter.releaseMic(MicrophoneArbiter.MicOwner.CONVERSATION)

        // Release wake lock
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {}
        wakeLock = null

        // Resume WakeWordService if enabled
        resumeWakeWordService()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun resumeWakeWordService() {
        try {
            val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
            if (prefs.getBoolean("wake_word_enabled", false)) {
                val intent = Intent(this, WakeWordService::class.java).apply {
                    action = WakeWordService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Log.i(TAG, "Resumed WakeWordService")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resume WakeWordService: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopConversation()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ═══════════════════════════════════════════
    //  TOOL SELF-AWARENESS
    // ═══════════════════════════════════════════

    private fun buildToolReminder(): String {
        val safePlugins = emmaEngine?.plugins?.keys?.joinToString(", ") ?: ""
        return """[MODO VOZ BACKGROUND — E.M.M.A. v7.2]
[HERRAMIENTAS ACTIVAS: $safePlugins]
[INSTRUCCIONES: Responde de forma CONCISA (máximo 3 oraciones) porque estoy escuchando por voz.
Para API keys usa emma_self_config. Para tu estado usa emma_diagnostics.
SIEMPRE usa herramientas cuando sea apropiado. NUNCA digas "no puedo".]"""
    }

    // ═══════════════════════════════════════════
    //  PERSISTENCE
    // ═══════════════════════════════════════════

    private fun persistMessage(content: String, role: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                chatHistoryDB?.chatHistoryDao()?.insertMessage(
                    ChatMessageEntity(
                        threadId = threadId,
                        senderId = if (role == "user") "user" else "conversation",
                        timestamp = System.currentTimeMillis(),
                        role = role,
                        content = content
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Persist failed: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════
    //  NOTIFICATIONS
    // ═══════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "E.M.M.A. Voice Conversation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Conversación de voz activa con E.M.M.A."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        // Open app intent
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_thread", threadId)
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 10, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop intent
        val stopIntent = Intent(this, BackgroundConversationService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 11, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause/Resume intent
        val isPaused = state == BCSState.PAUSED
        val toggleIntent = Intent(this, BackgroundConversationService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pendingToggle = PendingIntent.getService(
            this, 12, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Duration
        val duration = if (sessionStartMs > 0) {
            val secs = (System.currentTimeMillis() - sessionStartMs) / 1000
            val mins = secs / 60
            val secsPart = secs % 60
            "%d:%02d".format(mins, secsPart)
        } else "0:00"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎙️ E.M.M.A. Voice — $duration")
            .setContentText(text)
            .setContentIntent(pendingOpen)
            .addAction(
                R.mipmap.ic_launcher,
                if (isPaused) "▶️ Reanudar" else "⏸️ Pausa",
                pendingToggle
            )
            .addAction(R.mipmap.ic_launcher, "🛑 Terminar", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val notification = buildNotification(text)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Notification update failed: ${e.message}")
        }
    }
}
