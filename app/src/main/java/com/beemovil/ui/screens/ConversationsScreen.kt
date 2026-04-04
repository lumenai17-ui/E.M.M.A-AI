package com.beemovil.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.R
import com.beemovil.agent.DefaultAgents
import com.beemovil.memory.ChatHistoryDB
import com.beemovil.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * ConversationsScreen — WhatsApp-style list of agent chats.
 * Shows all agents with their last message and timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    chatHistoryDB: ChatHistoryDB?,
    onAgentClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    skillCount: Int = 25
) {
    val previews = remember { mutableStateListOf<ChatHistoryDB.ConversationPreview>() }
    val agents = DefaultAgents.ALL

    // Load previews
    LaunchedEffect(Unit) {
        chatHistoryDB?.let { db ->
            previews.clear()
            previews.addAll(db.getConversationPreviews())
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = BeeBlack,
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(BeeBlack, BeeBlack.copy(alpha = 0.95f))
                    )
                )
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.bee_logo),
                                contentDescription = "Bee Logo",
                                modifier = Modifier.size(36.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Bee-Movil",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = BeeWhite
                                )
                                Text(
                                    "$skillCount skills activos",
                                    fontSize = 11.sp,
                                    color = BeeGrayLight
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BeeBlack,
                        titleContentColor = BeeWhite
                    ),
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, "Settings", tint = BeeYellow)
                        }
                    }
                )
            }
        },
        containerColor = BeeBlack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            // All agents
            items(agents) { agentConfig ->
                val preview = previews.find { it.agentId == agentConfig.id }
                AgentChatRow(
                    icon = agentConfig.icon,
                    name = agentConfig.name,
                    description = agentConfig.description,
                    lastMessage = preview?.lastMessage,
                    timestamp = preview?.lastTimestamp,
                    messageCount = preview?.messageCount ?: 0,
                    isMainAgent = agentConfig.id == "main",
                    onClick = { onAgentClick(agentConfig.id) }
                )
            }

            // Stats footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                val totalMessages = previews.sumOf { it.messageCount }
                Card(
                    colors = CardDefaults.cardColors(containerColor = BeeBlackLight),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🐝 Bee-Movil v2.0", fontSize = 14.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "$skillCount skills · $totalMessages mensajes · ${agents.size} agentes",
                            fontSize = 12.sp, color = BeeGrayLight
                        )
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun AgentChatRow(
    icon: String,
    name: String,
    description: String,
    lastMessage: String?,
    timestamp: Long?,
    messageCount: Int,
    isMainAgent: Boolean,
    onClick: () -> Unit
) {
    val timeText = timestamp?.let { formatTimestamp(it) } ?: ""

    Surface(
        onClick = onClick,
        color = BeeBlack,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Agent avatar
            Surface(
                color = if (isMainAgent) BeeYellow.copy(alpha = 0.2f) else BeeGray.copy(alpha = 0.3f),
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isMainAgent) {
                        Image(
                            painter = painterResource(id = R.drawable.bee_logo),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(icon, fontSize = 26.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Name + last message
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = BeeWhite
                    )
                    if (timeText.isNotBlank()) {
                        Text(
                            text = timeText,
                            fontSize = 11.sp,
                            color = BeeGrayLight
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lastMessage ?: description,
                    fontSize = 13.sp,
                    color = if (lastMessage != null) BeeGrayLight else BeeGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Message count badge
            if (messageCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = BeeYellow.copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (messageCount > 99) "99+" else "$messageCount",
                            fontSize = 9.sp,
                            color = BeeYellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Divider
    HorizontalDivider(
        modifier = Modifier.padding(start = 82.dp),
        color = BeeGray.copy(alpha = 0.2f),
        thickness = 0.5.dp
    )
}

private fun formatTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts

    return when {
        diff < 60_000 -> "ahora"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        diff < 172800_000 -> "ayer"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ts))
    }
}
