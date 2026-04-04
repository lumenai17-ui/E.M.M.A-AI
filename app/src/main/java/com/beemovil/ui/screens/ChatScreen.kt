package com.beemovil.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.R
import com.beemovil.ui.ChatUiMessage
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onSettingsClick: () -> Unit = {}, onBackClick: () -> Unit = {}) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showAgentPicker by remember { mutableStateOf(false) }

    // Auto-scroll on new messages
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.Transparent,
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(BeeBlack, BeeBlack.copy(alpha = 0.95f))
                    )
                )
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            // Agent avatar — bee logo
                            Surface(
                                onClick = { showAgentPicker = true },
                                color = BeeYellow.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.bee_logo),
                                    contentDescription = "Bee Logo",
                                    modifier = Modifier.size(32.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    viewModel.currentAgentConfig.value.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = BeeWhite
                                )
                                Text(
                                    "${viewModel.currentProvider.value} · ${viewModel.currentModel.value.substringAfterLast("/")}",
                                    fontSize = 11.sp,
                                    color = BeeGrayLight
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = BeeWhite
                    ),
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, "Settings", tint = BeeYellow)
                        }
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(Icons.Filled.Delete, "Clear", tint = BeeGrayLight)
                        }
                    }
                )
            }
        },
        bottomBar = {
            Surface(
                color = BeeBlackLight,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Mic button
                    if (viewModel.voiceManager != null) {
                        val isRecording = viewModel.isRecording.value
                        IconButton(
                            onClick = { viewModel.toggleVoiceInput { inputText = it } },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = "Voice",
                                tint = if (isRecording) Color(0xFFFF4444) else BeeYellow,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                if (viewModel.isRecording.value) "🎙️ Escuchando..." else "Escribe un mensaje...",
                                color = if (viewModel.isRecording.value) Color(0xFFFF6666) else BeeGrayLight
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = BeeGray,
                            unfocusedContainerColor = BeeGray,
                            focusedTextColor = BeeWhite,
                            unfocusedTextColor = BeeWhite,
                            cursorColor = BeeYellow,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = false,
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            val msg = inputText.trim()
                            if (msg.isNotEmpty()) {
                                inputText = ""
                                viewModel.sendMessage(msg)
                            }
                        },
                        containerColor = if (viewModel.isLoading.value) BeeGray else BeeYellow,
                        contentColor = BeeBlack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (viewModel.isLoading.value) {
                            TypingDots()
                        } else {
                            Icon(Icons.Filled.Send, "Send", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Honeycomb background
            Image(
                painter = painterResource(id = R.drawable.chat_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            items(
                items = viewModel.messages.toList()
            ) { message ->
                MessageBubble(message)
            }

            // Typing indicator
            if (viewModel.isLoading.value) {
                item {
                    TypingIndicator(viewModel.currentAgentConfig.value.icon)
                }
            }
        }
        }
    }

    // Agent Picker Dialog
    if (showAgentPicker) {
        AgentPickerDialog(
            agents = viewModel.availableAgents,
            currentId = viewModel.currentAgentConfig.value.id,
            onSelect = { config ->
                viewModel.switchAgent(config)
                showAgentPicker = false
            },
            onDismiss = { showAgentPicker = false }
        )
    }
}

@Composable
fun MessageBubble(message: ChatUiMessage) {
    val isUser = message.isUser

    val bubbleGradient = when {
        message.isError -> Brush.linearGradient(listOf(Color(0xFF4A1A1A), Color(0xFF3A1515)))
        isUser -> Brush.linearGradient(listOf(Color(0xFF2D3250), Color(0xFF424669)))
        else -> Brush.linearGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Agent icon for non-user messages
        if (!isUser) {
            Text(
                message.agentIcon,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        // Bubble
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(bubbleGradient)
                    .padding(12.dp)
            ) {
                Column {
                    // Tool badges
                    if (message.toolsUsed.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            message.toolsUsed.forEach { tool ->
                                Surface(
                                    color = BeeYellow.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "🔧 $tool",
                                        fontSize = 10.sp,
                                        color = BeeYellow,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Message content with basic markdown
                    RenderMarkdown(message.text, isUser)
                }
            }
        }
    }
}

/**
 * Simple markdown renderer: bold, code, lists, headers
 */
@Composable
fun RenderMarkdown(text: String, isUser: Boolean) {
    val textColor = if (isUser) Color(0xFFE0E0E0) else Color.White

    val annotated = buildAnnotatedString {
        var i = 0
        val src = text

        while (i < src.length) {
            when {
                // **bold**
                i + 1 < src.length && src[i] == '*' && src[i + 1] == '*' -> {
                    val end = src.indexOf("**", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = BeeYellow)) {
                            append(src.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(src[i])
                        i++
                    }
                }
                // `code`
                src[i] == '`' && !(i + 2 < src.length && src[i+1] == '`' && src[i+2] == '`') -> {
                    val end = src.indexOf('`', i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7DFFB3),
                            background = Color(0xFF1A2A1A)
                        )) {
                            append(src.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(src[i])
                        i++
                    }
                }
                // _italic_
                src[i] == '_' && (i == 0 || src[i-1] == ' ' || src[i-1] == '\n') -> {
                    val end = src.indexOf('_', i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(color = BeeGrayLight)) {
                            append(src.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(src[i])
                        i++
                    }
                }
                // Bullet points
                (src[i] == '-' || src[i] == '•') && (i == 0 || src[i-1] == '\n') && i + 1 < src.length && src[i+1] == ' ' -> {
                    append("  • ")
                    i += 2
                }
                else -> {
                    append(src[i])
                    i++
                }
            }
        }
    }

    Text(
        text = annotated,
        color = textColor,
        fontSize = 15.sp,
        lineHeight = 21.sp
    )
}

/**
 * Animated typing dots
 */
@Composable
fun TypingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600), repeatMode = RepeatMode.Reverse
        ), label = "d1"
    )
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200), repeatMode = RepeatMode.Reverse
        ), label = "d2"
    )
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400), repeatMode = RepeatMode.Reverse
        ), label = "d3"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(BeeBlack.copy(alpha = dotAlpha1)))
        Box(Modifier.size(6.dp).clip(CircleShape).background(BeeBlack.copy(alpha = dotAlpha2)))
        Box(Modifier.size(6.dp).clip(CircleShape).background(BeeBlack.copy(alpha = dotAlpha3)))
    }
}

