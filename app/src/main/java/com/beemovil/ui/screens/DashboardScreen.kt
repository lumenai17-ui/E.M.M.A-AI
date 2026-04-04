package com.beemovil.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.R
import com.beemovil.memory.BeeMemoryDB
import com.beemovil.memory.ChatHistoryDB
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*

/**
 * DashboardScreen — Mission Control. Main home screen.
 */
@Composable
fun DashboardScreen(
    viewModel: ChatViewModel,
    chatHistoryDB: ChatHistoryDB?,
    memoryDB: BeeMemoryDB?,
    onAgentClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    skillCount: Int
) {
    // Collect stats
    val totalMessages = remember { mutableStateOf(0) }
    val memoryCount = remember { mutableStateOf(0) }
    val agentCount = remember { mutableStateOf(viewModel.availableAgents.size) }
    val recentChats = remember { mutableStateListOf<ChatHistoryDB.ConversationPreview>() }

    LaunchedEffect(Unit) {
        totalMessages.value = chatHistoryDB?.getTotalMessageCount() ?: 0
        memoryCount.value = memoryDB?.getMemoryCount() ?: 0
        chatHistoryDB?.let { db ->
            recentChats.clear()
            recentChats.addAll(db.getConversationPreviews().take(4))
        }
    }

    val telegramStatus = viewModel.telegramBotStatus.value
    val telegramName = viewModel.telegramBotName.value

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Header
        item {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BeeYellow.copy(alpha = 0.12f),
                                BeeBlack
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.bee_logo),
                            contentDescription = "Bee",
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Bee-Movil", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = BeeWhite)
                            Text("Mission Control", fontSize = 12.sp, color = BeeYellow)
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, "Settings", tint = BeeYellow, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }

        // Metrics row
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard("Agentes", "${agentCount.value}", "🤖", BeeYellow.copy(alpha = 0.15f), Modifier.weight(1f))
                MetricCard("Mensajes", "${totalMessages.value}", "💬", BeeYellow.copy(alpha = 0.10f), Modifier.weight(1f))
                MetricCard("Memorias", "${memoryCount.value}", "🧠", BeeYellow.copy(alpha = 0.08f), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // System status
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BeeBlackLight),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Estado del sistema", fontSize = 13.sp, color = BeeGrayLight, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Telegram bot status
                    StatusRow(
                        icon = "🤖",
                        label = "Telegram Bot",
                        value = when (telegramStatus) {
                            "online" -> if (telegramName.isNotBlank()) "@$telegramName" else "Conectado"
                            "connecting" -> "Conectando..."
                            else -> "Desconectado"
                        },
                        statusColor = when (telegramStatus) {
                            "online" -> Color(0xFF4CAF50)
                            "connecting" -> BeeYellow
                            else -> BeeGray
                        }
                    )

                    StatusRow(
                        icon = "🧠",
                        label = "Proveedor AI",
                        value = viewModel.getProviderDisplayName(),
                        statusColor = if (viewModel.hasApiKey()) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )

                    StatusRow(
                        icon = "🔧",
                        label = "Skills activos",
                        value = "$skillCount/25",
                        statusColor = BeeYellow
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Recent conversations
        if (recentChats.isNotEmpty()) {
            item {
                Text(
                    "Conversaciones recientes",
                    fontSize = 13.sp,
                    color = BeeGrayLight,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            recentChats.forEachIndexed { _, preview ->
                item {
                    RecentChatRow(
                        agentIcon = preview.agentIcon,
                        agentName = preview.agentName,
                        lastMessage = preview.lastMessage,
                        timestamp = preview.lastTimestamp,
                        messageCount = preview.messageCount,
                        onClick = { onAgentClick(preview.agentId) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Quick actions
        item {
            Text(
                "Accesos rápidos",
                fontSize = 13.sp,
                color = BeeGrayLight,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    QuickAction("🐝", "Chat", BeeYellow.copy(alpha = 0.15f)) {
                        onAgentClick("main")
                    }
                }
                item {
                    QuickAction("🌤️", "Clima", Color(0xFF2196F3).copy(alpha = 0.15f)) {
                        onAgentClick("main")
                    }
                }
                item {
                    QuickAction("🔋", "Batería", Color(0xFF4CAF50).copy(alpha = 0.15f)) {
                        onAgentClick("main")
                    }
                }
                item {
                    QuickAction("📱", "QR", Color(0xFF9C27B0).copy(alpha = 0.15f)) {
                        onAgentClick("main")
                    }
                }
                item {
                    QuickAction("🔍", "Buscar", Color(0xFFFF5722).copy(alpha = 0.15f)) {
                        onAgentClick("main")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Version footer
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("🐝 Bee-Movil v3.0 · $skillCount skills · Kotlin nativo", fontSize = 11.sp, color = BeeGray)
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, emoji: String, bgColor: Color, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BeeBlackLight),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = BeeYellow)
            Text(label, fontSize = 11.sp, color = BeeGrayLight)
        }
    }
}

@Composable
private fun StatusRow(icon: String, label: String, value: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontSize = 14.sp, color = BeeWhite, modifier = Modifier.weight(1f))
        Surface(
            color = statusColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(value, fontSize = 12.sp, color = statusColor)
            }
        }
    }
}

@Composable
private fun RecentChatRow(
    agentIcon: String,
    agentName: String,
    lastMessage: String,
    timestamp: Long,
    messageCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = BeeBlack,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = BeeGray.copy(alpha = 0.3f), shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(agentIcon, fontSize = 20.sp) }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agentName, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = BeeWhite)
                Text(lastMessage, fontSize = 12.sp, color = BeeGrayLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                val diff = System.currentTimeMillis() - timestamp
                val timeText = when {
                    diff < 60_000 -> "ahora"
                    diff < 3600_000 -> "${diff / 60_000}m"
                    diff < 86400_000 -> "${diff / 3600_000}h"
                    else -> "${diff / 86400_000}d"
                }
                Text(timeText, fontSize = 11.sp, color = BeeGrayLight)
                if (messageCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(color = BeeYellow.copy(alpha = 0.2f), shape = CircleShape) {
                        Text("$messageCount", fontSize = 10.sp, color = BeeYellow,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(emoji: String, label: String, bgColor: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = BeeBlackLight),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(80.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(color = bgColor, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 22.sp) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = BeeGrayLight)
        }
    }
}
