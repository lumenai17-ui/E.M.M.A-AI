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
import com.beemovil.R
import com.beemovil.llm.*
import com.beemovil.ui.screens.autoSelectVisionModel
import com.beemovil.ui.screens.getApiKeyForProvider
import com.beemovil.vision.*
import com.beemovil.voice.DeepgramVoiceManager
import kotlinx.coroutines.*

/**
 * PocketVisionService — Phase V6: El Bolsillo
 *
 * ForegroundService that keeps E.M.M.A. Vision alive with screen off.
 * Camera is OFF. GPS + LLM + TTS are active.
 * User hears narration through earbuds.
 *
 * Lifecycle:
 *   1. User activates Pocket Mode in LiveVisionScreen
 *   2. Service starts foreground with persistent notification
 *   3. GPS loop runs every 10-15s → geocode → LLM → TTS narration
 *   4. STT available on-demand (tap notification action)
 *   5. User stops via notification or returns to app
 */
class PocketVisionService : Service() {

    companion object {
        private const val TAG = "PocketVisionSvc"
        private const val CHANNEL_ID = "emma_pocket_channel"
        private const val NOTIFICATION_ID = 4001

        const val ACTION_START = "ACTION_START_POCKET"
        const val ACTION_STOP = "ACTION_STOP_POCKET"
        const val ACTION_TALK = "ACTION_TALK"
        const val EXTRA_INTERVAL = "EXTRA_INTERVAL"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_PERSONALITY = "EXTRA_PERSONALITY"

        var isRunning = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Engines
    private lateinit var gpsModule: GpsModule
    private lateinit var voiceManager: DeepgramVoiceManager
    private lateinit var voiceController: VisionVoiceController
    private lateinit var conversation: VisionConversation
    private lateinit var contextProvider: LiveContextProvider
    private lateinit var gpsNavigator: GpsNavigator
    private lateinit var intentDetector: VisionIntentDetector
    private lateinit var offlineCache: OfflineContextCache

    private var currentGpsData = GpsData()
    private var webContext = ""
    private var intervalSeconds = 15
    private var selectedMode = VisionMode.POCKET
    private var provider: LlmProvider? = null

    // ═══ LIFECYCLE ═══

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize engines
        gpsModule = GpsModule(applicationContext)
        voiceManager = DeepgramVoiceManager(applicationContext).also { it.initialize() }
        voiceController = VisionVoiceController(applicationContext, voiceManager)
        conversation = VisionConversation()
        contextProvider = LiveContextProvider(applicationContext)
        gpsNavigator = GpsNavigator()
        intentDetector = VisionIntentDetector(applicationContext)
        offlineCache = OfflineContextCache.getInstance(applicationContext)

        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // BUG-7 FIX: If Android re-created the service without intent (after kill), stop immediately
        if (intent == null) {
            Log.w(TAG, "Service restarted by OS without intent — stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                intervalSeconds = intent.getIntExtra(EXTRA_INTERVAL, 15)
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: "POCKET"
                selectedMode = try { VisionMode.valueOf(modeName) } catch (_: Exception) { VisionMode.POCKET }

                startForeground(NOTIFICATION_ID, buildNotification("🎧 Iniciando modo bolsillo..."))
                acquireWakeLock()
                startPocketLoop()
                isRunning = true
            }
            ACTION_STOP -> {
                stopPocketLoop()
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                releaseWakeLock()
                stopSelf()
            }
            ACTION_TALK -> {
                // On-demand STT from notification
                voiceController.startListening()
            }
        }
        return START_NOT_STICKY  // BUG-7 FIX: Don't auto-restart after kill
    }

