package com.beemovil

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.agent.CustomAgentDB
import com.beemovil.memory.ChatHistoryDB
import com.beemovil.skills.*
import com.beemovil.ui.ChatUiMessage
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.screens.*
import com.beemovil.ui.theme.*
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

        // Phase 6 skills (Production Ready — high value)
        try { skills["calendar"] = CalendarSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "CalendarSkill: ${e.message}") }
        try { skills["email"] = EmailSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "EmailSkill: ${e.message}") }
        try { skills["music_control"] = MusicControlSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "MusicControlSkill: ${e.message}") }
        try { skills["weather"] = WeatherSkill() } catch (e: Throwable) { Log.e("BeeMovil", "WeatherSkill: ${e.message}") }
        try { skills["web_search"] = WebSearchSkill() } catch (e: Throwable) { Log.e("BeeMovil", "WebSearchSkill: ${e.message}") }
        try { skills["brightness"] = BrightnessSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "BrightnessSkill: ${e.message}") }
        try { skills["battery_saver"] = BatterySaverSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "BatterySaverSkill: ${e.message}") }
        try { skills["qr_generator"] = QrGeneratorSkill() } catch (e: Throwable) { Log.e("BeeMovil", "QrGeneratorSkill: ${e.message}") }

        // Load saved preferences
        val prefs = getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val orKey = prefs.getString("openrouter_api_key", "") ?: ""
        val olKey = prefs.getString("ollama_api_key", "") ?: ""
        val savedProvider = prefs.getString("selected_provider", "openrouter") ?: "openrouter"
        val savedModel = prefs.getString("selected_model", "qwen/qwen3.6-plus:free") ?: "qwen/qwen3.6-plus:free"


        // Init voice input
        val voiceManager = com.beemovil.skills.VoiceInputManager(this)
        voiceManager.initialize()
        viewModel.voiceManager = voiceManager

        // Request permissions (mic, location, contacts, calendar)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val perms = mutableListOf<String>()
            if (!voiceManager.hasPermission) perms.add(android.Manifest.permission.RECORD_AUDIO)
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.READ_CONTACTS)
            if (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.READ_CALENDAR)
            if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1001)
        }

        val hasKey = when (savedProvider) {
            "ollama" -> olKey.isNotBlank()
            else -> orKey.isNotBlank()
        }

        // Init chat history DB
        val chatHistoryDB = ChatHistoryDB(this)

        viewModel.initialize(skills, orKey, olKey, memoryDB, chatHistoryDB)
        viewModel.currentProvider.value = savedProvider
        viewModel.currentModel.value = savedModel

        // Init custom agents DB
        val customAgentDB = CustomAgentDB(this)
        viewModel.reloadCustomAgents(customAgentDB)

        // If no API key, go straight to settings
        if (!hasKey) viewModel.currentScreen.value = "settings"
        else viewModel.currentScreen.value = "dashboard"

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
                Scaffold(
                    containerColor = BeeBlack,
                    bottomBar = {
                        val screen = viewModel.currentScreen.value
                        // Show bottom bar only on main screens
                        if (screen in listOf("dashboard", "conversations", "email_inbox", "settings")) {
                            NavigationBar(
                                containerColor = BeeBlackLight,
                                contentColor = BeeYellow,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = screen == "dashboard",
                                    onClick = { viewModel.currentScreen.value = "dashboard" },
                                    icon = { Icon(Icons.Filled.Home, "Home") },
                                    label = { Text("Home", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BeeYellow,
                                        selectedTextColor = BeeYellow,
                                        unselectedIconColor = BeeGray,
                                        unselectedTextColor = BeeGray,
                                        indicatorColor = BeeYellow.copy(alpha = 0.12f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = screen == "conversations",
                                    onClick = { viewModel.currentScreen.value = "conversations" },
                                    icon = { Icon(Icons.Filled.Forum, "Agentes") },
                                    label = { Text("Agentes", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BeeYellow,
                                        selectedTextColor = BeeYellow,
                                        unselectedIconColor = BeeGray,
                                        unselectedTextColor = BeeGray,
                                        indicatorColor = BeeYellow.copy(alpha = 0.12f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = screen == "email_inbox",
                                    onClick = { viewModel.currentScreen.value = "email_inbox" },
                                    icon = { Icon(Icons.Filled.Email, "Email") },
                                    label = { Text("Correo", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BeeYellow,
                                        selectedTextColor = BeeYellow,
                                        unselectedIconColor = BeeGray,
                                        unselectedTextColor = BeeGray,
                                        indicatorColor = BeeYellow.copy(alpha = 0.12f)
                                    )
                                )
                                NavigationBarItem(
                                    selected = screen == "settings",
                                    onClick = { viewModel.currentScreen.value = "settings" },
                                    icon = { Icon(Icons.Filled.Settings, "Settings") },
                                    label = { Text("Config", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = BeeYellow,
                                        selectedTextColor = BeeYellow,
                                        unselectedIconColor = BeeGray,
                                        unselectedTextColor = BeeGray,
                                        indicatorColor = BeeYellow.copy(alpha = 0.12f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        color = BeeBlack
                    ) {
                        val screen = viewModel.currentScreen.value
                        val editingAgentId = remember { mutableStateOf<String?>(null) }
                        when (screen) {
                            "dashboard" -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    chatHistoryDB = chatHistoryDB,
                                    memoryDB = memoryDB,
                                    onAgentClick = { agentId -> viewModel.openAgentChat(agentId) },
                                    onSettingsClick = { viewModel.currentScreen.value = "settings" },
                                    skillCount = skills.size
                                )
                            }
                            "conversations" -> {
                                ConversationsScreen(
                                    viewModel = viewModel,
                                    chatHistoryDB = chatHistoryDB,
                                    onAgentClick = { agentId -> viewModel.openAgentChat(agentId) },
                                    onCreateAgent = {
                                        editingAgentId.value = null
                                        viewModel.currentScreen.value = "create_agent"
                                    },
                                    onEditAgent = { agentId ->
                                        editingAgentId.value = agentId
                                        viewModel.currentScreen.value = "create_agent"
                                    },
                                    skillCount = skills.size
                                )
                            }
                            "chat" -> {
                                ChatScreen(
                                    viewModel = viewModel,
                                    onSettingsClick = { viewModel.currentScreen.value = "settings" },
                                    onBackClick = { viewModel.currentScreen.value = "conversations" }
                                )
                            }
                            "create_agent" -> {
                                AgentCreatorScreen(
                                    viewModel = viewModel,
                                    customAgentDB = customAgentDB,
                                    editAgentId = editingAgentId.value,
                                    onSaved = { viewModel.currentScreen.value = "conversations" },
                                    onBack = { viewModel.currentScreen.value = "conversations" }
                                )
                            }
                            "email_inbox" -> {
                                val selectedEmailUid = remember { mutableStateOf(0L) }
                                val replyTo = remember { mutableStateOf<String?>(null) }
                                val replySubject = remember { mutableStateOf<String?>(null) }
                                EmailInboxScreen(
                                    onEmailClick = { uid ->
                                        selectedEmailUid.value = uid
                                        viewModel.currentScreen.value = "email_detail"
                                    },
                                    onCompose = { viewModel.currentScreen.value = "email_compose" }
                                )
                            }
                            "email_detail" -> {
                                EmailDetailScreen(
                                    uid = 0L, // will be passed via state
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "email_inbox" },
                                    onReply = { to, subject ->
                                        viewModel.currentScreen.value = "email_compose"
                                    }
                                )
                            }
                            "email_compose" -> {
                                EmailComposeScreen(
                                    onBack = { viewModel.currentScreen.value = "email_inbox" }
                                )
                            }
                            "camera" -> {
                                CameraScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "dashboard" }
                                )
                            }
                            "settings" -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        if (viewModel.hasApiKey()) {
                                            viewModel.currentScreen.value = "dashboard"
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
