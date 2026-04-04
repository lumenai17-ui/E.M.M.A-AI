package com.beemovil.ui.screens

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import java.util.Locale

/**
 * VoiceChatScreen — Continuous voice conversation mode.
 * Flow: Listen → Transcribe → Send to agent → Agent responds → TTS speaks → Listen again
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Voice state
    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    var transcribedText by remember { mutableStateOf("") }
    var partialText by remember { mutableStateOf("") }
    var agentResponse by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var conversationLog by remember { mutableStateOf(listOf<VoiceTurn>()) }
    var autoListen by remember { mutableStateOf(true) }  // Auto-listen after TTS finishes

    // TTS engine
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale("es", "MX")
                engine?.setSpeechRate(1.0f)
            }
        }
        engine
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
            viewModel.voiceManager?.stopListening()
        }
    }

    // Animation for listening pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Start listening function
    fun startListening() {
        val vm = viewModel.voiceManager ?: run {
            errorText = "Voice input not available"
            return
        }
        if (!vm.hasPermission) {
            errorText = "Permiso de micrófono necesario"
            return
        }
        voiceState = VoiceState.LISTENING
        partialText = ""
        transcribedText = ""
        errorText = ""

        vm.startListening(
            language = "es-MX",
            onPartialResult = { partial ->
                partialText = partial
            },
            onListeningState = { listening ->
                if (!listening && voiceState == VoiceState.LISTENING) {
                    // Will transition when result comes
                }
            },
            onErrorCallback = { error ->
                if (error.contains("No se entendió") || error.contains("No se detectó")) {
                    // Silence — auto-restart if in continuous mode
                    if (autoListen && voiceState == VoiceState.LISTENING) {
                        startListening()
                    } else {
                        voiceState = VoiceState.IDLE
                    }
                } else {
                    errorText = error
                    voiceState = VoiceState.IDLE
                }
            },
            onFinalResult = { text ->
                transcribedText = text
                voiceState = VoiceState.THINKING
                partialText = ""

                // Add to log
                conversationLog = conversationLog + VoiceTurn("user", text)

                // Send to agent
                Thread {
                    try {
                        val response = viewModel.sendMessageSync(text)
                        agentResponse = response
                        conversationLog = conversationLog + VoiceTurn("agent", response)
                        voiceState = VoiceState.SPEAKING

                        // Speak response
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                voiceState = if (autoListen) VoiceState.LISTENING else VoiceState.IDLE
                                // Auto-restart listening
                                if (autoListen) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        startListening()
                                    }
                                }
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                voiceState = VoiceState.IDLE
                            }
                        })

                        // Clean response for TTS (remove emojis, markdown)
                        val cleanText = response
                            .replace(Regex("[\\p{So}\\p{Cn}]"), "") // Remove emojis
                            .replace(Regex("[*_#`]"), "")           // Remove markdown
                            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "") // Remove links
                            .take(500) // Limit TTS length

                        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "bee_voice_${System.currentTimeMillis()}")
                    } catch (e: Exception) {
                        errorText = "Error: ${e.message}"
                        voiceState = VoiceState.IDLE
                    }
                }.start()
            }
        )
    }

    // Stop everything
    fun stopAll() {
        viewModel.voiceManager?.stopListening()
        tts?.stop()
        voiceState = VoiceState.IDLE
        autoListen = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A1A), Color(0xFF1A1A2E), Color(0xFF0A0A1A))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            TopAppBar(
                title = {
                    Column {
                        Text("Modo Voz", fontWeight = FontWeight.Bold)
                        Text(
                            when (voiceState) {
                                VoiceState.IDLE -> "Toca para hablar"
                                VoiceState.LISTENING -> "Escuchando..."
                                VoiceState.THINKING -> "Procesando..."
                                VoiceState.SPEAKING -> "Hablando..."
                            },
                            fontSize = 11.sp, color = when (voiceState) {
                                VoiceState.LISTENING -> Color(0xFF4CAF50)
                                VoiceState.THINKING -> BeeYellow
                                VoiceState.SPEAKING -> Color(0xFF2196F3)
                                else -> BeeGray
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { stopAll(); onBack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow)
                    }
                },
                actions = {
                    // Auto-listen toggle
                    IconButton(onClick = { autoListen = !autoListen }) {
                        Icon(
                            if (autoListen) Icons.Filled.Loop else Icons.Filled.MicOff,
                            "Auto",
                            tint = if (autoListen) BeeYellow else BeeGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = BeeWhite
                )
            )

            // Conversation log (scrollable, takes remaining space above controls)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                if (conversationLog.isEmpty()) {
                    // Empty state
                    Spacer(modifier = Modifier.height(40.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎤", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Conversación por voz", fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, color = BeeWhite)
                        Text("Habla naturalmente, el agente responde por voz",
                            fontSize = 14.sp, color = BeeGray, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = BeeYellow.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                if (autoListen) "🔄 Modo continuo activado" else "🎤 Toca el botón para hablar",
                                fontSize = 12.sp, color = BeeYellow,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Conversation bubbles
                conversationLog.forEach { turn ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val isUser = turn.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        if (!isUser) {
                            Text("🐝", fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) BeeYellow.copy(alpha = 0.15f) else Color(0xFF1A1A2E)
                            ),
                            shape = RoundedCornerShape(
                                topStart = 14.dp, topEnd = 14.dp,
                                bottomStart = if (isUser) 14.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 14.dp
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                turn.text,
                                fontSize = 14.sp,
                                color = if (isUser) BeeYellow else Color(0xFFE0E0E0),
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        if (isUser) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🎤", fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                // Show current state
                if (partialText.isNotBlank() && voiceState == VoiceState.LISTENING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BeeYellow.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text("$partialText...", fontSize = 14.sp, color = BeeGray,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.padding(12.dp))
                        }
                    }
                }

                // Error
                if (errorText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("⚠️ $errorText", fontSize = 12.sp, color = Color(0xFFF44336))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF0A0A1A))
                        )
                    )
                    .padding(bottom = 32.dp, top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status text
                Text(
                    when (voiceState) {
                        VoiceState.IDLE -> if (autoListen) "Toca para iniciar conversación" else "Toca para hablar"
                        VoiceState.LISTENING -> "🎙️ Escuchando..."
                        VoiceState.THINKING -> "🧠 Procesando respuesta..."
                        VoiceState.SPEAKING -> "🔊 Hablando..."
                    },
                    fontSize = 13.sp,
                    color = when (voiceState) {
                        VoiceState.LISTENING -> Color(0xFF4CAF50)
                        VoiceState.THINKING -> BeeYellow
                        VoiceState.SPEAKING -> Color(0xFF2196F3)
                        else -> BeeGray
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Main button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clear history
                    IconButton(
                        onClick = { conversationLog = emptyList() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.DeleteSweep, "Clear", tint = BeeGray)
                    }

                    // Main mic button
                    Button(
                        onClick = {
                            when (voiceState) {
                                VoiceState.IDLE -> startListening()
                                VoiceState.LISTENING -> {
                                    viewModel.voiceManager?.stopListening()
                                }
                                VoiceState.SPEAKING -> {
                                    tts?.stop()
                                    voiceState = VoiceState.IDLE
                                }
                                VoiceState.THINKING -> { /* Can't interrupt */ }
                            }
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .then(
                                if (voiceState == VoiceState.LISTENING) Modifier.scale(pulseScale) else Modifier
                            ),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (voiceState) {
                                VoiceState.LISTENING -> Color(0xFF4CAF50)
                                VoiceState.THINKING -> BeeYellow
                                VoiceState.SPEAKING -> Color(0xFF2196F3)
                                else -> BeeYellow
                            }
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            when (voiceState) {
                                VoiceState.LISTENING -> Icons.Filled.Mic
                                VoiceState.THINKING -> Icons.Filled.HourglassTop
                                VoiceState.SPEAKING -> Icons.Filled.VolumeUp
                                else -> Icons.Filled.Mic
                            },
                            contentDescription = "Voice",
                            modifier = Modifier.size(36.dp),
                            tint = BeeBlack
                        )
                    }

                    // Toggle continuous mode
                    IconButton(
                        onClick = { autoListen = !autoListen },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Loop,
                                "Auto-listen",
                                tint = if (autoListen) BeeYellow else BeeGray,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                if (autoListen) "ON" else "OFF",
                                fontSize = 9.sp,
                                color = if (autoListen) BeeYellow else BeeGray
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class VoiceState {
    IDLE, LISTENING, THINKING, SPEAKING
}

private data class VoiceTurn(
    val role: String,
    val text: String
)