/**
 * Typing indicator bubble
 */
@Composable
fun TypingIndicator(agentIcon: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800), repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(agentIcon, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Surface(
            color = Color(0xFF1A1A2E).copy(alpha = alpha),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { i ->
                    val dotAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, delayMillis = i * 150),
                            repeatMode = RepeatMode.Reverse
                        ), label = "dot$i"
                    )
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(BeeYellow.copy(alpha = dotAlpha))
                    )
                }
            }
        }
    }
}

/**
 * Agent picker dialog
 */
@Composable
fun AgentPickerDialog(
    agents: List<com.beemovil.agent.AgentConfig>,
    currentId: String,
    onSelect: (com.beemovil.agent.AgentConfig) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BeeBlackLight,
        title = {
            Text("Cambiar Agente", color = BeeYellow, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                agents.forEach { agent ->
                    val isSelected = agent.id == currentId
                    Surface(
                        onClick = { onSelect(agent) },
                        color = if (isSelected) BeeYellow.copy(alpha = 0.15f) else BeeGray.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(agent.icon, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(agent.name, color = BeeWhite, fontWeight = FontWeight.Bold)
                                Text(agent.description, color = BeeGrayLight, fontSize = 12.sp)
                            }
                            if (isSelected) {
                                Text("✓", color = BeeYellow, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = BeeYellow)
            }
        }
    )
}
