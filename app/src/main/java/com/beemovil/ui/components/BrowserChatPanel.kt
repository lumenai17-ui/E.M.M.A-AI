package com.beemovil.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.agent.TaskStatus
import com.beemovil.ui.theme.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

// Bee tokens for browser panel
private val PanelBg = Color(0xFF141428)
private val PanelBorder = Color(0xFF2A2A4E)
private val AgentBubble = Color(0xFF1E1E3F)
private val UserBubble = Color(0xFF2A3A2A)
private val StatusGreen = Color(0xFF4CAF50)
private val StatusRed = Color(0xFFF44336)
private val StatusYellow = Color(0xFFD4A843)
private val StatusBlue = Color(0xFF2196F3)

/**
 * BrowserChatPanel — Bottom sheet chat panel for the browser agent.
 *
 * Features:
 * - Message list (user <-> agent) with auto-scroll
 * - Text input + send button
 * - Quick action chips
 * - Model selector dropdown
 * - STOP button when agent is active
 * - Status indicator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserChatPanel(
    messages: List<BrowserChatMessage>,
    agentStatus: TaskStatus,
    statusText: String,
    currentModel: String,
    availableModels: List<String>,
    onSendMessage: (String) -> Unit,
    onQuickAction: (String) -> Unit,
    onStopAgent: () -> Unit,
    onResumeAgent: () -> Unit,
    onModelChange: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelBg, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        // ── Handle bar (drag indicator) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() }
                .padding(top = 8.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(StatusYellow)
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown, 
                    contentDescription = "Ocultar",
                    tint = StatusYellow.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ── Header: Status + Model + Close ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            val statusColor = when (agentStatus) {
                TaskStatus.RUNNING -> StatusGreen
                TaskStatus.PAUSED_NEED_HELP, TaskStatus.PAUSED_LOOP -> StatusYellow
                TaskStatus.COMPLETED -> StatusBlue
                TaskStatus.FAILED, TaskStatus.CANCELLED -> StatusRed
                else -> BeeGray
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Status text
            Text(
                statusText.take(40),
                fontSize = 12.sp,
                color = BeeGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // STOP button (visible when agent is running)
            if (agentStatus == TaskStatus.RUNNING) {
                FilledTonalButton(
                    onClick = onStopAgent,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StatusRed.copy(alpha = 0.2f),
                        contentColor = StatusRed
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Filled.Stop, "Stop", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Resume button (visible when paused)
            if (agentStatus == TaskStatus.PAUSED_NEED_HELP || agentStatus == TaskStatus.PAUSED_LOOP) {
                FilledTonalButton(
                    onClick = onResumeAgent,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StatusGreen.copy(alpha = 0.2f),
                        contentColor = StatusGreen
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, "Resume", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Listo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Close button
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "Close", tint = BeeGray, modifier = Modifier.size(16.dp))
            }
        }

        Divider(color = PanelBorder, thickness = 0.5.dp)

        // ── Messages list ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages) { msg ->
                BrowserMessageBubble(msg)
            }
        }

        // ── Quick action chips ──
        if (agentStatus == TaskStatus.IDLE || agentStatus == TaskStatus.COMPLETED) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickChip("Leer pagina") { onQuickAction("read_page") }
                QuickChip("Screenshot") { onQuickAction("screenshot") }
                QuickChip("Links") { onQuickAction("extract_links") }
                QuickChip("Elementos") { onQuickAction("get_elements") }
            }
        }

        // ── Input bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        if (agentStatus == TaskStatus.PAUSED_NEED_HELP) "Describe que hiciste..."
                        else "Instruccion para el agente...",
                        color = BeeGray.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = BeeWhite),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StatusYellow,
                    unfocusedBorderColor = PanelBorder,
                    cursorColor = StatusYellow,
                    focusedContainerColor = Color(0xFF1A1A30),
                    unfocusedContainerColor = Color(0xFF1A1A30)
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText.trim())
                        inputText = ""
                    }
                })
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(StatusYellow.copy(alpha = 0.15f))
            ) {
                Icon(
                    Icons.Filled.Send,
                    "Send",
                    tint = StatusYellow,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * BrowserMessageBubble — Single message in the browser chat.
 */
@Composable
private fun BrowserMessageBubble(message: BrowserChatMessage) {
    val isAgent = message.sender == MessageSender.AGENT
    val isSystem = message.sender == MessageSender.SYSTEM

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAgent || isSystem) Arrangement.Start else Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (message.sender) {
                    MessageSender.USER -> UserBubble
                    MessageSender.AGENT -> AgentBubble
                    MessageSender.SYSTEM -> PanelBorder.copy(alpha = 0.3f)
                }
            ),
            shape = RoundedCornerShape(
                topStart = if (isAgent) 4.dp else 16.dp,
                topEnd = if (isAgent) 16.dp else 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                
                // ── Image renderer ──
                if (message.imageUrl != null) {
                    val bitmap = remember(message.imageUrl) {
                        try {
                            BitmapFactory.decodeFile(message.imageUrl)?.asImageBitmap()
                        } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Screenshot",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (isSystem) {
                    Text(
                        message.text,
                        fontSize = 11.sp,
                        color = StatusYellow,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    SelectionContainer {
                        Text(
                            message.text,
                            fontSize = 13.sp,
                            color = if (isAgent) BeeWhite else Color(0xFFCCDDCC),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * QuickChip — Quick action button for common browser operations.
 */
@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = PanelBorder.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = BeeGray,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

// ── Data classes ──

data class BrowserChatMessage(
    val text: String,
    val sender: MessageSender,
    val imageUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
    USER,
    AGENT,
    SYSTEM
}
