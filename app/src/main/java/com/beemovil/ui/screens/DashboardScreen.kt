package com.beemovil.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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

// Premium palette
private val Honey = Color(0xFFFFB300)
private val HoneyLight = Color(0xFFFFD54F)
private val HoneyDark = Color(0xFFFF8F00)
private val BeeCard = Color(0xFF111118)
private val BeeSurface = Color(0xFF0D0D14)
private val BeeText = Color(0xFFE8E8F0)
private val BeeSubtext = Color(0xFF6B6B80)
private val BeeAccentGreen = Color(0xFF66BB6A)
private val BeeAccentBlue = Color(0xFF42A5F5)
private val BeeAccentPink = Color(0xFFEC407A)
private val BeeAccentOrange = Color(0xFFFF7043)
private val BeeAccentPurple = Color(0xFFAB47BC)

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
            .background(BeeSurface),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // ─────────────────────────────────────────
        // HEADER
        // ─────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF18180A), Color(0xFF111110), BeeSurface)
                        )
                    )
            ) {
                // Ambient glow
                Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    drawCircle(Honey.copy(alpha = 0.04f), radius = 240f, center = Offset(size.width * 0.7f, 20f))
                    drawCircle(HoneyDark.copy(alpha = 0.03f), radius = 180f, center = Offset(size.width * 0.15f, 140f))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = 12.dp, bottom = 20.dp)
                ) {
                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.bee_logo),
                                contentDescription = "Bee-Movil",
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Bee-Movil", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BeeText)
                                Text("v3.7 • $skillCount skills", fontSize = 10.sp, color = BeeSubtext)
                            }
                        }
                        Row {
                            // Status dot
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (viewModel.hasApiKey()) BeeAccentGreen else Color(0xFFF44336))
                            )
                            IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Outlined.Settings, "Settings", tint = BeeSubtext, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Greeting
                    Text(greeting, fontSize = 13.sp, color = BeeSubtext, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(now, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = BeeText)
                }
            }
        }

        // ─────────────────────────────────────────
        // STATS ROW
        // ─────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    value = "$skillCount", label = "Skills",
                    icon = Icons.Outlined.Build, accent = Honey,
                    progress = minOf(skillCount / 40f, 1f),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = "${totalMessages.value}", label = "Mensajes",
                    icon = Icons.Outlined.ChatBubbleOutline, accent = BeeAccentGreen,
                    progress = minOf(totalMessages.value / 100f, 1f),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = "${memoryCount.value}", label = "Memorias",
                    icon = Icons.Outlined.Psychology, accent = BeeAccentPurple,
                    progress = minOf(memoryCount.value / 20f, 1f),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ─────────────────────────────────────────
        // HERO FEATURES
        // ─────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Row 1: Chat AI (wide) + Voice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroCard(
                        icon = Icons.Filled.SmartToy,
                        title = "Chat AI",
                        subtitle = "Tu asistente personal",
                        gradient = Brush.linearGradient(listOf(Color(0xFF1A1700), Color(0xFF141400))),
                        accent = Honey,
                        modifier = Modifier.weight(1.3f),
                        onClick = { onAgentClick("main") }
                    )
                    HeroCard(
                        icon = Icons.Filled.Mic,
                        title = "Voz",
                        subtitle = "Hands-free",
                        gradient = Brush.linearGradient(listOf(Color(0xFF0A1A0A), Color(0xFF0A140A))),
                        accent = BeeAccentGreen,
                        modifier = Modifier.weight(0.7f),
                        onClick = { viewModel.currentScreen.value = "voice_chat" }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Vision AI + Live
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroCard(
                        icon = Icons.Filled.CameraAlt,
                        title = "Visión AI",
                        subtitle = "Gemma 4",
                        gradient = Brush.linearGradient(listOf(Color(0xFF1A0A14), Color(0xFF140A10))),
                        accent = BeeAccentPink,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.currentScreen.value = "camera" }
                    )
                    HeroCard(
                        icon = Icons.Filled.Videocam,
                        title = "Live",
                        subtitle = "Reconocimiento en vivo",
                        gradient = Brush.linearGradient(listOf(Color(0xFF1A100A), Color(0xFF14080A))),
                        accent = BeeAccentOrange,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.currentScreen.value = "live_vision" }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ─────────────────────────────────────────
        // TOOLS
        // ─────────────────────────────────────────
        item {
            SectionHeader(Icons.Outlined.Widgets, "Herramientas")
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard(Icons.Outlined.Email, "Correo", BeeAccentBlue, Modifier.weight(1f)) { viewModel.currentScreen.value = "email_inbox" }
                    ToolCard(Icons.Outlined.Search, "Investigar", BeeAccentOrange, Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("main", "Busca las últimas noticias de tecnología") }
                    ToolCard(Icons.Outlined.PictureAsPdf, "PDF", Color(0xFFEF5350), Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("main", "Hazme un PDF resumen ejecutivo sobre inteligencia artificial") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard(Icons.Outlined.CalendarMonth, "Agenda", BeeAccentPurple, Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("agenda", "¿Qué tengo programado hoy?") }
                    ToolCard(Icons.Outlined.Language, "Landing", Color(0xFF26C6DA), Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("main", "Crea una landing page moderna para un café artesanal") }
                    ToolCard(Icons.Outlined.TableChart, "Excel", BeeAccentGreen, Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("main", "Hazme un spreadsheet comparativo de precios") }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ─────────────────────────────────────────
        // SYSTEM
        // ─────────────────────────────────────────
        item {
            SectionHeader(Icons.Outlined.SettingsInputAntenna, "Sistema")
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BeeCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Status chips
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val bat = batteryLevel.value
                        val batColor = when { bat > 60 -> BeeAccentGreen; bat > 20 -> Honey; else -> Color(0xFFF44336) }
                        StatusChip(Icons.Outlined.BatteryChargingFull, "${bat}%", batColor, Modifier.weight(1f))
                        StatusChip(Icons.Outlined.Wifi, "Online", BeeAccentGreen, Modifier.weight(1f))
                        StatusChip(
                            Icons.Outlined.Send,
                            if (telegramStatus == "online") "TG" else "TG",
                            if (telegramStatus == "online") BeeAccentGreen else BeeSubtext,
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Honey.copy(alpha = 0.15f), Color.Transparent)
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Provider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Memory, "AI", tint = Honey, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(viewModel.getProviderDisplayName(), fontSize = 13.sp, color = BeeText, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(color = Honey.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                            Text("$skillCount activos", fontSize = 10.sp, color = Honey,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ─────────────────────────────────────────
        // RECENT CHATS
        // ─────────────────────────────────────────
        if (recentChats.isNotEmpty()) {
            item {
                SectionHeader(Icons.Outlined.History, "Recientes")
                Spacer(modifier = Modifier.height(10.dp))
            }

            recentChats.forEach { preview ->
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

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // ─────────────────────────────────────────
        // AGENTS
        // ─────────────────────────────────────────
        item {
            SectionHeader(Icons.Outlined.Groups, "Agentes")
            Spacer(modifier = Modifier.height(10.dp))
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
            Spacer(modifier = Modifier.height(20.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.bee_logo),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bee-Movil v3.7 • Kotlin nativo", fontSize = 10.sp, color = Color(0xFF333344))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────
// COMPONENTS
// ─────────────────────────────────────────────────

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
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Honey)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(icon, title, tint = HoneyLight.copy(alpha = 0.5f), modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            color = BeeSubtext,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun StatCard(
    value: String, label: String, icon: ImageVector,
    accent: Color, progress: Float, modifier: Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BeeCard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(50.dp)) {
                    drawArc(
                        color = accent.copy(alpha = 0.1f),
                        startAngle = 135f, sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                    drawArc(
                        color = accent,
                        startAngle = 135f, sweepAngle = 270f * progress,
                        useCenter = false,
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                }
                Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BeeText)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, label, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(11.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(label, fontSize = 10.sp, color = BeeSubtext)
            }
        }
    }
}

@Composable
private fun HeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradient: Brush,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.height(96.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .then(
                    Modifier.background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.06f), Color.Transparent),
                            center = Offset(200f, 30f),
                            radius = 200f
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, title, tint = accent, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeText)
                }
                Text(subtitle, fontSize = 11.sp, color = accent.copy(alpha = 0.6f))
            }

            // Accent bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(2.5.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
private fun ToolCard(icon: ImageVector, label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = BeeCard),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = accent.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, label, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 11.sp, color = BeeSubtext, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatusChip(icon: ImageVector, label: String, color: Color, modifier: Modifier) {
    Surface(
        color = color.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, label, tint = color, modifier = Modifier.size(14.dp))
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
        colors = CardDefaults.cardColors(containerColor = BeeCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Honey.copy(alpha = 0.08f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text(agentIcon, fontSize = 18.sp) }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agentName, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = BeeText)
                Spacer(modifier = Modifier.height(2.dp))
                Text(lastMessage, fontSize = 12.sp, color = BeeSubtext,
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
                Text(timeText, fontSize = 10.sp, color = BeeSubtext)
                if (messageCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(color = Honey.copy(alpha = 0.15f), shape = CircleShape) {
                        Text("$messageCount", fontSize = 10.sp, color = Honey,
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
        colors = CardDefaults.cardColors(containerColor = BeeCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Honey.copy(alpha = 0.06f),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(name, fontSize = 11.sp, color = BeeSubtext,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
