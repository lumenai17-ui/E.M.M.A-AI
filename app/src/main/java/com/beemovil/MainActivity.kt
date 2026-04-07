package com.beemovil

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import com.beemovil.memory.NotificationLogDB
import com.beemovil.memory.TaskDB
import com.beemovil.skills.*
import com.beemovil.ui.ChatUiMessage
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.screens.*
import com.beemovil.ui.components.PremiumBottomNav
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
        try { skills["task_manager"] = TaskSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "TaskSkill: ${e.message}") }
        try { skills["email"] = EmailSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "EmailSkill: ${e.message}") }
        try { skills["music_control"] = MusicControlSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "MusicControlSkill: ${e.message}") }
        try { skills["weather"] = WeatherSkill() } catch (e: Throwable) { Log.e("BeeMovil", "WeatherSkill: ${e.message}") }
        try { skills["web_search"] = WebSearchSkill() } catch (e: Throwable) { Log.e("BeeMovil", "WebSearchSkill: ${e.message}") }
        try { skills["brightness"] = BrightnessSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "BrightnessSkill: ${e.message}") }
        try { skills["battery_saver"] = BatterySaverSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "BatterySaverSkill: ${e.message}") }
        try { skills["qr_generator"] = QrGeneratorSkill() } catch (e: Throwable) { Log.e("BeeMovil", "QrGeneratorSkill: ${e.message}") }

        // Phase 9 skills (Productivity)
        try { skills["web_fetch"] = WebFetchSkill() } catch (e: Throwable) { Log.e("BeeMovil", "WebFetchSkill: ${e.message}") }
        try { skills["generate_pdf"] = PdfGeneratorSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "PdfGenSkill: ${e.message}") }
        try { skills["generate_html"] = HtmlGeneratorSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "HtmlGenSkill: ${e.message}") }
        try { skills["generate_spreadsheet"] = SpreadsheetSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "SpreadsheetSkill: ${e.message}") }
        try { skills["read_document"] = DocumentReaderSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "DocReaderSkill: ${e.message}") }
        try { skills["run_code"] = CodeRunnerSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "CodeRunnerSkill: ${e.message}") }
        try { skills["file_manager"] = FileManagerSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "FileManagerSkill: ${e.message}") }
        try { skills["git"] = GitSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "GitSkill: ${e.message}") }
        try { skills["browser_agent"] = BrowserSkill(this) } catch (e: Throwable) { Log.e("BeeMovil", "BrowserSkill: ${e.message}") }

        // Phase 18B-2: Multi-Agent Delegation
        try {
            skills["delegate_to_agent"] = DelegateSkill(
                agentResolver = { agentId -> viewModel.resolveAgent(agentId) },
                getAvailableAgents = { viewModel.getAvailableAgentConfigs() }
            )
        } catch (e: Throwable) { Log.e("BeeMovil", "DelegateSkill: ${e.message}") }

        // Phase 18B-4: A2A Remote Agent Communication
        try {
            skills["call_remote_agent"] = RemoteAgentSkill(
                getRegisteredAgents = { com.beemovil.a2a.RemoteAgentRegistry.getRegisteredAgents() }
            )
        } catch (e: Throwable) { Log.e("BeeMovil", "RemoteAgentSkill: ${e.message}") }

        // Phase 19D: Notification Intelligence
        val notifDB = NotificationLogDB(this)
        try { skills["notification_query"] = NotificationQuerySkill(notifDB) } catch (e: Throwable) { Log.e("BeeMovil", "NotifQuerySkill: ${e.message}") }

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

        // Init Deepgram voice manager (Phase 20)
        val deepgramVoiceMgr = com.beemovil.voice.DeepgramVoiceManager(this)
        deepgramVoiceMgr.initialize()
        viewModel.deepgramVoiceManager = deepgramVoiceMgr

        // Request permissions (mic, location, contacts, calendar, notifications)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val perms = mutableListOf<String>()
            if (!voiceManager.hasPermission) perms.add(android.Manifest.permission.RECORD_AUDIO)
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.READ_CONTACTS)
            if (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.READ_CALENDAR)
            if (checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                perms.add(android.Manifest.permission.WRITE_CALENDAR)
            // Android 13+ notification permission
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1001)
        }

        val hasKey = when (savedProvider) {
            "ollama" -> olKey.isNotBlank()
            "local" -> true  // Local models don't need API key
            else -> orKey.isNotBlank()
        }

        // Init chat history DB
        val chatHistoryDB = ChatHistoryDB(this)
        val taskDB = TaskDB(this)

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
                text = "Crash detectado:\n```\n$shortCrash\n```",
                isUser = false, agentIcon = "ERR", isError = true
            ))
        }

        // Restore theme preference
        val savedTheme = prefs.getString("app_theme", null)
        BeeThemeState.forceDark.value = when (savedTheme) {
            "dark" -> true
            "light" -> false
            else -> null
        }

        setContent {
            BeeMovilTheme {
                val isDark = BeeThemeState.forceDark.value ?: true
                Scaffold(
                    containerColor = if (isDark) BeeBlack else LightBackground,
                    bottomBar = {
                        val screen = viewModel.currentScreen.value
                        // Show bottom bar only on main screens
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
                        val screen = viewModel.currentScreen.value
                        val editingAgentId = remember { mutableStateOf<String?>(null) }
                        AnimatedContent(
                            targetState = screen,
                            label = "screen_transition",
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(250)) +
                                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)))
                                    .togetherWith(
                                        fadeOut(animationSpec = tween(200)) +
                                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200))
                                    )
                            }
                        ) { currentScreen ->
                        when (currentScreen) {
                            "dashboard" -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    chatHistoryDB = chatHistoryDB,
                                    memoryDB = memoryDB,
                                    taskDB = taskDB,
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
                            "tasks" -> {
                                TaskScreen(
                                    viewModel = viewModel,
                                    taskDB = taskDB
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
                                EmailInboxScreen(
                                    onEmailClick = { uid ->
                                        viewModel.selectedEmailUid.value = uid
                                        viewModel.currentScreen.value = "email_detail"
                                    },
                                    onCompose = { viewModel.currentScreen.value = "email_compose" }
                                )
                            }
                            "email_detail" -> {
                                EmailDetailScreen(
                                    uid = viewModel.selectedEmailUid.value,
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "email_inbox" },
                                    onReply = { to, subject ->
                                        viewModel.replyTo.value = to
                                        viewModel.replySubject.value = subject
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
                            "live_vision" -> {
                                LiveVisionScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "camera" }
                                )
                            }
                            "voice_chat" -> {
                                VoiceChatScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "dashboard" }
                                )
                            }
                            "browser" -> {
                                BrowserScreen(
                                    browserSkill = skills["browser_agent"] as? BrowserSkill,
                                    onBack = { viewModel.currentScreen.value = "dashboard" }
                                )
                            }
                            "file_explorer" -> {
                                FileExplorerScreen(
                                    onBack = { viewModel.currentScreen.value = "dashboard" },
                                    onOpenFile = { path ->
                                        if (path.endsWith(".html") || path.endsWith(".htm")) {
                                            viewModel.currentScreen.value = "browser"
                                        }
                                    }
                                )
                            }
                            "git_repos" -> {
                                GitScreen(
                                    onBack = { viewModel.currentScreen.value = "dashboard" }
                                )
                            }
                            "workflows" -> {
                                WorkflowScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.currentScreen.value = "dashboard" },
                                    onSendToChat = { agentId, content ->
                                        viewModel.prefillAgentChat(agentId, "Resultado del workflow:\n\n$content")
                                    }
                                )
                            }
                            "notification_dashboard" -> {
                                NotificationDashboardScreen(
                                    viewModel = viewModel,
                                    notifDB = notifDB,
                                    onBack = { viewModel.currentScreen.value = "dashboard" },
                                    onConfigClick = { viewModel.currentScreen.value = "notification_config" }
                                )
                            }
                            "notification_config" -> {
                                NotificationConfigScreen(
                                    notifDB = notifDB,
                                    onBack = { viewModel.currentScreen.value = "notification_dashboard" }
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
}
