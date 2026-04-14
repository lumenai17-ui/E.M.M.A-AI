package com.beemovil

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.screens.*
import com.beemovil.ui.components.PremiumBottomNav
import com.beemovil.ui.theme.*

import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Aquí podríamos iterar y notificar al usuario si faltó alguno crítico, 
        // pero por ahora solo forzamos la petición inicial.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())

        // FILE-09: Manejar incoming Share Intent desde otras apps
        handleIncomingShareIntent(intent)

        // S-14: Restaurar tema visual guardado en prefs
        val themePrefs = getSharedPreferences("beemovil", MODE_PRIVATE)
        val savedTheme = themePrefs.getString("app_theme", null)
        BeeThemeState.forceDark.value = when(savedTheme) {
            "dark" -> true
            "light" -> false
            else -> null  // seguir sistema
        }

        setContent {
            BeeMovilTheme {
                val isDark = BeeThemeState.forceDark.value ?: true
                Scaffold(
                    containerColor = if (isDark) BeeBlack else LightBackground,
                    bottomBar = {
                        val screen = viewModel.currentScreen.value
                        if (screen in listOf("dashboard", "conversations", "tasks", "email_inbox", "settings")) {
                            PremiumBottomNav(
                                currentScreen = screen,
                                onNavigate = { viewModel.currentScreen.value = it }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        color = BeeBlack
                    ) {
                        val currentScreen = viewModel.currentScreen.value
                        AnimatedContent(
                            targetState = currentScreen,
                            label = "screen_transition",
                            transitionSpec = {
                                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                            }
                        ) { screen ->
                            when (screen) {
                                "chat" -> ChatScreen(
                                    viewModel = viewModel,
                                    agentId = "main",
                                    onSettingsClick = { viewModel.currentScreen.value = "settings" },
                                    onBackClick = { viewModel.currentScreen.value = "dashboard" }
                                )
                                "conversations" -> com.beemovil.ui.screens.ConversationsScreen(
                                    viewModel = viewModel
                                )
                                "dashboard" -> DashboardScreen(
                                    viewModel = viewModel,
                                    onAgentClick = { threadId ->
                                        viewModel.navigateToThread(threadId)
                                    },
                                    onSettingsClick = { viewModel.currentScreen.value = "settings" }
                                )
                                "tasks" -> com.beemovil.ui.screens.TasksScreen(
                                    viewModel = viewModel
                                )
                                "email_inbox" -> com.beemovil.ui.screens.EmailInboxScreen(
                                    viewModel = viewModel
                                )
                                "settings" -> SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "dashboard" }
                                )
                                "live_vision" -> LiveVisionScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "dashboard" }
                                )
                                "voice" -> VoiceChatScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "dashboard" }
                                )
                                // UI-02: Eliminada ruta "notifications" huérfana (nadie navegaba a ella)
                                else -> DashboardScreen(
                                    viewModel = viewModel,
                                    onAgentClick = { threadId ->
                                        viewModel.navigateToThread(threadId)
                                    },
                                    onSettingsClick = { viewModel.currentScreen.value = "settings" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // FILE-09: Procesar archivos/texto compartidos desde otras apps
    private fun handleIncomingShareIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action != Intent.ACTION_SEND) return
        
        val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
        val sharedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        when {
            sharedUri != null -> {
                // Archivo compartido → abrir chat con adjunto pre-cargado
                viewModel.currentScreen.value = "chat"
                viewModel.sendMessage(sharedText ?: "Archivo compartido desde otra app", sharedUri.toString())
            }
            !sharedText.isNullOrBlank() -> {
                // Texto/Link compartido → abrir chat con texto pre-cargado
                viewModel.currentScreen.value = "chat"
                viewModel.sendMessage(sharedText)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }
}
