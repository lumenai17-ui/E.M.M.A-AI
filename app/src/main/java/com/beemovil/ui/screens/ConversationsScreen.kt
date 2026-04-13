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
    var showFactorySheet by remember { mutableStateOf(false) }

    if (showFactorySheet) {
        AgentFactorySheet(
            onDismiss = { showFactorySheet = false },
            onForgeAgent = { name, icon, prompt, model ->
                viewModel.forgeAgent(name, icon, prompt, model)
                showFactorySheet = false
            }
        )
    }

    if (viewModel.showHermesDialog.value) {
        var serverUrl by remember { mutableStateOf("ws://10.0.2.2:8643/mobile/ws") }
        var token by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { viewModel.showHermesDialog.value = false },
            containerColor = Color(0xFF1E1E2C),
            title = { Text("Conexión Hermes (A2A)", color = BeeWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("URL del Servidor", color = BeeGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                            focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Access Token", color = BeeGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                            focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.connectHermes(serverUrl, token) }) {
                    Text("Conectar", color = BeeYellow, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showHermesDialog.value = false }) {
                    Text("Cancelar", color = BeeGray)
                }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFF0F0F16), // Deep Space Black
        topBar = {
            TopAppBar(
                title = { Text("Agent Hub", fontWeight = FontWeight.Bold, color = BeeWhite) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (viewModel.isHermesConnected.value) "Hermes On" else "Hermes Off", 
                            color = if(viewModel.isHermesConnected.value) Color.Green else BeeGray, 
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
                                checkedThumbColor = BeeYellow, 
                                checkedTrackColor = Color(0xFF2A2A3D)
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
                containerColor = BeeYellow,
                contentColor = BeeBlack,
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
            // Fila de Agentes Pineados (Expertos Directos)
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
                                    .background(Color(0xFF1E1E2C).copy(alpha = 0.8f))
                                    .border(1.dp, BeeYellow.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(agent.icon, fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(agent.name.take(10), color = BeeWhite, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF1E1E2C), thickness = 1.dp)
            } else {
                Text("No hay expertos forjados.", color = BeeGray, modifier = Modifier.padding(16.dp))
            }

            // Lista de Threads (Grupos e Individuales)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (viewModel.allThreads.isEmpty()) {
                    item {
                        Text(
                            "Presiona '+' para forjar tu primer Agente o Swarm Group.",
                            color = BeeGray, fontSize = 14.sp,
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
                                .background(if (thread.type == "GROUP") Color(0xFF2A2A3D) else Color(0xFF1E1E2C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (thread.type == "GROUP") "👥" else "🧠", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(thread.title, color = BeeWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            // UI-12: Subtítulo descriptivo en vez de genérico
                            val subtitle = when (thread.type) {
                                "GROUP" -> "Grupo de agentes"
                                else -> if (thread.threadId == "main") "Chat principal" else "Conversación directa"
                            }
                            Text(subtitle, color = BeeGray, fontSize = 13.sp, maxLines = 1)
                        }
                        // UI-11: Timestamp relativo real
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
                        Text(timeAgo, color = BeeYellow, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
