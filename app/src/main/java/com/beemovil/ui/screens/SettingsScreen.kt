package com.beemovil.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.llm.LlmFactory
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow)
            }
            Text("⚙️ Configuración", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BeeWhite)
        }

        // === PROVIDER SELECTOR ===
        Card(colors = CardDefaults.cardColors(containerColor = BeeBlackLight), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🌐 Proveedor LLM", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeYellow)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderChip("OpenRouter", "openrouter", selectedProvider) { selectedProvider = it }
                    ProviderChip("Ollama Cloud", "ollama", selectedProvider) { selectedProvider = it }
                }
            }
        }

        // === OPENROUTER CONFIG ===
        if (selectedProvider == "openrouter") {
            ApiKeyCard(
                title = "🔑 OpenRouter API Key",
                subtitle = "Modelos gratis + premium. openrouter.ai",
                apiKey = openRouterKey,
                showKey = showOrKey,
                onKeyChange = { openRouterKey = it },
                onToggleShow = { showOrKey = !showOrKey },
                onSave = {
                    prefs.edit().putString("openrouter_api_key", openRouterKey.trim()).apply()
                    viewModel.updateApiKey("openrouter", openRouterKey.trim())
                }
            )

            // OpenRouter model picker
            ModelPickerCard(
                models = LlmFactory.OPENROUTER.models,
                selectedModel = selectedModel,
                onSelect = { selectedModel = it }
            )
        }

        // === OLLAMA CLOUD CONFIG ===
        if (selectedProvider == "ollama") {
            ApiKeyCard(
                title = "🔑 Ollama Cloud API Key",
                subtitle = "Tu cuenta Ollama Cloud Max. ollama.com",
                apiKey = ollamaKey,
                showKey = showOlKey,
                onKeyChange = { ollamaKey = it },
                onToggleShow = { showOlKey = !showOlKey },
                onSave = {
                    prefs.edit().putString("ollama_api_key", ollamaKey.trim()).apply()
                    viewModel.updateApiKey("ollama", ollamaKey.trim())
                }
            )

            // Ollama model picker
            ModelPickerCard(
                models = LlmFactory.OLLAMA_CLOUD.models,
                selectedModel = selectedModel,
                onSelect = { selectedModel = it }
            )
        }

        // === APPLY BUTTON ===
        Button(
            onClick = {
                viewModel.switchProvider(selectedProvider, selectedModel)
                // Save selection
                prefs.edit()
                    .putString("selected_provider", selectedProvider)
                    .putString("selected_model", selectedModel)
                    .apply()
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("✅ Aplicar y Volver", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // === SKILLS INFO ===
        Card(colors = CardDefaults.cardColors(containerColor = BeeBlackLight), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔧 Skills Nativos (25)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeYellow)
                Spacer(modifier = Modifier.height(8.dp))

                Text("Core", fontSize = 12.sp, color = BeeGrayLight)
                SkillRow("📱", "device_info", "Info del dispositivo")
                SkillRow("📋", "clipboard", "Copiar/leer clipboard")
                SkillRow("🔔", "notify", "Notificaciones push")
                SkillRow("🔊", "tts", "Texto a voz")
                SkillRow("🌐", "browser", "Abrir URLs")
                SkillRow("📤", "share", "Compartir contenido")
                SkillRow("📁", "file", "Archivos (leer/escribir)")

                Spacer(modifier = Modifier.height(4.dp))
                Text("Inteligencia", fontSize = 12.sp, color = BeeGrayLight)
                SkillRow("🧠", "memory", "RAG Memory")
                SkillRow("🧮", "calculator", "Calculadora")
                SkillRow("📅", "datetime", "Fecha/hora")

                Spacer(modifier = Modifier.height(4.dp))
                Text("Multimedia", fontSize = 12.sp, color = BeeGrayLight)
                SkillRow("📸", "camera", "Cámara / fotos")
                SkillRow("🎨", "image_gen", "Generación IA")

                Spacer(modifier = Modifier.height(4.dp))
                Text("Sistema", fontSize = 12.sp, color = BeeGrayLight)
                SkillRow("🔦", "flashlight", "Linterna")
                SkillRow("🔉", "volume", "Volumen / vibración")
                SkillRow("⏰", "alarm", "Alarmas y timers")
                SkillRow("📱", "app_launcher", "Abrir apps")
                SkillRow("📞", "contacts", "Contactos / llamar / SMS")
                SkillRow("📡", "connectivity", "WiFi / GPS")

                Spacer(modifier = Modifier.height(4.dp))
                Text("Productividad", fontSize = 12.sp, color = BeeGrayLight)
                SkillRow("📅", "calendar", "Calendario nativo")
                SkillRow("📧", "email", "Enviar emails")
                SkillRow("🎵", "music_control", "Control de música")
                SkillRow("🌤️", "weather", "Clima actual")
                SkillRow("🔍", "web_search", "Buscar en internet")
                SkillRow("🔆", "brightness", "Brillo pantalla")
                SkillRow("🔋", "battery_saver", "Estado batería")
                SkillRow("📱", "qr_generator", "Generar QR")

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "🎙️ Voice Input · 💬 Multi-agente WhatsApp-style",
                    fontSize = 11.sp, color = BeeGrayLight
                )
            }
        }

        // === TELEGRAM BOT ===
        Card(colors = CardDefaults.cardColors(containerColor = BeeBlackLight), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🤖 Telegram Bot", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeYellow)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Conecta un bot de Telegram a tu agente (sin VPS)", fontSize = 12.sp, color = BeeGrayLight)
                Spacer(modifier = Modifier.height(8.dp))

                var telegramToken by remember { mutableStateOf(prefs.getString("telegram_bot_token", "") ?: "") }
                var showToken by remember { mutableStateOf(false) }
                val botRunning = com.beemovil.telegram.TelegramBotService.isRunning.get()

                OutlinedTextField(
                    value = telegramToken,
                    onValueChange = { telegramToken = it },
                    label = { Text("Bot Token (@BotFather)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Filled.Lock else Icons.Filled.Lock, "Toggle", tint = BeeGrayLight)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeeYellow,
                        unfocusedBorderColor = BeeGray,
                        focusedTextColor = BeeWhite,
                        unfocusedTextColor = BeeWhite,
                        focusedLabelColor = BeeYellow,
                        unfocusedLabelColor = BeeGrayLight,
                        cursorColor = BeeYellow
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            prefs.edit().putString("telegram_bot_token", telegramToken).apply()
                            val intent = android.content.Intent(context, com.beemovil.telegram.TelegramBotService::class.java).apply {
                                action = com.beemovil.telegram.TelegramBotService.ACTION_START
                                putExtra(com.beemovil.telegram.TelegramBotService.EXTRA_BOT_TOKEN, telegramToken)
                                putExtra(com.beemovil.telegram.TelegramBotService.EXTRA_PROVIDER, selectedProvider)
                                putExtra(com.beemovil.telegram.TelegramBotService.EXTRA_MODEL, selectedModel)
                                putExtra(com.beemovil.telegram.TelegramBotService.EXTRA_API_KEY,
                                    if (selectedProvider == "ollama") ollamaKey else openRouterKey)
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        },
                        enabled = telegramToken.isNotBlank() && !botRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (botRunning) "✅ Activo" else "▶️ Iniciar Bot")
                    }

                    if (botRunning) {
                        Button(
                            onClick = {
                                val intent = android.content.Intent(context, com.beemovil.telegram.TelegramBotService::class.java).apply {
                                    action = com.beemovil.telegram.TelegramBotService.ACTION_STOP
                                }
                                context.startService(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BeeGray, contentColor = BeeWhite),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⏹️ Detener")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "1. Abre @BotFather en Telegram\n2. /newbot → copia el token\n3. Pega aquí → Iniciar Bot",
                    fontSize = 11.sp, color = BeeGrayLight
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
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
            containerColor = BeeGray,
            labelColor = BeeWhite
        )
    )
}

@Composable
private fun ApiKeyCard(
    title: String, subtitle: String,
    apiKey: String, showKey: Boolean,
    onKeyChange: (String) -> Unit,
    onToggleShow: () -> Unit,
    onSave: () -> Unit
) {
    var saved by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = BeeBlackLight), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeYellow)
            Text(subtitle, fontSize = 12.sp, color = BeeGrayLight)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { onKeyChange(it); saved = false },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("API Key...") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleShow) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            "Toggle", tint = BeeGrayLight
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray,
                    cursorColor = BeeYellow, focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onSave(); saved = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BeeGray)
            ) {
                Text(if (saved) "✅ Guardada" else "💾 Guardar Key", color = BeeWhite)
            }
        }
    }
}

@Composable
private fun ModelPickerCard(
    models: List<LlmFactory.ModelOption>,
    selectedModel: String,
    onSelect: (String) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = BeeBlackLight), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🤖 Modelo", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeYellow)
            Spacer(modifier = Modifier.height(8.dp))

            models.forEach { model ->
                val isSelected = selectedModel == model.id
                Surface(
                    onClick = { onSelect(model.id) },
                    color = if (isSelected) BeeYellow.copy(alpha = 0.15f) else BeeGray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (model.free) "🆓" else "💎",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, color = if (isSelected) BeeYellow else BeeWhite, fontSize = 14.sp)
                            Text(model.id, color = BeeGrayLight, fontSize = 10.sp)
                        }
                        if (isSelected) {
                            Text("✓", color = BeeYellow, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRow(icon: String, name: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(name, color = BeeWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
        Text(desc, color = BeeGrayLight, fontSize = 13.sp)
    }
}
