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
import androidx.compose.foundation.lazy.items
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import com.beemovil.ui.components.BrowserChatPanel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    agentId: String,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showMenuForMessage by remember { mutableStateOf<String?>(null) }
    var attachedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val prefs = context.getSharedPreferences("beemovil", android.content.Context.MODE_PRIVATE)
    val provider = prefs.getString("selected_provider", "openrouter")?.uppercase() ?: "OPENROUTER"
    val modelId = prefs.getString("selected_model", "gpt-4o-mini")?.split("/")?.last() ?: "gpt-4"

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        attachedFileUri = uri
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = BeeBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = BeeYellow.copy(alpha = 0.3f), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.SmartToy, "EMMA", tint = BeeYellow, modifier = Modifier.padding(6.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.app_name), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BeeWhite)
                            Text("[$provider] $modelId", fontSize = 10.sp, color = BeeYellow.copy(alpha=0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow) }
                },
                actions = {
                    IconButton(onClick = { viewModel.isMuted.value = !viewModel.isMuted.value }) {
                        Icon(
                            if (viewModel.isMuted.value) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            "Mute Toggle",
                            tint = if (viewModel.isMuted.value) Color.Red else BeeYellow
                        )
                    }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Filled.MoreVert, "Settings", tint = BeeGray) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161622))
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF161622),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp)) {
                    if (attachedFileUri != null) {
                        Surface(
                            color = Color(0xFF222234),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(bottom = 8.dp, start = 48.dp, end = 48.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                Icon(Icons.Filled.InsertDriveFile, "File", tint = BeeYellow, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Archivo adjunto", color = BeeWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { attachedFileUri = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.Close, "Remove", tint = BeeGray)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                            Icon(Icons.Outlined.AttachFile, "Attach", tint = BeeGray)
                        }
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.chat_input_placeholder), color = BeeGray) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF222234),
                                unfocusedContainerColor = Color(0xFF222234),
                                focusedTextColor = BeeWhite,
                                unfocusedTextColor = BeeWhite,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        Box(contentAlignment = Alignment.Center) {
                            if (inputText.isNotBlank() || attachedFileUri != null) {
                                IconButton(onClick = { 
                                    if (inputText.isNotBlank() || attachedFileUri != null) {
                                        val sendingText = if (attachedFileUri != null) "[Archivo Adjunto] $inputText" else inputText
                                        viewModel.sendMessage(sendingText)
                                        inputText = ""
                                        attachedFileUri = null
                                    }
                                }) {
                                    Icon(Icons.Filled.Send, "Send", tint = BeeYellow)
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
                                    Icon(
                                        if (viewModel.isRecording.value) Icons.Filled.Stop else Icons.Filled.Mic, 
                                        "Voice", 
                                        tint = if (viewModel.isRecording.value) Color.Red else BeeYellow
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
            modifier = Modifier.fillMaxSize().padding(padding).background(BeeBlack)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
            ) {
                val displayMessages = if (viewModel.isSearchMode.value) viewModel.searchResults else viewModel.messages
                items(displayMessages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .clip(RoundedCornerShape(if (msg.isUser) 16.dp else 0.dp, 16.dp, 16.dp, if (msg.isUser) 0.dp else 16.dp))
                                    .background(if (msg.isUser) BeeYellow else Color(0xFF222234))
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { showMenuForMessage = msg.text }
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    color = if (msg.isUser) BeeBlack else BeeWhite,
                                    fontSize = 15.sp
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenuForMessage == msg.text,
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
                            }
                        }
                    }
                }
                if (viewModel.isLoading.value) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Start) {
                            CircularProgressIndicator(color = BeeYellow, modifier = Modifier.size(24.dp))
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
    }
}
