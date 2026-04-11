package com.beemovil

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

        setContent {
            BeeMovilTheme {
                val isDark = BeeThemeState.forceDark.value ?: true
                Scaffold(
                    containerColor = if (isDark) BeeBlack else LightBackground,
                    bottomBar = {
                        val screen = viewModel.currentScreen.value
                        if (screen in listOf("dashboard", "conversations", "settings")) {
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
                                    onAgentClick = { viewModel.currentScreen.value = "chat" },
                                    onSettingsClick = { viewModel.currentScreen.value = "settings" }
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
                                "notifications" -> NotificationDashboardScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "dashboard" }
                                )
                                else -> DashboardScreen(
                                    viewModel = viewModel,
                                    onAgentClick = { viewModel.currentScreen.value = "chat" },
                                    onSettingsClick = { viewModel.currentScreen.value = "settings" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
