package com.beemovil.ui.screens

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.beemovil.memory.TaskDB
import com.beemovil.memory.TaskPriority
import com.beemovil.memory.TaskStatus
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ── Palette ─────────────────────────────────────
private val Bg = Color(0xFF0A0A0C)
private val CardBg = Color(0xFF161622)
private val CardBorder = Color(0xFF222234)
private val Gold = Color(0xFFF5A623)
private val GoldDim = Color(0xFFD4850A)
private val Green = Color(0xFF34C759)
private val Blue = Color(0xFF0A84FF)
private val Purple = Color(0xFFBF5AF2)
private val Pink = Color(0xFFFF2D55)
private val Orange = Color(0xFFFF9500)
private val Teal = Color(0xFF5AC8FA)
private val Txt = Color(0xFFFFFFFF)
private val TxtSub = Color(0xFF9999AA)
private val TxtMuted = Color(0xFF666680)

@Composable
fun DashboardScreen(
    viewModel: ChatViewModel,
    chatHistoryDB: ChatHistoryDB?,
    memoryDB: BeeMemoryDB?,
    taskDB: TaskDB? = null,
    onAgentClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    skillCount: Int
) {
    val context = LocalContext.current
    val totalMessages = remember { mutableStateOf(0) }
    val recentChats = remember { mutableStateListOf<ChatHistoryDB.ConversationPreview>() }
    val batteryLevel = remember { mutableStateOf(getBatteryLevel(context)) }
    val memoryCount = remember { mutableStateOf(0) }

    // Read user name from prefs
    val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
    val userName = remember { prefs.getString("user_display_name", null) }

    // AI Activity Scanner — shows what AI is doing on load
    var scanPhase by remember { mutableStateOf(0) } // 0=scanning, 1=tasks, 2=memory, 3=done
    var scanText by remember { mutableStateOf("Escaneando sistema...") }
    val scanMessages = listOf(
        "Escaneando sistema...",
        "Revisando tareas pendientes...",
        "Cargando memoria del agente...",
        "Analizando conversaciones recientes...",
        "Dashboard listo ✓"
    )

    // Pull-to-refresh
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    fun refreshData() {
        refreshScope.launch {
            isRefreshing = true
            totalMessages.value = chatHistoryDB?.getTotalMessageCount() ?: 0
            batteryLevel.value = getBatteryLevel(context)
            memoryCount.value = memoryDB?.getMemoryCount() ?: 0
            chatHistoryDB?.let { db ->
                recentChats.clear()
                recentChats.addAll(db.getConversationPreviews().take(5))
            }
            delay(600)
            isRefreshing = false
        }
    }

    // Boot scan animation
    LaunchedEffect(Unit) {
        totalMessages.value = chatHistoryDB?.getTotalMessageCount() ?: 0
        batteryLevel.value = getBatteryLevel(context)
        memoryCount.value = memoryDB?.getMemoryCount() ?: 0
        chatHistoryDB?.let { db ->
            recentChats.clear()
            recentChats.addAll(db.getConversationPreviews().take(5))
        }
        // Animate scan phases
        for (i in scanMessages.indices) {
            scanPhase = i
            scanText = scanMessages[i]
            delay(if (i < scanMessages.size - 1) 500L else 800L)
        }
        scanPhase = scanMessages.size // done
    }

    val telegramStatus = viewModel.telegramBotStatus.value
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Buenos dias"
            hour < 18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    // Heartbeat animation
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartbeatPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "phase"
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // ━━━━ TOP BAR (IG-style) ━━━━━━━━━━━━━━━━━━━━
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App logo + name (left)
            Image(
                painter = painterResource(id = R.drawable.bee_agent_avatar),
                contentDescription = "Bee-Movil",
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Bee-Movil",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Txt
            )
            Spacer(modifier = Modifier.weight(1f))
            // Action icons (right) - IG style
            IconButton(onClick = { viewModel.currentScreen.value = "conversations" }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Forum, "Chats", tint = Txt, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Settings, "Settings", tint = Txt, modifier = Modifier.size(24.dp))
            }
        }

        // ━━━━ GREETING + USER NAME ━━━━━━━━━━━━━━━━━━
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp)
        ) {
            Text(
                greeting + if (!userName.isNullOrBlank()) ", $userName" else "",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Txt,
                lineHeight = 34.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            val now = remember { SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(Date()).replaceFirstChar { it.uppercase() } }
            Text(now, fontSize = 15.sp, color = TxtSub)

            // AI-generated contextual insight
            LaunchedEffect(Unit) {
                if (viewModel.dashboardInsight.value.isBlank()) {
                    viewModel.generateDashboardInsight()
                }
            }
            val insight = viewModel.dashboardInsight.value
            if (insight.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, "AI", tint = Gold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        insight,
                        fontSize = 14.sp,
                        color = Gold.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (viewModel.dashboardInsightLoading.value) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, "AI", tint = TxtMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("...", fontSize = 14.sp, color = TxtMuted)
                }
            }

        // ━━━━ AI ACTIVITY SCANNER ━━━━━━━━━━━━━━━━━━
        if (scanPhase < scanMessages.size) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = Color(0xFF0D1117),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Gold,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        scanText,
                        fontSize = 13.sp,
                        color = Gold.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        }

        // ━━━━ AI STATUS CARD — Heartbeat Monitor ━━━━
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Provider + Model row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status LED
                    val isOnline = viewModel.hasApiKey()
                    val statusColor = if (isOnline) Green else Pink
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(statusColor, radius = 5f)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            viewModel.getProviderDisplayName(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Txt
                        )
                        Text(
                            viewModel.currentModel.value.substringAfterLast("/").take(25),
                            fontSize = 13.sp,
                            color = TxtSub
                        )
                    }
                    // Switch button
                    Surface(
                        onClick = onSettingsClick,
                        color = Gold.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.SwapHoriz, "Switch", tint = Gold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cambiar", fontSize = 13.sp, color = Gold, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Heartbeat Graph ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0D14))
                ) {
                    val isActive = viewModel.hasApiKey()
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        val w = size.width
                        val h = size.height
                        val mid = h / 2
                        val path = Path()

                        // Draw heartbeat line
                        val lineColor = if (isActive) Green else Pink.copy(alpha = 0.5f)
                        val offset = heartbeatPhase * w * 0.3f

                        path.moveTo(-offset, mid)
                        var x = -offset
                        while (x < w + 20f) {
                            val segmentWidth = w * 0.15f
                            // Flat line
                            path.lineTo(x + segmentWidth * 0.4f, mid)
                            // Small bump
                            path.lineTo(x + segmentWidth * 0.45f, mid - h * 0.1f)
                            path.lineTo(x + segmentWidth * 0.5f, mid)
                            // Big spike up
                            path.lineTo(x + segmentWidth * 0.55f, mid - h * 0.6f)
                            // Big spike down
                            path.lineTo(x + segmentWidth * 0.6f, mid + h * 0.3f)
                            // Return
                            path.lineTo(x + segmentWidth * 0.65f, mid)
                            // Small bump
                            path.lineTo(x + segmentWidth * 0.75f, mid - h * 0.15f)
                            path.lineTo(x + segmentWidth * 0.8f, mid)
                            // Flat
                            path.lineTo(x + segmentWidth, mid)
                            x += segmentWidth
                        }
                        drawPath(path, lineColor, style = Stroke(2.5f, cap = StrokeCap.Round))
                    }

                    // Health label
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bat = batteryLevel.value
                        val health = when {
                            !viewModel.hasApiKey() -> "Offline"
                            bat > 60 -> "Excelente"
                            bat > 30 -> "Buena"
                            else -> "Baja"
                        }
                        val healthColor = when {
                            !viewModel.hasApiKey() -> Pink
                            bat > 60 -> Green
                            bat > 30 -> Orange
                            else -> Pink
                        }
                        Icon(Icons.Filled.FavoriteBorder, "Health", tint = healthColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(health, fontSize = 11.sp, color = healthColor, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickStat(Icons.Outlined.Bolt, "$skillCount", "Skills", Gold)
                    QuickStat(Icons.Outlined.ChatBubble, "${totalMessages.value}", "Chats", Blue)
                    QuickStat(Icons.Outlined.Memory, "${memoryCount.value}", "Memorias", Purple)
                    QuickStat(Icons.Outlined.Battery5Bar, "${batteryLevel.value}%", "Bateria", 
                        if (batteryLevel.value > 60) Green else if (batteryLevel.value > 30) Orange else Pink)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ━━━━ AGENTS (IG Stories-style) ━━━━━━━━━━━━
        Text(
            "Agentes",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Txt,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 0.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            viewModel.availableAgents.forEach { agent ->
                item {
                    AgentStory(
                        icon = agentIconToMaterial(agent.icon),
                        color = agentIconToColor(agent.icon),
                        name = agent.name,
                        onClick = { onAgentClick(agent.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ━━━━ QUICK ACTIONS (2x4 grid) ━━━━━━━━━━━━
        Text(
            "Acciones",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Txt,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard(Icons.Filled.SmartToy, "Chat AI", Gold, Modifier.weight(1f)) { onAgentClick("main") }
                ActionCard(Icons.Filled.Mic, "Voz", Green, Modifier.weight(1f)) { viewModel.currentScreen.value = "voice_chat" }
                ActionCard(Icons.Filled.CameraAlt, "Camara", Pink, Modifier.weight(1f)) { viewModel.currentScreen.value = "camera" }
                ActionCard(Icons.Filled.Videocam, "Live", Orange, Modifier.weight(1f)) { viewModel.currentScreen.value = "live_vision" }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard(Icons.Filled.Language, "Web", Blue, Modifier.weight(1f)) { viewModel.currentScreen.value = "browser" }
                ActionCard(Icons.Filled.Email, "Correo", Teal, Modifier.weight(1f)) { viewModel.currentScreen.value = "email_inbox" }
                ActionCard(Icons.Filled.Folder, "Archivos", Purple, Modifier.weight(1f)) { viewModel.currentScreen.value = "file_explorer" }
                ActionCard(Icons.Filled.AccountTree, "Workflows", Color(0xFF4DD0E1), Modifier.weight(1f)) { viewModel.currentScreen.value = "workflows" }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard(Icons.Filled.Notifications, "Notif", Color(0xFFFF6B6B), Modifier.weight(1f)) { viewModel.currentScreen.value = "notification_dashboard" }
                ActionCard(Icons.Filled.TaskAlt, "Tareas", Gold, Modifier.weight(1f)) { viewModel.currentScreen.value = "tasks" }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ━━━━ TOOLS (quick prompts) ━━━━━━━━━━━━━━━
        Text(
            "Herramientas",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Txt,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val tools = listOf(
                Triple(Icons.Outlined.PictureAsPdf, "Crear PDF", "Hazme un PDF sobre: "),
                Triple(Icons.Outlined.Search, "Investigar", "Investiga sobre: "),
                Triple(Icons.Outlined.Web, "Landing Page", "Crea una landing page para: "),
                Triple(Icons.Outlined.TableChart, "Excel", "Hazme un spreadsheet de: "),
                Triple(Icons.Outlined.Code, "Codigo", "Ayudame a escribir codigo en: "),
                Triple(Icons.Outlined.CalendarMonth, "Agenda", "Que tengo programado hoy?"),
                Triple(Icons.Outlined.Architecture, "Deploy", "Quiero publicar: ")
            )
            tools.forEach { (icon, label, prompt) ->
                item {
                    ToolChip(icon, label) {
                        viewModel.prefillAgentChat("main", prompt)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ---- PENDING TASKS WIDGET ----
        val pendingTasks = remember { taskDB?.getPendingTasks(3) ?: emptyList() }
        if (pendingTasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tareas pendientes", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Txt)
                Spacer(modifier = Modifier.weight(1f))
                val totalPending = taskDB?.getTaskCount() ?: 0
                if (totalPending > 3) {
                    Surface(
                        onClick = { viewModel.currentScreen.value = "tasks" },
                        color = Color.Transparent, shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Ver todas ($totalPending)", fontSize = 13.sp, color = Gold,
                            modifier = Modifier.padding(4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                pendingTasks.forEach { task ->
                    val pColor = when (task.priority) {
                        TaskPriority.URGENT -> Color(0xFFFF3B30)
                        TaskPriority.HIGH -> Color(0xFFFF9500)
                        TaskPriority.NORMAL -> Color(0xFF0A84FF)
                        TaskPriority.LOW -> TxtMuted
                    }
                    Surface(
                        onClick = { viewModel.currentScreen.value = "tasks" },
                        color = CardBg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp).height(28.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(pColor)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                if (task.status == TaskStatus.IN_PROGRESS) Icons.Filled.PlayCircle
                                else Icons.Outlined.RadioButtonUnchecked,
                                "Status",
                                tint = if (task.status == TaskStatus.IN_PROGRESS) Gold else TxtMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                task.title, fontSize = 14.sp, color = Txt,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (task.dueDate > 0) {
                                val diff = task.dueDate - System.currentTimeMillis()
                                val timeText = when {
                                    diff < 0 -> "Vencida"
                                    diff < 3600_000 -> "${diff / 60_000}m"
                                    diff < 86400_000 -> "${diff / 3600_000}h"
                                    else -> "${diff / 86400_000}d"
                                }
                                val isOverdue = diff < 0
                                Text(
                                    timeText, fontSize = 11.sp,
                                    color = if (isOverdue) Color(0xFFFF3B30) else TxtMuted
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }

        // ━━━━ RECENT CONVERSATIONS ━━━━━━━━━━━━━━━
        if (recentChats.isNotEmpty()) {
            Text(
                "Recientes",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Txt,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))

            recentChats.forEach { preview ->
                RecentChatRow(
                    icon = agentIconToMaterial(preview.agentIcon),
                    iconColor = agentIconToColor(preview.agentIcon),
                    agentName = preview.agentName,
                    lastMessage = preview.lastMessage,
                    timestamp = preview.lastTimestamp,
                    messageCount = preview.messageCount,
                    onClick = { onAgentClick(preview.agentId) }
                )
            }
        }

        // Footer
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Bee-Movil v4.9.0",
            fontSize = 12.sp,
            color = TxtMuted.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth().padding(bottom = 80.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    } // Column end
    // Pull-to-refresh indicator
    if (isRefreshing) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            color = Gold,
            trackColor = CardBg
        )
    }
    // Pull-down hint (swipe gesture)
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 4.dp)
            .clickable { refreshData() }
    ) {
        if (!isRefreshing) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                "Refresh",
                tint = TxtMuted.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
    } // Box end
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// COMPONENTS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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
private fun QuickStat(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Txt)
        Text(label, fontSize = 11.sp, color = TxtMuted)
    }
}

/** IG Stories-style agent circle */
@Composable
private fun AgentStory(icon: ImageVector, color: Color, name: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).width(72.dp)
    ) {
        // Ring + Icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp)
        ) {
            // Gradient ring (like IG stories)
            Canvas(modifier = Modifier.size(64.dp)) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(Gold, color, Gold)),
                    radius = size.minDimension / 2,
                    style = Stroke(3f)
                )
            }
            // Inner circle with icon
            Surface(
                color = color.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, name, tint = color, modifier = Modifier.size(26.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            name,
            fontSize = 12.sp,
            color = TxtSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Compact action card — Premium with gradient icon bg */
@Composable
private fun ActionCard(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.05f))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, color = TxtSub, fontWeight = FontWeight.Medium)
        }
    }
}

/** Scrollable tool chip */
@Composable
private fun ToolChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = CardBg,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, label, tint = Gold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = Txt, fontWeight = FontWeight.Medium)
        }
    }
}

/** Recent conversation row — premium glow up */
@Composable
private fun RecentChatRow(
    icon: ImageVector, iconColor: Color, agentName: String,
    lastMessage: String, timestamp: Long, messageCount: Int, onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Agent color accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(iconColor, iconColor.copy(alpha = 0.3f))))
            )
            Spacer(modifier = Modifier.width(12.dp))
            // Agent avatar
            Surface(color = iconColor.copy(alpha = 0.12f), shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, agentName, tint = iconColor, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(agentName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Txt)
                Spacer(modifier = Modifier.height(2.dp))
                Text(lastMessage, fontSize = 13.sp, color = TxtSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                val diff = System.currentTimeMillis() - timestamp
                val timeText = when {
                    diff < 60_000 -> "ahora"
                    diff < 3600_000 -> "${diff / 60_000}m"
                    diff < 86400_000 -> "${diff / 3600_000}h"
                    else -> "${diff / 86400_000}d"
                }
                Text(timeText, fontSize = 12.sp, color = TxtMuted)
                if (messageCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(color = Gold, shape = CircleShape) {
                        Text(
                            "$messageCount", fontSize = 11.sp, color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Emoji to Material Icon Mapping ──────────────
private fun agentIconToMaterial(emoji: String): ImageVector {
    return when (emoji) {
        "\uD83D\uDC1D" -> Icons.Filled.SmartToy
        "\uD83D\uDCBC" -> Icons.Filled.Work
        "\uD83D\uDCC5" -> Icons.Filled.CalendarMonth
        "\uD83C\uDFA8" -> Icons.Filled.Palette
        "\uD83D\uDD0D" -> Icons.Filled.Search
        "\uD83D\uDCE7" -> Icons.Filled.Email
        "\u2699\uFE0F" -> Icons.Filled.Settings
        "\uD83E\uDD16" -> Icons.Filled.SmartToy
        "\uD83D\uDCCA" -> Icons.Filled.Analytics
        "\uD83C\uDF10" -> Icons.Filled.Language
        "\uD83D\uDCF7" -> Icons.Filled.CameraAlt
        "\uD83D\uDCC4" -> Icons.Filled.Description
        else -> Icons.Filled.SmartToy
    }
}

private fun agentIconToColor(emoji: String): Color {
    return when (emoji) {
        "\uD83D\uDC1D" -> Gold
        "\uD83D\uDCBC" -> Blue
        "\uD83D\uDCC5" -> Green
        "\uD83C\uDFA8" -> Purple
        "\uD83D\uDD0D" -> Orange
        "\uD83D\uDCE7" -> Blue
        "\u2699\uFE0F" -> TxtSub
        "\uD83E\uDD16" -> Teal
        "\uD83D\uDCCA" -> Teal
        else -> Gold
    }
}
