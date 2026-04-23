package com.beemovil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.beemovil.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import com.beemovil.ui.components.BrowserChatPanel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import android.content.Intent
import coil.compose.AsyncImage


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    agentId: String,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isDark = isDarkTheme()
    val bg = if (isDark) BeeBlack else LightBackground
    val accent = if (isDark) BeeYellow else BrandBlue
    val accentSecondary = if (isDark) BeeYellow else BrandGreen
    val textPrimary = if (isDark) BeeWhite else TextDark
    val textSecondary = if (isDark) BeeGray else TextGrayDark
    val topBarBg = if (isDark) Color(0xFF161622) else LightSurface
    val inputBg = if (isDark) Color(0xFF222234) else LightCard
    val userBubble = if (isDark) BeeYellow else BrandGreenLight
    val userBubbleText = if (isDark) BeeBlack else TextDark
    val assistantBubble = if (isDark) Color(0xFF222234) else LightSurface
    val assistantBubbleText = if (isDark) BeeWhite else TextDark
    val fileSurface = if (isDark) Color(0xFF1E1E2C).copy(alpha=0.6f) else LightCard

    var inputText by remember { mutableStateOf("") }
    var showMenuForMessage by remember { mutableStateOf<Int?>(null) }
    var attachedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    var showTopMenu by remember { mutableStateOf(false) }
    var showEditAgentDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val prefs = context.getSharedPreferences("beemovil", android.content.Context.MODE_PRIVATE)
    val provider = prefs.getString("selected_provider", "openrouter")?.uppercase() ?: "OPENROUTER"
    val modelId = prefs.getString("selected_model", "gpt-4o-mini")?.split("/")?.last() ?: "gpt-4"

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // If the provider doesn't support persistable permissions, it will be skipped
            }
        }
        attachedFileUri = uri
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = accent.copy(alpha = 0.3f), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.SmartToy, "EMMA", tint = accent, modifier = Modifier.padding(6.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(viewModel.activeAgentName.value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                            Text("[$provider] $modelId", fontSize = 10.sp, color = accent.copy(alpha=0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, "Back", tint = accent) }
                },
                actions = {
                    IconButton(onClick = { viewModel.isMuted.value = !viewModel.isMuted.value }) {
                        Icon(
                            if (viewModel.isMuted.value) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            "Mute Toggle",
                            tint = if (viewModel.isMuted.value) Color.Red else accent
                        )
                    }
                    IconButton(onClick = { showTopMenu = true }) { Icon(Icons.Filled.MoreVert, "Settings", tint = textSecondary) }
                    DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Configurar Agente Mente") },
                            onClick = { 
                                showTopMenu = false
                                if (viewModel.activeAgentConfig.value != null) {
                                    showEditAgentDialog = true
                                } else {
                                    android.widget.Toast.makeText(context, "No es un Agente Editable", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ajustes del Sistema") },
                            onClick = {
                                showTopMenu = false
                                onSettingsClick()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBg)
            )
        },
        bottomBar = {
            Surface(
                color = topBarBg,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)) {
                    if (attachedFileUri != null) {
                        Surface(
                            color = inputBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(bottom = 8.dp, start = 48.dp, end = 48.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                Icon(Icons.Filled.InsertDriveFile, "File", tint = accent, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Archivo adjunto", color = textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { attachedFileUri = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, "Remove", tint = textSecondary)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            Icon(Icons.Outlined.AttachFile, "Attach", tint = textSecondary)
                        }
                        AnimatedVisibility(
                            visible = !viewModel.isRecording.value,
                            modifier = Modifier.weight(1f)
                        ) {
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text(stringResource(R.string.chat_input_placeholder), color = textSecondary) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = inputBg,
                                    unfocusedContainerColor = inputBg,
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                        }
                        AnimatedVisibility(
                            visible = viewModel.isRecording.value,
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                        ) {
                            Text("🔴 Grabando Audio...", color = Color.Red, fontSize = 14.sp)
                        }
                        Box(contentAlignment = Alignment.Center) {
                            if (inputText.isNotBlank() || attachedFileUri != null) {
                                IconButton(onClick = { 
                                    if (inputText.isNotBlank() || attachedFileUri != null) {
                                        val sendingUri = attachedFileUri?.toString()
                                        viewModel.sendMessage(inputText, sendingUri)
                                        inputText = ""
                                        attachedFileUri = null
                                    }
                                }) {
                                    Icon(Icons.Filled.Send, "Send", tint = accent)
                                }
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    viewModel.toggleVoiceInput { text -> 
                                                        viewModel.sendMessage(text)
                                                    }
                                                    tryAwaitRelease()
                                                    if (viewModel.isRecording.value) {
                                                        viewModel.toggleVoiceInput { } // Stop recording
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    val scaleAnim by animateFloatAsState(targetValue = if (viewModel.isRecording.value) 1.3f else 1.0f)
                                    Icon(
                                        if (viewModel.isRecording.value) Icons.Filled.Stop else Icons.Filled.Mic, 
                                        "Voice", 
                                        tint = if (viewModel.isRecording.value) Color.Red else accent,
                                        modifier = Modifier.scale(scaleAnim)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(bg)
        ) {
            val listState = rememberLazyListState()
            val displayMessages = if (viewModel.isSearchMode.value) viewModel.searchResults else viewModel.messages
            
            // U-01 fix: Salto instantáneo en carga inicial, animación solo para mensajes nuevos
            var previousSize by remember { mutableIntStateOf(0) }
            LaunchedEffect(displayMessages.size) {
                if (displayMessages.isNotEmpty()) {
                    if (previousSize == 0) {
                        listState.scrollToItem(displayMessages.size - 1)
                    } else {
                        listState.animateScrollToItem(displayMessages.size - 1)
                    }
                    previousSize = displayMessages.size
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
            ) {
                itemsIndexed(displayMessages) { index, msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .clip(RoundedCornerShape(if (msg.isUser) 16.dp else 0.dp, 16.dp, 16.dp, if (msg.isUser) 0.dp else 16.dp))
                                    .background(if (msg.isUser) userBubble else assistantBubble)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { showMenuForMessage = index }
                                    )
                                    .padding(12.dp)
                            ) {
                                Column {
                                    if (msg.filePaths.isNotEmpty()) {
                                        val fPath = msg.filePaths.first()
                                        val fName = msg.attachmentNames.firstOrNull() ?: java.io.File(fPath).name
                                        val fMime = msg.attachmentMimeTypes.firstOrNull() ?: ""

                                        val isImage = fMime.startsWith("image/") ||
                                            fPath.endsWith(".jpg") || fPath.endsWith(".jpeg") ||
                                            fPath.endsWith(".png") || fPath.endsWith(".webp") ||
                                            fPath.endsWith(".gif")

                                        Surface(
                                            color = fileSurface,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .padding(bottom = 6.dp)
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .combinedClickable(
                                                    onClick = {
                                                        try {
                                                            val viewUri = if (fPath.startsWith("content://")) {
                                                                android.net.Uri.parse(fPath)
                                                            } else {
                                                                androidx.core.content.FileProvider.getUriForFile(
                                                                    context, "${context.packageName}.fileprovider",
                                                                    java.io.File(fPath)
                                                                )
                                                            }
                                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                                setDataAndType(viewUri, if (fMime.isNotBlank()) fMime else "*/*")
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(viewIntent)
                                                        } catch (e: Exception) {
                                                            android.widget.Toast.makeText(context, "No se pudo abrir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    onLongClick = { showMenuForMessage = index }
                                                )
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                                if (isImage) {
                                                    val imageFile = java.io.File(fPath)
                                                    AsyncImage(
                                                        model = if (fPath.startsWith("content://")) fPath else imageFile,
                                                        contentDescription = fName,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 280.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                                                    )
                                                } else {
                                                    val fileIcon = when {
                                                        fMime.contains("pdf") || fName.endsWith(".pdf") -> Icons.Filled.PictureAsPdf
                                                        fMime.startsWith("audio/") -> Icons.Filled.AudioFile
                                                        fMime.startsWith("video/") -> Icons.Filled.VideoFile
                                                        else -> Icons.Filled.InsertDriveFile
                                                    }
                                                    Icon(fileIcon, "File", tint=accent, modifier = Modifier.size(32.dp))
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(fName, color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    if (fMime.isNotBlank()) {
                                                        Text(fMime.uppercase(), color = textSecondary, fontSize = 9.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (msg.text.isNotBlank()) {
                                        Text(
                                            text = msg.text,
                                            color = if (msg.isUser) userBubbleText else assistantBubbleText,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showMenuForMessage == index,
                                onDismissRequest = { showMenuForMessage = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copiar texto") },
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.text))
                                        android.widget.Toast.makeText(context, "Copiado al portapapeles", android.widget.Toast.LENGTH_SHORT).show()
                                        showMenuForMessage = null
                                    }
                                )
                                if (msg.filePaths.isNotEmpty()) {
                                    val rawPath = msg.filePaths.first()
                                    val fileMime = msg.attachmentMimeTypes.firstOrNull() ?: "*/*"

                                    DropdownMenuItem(
                                        text = { Text("Abrir archivo") },
                                        onClick = {
                                            try {
                                                val viewUri = if (rawPath.startsWith("content://")) {
                                                    android.net.Uri.parse(rawPath)
                                                } else {
                                                    androidx.core.content.FileProvider.getUriForFile(
                                                        context, "${context.packageName}.fileprovider",
                                                        java.io.File(rawPath)
                                                    )
                                                }
                                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(viewUri, if (fileMime.isNotBlank()) fileMime else "*/*")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(viewIntent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "No se pudo abrir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            showMenuForMessage = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Compartir archivo") },
                                        onClick = {
                                            try {
                                                val shareUri = if (rawPath.startsWith("content://")) {
                                                    android.net.Uri.parse(rawPath)
                                                } else {
                                                    androidx.core.content.FileProvider.getUriForFile(
                                                        context, "${context.packageName}.fileprovider",
                                                        java.io.File(rawPath)
                                                    )
                                                }
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = if (fileMime.isNotBlank()) fileMime else "*/*"
                                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Compartir con..."))
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            showMenuForMessage = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (viewModel.isLoading.value) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            val progressText = viewModel.swarmInsight.value
                            if (progressText.isNotBlank()) {
                                // Detect agentic loop rounds for enhanced display
                                val isRoundProgress = progressText.startsWith("Ronda ")
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    if (isRoundProgress) {
                                        Text("⚙️", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp))
                                    }
                                    Text(
                                        text = progressText,
                                        color = accent,
                                        fontSize = 12.sp,
                                        fontWeight = if (isRoundProgress) androidx.compose.ui.text.font.FontWeight.Medium else null
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.Start) {
                                CircularProgressIndicator(color = accent, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
        
        if (viewModel.showBrowser.value) {
            BrowserChatPanel(
                url = viewModel.browserUrl.value,
                onDismiss = { viewModel.showBrowser.value = false }
            )
        }
        
        if (showEditAgentDialog) {
            val agent = viewModel.activeAgentConfig.value
            if (agent != null) {
                var editName by remember { mutableStateOf(agent.name) }
                var editPrompt by remember { mutableStateOf(agent.systemPrompt) }
                var editModel by remember { mutableStateOf(agent.fallbackModel) }

                AlertDialog(
                    onDismissRequest = { showEditAgentDialog = false },
                    containerColor = if (isDark) Color(0xFF222234) else LightSurface,
                    title = { Text("ADN de ${agent.name}", color = accent, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Nombre del Agente", color = textSecondary) },
                                colors = TextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editModel,
                                onValueChange = { editModel = it },
                                label = { Text("Motor LLM (o Túnel)", color = textSecondary) },
                                colors = TextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editPrompt,
                                onValueChange = { editPrompt = it },
                                label = { Text("Directriz Primaria (System Prompt)", color = textSecondary) },
                                modifier = Modifier.height(180.dp),
                                maxLines = 8,
                                colors = TextFieldDefaults.colors(focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Para Túnel Dinámico: hermes-a2a|wss://ip|token", fontSize = 10.sp, color = textSecondary)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val updated = agent.copy(name = editName, systemPrompt = editPrompt, fallbackModel = editModel)
                            viewModel.updateAgentConfig(updated)
                            showEditAgentDialog = false
                        }) { Text("Aplicar Mutación", color = accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditAgentDialog = false }) { Text("Cancelar", color = textSecondary) }
                    }
                )
            }
        }
    }
}
