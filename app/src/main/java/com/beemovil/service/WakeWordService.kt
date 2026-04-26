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

    // M-04: track the last wake-up so the activity can release it on destroy
    // and so we de-bounce repeated "Hello Emma" detections within 3 seconds.
    private var lastWakeLock: android.os.PowerManager.WakeLock? = null
    private var lastWakeAtMs: Long = 0L
    private val WAKE_DEBOUNCE_MS = 3_000L

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

    /** Called when "Hello Emma" is detected — route to in-app or background conversation */
    private fun onWakeDetected() {
        // M-04: de-bounce
        val now = System.currentTimeMillis()
        if (now - lastWakeAtMs < WAKE_DEBOUNCE_MS) {
            Log.d(TAG, "Wake word debounced (within ${WAKE_DEBOUNCE_MS} ms)")
            return
        }
        lastWakeAtMs = now

        Log.i(TAG, "🎯 WAKE WORD DETECTED")

        // Stop wake word engine (conversation will claim mic)
        wakeEngine?.stop()

        // Detect if app is in foreground
        val isAppForeground = try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = am.getRunningTasks(1)
            if (tasks.isNotEmpty()) {
                tasks[0].topActivity?.packageName == packageName
            } else false
        } catch (_: Exception) { false }

        if (isAppForeground) {
            // ═══════════════════════════════════════════════════════
            //  PATH A: App is visible → use ConversationScreen
            //  (startActivity works from foreground)
            // ═══════════════════════════════════════════════════════
            Log.i(TAG, "App is in foreground → routing to ConversationScreen")
            updateNotification("¡Hello Emma detectado! Abriendo conversación...")

            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = ACTION_WAKE_DETECTED
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("auto_start", true)
            }
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.w(TAG, "startActivity failed even from foreground: ${e.message}")
            }

            // Re-start wake word listening after conversation is done
            android.os.Handler(mainLooper).postDelayed({
                if (isRunning && !BackgroundConversationService.isRunning) {
                    updateNotification("Escuchando \"Hello Emma\"...")
                    wakeEngine?.start(
                        onWakeWordDetected = { onWakeDetected() },
                        onError = { err -> Log.w(TAG, "Wake re-start error: $err") }
                    )
                }
            }, 20000)

        } else {
            // ═══════════════════════════════════════════════════════
            //  PATH B: App is in background/closed → use BCS
            //  (service-to-service always works on Android 10+)
            // ═══════════════════════════════════════════════════════
            Log.i(TAG, "App is in background → starting BackgroundConversationService")
            updateNotification("¡Hello Emma detectado! Iniciando voz...")

            try {
                val bcsIntent = Intent(applicationContext, BackgroundConversationService::class.java).apply {
                    action = BackgroundConversationService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(bcsIntent)
                } else {
                    startService(bcsIntent)
                }
                Log.i(TAG, "BCS started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BCS: ${e.message}")
            }

            // Also try wake lock + full-screen notification to optionally show UI
            try {
                lastWakeLock?.takeIf { it.isHeld }?.release()
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val wakeLock = pm.newWakeLock(
                    android.os.PowerManager.FULL_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
                    "emma:wakeword"
                )
                wakeLock.acquire(5000)
                lastWakeLock = wakeLock
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock failed: ${e.message}")
            }

            // Fire notification
            try {
                val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext, 42, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val prefs = applicationContext.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                val fullScreen = prefs.getBoolean("wake_word_full_screen_intent", true)

                val alertChannel = "emma_wake_alert"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        alertChannel, "Wake Word Alert",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alerta cuando Hello Emma es detectado"
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(channel)
                }

                val alertBuilder = NotificationCompat.Builder(this, alertChannel)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("E.M.M.A. Voice")
                    .setContentText("Conversación activa — toca para abrir")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setTimeoutAfter(15000)
                if (fullScreen) {
                    alertBuilder
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setFullScreenIntent(pendingIntent, true)
                }

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(4243, alertBuilder.build())
            } catch (e: Exception) {
                Log.w(TAG, "Alert notification failed: ${e.message}")
            }

            // NOTE: BCS will resume WakeWordService when conversation ends
        }
    }

    private fun stopWakeWordDetection() {
        Log.i(TAG, "Stopping wake word detection")
        wakeEngine?.destroy()
        wakeEngine = null
        isRunning = false
    }

    override fun onDestroy() {
        stopWakeWordDetection()
        // M-04: release wake lock if still held instead of relying on the timeout.
        try {
            lastWakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.w(TAG, "onDestroy wake lock release failed: ${e.message}")
        }
        lastWakeLock = null
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
