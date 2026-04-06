package com.beemovil.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.R
import com.beemovil.agent.AgentConfig
import com.beemovil.agent.DefaultAgents
import com.beemovil.memory.ChatHistoryDB
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * ConversationsScreen — Premium agent list with cards, status, and create button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: ChatViewModel,
    chatHistoryDB: ChatHistoryDB?,
    onAgentClick: (String) -> Unit,
    onCreateAgent: () -> Unit,
    onEditAgent: (String) -> Unit,
    skillCount: Int = 35
) {
    val previews = remember { mutableStateListOf<ChatHistoryDB.ConversationPreview>() }
    val agents = viewModel.availableAgents
    val defaultIds = DefaultAgents.ALL.map { it.id }.toSet()
    val telegramStatus = viewModel.telegramBotStatus.value

    LaunchedEffect(agents.size) {
        chatHistoryDB?.let { db ->
            previews.clear()
            previews.addAll(db.getConversationPreviews())
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Header
        item {
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(BeeYellow.copy(alpha = 0.1f), BeeBlack))
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
                    Column {
                        Text("Mis Agentes", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = BeeWhite)
                        Text("${agents.size} agentes · $skillCount skills", fontSize = 12.sp, color = BeeGray)
                    }
                    FloatingActionButton(
                        onClick = onCreateAgent,
                        containerColor = BeeYellow,
                        contentColor = BeeBlack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Filled.Add, "Crear", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // Main agent (featured card)
        val mainAgent = agents.find { it.id == "main" }
        val mainPreview = previews.find { it.agentId == "main" }
        if (mainAgent != null) {
            item {
                FeaturedAgentCard(
                    agent = mainAgent,
                    preview = mainPreview,
                    telegramStatus = telegramStatus,
                    onClick = { onAgentClick("main") }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Default agents section
        val defaultAgents = agents.filter { it.id != "main" && it.id in defaultIds }
        if (defaultAgents.isNotEmpty()) {
            item {
                Text(
                    "AGENTES PREDEFINIDOS",
                    fontSize = 11.sp, color = BeeYellow,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            defaultAgents.forEach { agent ->
                item {
                    val preview = previews.find { it.agentId == agent.id }
                    AgentCard(
                        agent = agent,
                        preview = preview,
                        isCustom = false,
                        onClick = { onAgentClick(agent.id) },
                        onEdit = { onEditAgent(agent.id) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // Custom agents section
        val customAgents = agents.filter { it.id !in defaultIds }
        if (customAgents.isNotEmpty()) {
            item {
                Text(
                    "AGENTES PERSONALIZADOS",
                    fontSize = 11.sp, color = BeeYellow,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            customAgents.forEach { agent ->
                item {
                    val preview = previews.find { it.agentId == agent.id }
                    AgentCard(
                        agent = agent,
                        preview = preview,
                        isCustom = true,
                        onClick = { onAgentClick(agent.id) },
                        onEdit = { onEditAgent(agent.id) }
                    )
                }
            }
        }

        // Empty state for custom
        if (customAgents.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    onClick = onCreateAgent
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Add, "Add", tint = BeeYellow, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Crear agente personalizado", color = BeeYellow,
                            fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("Hasta 10 agentes custom con su propia personalidad",
                            color = BeeGray, fontSize = 12.sp)
                    }
                }
            }
        }

        // Footer
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Bee-Movil v4.3.3", fontSize = 11.sp, color = BeeGray)
            }
        }
    }
}

@Composable
private fun FeaturedAgentCard(
    agent: AgentConfig,
    preview: ChatHistoryDB.ConversationPreview?,
    telegramStatus: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.bee_agent_avatar),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(agent.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BeeWhite)
                    Text(agent.description, fontSize = 12.sp, color = BeeGray)
                }
                if (telegramStatus == "online") {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("TG", fontSize = 11.sp, color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }

            if (preview != null) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BeeGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        preview.lastMessage,
                        fontSize = 13.sp, color = Color(0xFFB0B0B0),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(formatTimestamp(preview.lastTimestamp), fontSize = 11.sp, color = BeeGray)
                }
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Surface(color = BeeYellow.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("${preview.messageCount} msgs", fontSize = 10.sp, color = BeeYellow,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(color = Color(0xFF2196F3).copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("35 skills", fontSize = 10.sp, color = Color(0xFF2196F3),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentCard(
    agent: AgentConfig,
    preview: ChatHistoryDB.ConversationPreview?,
    isCustom: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                color = Color(0xFF2A2A3E),
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SmartToy, agent.name, tint = HoneyGold, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(agent.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = BeeWhite)
                    if (isCustom) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = BeeGray.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp)) {
                            Text("custom", fontSize = 9.sp, color = BeeGray,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    preview?.lastMessage ?: agent.description,
                    fontSize = 12.sp,
                    color = if (preview != null) Color(0xFFB0B0B0) else BeeGray,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            // Right side
            Column(horizontalAlignment = Alignment.End) {
                if (preview != null) {
                    Text(formatTimestamp(preview.lastTimestamp), fontSize = 11.sp, color = BeeGray)
                    if (preview.messageCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(color = BeeYellow, shape = CircleShape) {
                            Text("${preview.messageCount}", fontSize = 10.sp, color = BeeBlack,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
                if (isCustom && onEdit != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, "Editar", tint = BeeGray, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "ahora"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> "${diff / 3600_000}h"
        diff < 172800_000 -> "ayer"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ts))
    }
}
