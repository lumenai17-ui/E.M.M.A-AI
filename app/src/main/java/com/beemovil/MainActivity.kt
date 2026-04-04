package com.beemovil

import android.content.Context
import android.os.Bundle
import android.util.Log
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

        // Init all native skills
        val skills = mutableMapOf<String, BeeSkill>()
        try { skills["device_info"] = DeviceSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "DeviceSkill: ${e.message}") }
        try { skills["clipboard"] = ClipboardSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "ClipboardSkill: ${e.message}") }
        try { skills["notify"] = NotifySkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "NotifySkill: ${e.message}") }
        try { skills["tts"] = TtsSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "TtsSkill: ${e.message}") }
        try { skills["browser"] = BrowserSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "BrowserSkill: ${e.message}") }
        try { skills["share"] = ShareSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "ShareSkill: ${e.message}") }
        try { skills["file"] = FileSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "FileSkill: ${e.message}") }

        // Load saved preferences
        val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val orKey = prefs.getString("openrouter_api_key", "") ?: ""
        val olKey = prefs.getString("ollama_api_key", "") ?: ""
        val savedProvider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
        val savedModel = prefs.getString("selected_model", "qwen/qwen3.6-plus:free") ?: "qwen/qwen3.6-plus:free"

        viewModel.initialize(skills, orKey, olKey)
        viewModel.currentProvider.value = savedProvider
        viewModel.currentModel.value = savedModel

        val hasKey = when (savedProvider) {
            "ollama" -> olKey.isNotBlank()
            else -> orKey.isNotBlank()
        }

        // Show crash log
        if (crashMsg != null) {
            val shortCrash = crashMsg.lines().take(5).joinToString("\n")
            viewModel.messages.add(0, ChatUiMessage(
                text = "⚠️ Crash detectado:\n```\n$shortCrash\n```",
                isUser = false, agentIcon = "💥", isError = true
            ))
        }

        setContent {
            BeeMovilTheme {
                var showSettings by remember { mutableStateOf(!hasKey) }

                Surface(modifier = Modifier.fillMaxSize(), color = BeeBlack) {
                    if (showSettings) {
                        SettingsScreen(
                            viewModel = viewModel,
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
