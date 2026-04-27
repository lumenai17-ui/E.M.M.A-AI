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
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

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

            // Ghost Mode Auto-Destruct
            lifecycleOwner?.lifecycleScope?.launch {
                BackgroundConversationService.stateFlow.collect { state ->
                    if (state == BackgroundConversationService.BCSState.IDLE) {
                        // When conversation stops, wait 2 seconds and vanish (Ghost mode)
                        kotlinx.coroutines.delay(2000)
                        if (BackgroundConversationService.stateFlow.value == BackgroundConversationService.BCSState.IDLE) {
                            Log.i(TAG, "Ghost Mode: Conversation IDLE. Auto-destroying bubble.")
                            stopFloatingAssistant()
                        }
                    }
                }
            }



            composeView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val scope = rememberCoroutineScope()
                    val screenWidth = resources.displayMetrics.widthPixels
                    val bubbleSizePx = 60 * resources.displayMetrics.density

                    val bcsState by BackgroundConversationService.stateFlow.collectAsState()

                    // Expand bubble when active
                    val size by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (bcsState != BackgroundConversationService.BCSState.IDLE) 80.dp else 60.dp
                    )
                    
                    val color by androidx.compose.animation.animateColorAsState(
                        targetValue = when (bcsState) {
                            BackgroundConversationService.BCSState.LISTENING -> Color(0xFFFF3B30) // Red
                            BackgroundConversationService.BCSState.PROCESSING -> Color(0xFFFF9500) // Orange
                            BackgroundConversationService.BCSState.SPEAKING -> Color(0xFF34C759) // Green
                            else -> Color(0xFF5AC8FA) // Blue (Idle)
                        }
                    )

                    Box(
                        modifier = Modifier
                            .size(size)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = {
                                        // Snap-to-edge logic
                                        val currentX = params.x
                                        val targetX = if (currentX < screenWidth / 2) 0 else (screenWidth - bubbleSizePx).toInt()
                                        
                                        scope.launch {
                                            val animatable = Animatable(currentX.toFloat())
                                            animatable.animateTo(targetX.toFloat()) {
                                                params.x = value.toInt()
                                                try {
                                                    windowManager?.updateViewLayout(composeView, params)
                                                } catch (e: Exception) { }
                                            }
                                        }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    params.x += dragAmount.x.toInt()
                                    params.y += dragAmount.y.toInt()
                                    try {
                                        windowManager?.updateViewLayout(composeView, params)
                                    } catch (e: Exception) { }
                                }
                            }
                            .background(
                                color = color,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (bcsState) {
                            BackgroundConversationService.BCSState.LISTENING -> {
                                Icon(Icons.Filled.Mic, contentDescription = "Listening", tint = Color.White)
                            }
                            BackgroundConversationService.BCSState.PROCESSING -> {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            }
                            BackgroundConversationService.BCSState.SPEAKING -> {
                                Icon(Icons.Filled.GraphicEq, contentDescription = "Speaking", tint = Color.White)
                            }
                            else -> {
                                Text(
                                    text = "E",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                            }
                        }
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
