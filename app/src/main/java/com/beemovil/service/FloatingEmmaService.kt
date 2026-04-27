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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

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

    private var windowManager: android.view.WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: FloatingLifecycleOwner? = null

    private fun startFloatingAssistant() {
        if (isRunning) return
        
        Log.i(TAG, "Iniciando Foreground Service para Asistente Flotante")
        val notification = buildNotification("Asistente Flotante Activo", "E.M.M.A. está lista para ayudarte.")
        startForeground(NOTIFICATION_ID, notification)
        
        isRunning = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Log.e(TAG, "No overlay permission. Cannot draw bubble.")
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                else 
                    android.view.WindowManager.LayoutParams.TYPE_PHONE,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            params.x = 0
            params.y = 200

            lifecycleOwner = FloatingLifecycleOwner()
            lifecycleOwner?.onCreate()

            composeView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                color = Color(0xFF5AC8FA),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "E",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }
                }
            }

            composeView!!.setViewTreeLifecycleOwner(lifecycleOwner)
            composeView!!.setViewTreeViewModelStoreOwner(lifecycleOwner)
            composeView!!.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            windowManager?.addView(composeView, params)
            lifecycleOwner?.onResume()
            Log.i(TAG, "Burbuja de Compose inyectada exitosamente en WindowManager.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error inyectando burbuja: ${e.message}")
        }
    }

    private fun stopFloatingAssistant() {
        Log.i(TAG, "Deteniendo Asistente Flotante")
        try {
            lifecycleOwner?.onPause()
            lifecycleOwner?.onDestroy()
            if (composeView != null && windowManager != null) {
                windowManager?.removeView(composeView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removiendo burbuja: ${e.message}")
        } finally {
            composeView = null
            windowManager = null
            lifecycleOwner = null
        }
        
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
