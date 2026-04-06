package com.beemovil.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.beemovil.R
import com.beemovil.ui.ChatUiMessage
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onSettingsClick: () -> Unit = {}, onBackClick: () -> Unit = {}) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showAgentPicker by remember { mutableStateOf(false) }
    var showAttachOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Pick up prefilled prompts from Dashboard tools
    val pending = viewModel.pendingPrompt.value
    LaunchedEffect(pending) {
        if (pending.isNotBlank()) {
            inputText = pending
            viewModel.pendingPrompt.value = ""
        }
    }

    // File picker — handles PDF, DOCX, XLSX, TXT, CSV, etc.
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                val displayName = cursor?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    c.moveToFirst()
                    if (idx >= 0) c.getString(idx) else "archivo"
                } ?: "archivo"

                val ext = displayName.substringAfterLast('.').lowercase()
                val mimeType = context.contentResolver.getType(it) ?: ""

                when {
                    // Images → Vision model
                    mimeType.startsWith("image/") -> {
                        val tempFile = File(context.cacheDir, "attached_${System.currentTimeMillis()}.$ext")
                        context.contentResolver.openInputStream(it)?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        viewModel.analyzeImageInChat(context, tempFile.absolutePath)
                    }

                    // Documents (PDF, DOC, XLSX) → Copy to cache, use read_document skill
                    ext in listOf("pdf", "docx", "doc", "xlsx", "xls") -> {
                        val tempFile = File(context.cacheDir, "doc_${System.currentTimeMillis()}_$displayName")
                        context.contentResolver.openInputStream(it)?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        viewModel.sendMessage("Lee y analiza el archivo ${tempFile.absolutePath}")
                    }

                    // Text files → Read inline
                    mimeType.startsWith("text/") || ext in listOf("csv", "json", "xml", "md", "txt", "log") -> {
                        val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } ?: ""
                        val truncated = if (text.length > 5000) text.take(5000) + "\n... (truncado)" else text
                        viewModel.sendMessage("[Archivo: $displayName]\n```\n$truncated\n```\nAnaliza este archivo")
                    }

                    // Fallback
                    else -> {
                        viewModel.sendMessage("Formato .$ext no soportado aún. Formatos soportados: PDF, DOCX, XLSX, TXT, CSV, JSON, imágenes.")
                    }
                }
            } catch (e: Exception) {
                viewModel.sendMessage("[Error cargando archivo: ${e.message}]")
            }
        }
    }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Copy to temp file
                val inputStream = context.contentResolver.openInputStream(it)
                val tempFile = File(context.cacheDir, "attached_image_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }

                // Route to vision model (not text model)
                viewModel.analyzeImageInChat(context, tempFile.absolutePath)
            } catch (e: Exception) {
                viewModel.sendMessage("[Error cargando imagen: ${e.message}]")
            }
        }
    }

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
                            // Agent avatar — premium bee
                            Surface(
                                onClick = { showAgentPicker = true },
                                color = Color.Transparent,
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.bee_agent_avatar),
                                    contentDescription = "Agent",
                                    modifier = Modifier.size(38.dp).clip(CircleShape),
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
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Attach file button with dropdown
                    Box {
                        IconButton(
                            onClick = { showAttachOptions = !showAttachOptions },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Outlined.AttachFile, "Attach",
                                tint = if (showAttachOptions) BeeYellow else BeeGrayLight,
                                modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(
                            expanded = showAttachOptions,
                            onDismissRequest = { showAttachOptions = false },
                            modifier = Modifier.background(BeeBlackLight)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Imagen", color = BeeWhite) },
                                leadingIcon = { Icon(Icons.Outlined.Image, "Imagen", tint = BeeYellow) },
                                onClick = {
                                    showAttachOptions = false
                                    imagePickerLauncher.launch("image/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Archivo", color = BeeWhite) },
                                leadingIcon = { Icon(Icons.Outlined.InsertDriveFile, "Archivo", tint = BeeYellow) },
                                onClick = {
                                    showAttachOptions = false
                                    filePickerLauncher.launch("*/*")
                                }
                            )
                        }
                    }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (viewModel.isRecording.value) {
                                    Icon(Icons.Filled.Mic, "Mic", tint = Color(0xFFFF6666), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    if (viewModel.isRecording.value) "Escuchando..." else "Escribe un mensaje...",
                                    color = if (viewModel.isRecording.value) Color(0xFFFF6666) else BeeGrayLight
                                )
                            }
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

            // ── Suggestion Chips (visible when few messages) ──
            if (viewModel.messages.size <= 1) {
                val suggestions = listOf(
                    "Que puedes hacer?",
                    "Genera un PDF",
                    "Busca en la web",
                    "Crea una landing page",
                    "Analiza una imagen",
                    "Clima actual",
                    "Programa una alarma",
                    "Lee mi correo"
                )
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions.size) { i ->
                        SuggestionChip(
                            onClick = {
                                inputText = suggestions[i]
                                viewModel.sendMessage(suggestions[i])
                                inputText = ""
                            },
                            label = {
                                Text(
                                    suggestions[i],
                                    fontSize = 13.sp,
                                    color = BeeWhite,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = BeeGray.copy(alpha = 0.7f)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                borderColor = BeeYellow.copy(alpha = 0.3f),
                                enabled = true
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
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
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }

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
        // Agent icon for non-user messages (Material Icon instead of emoji)
        if (!isUser) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = "AI",
                tint = HoneyGold.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp).padding(start = 4.dp, bottom = 2.dp)
            )
        }

        // Bubble with gestures
        Box {
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // Double-tap to copy
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text))
                                Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                            },
                            onLongPress = {
                                showContextMenu = true
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .background(bubbleGradient)
                        .padding(12.dp)
                ) {
                    Column {
                        // Tool badges + delegation cards
                        if (message.toolsUsed.isNotEmpty()) {
                            val hasDelegation = message.toolsUsed.contains("delegate_to_agent")
                            val regularTools = message.toolsUsed.filter { it != "delegate_to_agent" }

                            // Delegation card
                            if (hasDelegation) {
                                Surface(
                                    color = Color(0xFF1A1A3E),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, BeeYellow.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.SwapHoriz, "Delegate", tint = BeeYellow, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                "Tarea delegada a agente especializado",
                                                fontSize = 10.sp, color = BeeYellow,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (regularTools.isNotEmpty()) {
                                                Text(
                                                    "Tools: ${regularTools.joinToString(", ")}",
                                                    fontSize = 9.sp, color = BeeGray
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Regular tool badges
                            if (regularTools.isNotEmpty() && !hasDelegation) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    regularTools.forEach { tool ->
                                        Surface(
                                            color = BeeYellow.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Build, tool, tint = BeeYellow, modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(tool, fontSize = 10.sp, color = BeeYellow)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Message content — selectable
                        SelectionContainer {
                            RenderMarkdown(message.text, isUser)
                        }

                        // File attachments
                        if (message.filePaths.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            message.filePaths.forEach { path ->
                                FileAttachmentCard(path)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }

            // Context menu popup
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = DpOffset(0.dp, 0.dp),
                modifier = Modifier.background(Color(0xFF1C1C2E))
            ) {
                DropdownMenuItem(
                    text = { Text("Copiar", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, "Copy", tint = HoneyGold) },
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text))
                        Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                        showContextMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Compartir", color = Color.White, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Share, "Share", tint = HoneyGold) },
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message.text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir"))
                        showContextMenu = false
                    }
                )
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
        Icon(Icons.Filled.SmartToy, "AI", tint = HoneyGold.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp).padding(start = 4.dp, bottom = 2.dp))
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
                            Icon(Icons.Filled.SmartToy, agent.name, tint = HoneyGold, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(agent.name, color = BeeWhite, fontWeight = FontWeight.Bold)
                                Text(agent.description, color = BeeGrayLight, fontSize = 12.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Filled.CheckCircle, "Selected", tint = BeeYellow, modifier = Modifier.size(20.dp))
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

/**
 * File attachment card — shown inline in chat bubbles.
 * Displays file icon, name, size, image preview, and action buttons.
 */
@Composable
fun FileAttachmentCard(filePath: String) {
    val context = LocalContext.current
    val resolvedPath = resolveFilePath(filePath)
    val file = File(resolvedPath)
    val exists = file.exists()
    val fileName = file.name
    val ext = fileName.substringAfterLast('.').lowercase()
    val isImage = ext in listOf("png", "jpg", "jpeg", "webp")
    val fileSize = if (exists) {
        val bytes = file.length()
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        }
    } else "—"

    val fileIcon = when (ext) {
        "pdf" -> Icons.Outlined.PictureAsPdf
        "html", "htm" -> Icons.Outlined.Language
        "csv", "tsv" -> Icons.Outlined.TableChart
        "txt" -> Icons.Outlined.Description
        "png", "jpg", "jpeg", "webp" -> Icons.Outlined.Image
        else -> Icons.Outlined.InsertDriveFile
    }

    val accentColor = when (ext) {
        "pdf" -> Color(0xFFFF2D55)
        "html", "htm" -> Color(0xFF5AC8FA)
        "csv", "tsv" -> Color(0xFF34C759)
        "png", "jpg", "jpeg", "webp" -> Color(0xFFBF5AF2)
        else -> Color(0xFFFF9500)
    }

    Surface(
        color = Color(0xFF0E0E18),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Image preview for images
            if (isImage && exists) {
                val bitmap = remember(resolvedPath) {
                    try {
                        BitmapFactory.decodeFile(resolvedPath)
                    } catch (_: Exception) { null }
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File icon
                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(fileIcon, fileName, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))

                // File info
                Column(modifier = Modifier.weight(1f)) {
                    Text(fileName, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${ext.uppercase()} · $fileSize", fontSize = 10.sp, color = Color(0xFF888899))
                }

                // Action buttons
                if (exists) {
                    // Open
                    IconButton(
                        onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val mime = when (ext) {
                                    "pdf" -> "application/pdf"
                                    "html", "htm" -> "text/html"
                                    "csv" -> "text/csv"
                                    "png" -> "image/png"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "webp" -> "image/webp"
                                    else -> "*/*"
                                }
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.OpenInNew, "Open", tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    // Share
                    IconButton(
                        onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir"))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Share, "Share", tint = Color(0xFF888899), modifier = Modifier.size(18.dp))
                    }
                } else {
                    Text("No encontrado", fontSize = 10.sp, color = Color(0xFFFF6666))
                }
            }
        }
    }
}

/**
 * Resolve a file path — handles relative paths like "Documents/BeeMovil/file.pdf"
 */
private fun resolveFilePath(path: String): String {
    if (path.startsWith("/")) return path
    // Try Documents directory
    val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    if (path.startsWith("Documents/")) {
        return File(docsDir.parentFile, path.removePrefix("Documents/")).absolutePath
    }
    return File(docsDir, path).absolutePath
}
