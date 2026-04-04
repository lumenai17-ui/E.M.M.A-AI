package com.beemovil.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * DashboardScreen — Premium Mission Control.
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
            recentChats.addAll(db.getConversationPreviews().take(3))
        }
    }

    val telegramStatus = viewModel.telegramBotStatus.value
    val telegramName = viewModel.telegramBotName.value
    val now = remember { SimpleDateFormat("EEEE, d MMM", Locale("es")).format(Date()).replaceFirstChar { it.uppercase() } }
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Buenos días"
            hour < 18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A14)),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ═══════════════════════════════════════════
        // HERO HEADER
        // ═══════════════════════════════════════════
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A2E),
                                Color(0xFF16162A),
                                Color(0xFF0A0A14)
                            )
                        )
                    )
            ) {
                // Subtle accent glow
                Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    drawCircle(
                        color = BeeYellow.copy(alpha = 0.06f),
                        radius = 200f,
                        center = Offset(size.width * 0.8f, 40f)
                    )
                    drawCircle(
                        color = Color(0xFF4CAF50).copy(alpha = 0.04f),
                        radius = 150f,
                        center = Offset(size.width * 0.2f, 120f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    // Top row: Logo + settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = BeeYellow.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(id = R.drawable.bee_logo),
                                        contentDescription = "Bee",
                                        modifier = Modifier.size(36.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Bee-Movil", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = BeeWhite)
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, "Settings", tint = Color(0xFF666688))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Greeting
                    Text(greeting, fontSize = 14.sp, color = BeeGray)
                    Text(now, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = BeeWhite)

                    Spacer(modifier = Modifier.height(20.dp))

                    // Stats cards in row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            value = "$skillCount",
                            label = "Skills",
                            accent = BeeYellow,
                            progress = minOf(skillCount / 40f, 1f),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            value = "${totalMessages.value}",
                            label = "Mensajes",
                            accent = Color(0xFF4CAF50),
                            progress = minOf(totalMessages.value / 100f, 1f),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            value = "${memoryCount.value}",
                            label = "Memorias",
                            accent = Color(0xFF9C27B0),
                            progress = minOf(memoryCount.value / 20f, 1f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ═══════════════════════════════════════════
        // HERO FEATURES — Big prominent cards
        // ═══════════════════════════════════════════
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Row 1: Chat AI (wide) + Voice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroCard(
                        emoji = "🐝",
                        title = "Chat AI",
                        subtitle = "Tu asistente inteligente",
                        gradient = listOf(Color(0xFF2A2200), Color(0xFF1A1A00)),
                        accent = BeeYellow,
                        modifier = Modifier.weight(1.2f),
                        onClick = { onAgentClick("main") }
                    )
                    HeroCard(
                        emoji = "🎤",
                        title = "Voz",
                        subtitle = "Hands-free",
                        gradient = listOf(Color(0xFF0A2A0A), Color(0xFF0A1A0A)),
                        accent = Color(0xFF4CAF50),
                        modifier = Modifier.weight(0.8f),
                        onClick = { viewModel.currentScreen.value = "voice_chat" }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Vision AI + Live Vision
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroCard(
                        emoji = "📸",
                        title = "Visión AI",
                        subtitle = "Gemma 4 · Analiza fotos",
                        gradient = listOf(Color(0xFF2A0A1A), Color(0xFF1A0A14)),
                        accent = Color(0xFFE91E63),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.currentScreen.value = "camera" }
                    )
                    HeroCard(
                        emoji = "🔴",
                        title = "Live",
                        subtitle = "Visión en vivo",
                        gradient = listOf(Color(0xFF2A1A0A), Color(0xFF1A0A0A)),
                        accent = Color(0xFFFF5722),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.currentScreen.value = "live_vision" }
                    )
                }
            }
        }

        // ═══════════════════════════════════════════
        // SECONDARY ACTIONS — 2-column grid
        // ═══════════════════════════════════════════
        item {
            SectionHeader("Herramientas", modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp))
        }

        item {
            val actions = listOf(
                ActionItem("📧", "Correo", "Bandeja de entrada", Color(0xFF3F51B5)) { viewModel.currentScreen.value = "email_inbox" },
                ActionItem("🔍", "Investigar", "Busca en la web", Color(0xFFFF5722)) { viewModel.openAgentChatWithPrompt("main", "Busca las últimas noticias de tecnología") },
                ActionItem("📄", "Crear PDF", "Genera documentos", Color(0xFF795548)) { viewModel.openAgentChatWithPrompt("main", "Hazme un PDF con un resumen ejecutivo sobre inteligencia artificial") },
                ActionItem("📅", "Agenda", "Tus eventos de hoy", Color(0xFF9C27B0)) { viewModel.openAgentChatWithPrompt("agenda", "¿Qué tengo programado hoy?") },
                ActionItem("🌐", "Landing", "Crea una página web", Color(0xFF00BCD4)) { viewModel.openAgentChatWithPrompt("main", "Crea una landing page moderna para un café artesanal") },
                ActionItem("📊", "Excel", "Genera hojas de cálculo", Color(0xFF388E3C)) { viewModel.openAgentChatWithPrompt("main", "Hazme un spreadsheet comparativo de precios") },
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                actions.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { action ->
                            ActionCard(action, Modifier.weight(1f))
                        }
                        // Fill empty spot if odd
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // ═══════════════════════════════════════════
        // SYSTEM STATUS
        // ═══════════════════════════════════════════
        item {
            SectionHeader("Sistema", modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12121E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Battery + connection row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val bat = batteryLevel.value
                        val batColor = when {
                            bat > 60 -> Color(0xFF4CAF50)
                            bat > 20 -> BeeYellow
                            else -> Color(0xFFF44336)
                        }
                        SystemChip("🔋", "${bat}%", batColor)
                        SystemChip("📶", "Online", Color(0xFF4CAF50))
                        SystemChip(
                            if (telegramStatus == "online") "🟢" else "⚪",
                            if (telegramStatus == "online") (if (telegramName.isNotBlank()) "@$telegramName" else "TG ✓") else "TG",
                            if (telegramStatus == "online") Color(0xFF4CAF50) else Color(0xFF555577)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = Color(0xFF222240), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Provider info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (viewModel.hasApiKey()) Color(0xFF4CAF50) else Color(0xFFF44336))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI: ", fontSize = 13.sp, color = BeeGray)
                        Text(viewModel.getProviderDisplayName(), fontSize = 13.sp, color = BeeWhite, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("$skillCount skills activos", fontSize = 11.sp, color = Color(0xFF666688))
                    }
                }
            }
        }

        // ═══════════════════════════════════════════
        // RECENT CONVERSATIONS
        // ═══════════════════════════════════════════
        if (recentChats.isNotEmpty()) {
            item {
                SectionHeader("Recientes", modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp))
            }

            recentChats.forEachIndexed { _, preview ->
                item {
                    RecentChatCard(
                        agentIcon = preview.agentIcon,
                        agentName = preview.agentName,
                        lastMessage = preview.lastMessage,
                        timestamp = preview.lastTimestamp,
                        messageCount = preview.messageCount,
                        onClick = { onAgentClick(preview.agentId) }
                    )
                }
            }
        }

        // ═══════════════════════════════════════════
        // AGENTS
        // ═══════════════════════════════════════════
        item {
            SectionHeader("Agentes", modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                viewModel.availableAgents.forEach { agent ->
                    item {
                        AgentChip(
                            icon = agent.icon,
                            name = agent.name,
                            onClick = { onAgentClick(agent.id) }
                        )
                    }
                }
            }
        }

        // Footer
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Bee-Movil v3.7 · $skillCount skills · Kotlin nativo",
                    fontSize = 10.sp, color = Color(0xFF333355))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// COMPONENTS