    override fun onDestroy() {
        stopPocketLoop()
        isRunning = false
        voiceController.stop()
        voiceManager.destroy()
        gpsModule.stop()
        gpsNavigator.stopNavigation()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ═══ POCKET LOOP ═══

    private fun startPocketLoop() {
        // Setup GPS
        if (gpsModule.hasPermission) {
            gpsModule.onLocationUpdate = { data ->
                currentGpsData = data
                if (gpsNavigator.isNavigating) {
                    val update = gpsNavigator.update(data)
                    if (update.phase == NavPhase.ARRIVED) {
                        gpsNavigator.stopNavigation()
                        voiceController.narrate("¡Llegaste a tu destino!")
                    }
                }
            }
            gpsModule.start()
        }

        // Setup voice controller
        voiceController.onSpeechResult = { spokenText ->
            val detectedIntent = intentDetector.detect(spokenText)
            when (detectedIntent.type) {
                VisionIntentDetector.IntentType.STOP_NAV -> {
                    gpsNavigator.stopNavigation()
                    voiceController.narrate("Navegación cancelada.")
                }
                VisionIntentDetector.IntentType.NAVIGATION -> {
                    voiceController.narrate("Buscando ${detectedIntent.destination}...")
                    scope.launch {
                        val dest = intentDetector.resolveDestination(
                            detectedIntent.destination,
                            currentGpsData.latitude,
                            currentGpsData.longitude
                        )
                        if (dest != null) {
                            gpsNavigator.startNavigation(dest)
                            voiceController.narrate("Navegando a ${dest.name}.")
                        } else {
                            voiceController.narrate("No pude encontrar ${detectedIntent.destination}.")
                        }
                    }
                }
                else -> {
                    conversation.addUserQuestion(spokenText)
                }
            }
        }
        voiceController.setNarrationEnabled(true)

        // Resolve LLM provider
        scope.launch {
            val prefs = applicationContext.getSharedPreferences("beemovil", 0)
            val model = prefs.getString("vision_model", "") ?: ""
            val modelEntry = ModelRegistry.findModel(model)
            val providerType = modelEntry?.provider ?: "openrouter"
            val apiKey = getApiKeyForProvider(applicationContext, providerType)

            if (apiKey.isBlank() && providerType != "local") {
                voiceController.narrate("Necesito una API key configurada para funcionar.")
                stopSelf()
                return@launch
            }

            provider = try {
                LlmFactory.createProvider(providerType, apiKey, model)
            } catch (e: Exception) {
                voiceController.narrate("Error iniciando el motor de inteligencia.")
                null
            }

            if (provider == null) {
                stopSelf()
                return@launch
            }

            voiceController.narrate("Modo bolsillo activado. Te narraré el entorno.")
            updateNotification("🎧 E.M.M.A. en tu bolsillo · ${currentGpsData.address.take(30)}")

            // Main narration loop
            loopJob = scope.launch {
                while (isActive) {
                    delay(intervalSeconds * 1000L)
                    if (!isActive) break

                    try {
                        // Fetch web context (with offline fallback)
                        if (currentGpsData.address.isNotBlank()) {
                            webContext = try {
                                val online = contextProvider.fetchContext(
                                    currentGpsData.address, selectedMode, currentGpsData.coordsShort
                                )
                                // Save to offline cache
                                if (online.isNotBlank()) {
                                    offlineCache.save(
                                        currentGpsData.latitude, currentGpsData.longitude,
                                        "web", online, "duckduckgo", selectedMode.name.lowercase(),
                                        currentGpsData.address
                                    )
                                }
                                online
                            } catch (_: Exception) {
                                // Offline fallback
                                offlineCache.get(
                                    currentGpsData.latitude, currentGpsData.longitude,
                                    selectedMode.name.lowercase()
                                )
                            }
                        }

                        // Build prompt (no image in pocket mode)
                        val userQ = conversation.consumeQuestion()
                        val navUpdate = if (gpsNavigator.isNavigating) {
                            gpsNavigator.update(currentGpsData)
                        } else null

                        val systemPrompt = conversation.buildSystemPrompt(
                            mode = selectedMode,
                            userQuestion = userQ,
                            gpsData = currentGpsData,
                            webContext = webContext,
                            navUpdate = navUpdate
                        )

                        // LLM call (no image — text only)
                        val messages = listOf(
                            ChatMessage(role = "system", content = systemPrompt),
                            ChatMessage(role = "user", content = userQ ?: "Describe el entorno actual basándote en la ubicación GPS.")
                        )
                        val response = provider!!.complete(messages, emptyList())
                        val result = response.text ?: ""

                        if (result.isNotBlank()) {
                            conversation.addFrame(result)
                            voiceController.narrate(result)
                            updateNotification("🎧 ${currentGpsData.address.take(30)} · ${result.take(40)}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Pocket loop tick failed: ${e.message}")
                    }
                }
            }
        }
    }

    private fun stopPocketLoop() {
        loopJob?.cancel()
        loopJob = null
        gpsModule.stop()
        voiceController.stop()
    }

    // ═══ NOTIFICATIONS ═══

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "E.M.M.A. Pocket Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Modo bolsillo activo — narración por auriculares"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, PocketVisionService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val talkIntent = Intent(this, PocketVisionService::class.java).apply { action = ACTION_TALK }
        val talkPending = PendingIntent.getService(this, 1, talkIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val openPending = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("👖 E.M.M.A. Pocket")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(Notification.Action.Builder(null, "🎙️ Hablar", talkPending).build())
            .addAction(Notification.Action.Builder(null, "⏹ Parar", stopPending).build())
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    // ═══ WAKELOCK ═══

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "emma:pocket_vision"
        ).apply { acquire(4 * 60 * 60 * 1000L) } // 4 hours max
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
