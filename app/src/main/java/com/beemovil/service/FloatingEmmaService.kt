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

class FloatingEmmaService : Service() {

    companion object {
        private const val TAG = "FloatingEmmaService"
        private const val CHANNEL_ID = "emma_floating_assistant"
        private const val NOTIFICATION_ID = 5001
        const val ACTION_START = "com.beemovil.action.FLOAT_START"
        const val ACTION_STOP = "com.beemovil.action.FLOAT_STOP"

        @Volatile
        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Inicializando Servicio Flotante Esqueleto (Sub-Fase 3.1)")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFloatingAssistant()
            return START_NOT_STICKY
        }

        startFloatingAssistant()
        return START_STICKY
    }

    private fun startFloatingAssistant() {
        if (isRunning) return
        
        Log.i(TAG, "Iniciando Foreground Service para Asistente Flotante")
        val notification = buildNotification("Asistente Flotante Activo", "E.M.M.A. está lista para ayudarte.")
        startForeground(NOTIFICATION_ID, notification)
        
        isRunning = true
        // TODO: Sub-Fase 3.2 - Aquí inyectaremos el WindowManager y Jetpack Compose View
    }

    private fun stopFloatingAssistant() {
        Log.i(TAG, "Deteniendo Asistente Flotante")
        // TODO: Sub-Fase 3.2 - Remover vista del WindowManager aquí
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFloatingAssistant()
    }

    // --- Notification (Requisito legal de Android para Foreground Services) ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asistente Flotante",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la burbuja de E.M.M.A. viva sobre otras apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingLaunch = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingEmmaService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingLaunch)
            .addAction(
                R.mipmap.ic_launcher,
                "Ocultar",
                pendingStop
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
