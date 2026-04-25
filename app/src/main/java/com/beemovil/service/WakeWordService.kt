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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.beemovil.MainActivity
import com.beemovil.R
import com.beemovil.voice.NativeWakeWordEngine
import com.beemovil.voice.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WakeWordService — Voice Intelligence Phase V4
 *
 * Foreground service that keeps the wake word engine running in background.
 * Detects "Hello Emma" and launches the app / starts conversation.
 *
 * Service lifecycle:
 * - Started via SettingsScreen toggle or app preference
 * - Shows persistent notification ("Listening for Hello Emma...")
 * - On wake word → sends Intent to MainActivity to open ConversationScreen
 * - Can be stopped via notification action or Settings toggle
 *
 * Battery considerations:
 * - NativeWakeWordEngine uses SpeechRecognizer cycles (moderate battery)
 * - Future: OpenWakeWord ONNX engine will be much lighter
 */
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "emma_wake_word"
        private const val NOTIFICATION_ID = 4242
        const val ACTION_START = "com.beemovil.action.WAKE_START"
        const val ACTION_STOP = "com.beemovil.action.WAKE_STOP"
        const val ACTION_WAKE_DETECTED = "com.beemovil.action.WAKE_DETECTED"

        /** Static flag to check service state from UI */
        @Volatile
        var isRunning = false
            private set
    }

    private var wakeEngine: WakeWordEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startWakeWordDetection()
            ACTION_STOP -> {
                stopWakeWordDetection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> startWakeWordDetection()
        }
        return START_STICKY // Restart if killed
    }

    private fun startWakeWordDetection() {
        Log.i(TAG, "Starting wake word detection service")

        // Show foreground notification
        val notification = buildNotification("Escuchando \"Hello Emma\"...")
        startForeground(NOTIFICATION_ID, notification)

        // Create and start engine
        wakeEngine = NativeWakeWordEngine(applicationContext).also { engine ->
            engine.start(
                onWakeWordDetected = { onWakeDetected() },
                onError = { error ->
                    Log.w(TAG, "Wake word error: $error")
                    updateNotification("⚠️ $error — Reintentando...")
                }
            )
        }

        isRunning = true
    }

    /** Called when "Hello Emma" is detected — greet, listen, respond (headless if screen off) */
    private fun onWakeDetected() {
        Log.i(TAG, "🎯 WAKE WORD DETECTED — launching conversation")
        updateNotification("¡Hello Emma detectado! Respondiendo...")

        // 1. Wake screen if locked/off
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = pm.newWakeLock(
                android.os.PowerManager.FULL_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "emma:wakeword"
            )
            wakeLock.acquire(15000) // 15 second wake — enough for greeting + listen + response
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }

        // 2. Dismiss keyguard (lock screen) so the Activity can show
        try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Request dismiss — won't work if device has secure lock (PIN/fingerprint)
                // but will work for swipe-to-unlock
                Log.d(TAG, "Requesting keyguard dismiss")
            }
        } catch (e: Exception) {
            Log.w(TAG, "KeyguardManager failed: ${e.message}")
        }

        // 3. Immediately greet the user via TTS (works even with screen off!)
        val greetings = listOf("Hola, te escucho", "Dime", "¿Sí? Te escucho", "Aquí estoy")
        val greeting = greetings.random()
        try {
            val tts = android.speech.tts.TextToSpeech(applicationContext) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    Log.i(TAG, "Headless greeting: '$greeting'")
                }
            }
            // Small delay to let TTS init, then speak
            android.os.Handler(mainLooper).postDelayed({
                tts.speak(greeting, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "wake_greeting")
                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        // 4. After greeting → start headless listening
                        startHeadlessListening(tts)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        startHeadlessListening(tts)
                    }
                })
            }, 600)
        } catch (e: Exception) {
            Log.e(TAG, "Headless TTS failed: ${e.message}")
        }

        // 5. Also try to launch Activity (will work if screen is on)
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_WAKE_DETECTED
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("auto_start", true)
        }

        try {
            startActivity(launchIntent)
            Log.i(TAG, "Direct startActivity succeeded")
        } catch (e: Exception) {
            Log.w(TAG, "Direct startActivity failed (screen likely off): ${e.message}")
        }

        // 6. Fire full-screen notification as backup
        try {
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 42, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alertChannel = "emma_wake_alert"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    alertChannel, "Wake Word Alert",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerta cuando Hello Emma es detectado"
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }

            val alertNotification = NotificationCompat.Builder(this, alertChannel)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("E.M.M.A. Voice")
                .setContentText("Hello Emma detectado — toca para abrir")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setTimeoutAfter(10000)
                .build()

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(4243, alertNotification)
            Log.i(TAG, "Full-screen notification fired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fire alert notification: ${e.message}")
        }
    }

    /**
     * Headless conversation: Listen via native STT → process with EmmaEngine → speak response.
     * Works entirely in the service — no Activity needed. Perfect for screen-off scenarios.
     */
    private fun startHeadlessListening(tts: android.speech.tts.TextToSpeech) {
        Log.i(TAG, "Starting headless listening (screen-off mode)")
        updateNotification("🎤 Escuchando tu pregunta...")

        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            Log.e(TAG, "SpeechRecognizer not available for headless mode")
            restartWakeWordAfterDelay(tts)
            return
        }

        android.os.Handler(mainLooper).post {
            val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(applicationContext)
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }

            recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    Log.d(TAG, "Headless: Ready for speech")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    updateNotification("🧠 Procesando...")
                }
                override fun onError(error: Int) {
                    Log.w(TAG, "Headless STT error: $error")
                    recognizer.destroy()
                    restartWakeWordAfterDelay(tts)
                }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    val userText = matches?.firstOrNull() ?: ""
                    recognizer.destroy()

                    if (userText.isBlank()) {
                        Log.w(TAG, "Headless: No speech detected")
                        restartWakeWordAfterDelay(tts)
                        return
                    }

                    Log.i(TAG, "Headless user said: '$userText'")
                    updateNotification("🧠 Pensando: \"${userText.take(30)}...\"")

                    // Process with EmmaEngine on background thread
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val app = applicationContext as? com.beemovil.BeeMovilApp
                            val engine = app?.emmaEngine
                            val response = if (engine != null) {
                                engine.processUserMessage(
                                    userText,
                                    threadId = "conversation",
                                    senderId = "conversation"
                                )
                            } else {
                                "Lo siento, no pude procesar tu mensaje."
                            }

                            Log.i(TAG, "Headless response: '${response.take(80)}...'")

                            // Speak response via TTS
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                updateNotification("🔊 Respondiendo...")
                                val cleanResponse = response
                                    .replace(Regex("[\\p{So}\\p{Cn}]"), "")
                                    .replace(Regex("[*_#`]"), "")
                                    .take(500)
                                tts.speak(cleanResponse, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "wake_response")
                                tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                    override fun onStart(utteranceId: String?) {}
                                    override fun onDone(utteranceId: String?) {
                                        restartWakeWordAfterDelay(tts)
                                    }
                                    @Deprecated("Deprecated in Java")
                                    override fun onError(utteranceId: String?) {
                                        restartWakeWordAfterDelay(tts)
                                    }
                                })
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Headless processing failed: ${e.message}")
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                restartWakeWordAfterDelay(tts)
                            }
                        }
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            recognizer.startListening(intent)
        }
    }

    /** Restart wake word detection after headless conversation completes */
    private fun restartWakeWordAfterDelay(tts: android.speech.tts.TextToSpeech?) {
        try { tts?.shutdown() } catch (_: Exception) {}
        android.os.Handler(mainLooper).postDelayed({
            if (isRunning) {
                updateNotification("Escuchando \"Hello Emma\"...")
                wakeEngine?.start(
                    onWakeWordDetected = { onWakeDetected() },
                    onError = { err -> Log.w(TAG, "Wake re-start error: $err") }
                )
            }
        }, 3000) // 3s cooldown before re-listening for wake word
    }

    private fun stopWakeWordDetection() {
        Log.i(TAG, "Stopping wake word detection")
        wakeEngine?.destroy()
        wakeEngine = null
        isRunning = false
    }

    override fun onDestroy() {
        stopWakeWordDetection()
        super.onDestroy()
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Detección de palabra clave para activar E.M.M.A."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingLaunch = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("E.M.M.A. Voice")
            .setContentText(text)
            .setContentIntent(pendingLaunch)
            .addAction(
                R.mipmap.ic_launcher,
                "Detener",
                pendingStop
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
