package com.beemovil

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.beemovil.skills.*
import com.beemovil.ui.ChatUiMessage
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.screens.ChatScreen
import com.beemovil.ui.screens.SettingsScreen
import com.beemovil.ui.theme.BeeBlack
import com.beemovil.ui.theme.BeeMovilTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check for previous crash
        val crashFile = File(filesDir, "crash.log")
        var crashMsg: String? = null
        if (crashFile.exists()) {
            crashMsg = crashFile.readText()
            Log.e("BeeMovil", "Previous crash:\n$crashMsg")
            crashFile.delete()
        }

        // Init skills safely
        val skills = mutableMapOf<String, BeeSkill>()
        try { skills["device_info"] = DeviceSkill(this) } catch (e: Throwable) {
            Log.e("BeeMovil", "DeviceSkill init failed: ${e.message}")
        }

        val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("openrouter_api_key", "") ?: ""
        viewModel.initialize(skills, savedKey)

        // Show previous crash to user
        if (crashMsg != null) {
            // Extract just the first few lines
            val shortCrash = crashMsg.lines().take(5).joinToString("\n")
            viewModel.messages.add(0, ChatUiMessage(
                text = "⚠️ Crash anterior detectado:\n```\n$shortCrash\n```",
                isUser = false, agentIcon = "💥", isError = true
            ))
        }

        setContent {
            BeeMovilTheme {
                var showSettings by remember { mutableStateOf(savedKey.isBlank()) }

                Surface(modifier = Modifier.fillMaxSize(), color = BeeBlack) {
                    if (showSettings) {
                        SettingsScreen(
                            onApiKeySaved = { newKey ->
                                viewModel.updateApiKey(newKey)
                                showSettings = false
                            },
                            onBack = { if (viewModel.hasApiKey()) showSettings = false }
                        )
                    } else {
                        ChatScreen(
                            viewModel = viewModel,
                            onSettingsClick = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}
