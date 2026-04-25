package com.beemovil.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.database.ChatHistoryDB
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import com.beemovil.voice.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

/**
 * ConversationScreen — Voice Intelligence Phase V3
 *
 * Full-screen immersive conversation interface replacing the old DeepVoice placeholder.
 * Features:
 * - Animated orb showing conversation state (listening/processing/speaking)
 * - Live transcription display
 * - Conversation history scroll
 * - Backend selector dropdown
 * - Mute, Stop, Auto-listen controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()
    val textPrimary = if (isDark) BeeWhite else TextDark
    val textSecondary = if (isDark) BeeGray else TextGrayDark
    val accent = if (isDark) BeeYellow else BrandBlue
    val bg = if (isDark) BeeBlack else LightBackground
    val cardBg = if (isDark) Color(0xFF161622) else LightSurface

    // V5: Haptic & visual feedback manager
    val feedbackManager = remember { VoiceFeedbackManager(context) }

    // Conversation Engine
    val engine = remember {
        ConversationEngine(
            context = context,
            voiceManager = viewModel.voiceManager,
            engine = viewModel.engine,
            chatHistoryDB = com.beemovil.database.ChatHistoryDB.getDatabase(context)
        )
    }

    // Persistent preferences
    val prefs = remember { context.getSharedPreferences("beemovil", android.content.Context.MODE_PRIVATE) }

    // Fixed thread for all voice conversations — unifies with Chats tab
    val conversationThreadId = "conversation"

    // State
    var conversationState by remember { mutableStateOf(ConversationState.IDLE) }
    var previousState by remember { mutableStateOf(ConversationState.IDLE) }
    var partialTranscript by remember { mutableStateOf("") }
    // Fix 1: Persist backend selection — remembers Gemini across sessions
    var selectedBackendId by remember {
        val saved = prefs.getString("conversation_backend", null)
        val backendId = if (saved != null && engine.backends.any { it.id == saved }) {
            saved
        } else {
            engine.getDefaultBackend().id
        }
        mutableStateOf(backendId)
    }
    var autoListen by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<ConversationTurn>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var audioLevel by remember { mutableFloatStateOf(0f) } // V5: Volume meter 0..1

    // Fix 2: Load conversation history from Room DB on entry
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.beemovil.database.ChatHistoryDB.getDatabase(context)
                val messages = db.chatHistoryDao().getHistory(conversationThreadId)
                // Pair user/assistant messages into ConversationTurns
                val turns = mutableListOf<ConversationTurn>()
                var i = 0
                while (i < messages.size) {
                    val msg = messages[i]
                    if (msg.role == "user") {
                        // Look for next assistant message
                        val nextAssistant = if (i + 1 < messages.size && messages[i + 1].role == "assistant") {
                            messages[i + 1].content
                        } else {
                            "..."
                        }
                        turns.add(ConversationTurn(msg.content, nextAssistant))
                        i += 2 // Skip both user + assistant
                    } else {
                        i++ // Skip orphan assistant messages
                    }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    history.addAll(turns.takeLast(50)) // Load last 50 turns
                }
            } catch (e: Exception) {
                android.util.Log.w("ConversationScreen", "Failed to load history: ${e.message}")
            }
        }
    }

    // V5: Fire haptic feedback on state transitions
    LaunchedEffect(conversationState) {
        if (conversationState != previousState) {
            when (conversationState) {
                ConversationState.LISTENING -> feedbackManager.onListeningStarted()
                ConversationState.PROCESSING -> feedbackManager.onProcessingStarted()
                ConversationState.SPEAKING -> feedbackManager.onSpeakingStarted()
                ConversationState.ERROR -> feedbackManager.onError()
                ConversationState.IDLE -> if (previousState != ConversationState.IDLE) {
                    feedbackManager.onConversationStopped()
                }
            }
            previousState = conversationState
        }
    }

    // V5: Simulate audio level during LISTENING (based on partial transcript activity)
    LaunchedEffect(conversationState, partialTranscript) {
        if (conversationState == ConversationState.LISTENING) {
            audioLevel = if (partialTranscript.isNotBlank()) 0.7f + (Math.random() * 0.3).toFloat() else 0.2f + (Math.random() * 0.3).toFloat()
        } else if (conversationState == ConversationState.SPEAKING) {
            audioLevel = 0.5f + (Math.random() * 0.5).toFloat()
        } else {
            audioLevel = 0f
        }
    }

    // Wire callbacks
    LaunchedEffect(Unit) {
        engine.onStateChange = { state ->
            conversationState = state
            if (state == ConversationState.LISTENING) {
                partialTranscript = ""
            }
        }
        engine.onPartialTranscript = { partial ->
            partialTranscript = partial
        }
        engine.onFinalTranscript = { transcript ->
            partialTranscript = transcript
        }
        engine.onTurnComplete = { userText, emmaText ->
            history.add(ConversationTurn(userText, emmaText))
            partialTranscript = ""
            scope.launch {
                if (history.isNotEmpty()) {
                    listState.animateScrollToItem(history.size - 1)
                }
            }
        }
        engine.onError = { error ->
            errorMessage = error
        }
    }

    // V4 Fix: Auto-start when triggered by wake word ("Hello Emma")
    LaunchedEffect(Unit) {
        if (viewModel.autoStartConversation.value) {
            viewModel.autoStartConversation.value = false // Consume flag
            delay(600)
            val backend = engine.backends.find { it.id == selectedBackendId }
                ?: engine.getDefaultBackend()
            android.util.Log.i("ConversationScreen", "Auto-start with backend: ${backend.displayName} (id=${backend.id})")

            // Greet the user first — natural voice cue that Emma is ready
            val greeting = listOf(
                "Hola, te escucho.",
                "Hola, ¿en qué te ayudo?",
                "Aquí estoy, dime.",
                "Hola, ¿qué necesitas?"
            ).random()

            history.add(ConversationTurn("Hello Emma", greeting))
            feedbackManager.onSpeakingStarted()

            // Speak greeting, then start listening
            viewModel.voiceManager.speak(
                text = greeting,
                language = java.util.Locale.getDefault().language,
                onDone = {
                    // After greeting, start the conversation engine
                    val config = ConversationConfig(
                        autoListenAfterTTS = autoListen,
                        language = java.util.Locale.getDefault().toLanguageTag(),
                        speakResponses = !isMuted,
                        llmProvider = viewModel.currentProvider.value,
                        llmModel = viewModel.currentModel.value,
                        threadId = conversationThreadId,
                        agentId = "conversation"
                    )
                    engine.start(backend, config)
                },
                onError = { _ ->
                    // Even if TTS fails, start listening
                    val config = ConversationConfig(
                        autoListenAfterTTS = autoListen,
                        language = java.util.Locale.getDefault().toLanguageTag(),
                        speakResponses = !isMuted,
                        llmProvider = viewModel.currentProvider.value,
                        llmModel = viewModel.currentModel.value,
                        threadId = conversationThreadId,
                        agentId = "conversation"
                    )
                    engine.start(backend, config)
                }
            )
        }
    }

    // Fix: Pause WakeWordService when conversation is open (avoids SpeechRecognizer conflict)
    DisposableEffect(Unit) {
        // Stop wake word to free the SpeechRecognizer for conversation
        val wasWakeWordRunning = com.beemovil.service.WakeWordService.isRunning
        if (wasWakeWordRunning) {
            try {
                val stopIntent = android.content.Intent(context, com.beemovil.service.WakeWordService::class.java).apply {
                    action = com.beemovil.service.WakeWordService.ACTION_STOP
                }
                context.startService(stopIntent)
                android.util.Log.i("ConversationScreen", "Paused WakeWordService to free SpeechRecognizer")
            } catch (e: Exception) {
                android.util.Log.w("ConversationScreen", "Failed to stop wake word: ${e.message}")
            }
        }

        onDispose {
            // Stop conversation safely
            try {
                engine.onStateChange = null
                engine.onPartialTranscript = null
                engine.onFinalTranscript = null
                engine.onResponse = null
                engine.onError = null
                engine.onTurnComplete = null
                engine.stop()
                engine.destroy()
            } catch (e: Exception) {
                android.util.Log.w("ConversationScreen", "Error cleaning up engine: ${e.message}")
            }

            // Resume WakeWordService if it was running before
            if (wasWakeWordRunning) {
                try {
                    val prefs = context.getSharedPreferences("beemovil", android.content.Context.MODE_PRIVATE)
                    if (prefs.getBoolean("wake_word_enabled", false)) {
                        val startIntent = android.content.Intent(context, com.beemovil.service.WakeWordService::class.java).apply {
                            action = com.beemovil.service.WakeWordService.ACTION_START
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(startIntent)
                        } else {
                            context.startService(startIntent)
                        }
                        android.util.Log.i("ConversationScreen", "Resumed WakeWordService")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ConversationScreen", "Failed to resume wake word: ${e.message}")
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) Brush.verticalGradient(
                    listOf(Color(0xFF0A0A1A), Color(0xFF0D1025), Color(0xFF0A0A1A))
                ) else Brush.verticalGradient(
                    listOf(LightBackground, LightCard, LightBackground)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopAppBar(
                title = { Text("Conversación", fontWeight = FontWeight.Bold, color = textPrimary) },
                navigationIcon = {
                    IconButton(onClick = {
                        engine.stop()
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = accent)
                    }
                },
                actions = {
                    // Backend indicator (auto-select: Pipeline if available, else Offline)
                    val activeBackend = engine.backends.find { it.id == selectedBackendId }
                        ?: engine.getDefaultBackend()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (activeBackend.isAvailable()) Color(0xFF4CAF50) else Color(0xFFF44336),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (activeBackend.requiresInternet) "Online" else "Offline",
                            color = textSecondary,
                            fontSize = 10.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            // Main content
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // === ANIMATED ORB (V5 Enhanced) ===
                ConversationOrb(
                    state = conversationState,
                    isDark = isDark,
                    accent = accent,
                    audioLevel = audioLevel
                )

                // V5: Audio level bar
                if (conversationState == ConversationState.LISTENING || conversationState == ConversationState.SPEAKING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AudioLevelBar(
                        level = audioLevel,
                        color = when (conversationState) {
                            ConversationState.LISTENING -> Color(0xFF4CAF50)
                            ConversationState.SPEAKING -> Color(0xFFFF9800)
                            else -> textSecondary
                        },
                        isDark = isDark
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // State label
                Text(
                    text = when (conversationState) {
                        ConversationState.IDLE -> "Toca para comenzar"
                        ConversationState.LISTENING -> "Escuchando..."
                        ConversationState.PROCESSING -> "Procesando..."
                        ConversationState.SPEAKING -> "Hablando..."
                        ConversationState.ERROR -> "Error"
                    },
                    color = when (conversationState) {
                        ConversationState.LISTENING -> Color(0xFF4CAF50)
                        ConversationState.PROCESSING -> Color(0xFF2196F3)
                        ConversationState.SPEAKING -> Color(0xFFFF9800)
                        ConversationState.ERROR -> Color(0xFFF44336)
                        else -> textSecondary
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                // Live transcription
                if (partialTranscript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = if (isDark) Color(0xFF1A1A2E) else Color(0xFFF0F4FF),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = partialTranscript,
                            color = textPrimary.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Error message
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = Color(0xFFF44336),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    LaunchedEffect(error) {
                        kotlinx.coroutines.delay(3000)
                        errorMessage = null
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === CONVERSATION HISTORY ===
                if (history.isNotEmpty()) {
                    Text(
                        "Conversación",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(history) { turn ->
                        ConversationTurnCard(turn, isDark, textPrimary, textSecondary, cardBg, accent, context)
                    }
                }

                // === CONTROLS ===
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute button
                    FilledIconToggleButton(
                        checked = isMuted,
                        onCheckedChange = {
                            isMuted = it
                            feedbackManager.setMuted(it)
                        },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = cardBg,
                            checkedContainerColor = Color(0xFFF44336).copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            "Mute",
                            tint = if (isMuted) Color(0xFFF44336) else textSecondary
                        )
                    }

                    // Main action button (Start/Stop)
                    val isActive = conversationState != ConversationState.IDLE
                    FloatingActionButton(
                        onClick = {
                            if (isActive) {
                                engine.stop()
                            } else {
                                errorMessage = null
                                val backend = engine.backends.find { it.id == selectedBackendId }
                                    ?: engine.getDefaultBackend()
                                android.util.Log.i("ConversationScreen", "Manual start with backend: ${backend.displayName} (id=${backend.id})")
                                val config = ConversationConfig(
                                    autoListenAfterTTS = autoListen,
                                    language = java.util.Locale.getDefault().toLanguageTag(),
                                    speakResponses = !isMuted,
                                    llmProvider = viewModel.currentProvider.value,
                                    llmModel = viewModel.currentModel.value,
                                    threadId = conversationThreadId,
                                    agentId = "conversation"
                                )
                                engine.start(backend, config)
                            }
                        },
                        containerColor = if (isActive) Color(0xFFF44336) else accent,
                        contentColor = if (isDark) BeeBlack else Color.White,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            if (isActive) Icons.Filled.Stop else Icons.Filled.Mic,
                            if (isActive) "Stop" else "Start",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Auto-listen toggle
                    FilledIconToggleButton(
                        checked = autoListen,
                        onCheckedChange = { autoListen = it },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = cardBg,
                            checkedContainerColor = accent.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.Loop,
                            "Auto-listen",
                            tint = if (autoListen) accent else textSecondary
                        )
                    }
                }
            }
        }
    }
}

// === Data ===

data class ConversationTurn(
    val userText: String,
    val emmaText: String
)

// === Animated Orb (V5 Enhanced) ===

@Composable
fun ConversationOrb(
    state: ConversationState,
    isDark: Boolean,
    accent: Color,
    audioLevel: Float = 0f
) {
    val orbColor = when (state) {
        ConversationState.LISTENING -> Color(0xFF4CAF50)
        ConversationState.PROCESSING -> Color(0xFF2196F3)
        ConversationState.SPEAKING -> Color(0xFFFF9800)
        ConversationState.ERROR -> Color(0xFFF44336)
        else -> if (isDark) Color(0xFF444466) else Color(0xFFBBBBCC)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    // Pulse for LISTENING — volume-reactive
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == ConversationState.LISTENING) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    // V5: Volume makes the orb grow extra
    val volumeBoost = if (state == ConversationState.LISTENING) audioLevel * 0.15f else 0f
    val pulse = basePulse + volumeBoost

    // Glow for SPEAKING
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (state == ConversationState.SPEAKING) 0.8f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // V5: Outer ripple wave (expands outward)
    val ripple by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state != ConversationState.IDLE) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    // V5: Ripple opacity (fades as it expands)
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = if (state != ConversationState.IDLE) 0.4f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.dp)
    ) {
        // V5: Outer ripple wave
        if (state != ConversationState.IDLE) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(ripple)
                    .border(
                        width = 2.dp,
                        color = orbColor.copy(alpha = rippleAlpha),
                        shape = CircleShape
                    )
            )
        }

        // Outer glow ring
        if (state != ConversationState.IDLE) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulse)
                    .background(
                        Brush.radialGradient(
                            listOf(orbColor.copy(alpha = glow * 0.4f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )
        }

        // Processing ring
        if (state == ConversationState.PROCESSING) {
            CircularProgressIndicator(
                modifier = Modifier.size(120.dp),
                color = orbColor.copy(alpha = 0.6f),
                strokeWidth = 3.dp
            )
        }

        // V5: Inner volume ring (volume-reactive border)
        if (state == ConversationState.LISTENING || state == ConversationState.SPEAKING) {
            val ringWidth = (1.dp + (audioLevel * 4).dp)
            Box(
                modifier = Modifier
                    .size(106.dp)
                    .border(
                        width = ringWidth,
                        color = orbColor.copy(alpha = 0.6f + audioLevel * 0.4f),
                        shape = CircleShape
                    )
            )
        }

        // Main orb
        Surface(
            color = orbColor.copy(alpha = if (state == ConversationState.IDLE) 0.3f else 0.8f),
            shape = CircleShape,
            modifier = Modifier
                .size(100.dp)
                .scale(if (state == ConversationState.LISTENING) pulse else 1f),
            shadowElevation = if (state != ConversationState.IDLE) 12.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = when (state) {
                        ConversationState.LISTENING -> "🎤"
                        ConversationState.PROCESSING -> "💭"
                        ConversationState.SPEAKING -> "🗣️"
                        ConversationState.ERROR -> "⚠️"
                        else -> "🎙️"
                    },
                    fontSize = 36.sp
                )
            }
        }
    }
}

// === V5: Audio Level Bar ===

@Composable
fun AudioLevelBar(
    level: Float,
    color: Color,
    isDark: Boolean
) {
    val barCount = 7
    val animatedLevel by animateFloatAsState(
        targetValue = level,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "level"
    )

    Row(
        modifier = Modifier.width(120.dp).height(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val barHeight = when {
                // Center bars are tallest (audio visualizer shape)
                i == barCount / 2 -> animatedLevel
                i == barCount / 2 - 1 || i == barCount / 2 + 1 -> animatedLevel * 0.8f
                i == 0 || i == barCount - 1 -> animatedLevel * 0.4f
                else -> animatedLevel * 0.6f
            }.coerceIn(0.15f, 1f)

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight(barHeight)
                    .background(
                        color.copy(alpha = 0.5f + barHeight * 0.5f),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

// === Turn Card (V8 Enhanced: Copy, Share, Timestamps) ===

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationTurnCard(
    turn: ConversationTurn,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    accent: Color,
    context: android.content.Context
) {
    val clipboard = remember { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // User bubble
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = accent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setPrimaryClip(ClipData.newPlainText("user", turn.userText))
                            android.widget.Toast.makeText(context, "Copiado", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = turn.userText,
                        color = textPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Emma bubble with actions
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = cardBg,
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setPrimaryClip(ClipData.newPlainText("emma", turn.emmaText))
                            android.widget.Toast.makeText(context, "Respuesta copiada", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (turn.emmaText.startsWith("TOOL_CALL::file_generated::")) {
                        val path = turn.emmaText.removePrefix("TOOL_CALL::file_generated::")
                        val ext = path.substringAfterLast('.', "").lowercase()
                        val isImage = ext in listOf("png", "jpg", "jpeg", "webp", "gif")
                        
                        if (isImage) {
                            Surface(
                                color = Color.Transparent,
                                modifier = Modifier.clickable {
                                    try {
                                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", java.io.File(path)
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(fileUri, "image/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Error al abrir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Column {
                                    coil.compose.AsyncImage(
                                        model = java.io.File(path),
                                        contentDescription = "Imagen generada",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "🖼️ Toca para ver completo",
                                        color = textPrimary.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = accent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                                context, "${context.packageName}.fileprovider", java.io.File(path)
                                            )
                                            val mimeType = when(ext) {
                                                "pdf" -> "application/pdf"
                                                "html" -> "text/html"
                                                "csv" -> "text/csv"
                                                "txt" -> "text/plain"
                                                else -> "*/*"
                                            }
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(fileUri, mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Error al abrir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                    Icon(
                                        imageVector = when(ext) {
                                            "pdf" -> Icons.Filled.PictureAsPdf
                                            "html" -> Icons.Filled.Language
                                            "csv" -> Icons.Filled.TableChart
                                            else -> Icons.Filled.InsertDriveFile
                                        },
                                        contentDescription = "Archivo",
                                        tint = accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Abrir ${java.io.File(path).name}",
                                        color = accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else if (turn.emmaText.startsWith("TOOL_CALL::open_browser::")) {
                        val url = turn.emmaText.removePrefix("TOOL_CALL::open_browser::")
                        Text(
                            text = "🌐 Navegando a: $url",
                            color = textPrimary.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = turn.emmaText,
                            color = textPrimary.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Action buttons row
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Copy button
                IconButton(
                    onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("emma", turn.emmaText))
                        android.widget.Toast.makeText(context, "📋 Copiado", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        "Copiar",
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Share button
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, turn.emmaText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir respuesta"))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Share,
                        "Compartir",
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
