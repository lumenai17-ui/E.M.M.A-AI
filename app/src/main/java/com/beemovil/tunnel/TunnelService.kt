package com.beemovil.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.beemovil.R

class TunnelService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1991, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "START"
        if (action == "STOP") {
            com.beemovil.tunnel.HermesTunnelManager.stopTunnel(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Recuperar configuraciones asegurando que pueda sobrevivir
        val config = com.beemovil.tunnel.HermesTunnelManager.getConfig(this)
        if (config != null) {
            com.beemovil.tunnel.HermesTunnelManager.startTunnel(this, config.first, config.second)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "HERMES_TUNNEL_CHANNEL",
                "Conexión A2A Hermes",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "HERMES_TUNNEL_CHANNEL")
            .setContentTitle("E.M.M.A. Hermes Tunnel")
            .setContentText("Conexión persistente establecida con la matriz externa.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }
}
