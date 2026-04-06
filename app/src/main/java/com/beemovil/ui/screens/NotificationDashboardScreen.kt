package com.beemovil.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.memory.NotificationEntry
import com.beemovil.memory.NotificationLogDB
import com.beemovil.service.BeeNotificationService
import com.beemovil.ui.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

// Premium palette
private val Bg = Color(0xFF08080A)
private val CardBg = Color(0xFF111118)
private val CardBorder = Color(0xFF1C1C2E)
private val Gold = Color(0xFFF5A623)
private val Txt = Color(0xFFF2F2F7)
private val TxtSub = Color(0xFF8E8E9A)
private val TxtMuted = Color(0xFF555566)
private val Green = Color(0xFF34C759)
private val Blue = Color(0xFF0A84FF)
private val Orange = Color(0xFFFF9500)
private val Red = Color(0xFFFF3B30)
private val Purple = Color(0xFFBF5AF2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDashboardScreen(
    viewModel: ChatViewModel,
    notifDB: NotificationLogDB,
    onBack: () -> Unit,
    onConfigClick: () -> Unit
) {
    val context = LocalContext.current
    val isEnabled = remember { BeeNotificationService.isServiceEnabled(context) }
    var showingView by remember { mutableStateOf("feed") } // "feed" or "stats"
    var selectedApp by remember { mutableStateOf<String?>(null) }
    var notifications by remember { mutableStateOf(notifDB.getRecent(100)) }
    val todayCount = remember { notifDB.getTodayCount() }
    val appStats = remember { notifDB.getAppStats(8) }

    fun refresh() {
        notifications = if (selectedApp != null) {
            notifDB.getByApp(selectedApp!!, 100)
        } else {
            notifDB.getRecent(100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // ── Top Bar ──
        Surface(color = CardBg) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = Txt)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notificaciones", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Txt)
                        Text(
                            "$todayCount hoy",
                            fontSize = 13.sp, color = TxtSub
                        )
                    }
                    // Toggle view
                    IconButton(onClick = {
                        showingView = if (showingView == "feed") "stats" else "feed"
                    }) {
                        Icon(
                            if (showingView == "feed") Icons.Filled.BarChart else Icons.Filled.ViewList,
                            "Toggle",
                            tint = Gold
                        )
                    }
                    IconButton(onClick = onConfigClick) {
                        Icon(Icons.Filled.Settings, "Config", tint = TxtSub)
                    }
                }

                // Permission banner
                if (!isEnabled) {
                    Surface(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        color = Orange.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, "Warning", tint = Orange, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Acceso no activado", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Orange)
                                Text("Toca para activar la captura de notificaciones", fontSize = 12.sp, color = TxtSub)
                            }
                            Icon(Icons.Filled.ChevronRight, "Go", tint = Orange, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // App filter chips
                if (appStats.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { selectedApp = null; refresh() },
                            color = if (selectedApp == null) Gold.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "Todas", fontSize = 12.sp,
                                color = if (selectedApp == null) Gold else TxtMuted,
                                fontWeight = if (selectedApp == null) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        appStats.take(4).forEach { stat ->
                            val shortName = stat.appName.take(10)
                            Surface(
                                onClick = { selectedApp = stat.packageName; refresh() },
                                color = if (selectedApp == stat.packageName) Blue.copy(alpha = 0.15f) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    shortName, fontSize = 12.sp,
                                    color = if (selectedApp == stat.packageName) Blue else TxtMuted,
                                    fontWeight = if (selectedApp == stat.packageName) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Content ──
        if (showingView == "stats") {
            // Stats view
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // Summary cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMiniCard("Hoy", "$todayCount", Gold, Modifier.weight(1f))
                        StatMiniCard("Total", "${notifDB.getCount()}", Blue, Modifier.weight(1f))
                        StatMiniCard("Apps", "${appStats.size}", Purple, Modifier.weight(1f))
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Text("Top Apps", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Txt)
                }
                items(appStats) { stat ->
                    AppStatRow(stat.appName, stat.count, stat.lastTimestamp, appStats.maxOf { it.count })
                }
            }
        } else {
            // Feed view
            if (notifications.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.NotificationsOff, "Empty", tint = TxtMuted, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sin notificaciones capturadas", fontSize = 16.sp, color = TxtSub)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (!isEnabled) "Activa el acceso para comenzar" else "Las notificaciones apareceran aqui",
                        fontSize = 13.sp, color = TxtMuted
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(notifications) { entry ->
                        NotificationRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, color = TxtSub)
        }
    }
}

@Composable
private fun AppStatRow(appName: String, count: Int, lastTs: Long, maxCount: Int) {
    val fraction = count.toFloat() / maxCount.coerceAtLeast(1)
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(appName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Txt, modifier = Modifier.weight(1f))
                Text("$count", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Bar chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1A1A2E))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(Gold, Orange)))
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val timeAgo = formatTimeAgo(lastTs)
            Text("Ultima: $timeAgo", fontSize = 11.sp, color = TxtMuted)
        }
    }
}

@Composable
private fun NotificationRow(entry: NotificationEntry) {
    val appColor = getAppColor(entry.packageName)

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // App color dot
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(appColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.appName, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, color = appColor
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        formatTimeAgo(entry.timestamp),
                        fontSize = 11.sp, color = TxtMuted
                    )
                }
                if (entry.title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(entry.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = Txt, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (entry.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(entry.text, fontSize = 13.sp, color = TxtSub, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "ahora"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        else -> SimpleDateFormat("dd MMM", Locale("es")).format(Date(timestamp))
    }
}

private fun getAppColor(packageName: String): Color {
    return when {
        packageName.contains("whatsapp") -> Color(0xFF25D366)
        packageName.contains("instagram") -> Color(0xFFE1306C)
        packageName.contains("telegram") -> Color(0xFF0088CC)
        packageName.contains("twitter") || packageName.contains("x.") -> Color(0xFF1DA1F2)
        packageName.contains("facebook") -> Color(0xFF1877F2)
        packageName.contains("youtube") -> Color(0xFFFF0000)
        packageName.contains("gmail") || packageName.contains("google") -> Color(0xFF4285F4)
        packageName.contains("messenger") -> Color(0xFF006AFF)
        packageName.contains("tiktok") -> Color(0xFFEE1D52)
        packageName.contains("spotify") -> Color(0xFF1DB954)
        packageName.contains("uber") -> Color(0xFF000000)
        packageName.contains("bank") || packageName.contains("banc") -> Color(0xFFFF9500)
        else -> {
            // Hash-based color for unknown apps
            val hash = packageName.hashCode()
            Color(
                red = ((hash and 0xFF0000) shr 16) / 255f * 0.7f + 0.3f,
                green = ((hash and 0x00FF00) shr 8) / 255f * 0.7f + 0.3f,
                blue = (hash and 0x0000FF) / 255f * 0.7f + 0.3f,
                alpha = 1f
            )
        }
    }
}
