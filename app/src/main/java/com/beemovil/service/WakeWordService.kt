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

    /** Called when "Hello Emma" is detected — launch app and restart listening */
    private fun onWakeDetected() {
        Log.i(TAG, "🎯 WAKE WORD DETECTED — launching conversation")
        updateNotification("¡Hello Emma detectado! Abriendo...")

        // Wake screen if locked/off
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = pm.newWakeLock(
                android.os.PowerManager.FULL_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "emma:wakeword"
            )
            wakeLock.acquire(5000) // 5 second wake
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }

        // Build launch intent
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = ACTION_WAKE_DETECTED
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("auto_start", true)
        }

        // Try direct launch first (works when app is in foreground)
        try {
            startActivity(launchIntent)
            Log.i(TAG, "Direct startActivity succeeded")
        } catch (e: Exception) {
            Log.w(TAG, "Direct startActivity failed: ${e.message}")
        }

        // Also fire a high-priority notification to bring app forward (Android 10+ background restriction workaround)
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
                .setFullScreenIntent(pendingIntent, true) // Brings app to front on lock screen
                .setTimeoutAfter(10000) // Auto-dismiss after 10s
                .build()

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(4243, alertNotification)
            Log.i(TAG, "Full-screen notification fired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fire alert notification: ${e.message}")
        }

        // Re-start listening after a delay (conversation will claim mic via arbiter)
        android.os.Handler(mainLooper).postDelayed({
            if (isRunning) {
                updateNotification("Escuchando \"Hello Emma\"...")
                wakeEngine?.start(
                    onWakeWordDetected = { onWakeDetected() },
                    onError = { err -> Log.w(TAG, "Wake re-start error: $err") }
                )
            }
        }, 20000) // 20s delay for greeting + conversation to start
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
