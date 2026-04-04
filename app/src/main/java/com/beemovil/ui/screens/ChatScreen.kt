package com.beemovil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.ui.ChatUiMessage
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onSettingsClick: () -> Unit = {}) {
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🐝 Bee-Movil", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BeeBlack,
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
        },
        bottomBar = {
            Surface(color = BeeBlackLight) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe un mensaje...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = BeeGray,
                            unfocusedContainerColor = BeeGray,
                            focusedTextColor = BeeWhite,
                            unfocusedTextColor = BeeWhite,
                            cursorColor = BeeYellow
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val msg = inputText.trim()
                            if (msg.isNotEmpty()) {
                                inputText = ""
                                viewModel.sendMessage(msg)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BeeYellow),
                        enabled = !viewModel.isLoading.value
                    ) {
                        if (viewModel.isLoading.value) {
                            Text("⏳", fontSize = 18.sp)
                        } else {
                            Text("➤", fontSize = 18.sp, color = BeeBlack)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BeeBlack),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = viewModel.messages.toList(),
                key = null
            ) { message ->
                SimpleBubble(message)
            }
        }
    }
}

@Composable
fun SimpleBubble(message: ChatUiMessage) {
    val isUser = message.isUser
    val bgColor = when {
        message.isError -> Color(0xFF4A1A1A)
        isUser -> Color(0xFF2D3250)
        else -> Color(0xFF1A1A2E)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Text(message.agentIcon, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
        }
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.toolsUsed.isNotEmpty()) {
                    Text(
                        "🔧 ${message.toolsUsed.joinToString(", ")}",
                        fontSize = 11.sp, color = BeeYellow
                    )
                }
                if (message.isLoading) {
                    Text("⏳ ${message.text}", color = BeeGrayLight, fontSize = 14.sp)
                } else {
                    Text(message.text, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}
