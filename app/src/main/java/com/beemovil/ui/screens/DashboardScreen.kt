package com.beemovil.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
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
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current

    // Collect stats
    val totalMessages = remember { mutableStateOf(0) }
    val memoryCount = remember { mutableStateOf(0) }
    val agentCount = remember { mutableStateOf(viewModel.availableAgents.size) }
    val recentChats = remember { mutableStateListOf<ChatHistoryDB.ConversationPreview>() }
    val batteryLevel = remember { mutableStateOf(getBatteryLevel(context)) }

    LaunchedEffect(Unit) {
        totalMessages.value = chatHistoryDB?.getTotalMessageCount() ?: 0
        memoryCount.value = memoryDB?.getMemoryCount() ?: 0
        batteryLevel.value = getBatteryLevel(context)
        chatHistoryDB?.let { db ->
            recentChats.clear()
            recentChats.addAll(db.getConversationPreviews().take(4))
        }
    }

    val telegramStatus = viewModel.telegramBotStatus.value
    val telegramName = viewModel.telegramBotName.value
    val now = remember { SimpleDateFormat("EEEE, d MMM", Locale("es")).format(Date()).replaceFirstChar { it.uppercase() } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Header with logo + date
        item {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                BeeYellow.copy(alpha = 0.15f),
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
                            contentDescription = "Bee-Movil",
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Bee-Movil", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = BeeWhite)
                            Text(now, fontSize = 12.sp, color = BeeYellowLight)
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
                MetricCard("Agentes", "${agentCount.value}", "🤖", Modifier.weight(1f))
                MetricCard("Mensajes", "${totalMessages.value}", "💬", Modifier.weight(1f))
                MetricCard("Memorias", "${memoryCount.value}", "🧠", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Device info bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val bat = batteryLevel.value
                    val batColor = when {
                        bat > 60 -> Color(0xFF4CAF50)
                        bat > 20 -> BeeYellow
                        else -> Color(0xFFF44336)
                    }
                    DeviceChip("🔋", "${bat}%", batColor)
                    DeviceChip("📶", "Online", Color(0xFF4CAF50))
                    DeviceChip("🔧", "$skillCount", BeeYellow)
                    DeviceChip(
                        if (telegramStatus == "online") "🟢" else "⚪",
                        "TG Bot",
                        if (telegramStatus == "online") Color(0xFF4CAF50) else BeeGray
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // System status
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ESTADO DEL SISTEMA", fontSize = 11.sp, color = BeeYellow,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    StatusRow(
                        icon = "🧠",
                        label = "Proveedor AI",
                        value = viewModel.getProviderDisplayName(),
                        statusColor = if (viewModel.hasApiKey()) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )

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
                        icon = "💾",
                        label = "Base de datos",
                        value = "SQLite OK",
                        statusColor = Color(0xFF4CAF50)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Recent conversations
        if (recentChats.isNotEmpty()) {
            item {
                Text(
                    "CONVERSACIONES RECIENTES",
                    fontSize = 11.sp,
                    color = BeeYellow,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
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

            item { Spacer(modifier = Modifier.height(14.dp)) }
        }

        // Quick actions — now functional
        item {
            Text(
                "ACCESOS RÁPIDOS",
                fontSize = 11.sp,
                color = BeeYellow,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    QuickAction("🐝", "Chat", Color(0xFFFFC107).copy(alpha = 0.2f)) {
                        onAgentClick("main")
                    }
                }
                item {
                    QuickAction("🌤️", "Clima", Color(0xFF2196F3).copy(alpha = 0.2f)) {
                        viewModel.openAgentChatWithPrompt("main", "¿Cómo está el clima ahora?")
                    }
                }
                item {
                    QuickAction("🔋", "Batería", Color(0xFF4CAF50).copy(alpha = 0.2f)) {
                        viewModel.openAgentChatWithPrompt("main", "¿Cómo está la batería de mi teléfono?")
                    }
                }
                item {
                    QuickAction("📅", "Agenda", Color(0xFF9C27B0).copy(alpha = 0.2f)) {
                        viewModel.openAgentChatWithPrompt("agenda", "¿Qué tengo programado hoy?")
                    }
                }
                item {
                    QuickAction("🔍", "Buscar", Color(0xFFFF5722).copy(alpha = 0.2f)) {
                        viewModel.openAgentChatWithPrompt("main", "Busca las últimas noticias de tecnología")
                    }
                }
                item {
                    QuickAction("📸", "Cámara", Color(0xFFE91E63).copy(alpha = 0.2f)) {
                        viewModel.currentScreen.value = "camera"
                    }
                }
                item {
                    QuickAction("📧", "Correo", Color(0xFF3F51B5).copy(alpha = 0.2f)) {
                        viewModel.currentScreen.value = "email_inbox"
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
                Text("Bee-Movil v3.4 · $skillCount skills · Kotlin nativo", fontSize = 11.sp, color = BeeGray)
            }
        }
    }
}

private fun getBatteryLevel(context: Context): Int {
    return try {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battery = context.registerReceiver(null, filter)
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        (level * 100) / scale
    } catch (_: Exception) { -1 }
}

@Composable
private fun MetricCard(label: String, value: String, emoji: String, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = BeeYellow)
            Text(label, fontSize = 11.sp, color = Color(0xFFB0B0B0))
        }
    }
}

@Composable
private fun DeviceChip(icon: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusRow(icon: String, label: String, value: String, statusColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontSize = 14.sp, color = Color(0xFFE0E0E0), modifier = Modifier.weight(1f))
        Surface(
            color = statusColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(value, fontSize = 12.sp, color = Color(0xFFE0E0E0), fontWeight = FontWeight.Medium)
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
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Color(0xFF2A2A3E), shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(agentIcon, fontSize = 22.sp) }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agentName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFFE8E8E8))
                Spacer(modifier = Modifier.height(2.dp))
                Text(lastMessage, fontSize = 12.sp, color = Color(0xFF888888), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                val diff = System.currentTimeMillis() - timestamp
                val timeText = when {
                    diff < 60_000 -> "ahora"
                    diff < 3600_000 -> "${diff / 60_000}m"
                    diff < 86400_000 -> "${diff / 3600_000}h"
                    else -> "${diff / 86400_000}d"
                }
                Text(timeText, fontSize = 11.sp, color = Color(0xFF888888))
                if (messageCount > 0) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(color = BeeYellow, shape = CircleShape) {
                        Text("$messageCount", fontSize = 10.sp, color = BeeBlack,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(82.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(color = bgColor, shape = CircleShape, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 22.sp) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = Color(0xFFB0B0B0), fontWeight = FontWeight.Medium)
        }
    }
}
