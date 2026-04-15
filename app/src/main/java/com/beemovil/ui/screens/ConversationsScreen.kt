package com.beemovil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*

import com.beemovil.ui.components.AgentFactorySheet
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(viewModel: ChatViewModel) {
    val isDark = isDarkTheme()
    val bg = if (isDark) Color(0xFF0F0F16) else LightBackground
    val textPrimary = if (isDark) BeeWhite else TextDark
    val textSecondary = if (isDark) BeeGray else TextGrayDark
    val accent = if (isDark) BeeYellow else BrandBlue
    val cardBg = if (isDark) Color(0xFF1E1E2C) else LightSurface
    val cardBgAlt = if (isDark) Color(0xFF2A2A3D) else LightCard
    val dividerColor = if (isDark) Color(0xFF1E1E2C) else LightBorder
    val dialogBg = if (isDark) Color(0xFF1E1E2C) else LightSurface

    var showFactorySheet by remember { mutableStateOf(false) }

    if (showFactorySheet) {
        AgentFactorySheet(
            onDismiss = { showFactorySheet = false },
            onForgeAgent = { name, icon, prompt, model ->
                viewModel.forgeAgent(name, icon, prompt, model)
                showFactorySheet = false
            },
            onForgeAgentWithAvatar = { name, icon, prompt, model, avatarUri ->
                viewModel.forgeAgent(name, icon, prompt, model, avatarUri)
                showFactorySheet = false
            }
        )
    }

    if (viewModel.showHermesDialog.value) {
        var serverUrl by remember { mutableStateOf("ws://10.0.2.2:8643/mobile/ws") }
        var token by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { viewModel.showHermesDialog.value = false },
            containerColor = dialogBg,
            title = { Text("Conexión Hermes (A2A)", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("URL del Servidor", color = textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                            focusedBorderColor = accent, unfocusedBorderColor = textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Access Token", color = textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                            focusedBorderColor = accent, unfocusedBorderColor = textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.connectHermes(serverUrl, token) }) {
                    Text("Conectar", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showHermesDialog.value = false }) {
                    Text("Cancelar", color = textSecondary)
                }
            }
        )
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("Agent Hub", fontWeight = FontWeight.Bold, color = textPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (viewModel.isHermesConnected.value) "Hermes On" else "Hermes Off", 
                            color = if(viewModel.isHermesConnected.value) Color.Green else textSecondary, 
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = viewModel.isHermesConnected.value,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    viewModel.showHermesDialog.value = true
                                } else {
                                    viewModel.disconnectHermes()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accent, 
                                checkedTrackColor = if (isDark) Color(0xFF2A2A3D) else accent.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFactorySheet = true },
                containerColor = accent,
                contentColor = if (isDark) BeeBlack else Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, "Nuevo Enjambre")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Fila de Agentes Pineados
            if (viewModel.allAgents.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(viewModel.allAgents) { agent ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { viewModel.openAgentChat(agent) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(cardBg.copy(alpha = 0.8f))
                                    .border(
                                        if (agent.avatarUri != null) 2.dp else 1.dp,
                                        if (agent.avatarUri != null) accent else accent.copy(alpha = 0.3f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (agent.avatarUri != null && java.io.File(agent.avatarUri).exists()) {
                                    coil.compose.AsyncImage(
                                        model = java.io.File(agent.avatarUri),
                                        contentDescription = agent.name,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(agent.icon, fontSize = 24.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(agent.name.take(10), color = textPrimary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = dividerColor, thickness = 1.dp)
            } else {
                Text("No hay expertos forjados.", color = textSecondary, modifier = Modifier.padding(16.dp))
            }

            // Lista de Threads
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (viewModel.allThreads.isEmpty()) {
                    item {
                        Text(
                            "Presiona '+' para forjar tu primer Agente o Swarm Group.",
                            color = textSecondary, fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(viewModel.allThreads) { thread ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openThread(thread) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (thread.type == "GROUP") cardBgAlt else cardBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (thread.type == "GROUP") "👥" else "🧠", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(thread.title, color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            val subtitle = when (thread.type) {
                                "GROUP" -> "Grupo de agentes"
                                else -> if (thread.threadId == "main") "Chat principal" else "Conversación directa"
                            }
                            Text(subtitle, color = textSecondary, fontSize = 13.sp, maxLines = 1)
                        }
                        val timeAgo = remember(thread.lastUpdateMillis) {
                            val diff = System.currentTimeMillis() - thread.lastUpdateMillis
                            val mins = diff / 60_000
                            val hours = mins / 60
                            val days = hours / 24
                            when {
                                mins < 1 -> "Ahora"
                                mins < 60 -> "${mins}m"
                                hours < 24 -> "${hours}h"
                                days < 7 -> "${days}d"
                                else -> "${days / 7}sem"
                            }
                        }
                        Text(timeAgo, color = accent, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
