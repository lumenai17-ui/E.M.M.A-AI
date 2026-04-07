package com.beemovil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.memory.BeeMemoryDB
import java.text.SimpleDateFormat
import java.util.*

// ── Local palette ──
private val BgDark = Color(0xFF0A0A0C)
private val Txt = Color(0xFFFFFFFF)
private val TxtSub = Color(0xFF9999AA)
private val TxtMuted = Color(0xFF666680)
private val Gold = Color(0xFFF5A623)

/**
 * ActionLogScreen — Timeline showing every skill the agent has executed.
 * Provides full visibility into agent behavior: what it did, when, and how long it took.
 */
@Composable
fun ActionLogScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { BeeMemoryDB(context) }
    var actions by remember { mutableStateOf(listOf<Map<String, String>>()) }
    var totalCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        actions = db.getRecentActions(100)
        totalCount = db.getActionCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // ━━━━ TOP BAR ━━━━
        Surface(
            color = Color(0xFF0D1117),
            shadowElevation = 4.dp
        ) {
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
                    Text("Agent Activity", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Txt)
                    Text(
                        "$totalCount acciones registradas",
                        fontSize = 12.sp,
                        color = TxtSub
                    )
                }
                Icon(Icons.Outlined.Timeline, "Timeline", tint = Gold, modifier = Modifier.size(24.dp))
            }
        }

        if (actions.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.History,
                        "No actions",
                        tint = TxtMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Sin actividad registrada",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = TxtMuted
                    )
                    Text(
                        "Las acciones del agente aparaceran aqui",
                        fontSize = 14.sp,
                        color = TxtMuted.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(actions) { action ->
                    ActionCard(action)
                }
            }
        }
    }
}

@Composable
private fun ActionCard(action: Map<String, String>) {
    val skillName = action["skill_name"] ?: "unknown"
    val agentId = action["agent_id"] ?: "main"
    val params = action["params"] ?: ""
    val result = action["result"] ?: ""
    val durationMs = (action["duration_ms"] ?: "0").toLongOrNull() ?: 0
    val timestamp = (action["timestamp"] ?: "0").toLongOrNull() ?: 0

    val timeStr = remember(timestamp) {
        if (timestamp > 0) {
            SimpleDateFormat("HH:mm • dd MMM", Locale("es")).format(Date(timestamp))
        } else "—"
    }

    val skillIcon = when {
        skillName.contains("email") -> Icons.Outlined.Email
        skillName.contains("calendar") -> Icons.Outlined.CalendarToday
        skillName.contains("web") -> Icons.Outlined.Language
        skillName.contains("file") || skillName.contains("pdf") || skillName.contains("html") -> Icons.Outlined.Description
        skillName.contains("image") || skillName.contains("camera") -> Icons.Outlined.Image
        skillName.contains("video") -> Icons.Outlined.Videocam
        skillName.contains("music") || skillName.contains("volume") -> Icons.Outlined.MusicNote
        skillName.contains("memory") -> Icons.Outlined.Psychology
        skillName.contains("notify") -> Icons.Outlined.Notifications
        skillName.contains("browser") -> Icons.Outlined.Public
        skillName.contains("git") -> Icons.Outlined.Code
        skillName.contains("drive") -> Icons.Outlined.Cloud
        skillName.contains("delegate") -> Icons.Outlined.SwapHoriz
        skillName.contains("weather") -> Icons.Outlined.WbSunny
        skillName.contains("alarm") -> Icons.Outlined.Alarm
        else -> Icons.Outlined.Build
    }

    val skillColor = when {
        skillName.contains("email") -> Color(0xFF4FC3F7)
        skillName.contains("drive") || skillName.contains("google") -> Color(0xFF4CAF50)
        skillName.contains("web") || skillName.contains("browser") -> Color(0xFFFF9800)
        skillName.contains("image") || skillName.contains("video") -> Color(0xFFE040FB)
        skillName.contains("file") || skillName.contains("pdf") -> Color(0xFFFF7043)
        skillName.contains("memory") -> Color(0xFF7B68EE)
        else -> Gold
    }

    Surface(
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Timeline dot + icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(skillColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(skillIcon, skillName, tint = skillColor, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        skillName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Txt
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "${durationMs}ms",
                        fontSize = 11.sp,
                        color = when {
                            durationMs < 500 -> Color(0xFF4CAF50)
                            durationMs < 2000 -> Gold
                            else -> Color(0xFFFF5252)
                        },
                        fontWeight = FontWeight.Medium
                    )
                }

                if (params.isNotBlank() && params != "{}") {
                    Text(
                        params.take(100),
                        fontSize = 11.sp,
                        color = TxtMuted,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Agent badge
                    Surface(
                        color = Color(0xFF21262D),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            agentId,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            color = TxtMuted
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(timeStr, fontSize = 10.sp, color = TxtMuted)
                }
            }
        }
    }
}
