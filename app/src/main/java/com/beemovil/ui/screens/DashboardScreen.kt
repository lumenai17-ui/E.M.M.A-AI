package com.beemovil.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.BorderStroke
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

// iOS-inspired palette with high contrast
private val Honey = Color(0xFFFFB300)
private val HoneyLight = Color(0xFFFFD54F)
private val HoneyGlow = Color(0xFFFFF8E1)
private val CardBg = Color(0xFF1C1C2E)
private val ScreenBg = Color(0xFF0E0E16)
private val Border = Color(0xFF2E2E42)
private val TextPrimary = Color(0xFFFFFFFF)       // Pure white — max readable
private val TextSecondary = Color(0xFFAAAAAA)      // Visible gray
private val TextTertiary = Color(0xFF777790)
private val AccentGreen = Color(0xFF34C759)        // iOS green
private val AccentBlue = Color(0xFF0A84FF)         // iOS blue
private val AccentPink = Color(0xFFFF2D55)         // iOS pink
private val AccentOrange = Color(0xFFFF9500)        // iOS orange
private val AccentPurple = Color(0xFFBF5AF2)       // iOS purple
private val AccentTeal = Color(0xFF5AC8FA)

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
    val now = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es")).format(Date()).replaceFirstChar { it.uppercase() } }
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
            .background(ScreenBg),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // ─── HEADER ──────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF161616), Color(0xFF111116), ScreenBg)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 24.dp)
            ) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bee_logo),
                        contentDescription = "Bee-Movil",
                        modifier = Modifier.size(38.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bee-Movil", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                        Text("$skillCount skills activos", fontSize = 11.sp, color = TextTertiary)
                    }
                    // Status indicator
                    Surface(
                        color = if (viewModel.hasApiKey()) AccentGreen.copy(alpha = 0.15f) else AccentPink.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(6.dp).clip(CircleShape)
                                    .background(if (viewModel.hasApiKey()) AccentGreen else AccentPink)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (viewModel.hasApiKey()) "AI activa" else "Sin API",
                                fontSize = 10.sp, color = if (viewModel.hasApiKey()) AccentGreen else AccentPink,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Settings, "Settings", tint = TextTertiary, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(greeting, fontSize = 15.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(now, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = TextPrimary, lineHeight = 34.sp)
            }
        }

        // ─── STATS ──────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("$skillCount", "Skills", Icons.Outlined.Build, Honey,
                    minOf(skillCount / 40f, 1f), Modifier.weight(1f))
                StatCard("${totalMessages.value}", "Mensajes", Icons.Outlined.ChatBubbleOutline, AccentGreen,
                    minOf(totalMessages.value / 100f, 1f), Modifier.weight(1f))
                StatCard("${memoryCount.value}", "Memorias", Icons.Outlined.Psychology, AccentPurple,
                    minOf(memoryCount.value / 20f, 1f), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ─── HERO FEATURES ──────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroCard(
                        Icons.Filled.SmartToy, "Chat AI", "Tu asistente inteligente",
                        Honey, listOf(Color(0xFF242010), Color(0xFF1C1C2E)),
                        Modifier.weight(1.3f)
                    ) { onAgentClick("main") }
                    HeroCard(
                        Icons.Filled.Mic, "Voz", "Hands-free",
                        AccentGreen, listOf(Color(0xFF102418), Color(0xFF1C1C2E)),
                        Modifier.weight(0.7f)
                    ) { viewModel.currentScreen.value = "voice_chat" }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroCard(
                        Icons.Filled.CameraAlt, "Visión AI", "Gemma 4 · Analiza fotos",
                        AccentPink, listOf(Color(0xFF241014), Color(0xFF1C1C2E)),
                        Modifier.weight(1f)
                    ) { viewModel.currentScreen.value = "camera" }
                    HeroCard(
                        Icons.Filled.Videocam, "Live", "Visión en vivo",
                        AccentOrange, listOf(Color(0xFF241C10), Color(0xFF1C1C2E)),
                        Modifier.weight(1f)
                    ) { viewModel.currentScreen.value = "live_vision" }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ─── TOOLS ──────────────────────────────
        item {
            Section("Herramientas")
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(Icons.Outlined.Email, "Correo", AccentBlue, Modifier.weight(1f)) { viewModel.currentScreen.value = "email_inbox" }
                    ToolCard(Icons.Outlined.Search, "Investigar", AccentOrange, Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("main", "Busca las últimas noticias de tecnología") }
                    ToolCard(Icons.Outlined.PictureAsPdf, "PDF", AccentPink, Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("main", "Hazme un PDF resumen ejecutivo sobre inteligencia artificial") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(Icons.Outlined.CalendarMonth, "Agenda", AccentPurple, Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("agenda", "¿Qué tengo programado hoy?") }
                    ToolCard(Icons.Outlined.Language, "Landing", AccentTeal, Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("main", "Crea una landing page moderna para un café artesanal") }
                    ToolCard(Icons.Outlined.TableChart, "Excel", AccentGreen, Modifier.weight(1f)) { viewModel.openAgentChatWithPrompt("main", "Hazme un spreadsheet comparativo de precios") }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ─── SYSTEM ─────────────────────────────
        item {
            Section("Sistema")
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Border),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val bat = batteryLevel.value
                        val batColor = when { bat > 60 -> AccentGreen; bat > 20 -> Honey; else -> AccentPink }
                        Chip(Icons.Outlined.BatteryChargingFull, "${bat}%", batColor, Modifier.weight(1f))
                        Chip(Icons.Outlined.Wifi, "Online", AccentGreen, Modifier.weight(1f))
                        Chip(
                            Icons.Outlined.Send,
                            if (telegramStatus == "online") "TG" else "TG",
                            if (telegramStatus == "online") AccentGreen else TextTertiary,
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(
                        Brush.horizontalGradient(listOf(Color.Transparent, Border, Color.Transparent))
                    ))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Memory, "AI", tint = Honey, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(viewModel.getProviderDisplayName(), fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(color = Honey.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                            Text("$skillCount activos", fontSize = 11.sp, color = Honey,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ─── RECENTS ────────────────────────────
        if (recentChats.isNotEmpty()) {
            item {
                Section("Recientes")
                Spacer(modifier = Modifier.height(12.dp))
            }

            recentChats.forEach { preview ->
                item {
                    RecentChat(preview.agentIcon, preview.agentName, preview.lastMessage,
                        preview.lastTimestamp, preview.messageCount) { onAgentClick(preview.agentId) }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }

        // ─── AGENTS ─────────────────────────────
        item {
            Section("Agentes")
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                viewModel.availableAgents.forEach { agent ->
                    item {
                        AgentPill(agent.icon, agent.name) { onAgentClick(agent.id) }
                    }
                }
            }
        }

        // Footer
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.bee_logo), contentDescription = null,
                    modifier = Modifier.size(12.dp).clip(CircleShape), contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("Bee-Movil v3.7", fontSize = 10.sp, color = TextTertiary.copy(alpha = 0.5f))
            }
        }
    }
}

// ── Components ──────────────────────────────────

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
private fun Section(title: String) {
    Text(
        title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
private fun StatCard(value: String, label: String, icon: ImageVector, accent: Color, progress: Float, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.5.dp, Border.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(48.dp)) {
                    drawArc(accent.copy(alpha = 0.12f), 135f, 270f, false,
                        style = Stroke(4f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                    drawArc(accent, 135f, 270f * progress, false,
                        style = Stroke(4f, cap = StrokeCap.Round), size = Size(size.width, size.height))
                }
                Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, label, tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun HeroCard(icon: ImageVector, title: String, subtitle: String, accent: Color,
                     gradientColors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.15f)),
        modifier = modifier.height(100.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradientColors))
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = accent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, title, tint = accent, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                }
                Text(subtitle, fontSize = 12.sp, color = accent.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ToolCard(icon: ImageVector, label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.5.dp, Border.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, label, tint = accent, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(label, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, color: Color, modifier: Modifier) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, label, tint = color, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecentChat(agentIcon: String, agentName: String, lastMessage: String,
                       timestamp: Long, messageCount: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.5.dp, Border.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Honey.copy(alpha = 0.1f), shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(agentIcon, fontSize = 20.sp) }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agentName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(3.dp))
                Text(lastMessage, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                val diff = System.currentTimeMillis() - timestamp
                val timeText = when {
                    diff < 60_000 -> "ahora"; diff < 3600_000 -> "${diff / 60_000}m"
                    diff < 86400_000 -> "${diff / 3600_000}h"; else -> "${diff / 86400_000}d"
                }
                Text(timeText, fontSize = 11.sp, color = TextTertiary)
                if (messageCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(color = Honey.copy(alpha = 0.15f), shape = CircleShape) {
                        Text("$messageCount", fontSize = 10.sp, color = Honey, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentPill(icon: String, name: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.5.dp, Border.copy(alpha = 0.4f)),
        modifier = Modifier.width(100.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Honey.copy(alpha = 0.08f), shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(icon, fontSize = 22.sp) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(name, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
