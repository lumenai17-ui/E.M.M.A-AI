package com.beemovil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.agent.AgentConfig
import com.beemovil.agent.CustomAgentDB
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*

private val ICON_OPTIONS = listOf(
    "💰", "📈", "🏦", "🤑", "👨‍💼", "🔬", "📚", "🎯", "🛡️", "🌍",
    "🎮", "🎵", "✈️", "🏠", "🍕", "📷", "💊", "🏋️", "🚗", "⚡",
    "🤖", "🧪", "📊", "🎭", "🌐", "🔐", "📝", "🗂️", "☕", "🧑‍🔧"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCreatorScreen(
    viewModel: ChatViewModel,
    customAgentDB: CustomAgentDB,
    editAgentId: String? = null,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    // Load existing agent if editing
    val existingAgent = remember { editAgentId?.let { customAgentDB.getAgent(it) } }
    val isEditing = existingAgent != null

    var name by remember { mutableStateOf(existingAgent?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(existingAgent?.icon ?: "🤖") }
    var systemPrompt by remember { mutableStateOf(existingAgent?.systemPrompt ?: "") }
    var useGlobalModel by remember { mutableStateOf(existingAgent?.model.isNullOrBlank()) }
    var customModel by remember { mutableStateOf(existingAgent?.model ?: "qwen/qwen3.6-plus:free") }
    var temperature by remember { mutableStateOf(existingAgent?.temperature ?: 0.7f) }
    var useAllSkills by remember { mutableStateOf(existingAgent?.enabledTools?.contains("*") != false) }
    var telegramToken by remember { mutableStateOf(
        editAgentId?.let { customAgentDB.getTelegramToken(it) } ?: ""
    ) }
    var enableTelegram by remember { mutableStateOf(telegramToken.isNotBlank()) }

    val agentCount = remember { customAgentDB.getAgentCount() }
    var showError by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        TopAppBar(
            title = { Text(if (isEditing) "Editar Agente" else "Nuevo Agente", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1A1A2E),
                titleContentColor = BeeWhite
            )
        )

        Column(modifier = Modifier.padding(20.dp)) {

            // Name
            SectionLabel("NOMBRE")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                placeholder = { Text("Bee Finanzas", color = BeeGray) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Icon picker
            SectionLabel("ICONO")
            LazyVerticalGrid(
                columns = GridCells.Fixed(10),
                modifier = Modifier.height(120.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(ICON_OPTIONS.size) { index ->
                    val icon = ICON_OPTIONS[index]
                    val isSelected = icon == selectedIcon
                    Surface(
                        color = if (isSelected) BeeYellow.copy(alpha = 0.2f) else Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                            .then(if (isSelected) Modifier.border(2.dp, BeeYellow, CircleShape) else Modifier)
                            .clickable { selectedIcon = icon }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(icon, fontSize = 18.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // System prompt
            SectionLabel("PERSONALIDAD")
            Text("Instrucciones para definir el comportamiento del agente", fontSize = 11.sp, color = BeeGray)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                placeholder = { Text("Eres un experto en finanzas personales. Ayudas con presupuestos, ahorro e inversiones...", color = BeeGray) },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                maxLines = 8,
                colors = fieldColors()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Model
            SectionLabel("MODELO LLM")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = useGlobalModel,
                    onClick = { useGlobalModel = true },
                    colors = RadioButtonDefaults.colors(selectedColor = BeeYellow)
                )
                Text("Usar modelo global", color = BeeWhite, fontSize = 14.sp,
                    modifier = Modifier.clickable { useGlobalModel = true })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = !useGlobalModel,
                    onClick = { useGlobalModel = false },
                    colors = RadioButtonDefaults.colors(selectedColor = BeeYellow)
                )
                Text("Modelo específico", color = BeeWhite, fontSize = 14.sp,
                    modifier = Modifier.clickable { useGlobalModel = false })
            }

            if (!useGlobalModel) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = customModel,
                    onValueChange = { customModel = it },
                    label = { Text("Modelo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Temperature
            SectionLabel("TEMPERATURA: ${"%.1f".format(temperature)}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Preciso", fontSize = 11.sp, color = BeeGray)
                Text("Creativo", fontSize = 11.sp, color = BeeGray)
            }
            Slider(
                value = temperature,
                onValueChange = { temperature = (it * 10).toInt() / 10f },
                valueRange = 0f..1.5f,
                colors = SliderDefaults.colors(
                    thumbColor = BeeYellow,
                    activeTrackColor = BeeYellow,
                    inactiveTrackColor = BeeGray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Skills
            SectionLabel("SKILLS")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = useAllSkills,
                    onCheckedChange = { useAllSkills = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BeeBlack,
                        checkedTrackColor = BeeYellow
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (useAllSkills) "Todos los skills (25)" else "Skills limitados",
                    color = BeeWhite, fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Telegram (optional)
            SectionLabel("TELEGRAM (OPCIONAL)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = enableTelegram,
                    onCheckedChange = { enableTelegram = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BeeBlack,
                        checkedTrackColor = BeeYellow
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Conectar bot de Telegram", color = BeeWhite, fontSize = 14.sp)
            }
            if (enableTelegram) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = telegramToken,
                    onValueChange = { telegramToken = it },
                    label = { Text("Bot Token (@BotFather)") },
                    placeholder = { Text("123456:ABC...", color = BeeGray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error
            if (showError.isNotBlank()) {
                Text(showError, color = Color(0xFFF44336), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Save button
            Button(
                onClick = {
                    when {
                        name.isBlank() -> showError = "⚠️ Escribe un nombre"
                        systemPrompt.isBlank() -> showError = "⚠️ Escribe la personalidad"
                        else -> {
                            val agentId = existingAgent?.id ?: "custom_${System.currentTimeMillis()}"
                            val config = AgentConfig(
                                id = agentId,
                                name = name,
                                icon = selectedIcon,
                                description = "$name — agente personalizado",
                                systemPrompt = systemPrompt,
                                enabledTools = if (useAllSkills) setOf("*") else setOf("calculator", "memory", "datetime"),
                                model = if (useGlobalModel) "" else customModel,
                                temperature = temperature
                            )
                            val token = if (enableTelegram) telegramToken else ""
                            val saved = customAgentDB.saveAgent(config, token)
                            if (saved) {
                                viewModel.reloadCustomAgents(customAgentDB)
                                onSaved()
                            } else {
                                showError = "⚠️ Máximo ${ CustomAgentDB.MAX_CUSTOM_AGENTS} agentes alcanzado"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isEditing) "💾  Guardar Cambios" else "💾  Crear Agente",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${if (isEditing) agentCount else agentCount}/${CustomAgentDB.MAX_CUSTOM_AGENTS} agentes personalizados",
                fontSize = 11.sp, color = BeeGray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (isEditing) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        editAgentId?.let { customAgentDB.deleteAgent(it) }
                        viewModel.reloadCustomAgents(customAgentDB)
                        onBack()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🗑️  Eliminar Agente")
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 11.sp, color = BeeYellow,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BeeYellow,
    unfocusedBorderColor = Color(0xFF333355),
    focusedTextColor = BeeWhite,
    unfocusedTextColor = BeeWhite,
    focusedLabelColor = BeeYellow,
    unfocusedLabelColor = BeeGrayLight,
    cursorColor = BeeYellow
)
