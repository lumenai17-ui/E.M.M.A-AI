package com.beemovil.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.llm.LlmFactory
import com.beemovil.telegram.TelegramBotService
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)

    var openRouterKey by remember { mutableStateOf(prefs.getString("openrouter_api_key", "") ?: "") }
    var ollamaKey by remember { mutableStateOf(prefs.getString("ollama_api_key", "") ?: "") }
    var showOrKey by remember { mutableStateOf(false) }
    var showOlKey by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(viewModel.currentProvider.value) }
    var selectedModel by remember { mutableStateOf(viewModel.currentModel.value) }

    // Telegram
    var telegramToken by remember { mutableStateOf(prefs.getString("telegram_bot_token", "") ?: "") }
    var showToken by remember { mutableStateOf(false) }
    val botStatus = viewModel.telegramBotStatus.value
    val botName = viewModel.telegramBotName.value

    // Telegram allowlist
    val allowedChatsStr = prefs.getString(TelegramBotService.PREF_ALLOWED_CHATS, "") ?: ""
    var telegramUsername by remember { mutableStateOf(prefs.getString("telegram_owner_username", "") ?: "") }

    // Register status callback
    DisposableEffect(Unit) {
        TelegramBotService.onStatusChange = { status, name, count ->
            viewModel.telegramBotStatus.value = status
            if (name.isNotBlank()) viewModel.telegramBotName.value = name
            viewModel.telegramBotMessages.value = count
        }
        onDispose { TelegramBotService.onStatusChange = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BeeBlack)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        TopAppBar(
            title = { Text("Configuración", fontWeight = FontWeight.Bold) },
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

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ═══════════════════════════════════════
            // PROVEEDOR AI
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("PROVEEDOR AI")
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderChip("OpenRouter", "openrouter", selectedProvider) { selectedProvider = it }
                    ProviderChip("Ollama Cloud", "ollama", selectedProvider) { selectedProvider = it }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // API Key
                val currentKey = if (selectedProvider == "openrouter") openRouterKey else ollamaKey
                val showKey = if (selectedProvider == "openrouter") showOrKey else showOlKey

                OutlinedTextField(
                    value = currentKey,
                    onValueChange = {
                        if (selectedProvider == "openrouter") openRouterKey = it else ollamaKey = it
                    },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (selectedProvider == "openrouter") showOrKey = !showOrKey else showOlKey = !showOlKey
                        }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                "Toggle", tint = BeeGrayLight
                            )
                        }
                    },
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status
                val hasKey = currentKey.isNotBlank()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(
                        if (hasKey) Color(0xFF4CAF50) else Color(0xFFF44336)
                    ))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (hasKey) "API Key configurada" else "Sin API Key",
                        fontSize = 12.sp, color = if (hasKey) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        val key = if (selectedProvider == "openrouter") openRouterKey.trim() else ollamaKey.trim()
                        val prefKey = if (selectedProvider == "openrouter") "openrouter_api_key" else "ollama_api_key"
                        prefs.edit().putString(prefKey, key).apply()
                        viewModel.updateApiKey(selectedProvider, key)
                        Toast.makeText(context, "✅ Key guardada", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BeeGray.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("💾 Guardar Key", color = BeeWhite, fontSize = 13.sp)
                }
            }

            // ═══════════════════════════════════════
            // MODELO
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("MODELO LLM")
                Spacer(modifier = Modifier.height(8.dp))

                val models = if (selectedProvider == "openrouter") LlmFactory.OPENROUTER.models
                    else LlmFactory.OLLAMA_CLOUD.models

                models.forEach { model ->
                    val isSelected = selectedModel == model.id
                    Surface(
                        onClick = { selectedModel = model.id },
                        color = if (isSelected) BeeYellow.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (model.free) "🆓" else "💎", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.name, color = if (isSelected) BeeYellow else Color(0xFFE0E0E0), fontSize = 14.sp)
                                Text(model.id, color = BeeGray, fontSize = 10.sp)
                            }
                            if (isSelected) Text("✓", color = BeeYellow, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            // APPLY BUTTON
            // ═══════════════════════════════════════
            Button(
                onClick = {
                    viewModel.switchProvider(selectedProvider, selectedModel)
                    prefs.edit()
                        .putString("selected_provider", selectedProvider)
                        .putString("selected_model", selectedModel)
                        .apply()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("✅ Aplicar y Volver", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // ═══════════════════════════════════════
            // TELEGRAM BOT
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("TELEGRAM BOT")
                Text("Conecta un bot de Telegram a tu agente principal", fontSize = 12.sp, color = BeeGray)
                Spacer(modifier = Modifier.height(8.dp))

                // Status
                if (botStatus != "offline") {
                    Surface(
                        color = when (botStatus) {
                            "online" -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                            "connecting" -> BeeYellow.copy(alpha = 0.12f)
                            else -> Color(0xFFF44336).copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (botStatus) {
                                    "online" -> "🟢"
                                    "connecting" -> "🟡"
                                    else -> "🔴"
                                }, fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    when (botStatus) {
                                        "online" -> if (botName.isNotBlank()) "@$botName conectado" else "Bot conectado"
                                        "connecting" -> "Conectando..."
                                        else -> "Error de conexión"
                                    },
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE0E0E0)
                                )
                                if (botStatus == "online" && viewModel.telegramBotMessages.value > 0) {
                                    Text("${viewModel.telegramBotMessages.value} mensajes procesados",
                                        fontSize = 11.sp, color = BeeGray)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = telegramToken,
                    onValueChange = { telegramToken = it },
                    label = { Text("Bot Token (@BotFather)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = BeeGrayLight)
                        }
                    },
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            prefs.edit().putString("telegram_bot_token", telegramToken).apply()
                            viewModel.telegramBotStatus.value = "connecting"
                            val intent = Intent(context, TelegramBotService::class.java).apply {
                                action = TelegramBotService.ACTION_START
                                putExtra(TelegramBotService.EXTRA_BOT_TOKEN, telegramToken)
                                putExtra(TelegramBotService.EXTRA_PROVIDER, selectedProvider)
                                putExtra(TelegramBotService.EXTRA_MODEL, selectedModel)
                                putExtra(TelegramBotService.EXTRA_API_KEY,
                                    if (selectedProvider == "ollama") ollamaKey else openRouterKey)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else context.startService(intent)
                        },
                        enabled = telegramToken.isNotBlank() && botStatus != "online" && botStatus != "connecting",
                        colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(when (botStatus) {
                            "online" -> "✅ Conectado"
                            "connecting" -> "⏳..."
                            else -> "▶️ Conectar"
                        }, fontSize = 13.sp)
                    }

                    if (botStatus == "online" || botStatus == "connecting") {
                        Button(
                            onClick = {
                                context.startService(
                                    Intent(context, TelegramBotService::class.java)
                                        .apply { action = TelegramBotService.ACTION_STOP }
                                )
                                viewModel.telegramBotStatus.value = "offline"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BeeGray.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("⏹️ Detener", color = BeeWhite, fontSize = 13.sp)
                        }
                    }
                }

                // Allowlist section
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BeeGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                Text("USUARIOS PERMITIDOS", fontSize = 11.sp, color = BeeYellow,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("El primer usuario en escribirle al bot se registra como dueño automáticamente.",
                    fontSize = 11.sp, color = BeeGray)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = telegramUsername,
                    onValueChange = { telegramUsername = it },
                    label = { Text("Tu @username de Telegram") },
                    placeholder = { Text("@mi_usuario", color = BeeGray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        prefs.edit().putString("telegram_owner_username", telegramUsername.trim()).apply()
                        Toast.makeText(context, "✅ Username guardado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BeeGray.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("💾 Guardar Username", color = BeeWhite, fontSize = 13.sp)
                }

                if (allowedChatsStr.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val ids = allowedChatsStr.split(",").filter { it.isNotBlank() }
                    ids.forEachIndexed { i, id ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👤", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chat ID: $id", fontSize = 13.sp, color = Color(0xFFE0E0E0),
                                modifier = Modifier.weight(1f))
                            if (i == 0) {
                                Surface(color = BeeYellow.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                    Text("Dueño", fontSize = 10.sp, color = BeeYellow,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "1. Abre Telegram → @BotFather → /newbot\n2. Copia el token → pega aquí → Conectar\n3. Escríbele al bot y te registra automáticamente",
                    fontSize = 11.sp, color = BeeGray
                )
            }

            // ═══════════════════════════════════════
            // DATOS Y ALMACENAMIENTO
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("DATOS Y ALMACENAMIENTO")
                Spacer(modifier = Modifier.height(8.dp))

                val msgCount = viewModel.chatHistoryDB?.getTotalMessageCount() ?: 0
                val memCount = viewModel.memoryDB?.getMemoryCount() ?: 0

                DataRow("💬", "Mensajes", "$msgCount")
                DataRow("🧠", "Memorias RAG", "$memCount")
                DataRow("🤖", "Agentes", "${viewModel.availableAgents.size}")

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.chatHistoryDB?.clearAll()
                            Toast.makeText(context, "🗑️ Historial limpiado", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🗑️ Limpiar chats", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "📦 Exportación próximamente", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeYellow),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📦 Exportar", fontSize = 12.sp)
                    }
                }
            }

            // ═══════════════════════════════════════
            // SKILLS
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("SKILLS NATIVOS (25)")
                Spacer(modifier = Modifier.height(6.dp))

                val skillGroups = listOf(
                    "Core" to listOf("📱 device_info", "📋 clipboard", "🔔 notify", "🔊 tts", "🌐 browser", "📤 share", "📁 file"),
                    "Inteligencia" to listOf("🧠 memory", "🧮 calculator", "📅 datetime"),
                    "Multimedia" to listOf("📸 camera", "🎨 image_gen"),
                    "Sistema" to listOf("🔦 flashlight", "🔉 volume", "⏰ alarm", "📱 app_launcher", "📞 contacts", "📡 connectivity"),
                    "Productividad" to listOf("📅 calendar", "📧 email", "🎵 music", "🌤️ weather", "🔍 search", "🔆 brightness", "🔋 battery", "📱 qr_gen")
                )

                skillGroups.forEach { (group, skills) ->
                    Text(group, fontSize = 11.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
                    Text(skills.joinToString("  "), fontSize = 12.sp, color = Color(0xFFB0B0B0))
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // ═══════════════════════════════════════
            // ACERCA DE
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("ACERCA DE")
                Spacer(modifier = Modifier.height(6.dp))
                Text("Bee-Movil v3.1", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeWhite)
                Text("25 skills · Kotlin nativo · Edge AI", fontSize = 12.sp, color = BeeGray)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Asistente AI que vive en tu teléfono.\nSin servidores. Sin tracking. 100% tuyo.",
                    fontSize = 12.sp, color = Color(0xFFB0B0B0))
                Spacer(modifier = Modifier.height(8.dp))
                Text("🐝 Powered by Bee-Movil Team", fontSize = 11.sp, color = BeeYellow)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ═══════════════════════════════════════
// REUSABLE COMPONENTS
// ═══════════════════════════════════════

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 11.sp, color = BeeYellow, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun DataRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, color = Color(0xFFE0E0E0), modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = BeeYellow, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProviderChip(label: String, id: String, selected: String, onClick: (String) -> Unit) {
    FilterChip(
        selected = selected == id,
        onClick = { onClick(id) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = BeeYellow,
            selectedLabelColor = BeeBlack,
            containerColor = BeeGray.copy(alpha = 0.3f),
            labelColor = Color(0xFFE0E0E0)
        )
    )
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
