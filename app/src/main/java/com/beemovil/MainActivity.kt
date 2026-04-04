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
        // Phase 2 skills (Core)
        try { skills["device_info"] = DeviceSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "DeviceSkill: ${e.message}") }
        try { skills["clipboard"] = ClipboardSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "ClipboardSkill: ${e.message}") }
        try { skills["notify"] = NotifySkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "NotifySkill: ${e.message}") }
        try { skills["tts"] = TtsSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "TtsSkill: ${e.message}") }
        try { skills["browser"] = BrowserSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "BrowserSkill: ${e.message}") }
        try { skills["share"] = ShareSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "ShareSkill: ${e.message}") }
        try { skills["file"] = FileSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "FileSkill: ${e.message}") }

        // Phase 4 skill (Memory)
        val memoryDB = com.beemovil.memory.BeeMemoryDB(this)
        try { skills["memory"] = MemorySkill(memoryDB) } catch (e: Throwable) { Log.e("BeeMovil", "MemorySkill: ${e.message}") }

        // Phase 5 skills (Multimedia + Utility)
        try { skills["camera"] = CameraSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "CameraSkill: ${e.message}") }
        try { skills["image_gen"] = ImageGenSkill(
            getApiKey = { viewModel.getApiKey(viewModel.currentProvider.value) },
            getProvider = { viewModel.currentProvider.value }
        ) } catch (e: Throwable) { Log.e("BeeMovil", "ImageGenSkill: ${e.message}") }
        try { skills["volume"] = VolumeSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "VolumeSkill: ${e.message}") }
        try { skills["alarm"] = AlarmSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "AlarmSkill: ${e.message}") }
        try { skills["connectivity"] = ConnectivitySkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "ConnectivitySkill: ${e.message}") }
        try { skills["app_launcher"] = AppLauncherSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "AppLauncherSkill: ${e.message}") }
        try { skills["contacts"] = ContactsSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "ContactsSkill: ${e.message}") }
        try { skills["flashlight"] = FlashlightSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "FlashlightSkill: ${e.message}") }
        try { skills["datetime"] = DateTimeSkill() } catch (e: Throwable) { Log.e("BeeMovil", "DateTimeSkill: ${e.message}") }
        try { skills["calculator"] = CalculatorSkill() } catch (e: Throwable) { Log.e("BeeMovil", "CalculatorSkill: ${e.message}") }


        // Load saved preferences
        val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val orKey = prefs.getString("openrouter_api_key", "") ?: ""
        val olKey = prefs.getString("ollama_api_key", "") ?: ""
        val savedProvider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
        val savedModel = prefs.getString("selected_model", "qwen/qwen3.6-plus:free") ?: "qwen/qwen3.6-plus:free"

        viewModel.initialize(skills, orKey, olKey, memoryDB)
        viewModel.currentProvider.value = savedProvider
        viewModel.currentModel.value = savedModel

        // Init voice input
        val voiceManager = com.beemovil.skills.VoiceInputManager(this)
        voiceManager.initialize()
        viewModel.voiceManager = voiceManager

        // Request permissions (mic, location, contacts)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val perms = mutableListOf<String>()
            if (!voiceManager.hasPermission) perms.add(android.Manifest.permission.RECORD_AUDIO)
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.READ_CONTACTS)
            if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1001)
        }

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