// ═══════════════════════════════════════════════════════

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
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BeeYellow)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            color = Color(0xFF888899),
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun HeroCard(
    emoji: String,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.height(100.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradient))
                .padding(16.dp)
        ) {
            // Decorative accent circle
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = accent.copy(alpha = 0.08f),
                    radius = 60f,
                    center = Offset(size.width - 20f, 30f)
                )
            }
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = accent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BeeWhite)
                }
                Text(subtitle, fontSize = 11.sp, color = accent.copy(alpha = 0.7f))
            }
            // Right accent line
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.3f))
            )
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, accent: Color, progress: Float, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12121E)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mini arc chart
            Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(52.dp)) {
                    // Background arc
                    drawArc(
                        color = accent.copy(alpha = 0.12f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 5f, cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                    // Progress arc
                    drawArc(
                        color = accent,
                        startAngle = 135f,
                        sweepAngle = 270f * progress,
                        useCenter = false,
                        style = Stroke(width = 5f, cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                }
                Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeWhite)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = Color(0xFF777799))
        }
    }
}

private data class ActionItem(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun ActionCard(action: ActionItem, modifier: Modifier) {
    Card(
        onClick = action.onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12121E)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = action.accent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(action.emoji, fontSize = 22.sp)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(action.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = BeeWhite)
                Text(action.subtitle, fontSize = 10.sp, color = Color(0xFF666688), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SystemChip(icon: String, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(5.dp))
            Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RecentChatCard(
    agentIcon: String,
    agentName: String,
    lastMessage: String,
    timestamp: Long,
    messageCount: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12121E)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = BeeYellow.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text(agentIcon, fontSize = 20.sp) }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agentName, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xFFE0E0E0))
                Text(lastMessage, fontSize = 12.sp, color = Color(0xFF555577),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                val diff = System.currentTimeMillis() - timestamp
                val timeText = when {
                    diff < 60_000 -> "ahora"
                    diff < 3600_000 -> "${diff / 60_000}m"
                    diff < 86400_000 -> "${diff / 3600_000}h"
                    else -> "${diff / 86400_000}d"
                }
                Text(timeText, fontSize = 11.sp, color = Color(0xFF555577))
                if (messageCount > 0) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(color = BeeYellow.copy(alpha = 0.15f), shape = CircleShape) {
                        Text("$messageCount", fontSize = 10.sp, color = BeeYellow,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentChip(icon: String, name: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12121E)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(110.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = BeeYellow.copy(alpha = 0.08f),
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text(icon, fontSize = 24.sp) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(name, fontSize = 12.sp, color = Color(0xFFB0B0CC),
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
