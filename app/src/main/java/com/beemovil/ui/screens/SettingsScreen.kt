package com.beemovil.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.llm.LlmFactory
import com.beemovil.llm.ModelRegistry
import com.beemovil.security.SecurePrefs
import com.beemovil.telegram.TelegramBotService
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
    val securePrefs = remember { SecurePrefs.get(context) }
    val scope = rememberCoroutineScope()

    var openRouterKey by remember { mutableStateOf(securePrefs.getString("openrouter_api_key", "") ?: "") }
    var ollamaKey by remember { mutableStateOf(securePrefs.getString("ollama_api_key", "") ?: "") }
    var showOrKey by remember { mutableStateOf(false) }
    var showOlKey by remember { mutableStateOf(false) }
    var hfToken by remember { mutableStateOf(securePrefs.getString("huggingface_token", "") ?: "") }
    var showHfToken by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(viewModel.currentProvider.value) }
    var selectedModel by remember { mutableStateOf(viewModel.currentModel.value) }

    // Telegram
    var telegramToken by remember { mutableStateOf(securePrefs.getString("telegram_bot_token", "") ?: "") }
    var showToken by remember { mutableStateOf(false) }
    val botStatus = viewModel.telegramBotStatus.value
    val botName = viewModel.telegramBotName.value

    // Telegram allowlist
    val allowedChatsStr = prefs.getString(TelegramBotService.PREF_ALLOWED_CHATS, "") ?: ""
    var telegramUsername by remember { mutableStateOf(securePrefs.getString("telegram_owner_username", "") ?: "") }

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
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        val isDark = isDarkTheme()
        val accent = if (isDark) BeeYellow else BrandBlue
        val textPrimary = if (isDark) BeeWhite else TextDark
        val textSecondary = if (isDark) BeeGray else TextGrayDark
        val topBarBg = if (isDark) Color(0xFF1A1A2E) else LightSurface

        TopAppBar(
            title = { Text("Configuración", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = accent)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = topBarBg,
                titleContentColor = textPrimary
            )
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ═══════════════════════════════════════
            // PERFIL
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("TU NOMBRE")
                Spacer(modifier = Modifier.height(8.dp))
                var displayName by remember { mutableStateOf(prefs.getString("user_display_name", "") ?: "") }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = { Text("Como quieres que te llame?", color = textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        prefs.edit().putString("user_display_name", displayName.trim()).apply()
                        Toast.makeText(context, "Nombre guardado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = textSecondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Guardar", color = textPrimary, fontSize = 13.sp)
                }
            }

            // ═══════════════════════════════════════
            // TEMA VISUAL
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("TEMA")
                Spacer(modifier = Modifier.height(8.dp))

                val currentTheme = BeeThemeState.forceDark.value
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(true, "Oscuro", Icons.Filled.DarkMode),
                        Triple(false, "Claro", Icons.Filled.LightMode),
                    ).forEach { (isDark, label, icon) ->
                        FilterChip(
                            selected = currentTheme == isDark,
                            onClick = {
                                BeeThemeState.forceDark.value = isDark
                                prefs.edit().putString("app_theme", if (isDark) "dark" else "light").apply()
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(icon, label, modifier = Modifier.size(16.dp))
                                    Text(label, fontSize = 13.sp)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.2f),
                                selectedLabelColor = accent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = textSecondary, selectedBorderColor = accent,
                                enabled = true, selected = currentTheme == isDark
                            )
                        )
                    }
                    FilterChip(
                        selected = currentTheme == null,
                        onClick = {
                            BeeThemeState.forceDark.value = null
                            prefs.edit().remove("app_theme").apply()
                        },
                        label = { Text("Sistema", fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.2f),
                            selectedLabelColor = accent
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = textSecondary, selectedBorderColor = accent,
                            enabled = true, selected = currentTheme == null
                        )
                    )
                }
            }

            // ═══════════════════════════════════════
            // IDIOMA
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("IDIOMA / LANGUAGE")
                Spacer(modifier = Modifier.height(8.dp))

                val savedLang = prefs.getString("app_language", "auto") ?: "auto"
                var currentLang by remember { mutableStateOf(savedLang) }
                
                val languages = listOf(
                    Triple("es", "Español", "🇪🇸"),
                    Triple("en", "English", "🇺🇸"),
                    Triple("auto", "Sistema", "🌐")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    languages.forEach { (code, label, flag) ->
                        FilterChip(
                            selected = currentLang == code,
                            onClick = {
                                currentLang = code
                                prefs.edit().putString("app_language", code).apply()
                                
                                // Apply locale change
                                val locale = if (code == "auto") {
                                    java.util.Locale.getDefault()
                                } else {
                                    java.util.Locale(code)
                                }
                                
                                val config = context.resources.configuration
                                config.setLocale(locale)
                                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                                
                                // Recreate activity to apply
                                (context as? android.app.Activity)?.recreate()
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(flag, fontSize = 16.sp)
                                    Text(label, fontSize = 13.sp)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.2f),
                                selectedLabelColor = accent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = textSecondary, selectedBorderColor = accent,
                                enabled = true, selected = currentLang == code
                            )
                        )
                    }
                }
            }

            // ═══════════════════════════════════════
            // PROVEEDOR AI
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("PROVEEDOR AI")
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderChip("OpenRouter", "openrouter", selectedProvider) {
                        selectedProvider = it
                        selectedModel = LlmFactory.OPENROUTER.models.first().id
                    }
                    ProviderChip("Ollama Cloud", "ollama", selectedProvider) {
                        selectedProvider = it
                        selectedModel = LlmFactory.OLLAMA_CLOUD.models.first().id
                    }
                    ProviderChip("Local", "local", selectedProvider) {
                        selectedProvider = it
                        selectedModel = LlmFactory.LOCAL.models.first().id
                    }
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
                                "Toggle", tint = textSecondary
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
                        securePrefs.edit().putString(prefKey, key).apply()
                        viewModel.updateApiKey(selectedProvider, key)
                        Toast.makeText(context, "Key guardada", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = textSecondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Guardar Key", color = textPrimary, fontSize = 13.sp)
                }
            }

            // ═══════════════════════════════════════
            // MODELO
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("MODELO LLM")
                Spacer(modifier = Modifier.height(8.dp))
                
                // U-03 fix: Modelos dinámicos desde el servidor
                var dynamicModels by remember { mutableStateOf<List<LlmFactory.ModelOption>>(emptyList()) }
                var isRefreshing by remember { mutableStateOf(false) }
                var refreshStatus by remember { mutableStateOf("") }
                
                // Cargar modelos iniciales (cache o estáticos)
                LaunchedEffect(selectedProvider) {
                    dynamicModels = when (selectedProvider) {
                        "openrouter" -> {
                            val cached = com.beemovil.llm.DynamicModelFetcher.getCachedOpenRouterModels(context)
                            cached.map { LlmFactory.ModelOption(it.id, it.name, it.free) }
                        }
                        "ollama" -> {
                            val cached = com.beemovil.llm.DynamicModelFetcher.getCachedOllamaModels(context)
                            cached.map { LlmFactory.ModelOption(it.id, it.name, it.free) }
                        }
                        "local" -> LlmFactory.LOCAL.models
                        else -> LlmFactory.OPENROUTER.models
                    }
                }
                
                val models = dynamicModels.ifEmpty {
                    when (selectedProvider) {
                        "openrouter" -> LlmFactory.OPENROUTER.models
                        "ollama" -> LlmFactory.OLLAMA_CLOUD.models
                        "local" -> LlmFactory.LOCAL.models
                        else -> LlmFactory.OPENROUTER.models
                    }
                }
                
                // Botón de actualizar modelos (solo para cloud providers)
                if (selectedProvider != "local") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${models.size} modelos disponibles",
                            fontSize = 11.sp, color = textSecondary
                        )
                        Button(
                            onClick = {
                                isRefreshing = true
                                refreshStatus = "Consultando servidor..."
                                scope.launch {
                                    try {
                                        val fetched = when (selectedProvider) {
                                            "openrouter" -> {
                                                val key = securePrefs.getString("openrouter_api_key", "") ?: ""
                                                com.beemovil.llm.DynamicModelFetcher.fetchOpenRouterModels(context, key)
                                            }
                                            "ollama" -> {
                                                val ollamaUrl = securePrefs.getString("ollama_base_url", "https://ollama.com") ?: "https://ollama.com"
                                                com.beemovil.llm.DynamicModelFetcher.fetchOllamaModels(context, ollamaUrl)
                                            }
                                            else -> emptyList()
                                        }
                                        dynamicModels = fetched.map { LlmFactory.ModelOption(it.id, it.name, it.free) }
                                        refreshStatus = "✅ ${fetched.size} modelos actualizados"
                                    } catch (e: Exception) {
                                        refreshStatus = "❌ Error: ${e.message}"
                                    } finally {
                                        isRefreshing = false
                                    }
                                }
                            },
                            enabled = !isRefreshing,
                            colors = ButtonDefaults.buttonColors(containerColor = textSecondary.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(color = accent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Filled.Refresh, "Refresh", tint = accent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("Actualizar", fontSize = 11.sp, color = textPrimary)
                        }
                    }
                    if (refreshStatus.isNotBlank()) {
                        Text(refreshStatus, fontSize = 10.sp, color = textSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (selectedProvider == "local") {
                    // Local model info
                    Text(
                        "Modelos que corren EN TU TELEFONO sin internet.",
                        fontSize = 12.sp, color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val availableGB = remember {
                        try { com.beemovil.llm.local.LocalModelManager.getAvailableStorageGB() } catch (_: Exception) { 0.0 }
                    }
                    Text(
                        "Espacio disponible: ${String.format("%.1f", availableGB)} GB",
                        fontSize = 11.sp, color = if (availableGB > 2.0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // HuggingFace Token (required for model download)
                    OutlinedTextField(
                        value = hfToken,
                        onValueChange = { hfToken = it },
                        label = { Text("HuggingFace Token") },
                        placeholder = { Text("hf_...", color = textSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showHfToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showHfToken = !showHfToken }) {
                                Icon(if (showHfToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = textSecondary)
                            }
                        },
                        colors = fieldColors()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Necesario para descargar Gemma 4.\nhuggingface.co/settings/tokens",
                            fontSize = 9.sp, color = textSecondary.copy(alpha = 0.8f)
                        )
                        Button(
                            onClick = {
                                securePrefs.edit().putString("huggingface_token", hfToken.trim()).apply()
                                Toast.makeText(context, "HuggingFace token guardado", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = textSecondary.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Guardar", fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                models.forEach { model ->
                    val isSelected = selectedModel == model.id
                    val isDownloaded = if (selectedProvider == "local")
                        com.beemovil.llm.local.LocalModelManager.isModelDownloaded(model.id) else true

                    Surface(
                        onClick = {
                            selectedModel = model.id
                        },
                        color = if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when {
                                    selectedProvider == "local" && isDownloaded -> "OK"
                                    selectedProvider == "local" -> "DL"
                                    model.free -> "Free"
                                    else -> "Pro"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    selectedProvider == "local" && isDownloaded -> Color(0xFF4CAF50)
                                    model.free -> accent
                                    else -> Color(0xFFBF5AF2)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.name, color = if (isSelected) accent else if (isDark) Color(0xFFE0E0E0) else TextDark, fontSize = 14.sp)
                                if (selectedProvider == "local") {
                                    Text(
                                        if (isDownloaded) "Listo para usar" else "Toca Descargar para usar",
                                        color = if (isDownloaded) Color(0xFF4CAF50) else BeeGray,
                                        fontSize = 10.sp
                                    )
                                } else {
                                    Text(model.id, color = textSecondary, fontSize = 10.sp)
                                }
                            }
                            if (isSelected && (selectedProvider != "local" || isDownloaded)) {
                                Icon(Icons.Filled.CheckCircle, "Selected", tint = accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Download button for local models
                if (selectedProvider == "local") {
                    Spacer(modifier = Modifier.height(8.dp))

                    var downloadProgress by remember { mutableStateOf(-1f) }
                    var downloadStatus by remember { mutableStateOf("") }

                    val modelToDownload = LlmFactory.LOCAL.models.find { it.id == selectedModel }
                    val isAlreadyDownloaded = modelToDownload?.let {
                        com.beemovil.llm.local.LocalModelManager.isModelDownloaded(it.id)
                    } ?: false

                    if (!isAlreadyDownloaded && modelToDownload != null) {
                        Button(
                            onClick = {
                                downloadProgress = 0f
                                downloadStatus = "Iniciando descarga..."
                                com.beemovil.llm.local.LocalModelManager.downloadModel(
                                    modelId = modelToDownload.id,
                                    onProgress = { downloaded, total ->
                                        downloadProgress = if (total > 0) downloaded.toFloat() / total else 0f
                                        downloadStatus = "${downloaded / 1_048_576} / ${total / 1_048_576} MB"
                                    },
                                    onComplete = { success, message ->
                                        downloadProgress = if (success) 1f else -1f
                                        downloadStatus = message
                                    }
                                )
                            },
                            enabled = downloadProgress < 0f || downloadProgress >= 1f,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Descargar ${modelToDownload.name}", color = textPrimary, fontSize = 13.sp)
                        }

                        if (downloadProgress in 0f..0.99f) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                color = accent,
                                trackColor = textSecondary.copy(alpha = 0.3f)
                            )
                        }
                    }

                    if (downloadStatus.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(downloadStatus, fontSize = 11.sp, color = textSecondary)
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
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (isDark) BeeBlack else Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Aplicar y Volver", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // ═══════════════════════════════════════
            // TELEGRAM BOT
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("TELEGRAM BOT")
                Text("Conecta un bot de Telegram a tu agente E.M.M.A.", fontSize = 12.sp, color = textSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                // Live status indicator
                Surface(
                    color = when {
                        botStatus.contains("Activo") -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                        botStatus.contains("Error") -> Color(0xFFF44336).copy(alpha = 0.12f)
                        botStatus.contains("Procesando") -> accent.copy(alpha = 0.12f)
                        else -> textSecondary.copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(
                            when {
                                botStatus.contains("Activo") -> Color(0xFF4CAF50)
                                botStatus.contains("Error") -> Color(0xFFF44336)
                                botStatus.contains("Procesando") -> accent
                                else -> textSecondary
                            }
                        ))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (botStatus.isBlank()) "Desconectado" else botStatus,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary
                            )
                            if (botName.isNotBlank()) {
                                Text("@$botName", fontSize = 11.sp, color = textSecondary)
                            }
                        }
                        if (viewModel.telegramBotMessages.value > 0) {
                            Surface(color = accent, shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    "${viewModel.telegramBotMessages.value} msgs",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (isDark) BeeBlack else Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = telegramToken,
                    onValueChange = { telegramToken = it },
                    label = { Text("Bot Token (@BotFather)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = textSecondary)
                        }
                    },
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = telegramUsername,
                    onValueChange = { telegramUsername = it },
                    label = { Text("Tu @username de Telegram") },
                    placeholder = { Text("@mi_usuario", color = textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )
                Text(
                    "Solo responderá a mensajes de este usuario. Déjalo vacío para modo abierto.",
                    fontSize = 9.sp, color = textSecondary.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        securePrefs.edit()
                            .putString("telegram_bot_token", telegramToken.trim())
                            .putString("telegram_owner_username", telegramUsername.trim())
                            .apply()
                        Toast.makeText(context, "Credenciales de Telegram guardadas", Toast.LENGTH_SHORT).show()
                    },
                    enabled = telegramToken.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = textSecondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Guardar Credenciales", color = textPrimary, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Start / Stop buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isRunning = botStatus.contains("Activo") || botStatus.contains("Procesando") || botStatus.contains("Reconectando")

                    Button(
                        onClick = {
                            if (telegramToken.isBlank()) {
                                Toast.makeText(context, "Primero guarda el token del bot", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            securePrefs.edit()
                                .putString("telegram_bot_token", telegramToken.trim())
                                .putString("telegram_owner_username", telegramUsername.trim())
                                .apply()
                            val intent = Intent(context, TelegramBotService::class.java).apply {
                                action = TelegramBotService.ACTION_START
                                putExtra(TelegramBotService.EXTRA_BOT_TOKEN, telegramToken.trim())
                                putExtra(TelegramBotService.EXTRA_PROVIDER, selectedProvider)
                                putExtra(TelegramBotService.EXTRA_MODEL, selectedModel)
                                putExtra(TelegramBotService.EXTRA_API_KEY,
                                    if (selectedProvider == "openrouter") openRouterKey else ollamaKey)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            Toast.makeText(context, "Iniciando bot de Telegram...", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isRunning && telegramToken.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Start", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Iniciar Bot", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val intent = Intent(context, TelegramBotService::class.java).apply {
                                action = TelegramBotService.ACTION_STOP
                            }
                            context.startService(intent)
                            Toast.makeText(context, "Bot detenido", Toast.LENGTH_SHORT).show()
                        },
                        enabled = isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF44336),
                            disabledContainerColor = Color(0xFFF44336).copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Stop, "Stop", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detener", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Cómo configurar", fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, color = accent)
                        Text(
                            "1. Abre Telegram → busca @BotFather → /newbot\n" +
                            "2. Copia el token → pégalo arriba\n" +
                            "3. Pon tu @username para que solo tú puedas usarlo\n" +
                            "4. Toca 'Iniciar Bot' → ¡listo! Háblale a tu bot.",
                            fontSize = 11.sp, color = textSecondary
                        )
                    }
                }
            }

            // ═══════════════════════════════════════
            // DEEPGRAM VOICE (Phase 20)
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("VOZ (DEEPGRAM)")
                Text("Text-to-Speech y Speech-to-Text avanzado", fontSize = 12.sp, color = textSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                var dgKey by remember { mutableStateOf(securePrefs.getString("deepgram_api_key", "") ?: "") }
                var showDgKey by remember { mutableStateOf(false) }
                var dgVoice by remember { mutableStateOf(prefs.getString("deepgram_voice", "aura-asteria-en") ?: "aura-asteria-en") }
                var useDgSTT by remember { mutableStateOf(prefs.getBoolean("use_deepgram_stt", true)) }
                var useDgTTS by remember { mutableStateOf(prefs.getBoolean("use_deepgram_tts", true)) }

                OutlinedTextField(
                    value = dgKey,
                    onValueChange = { dgKey = it },
                    label = { Text("Deepgram API Key") },
                    placeholder = { Text("dg_xxxxxxxxxxxx", color = textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showDgKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showDgKey = !showDgKey }) {
                            Icon(if (showDgKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = textSecondary)
                        }
                    },
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Voice selector
                Text("VOZ DEL AGENTE", fontSize = 11.sp, color = accent,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))

                val voices = listOf(
                    Triple("aura-asteria-en", "Asteria", "Femenina, calida"),
                    Triple("aura-luna-en", "Luna", "Femenina, suave"),
                    Triple("aura-stella-en", "Stella", "Femenina, segura"),
                    Triple("aura-orion-en", "Orion", "Masculina, profunda"),
                    Triple("aura-arcas-en", "Arcas", "Masculina, amigable")
                )

                voices.forEach { (id, name, desc) ->
                    Surface(
                        onClick = { dgVoice = id },
                        color = if (dgVoice == id) accent.copy(alpha = 0.1f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (name.startsWith("A") || name.startsWith("L") || name.startsWith("S"))
                                    Icons.Filled.Face else Icons.Filled.RecordVoiceOver,
                                name,
                                tint = if (dgVoice == id) accent else textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (dgVoice == id) accent else textPrimary)
                                Text(desc, fontSize = 11.sp, color = textSecondary)
                            }
                            if (dgVoice == id) {
                                Icon(Icons.Filled.CheckCircle, "Selected", tint = accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = textSecondary.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                // STT Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Deepgram STT", fontSize = 14.sp, color = textPrimary)
                        Text("Transcripcion con Nova-3", fontSize = 11.sp, color = textSecondary)
                    }
                    Switch(
                        checked = useDgSTT,
                        onCheckedChange = { useDgSTT = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = if (isDark) BeeBlack else Color.White,
                            checkedTrackColor = accent,
                            uncheckedTrackColor = textSecondary.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // TTS Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Deepgram TTS", fontSize = 14.sp, color = textPrimary)
                        Text("Voz Aura (alta calidad)", fontSize = 11.sp, color = textSecondary)
                    }
                    Switch(
                        checked = useDgTTS,
                        onCheckedChange = { useDgTTS = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = if (isDark) BeeBlack else Color.White,
                            checkedTrackColor = accent,
                            uncheckedTrackColor = textSecondary.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        // API key goes to SecurePrefs (encrypted)
                        securePrefs.edit().putString("deepgram_api_key", dgKey.trim()).apply()
                        // Non-sensitive settings stay in regular prefs
                        prefs.edit()
                            .putString("deepgram_voice", dgVoice)
                            .putBoolean("use_deepgram_stt", useDgSTT)
                            .putBoolean("use_deepgram_tts", useDgTTS)
                            .apply()
                        Toast.makeText(context, if (dgKey.isNotBlank()) "Deepgram activado" else "Deepgram desactivado (voz nativa)", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (isDark) BeeBlack else Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Mic, "Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar Deepgram", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (dgKey.isBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Sin API key: se usa voz nativa de Android (gratis)",
                        fontSize = 11.sp, color = textSecondary
                    )
                }
            }
            
            // ═══════════════════════════════════════
            // ELEVENLABS (CLONACION DE VOZ)
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("CLONACION DE VOZ (ELEVENLABS)")
                Text("Voz hiper-realista por defecto para tu asistente", fontSize = 12.sp, color = textSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                var elKey by remember { mutableStateOf(securePrefs.getString("elevenlabs_api_key", "") ?: "") }
                var showElKey by remember { mutableStateOf(false) }
                var elVoiceId by remember { mutableStateOf(securePrefs.getString("elevenlabs_voice_id", "") ?: "") }

                OutlinedTextField(
                    value = elKey,
                    onValueChange = { elKey = it },
                    label = { Text("ElevenLabs API Key") },
                    placeholder = { Text("sk_...", color = textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showElKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showElKey = !showElKey }) {
                            Icon(if (showElKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = textSecondary)
                        }
                    },
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = elVoiceId,
                    onValueChange = { elVoiceId = it },
                    label = { Text("Voice ID (La voz de Emma)") },
                    placeholder = { Text("ID alfanumerico", color = textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        securePrefs.edit()
                            .putString("elevenlabs_api_key", elKey.trim())
                            .putString("elevenlabs_voice_id", elVoiceId.trim())
                            .apply()
                        Toast.makeText(context, "Configuracion de voz viva guardada", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63), contentColor = textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, "Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar ElevenLabs", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (elKey.isBlank() || elVoiceId.isBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Inactivo: Se usará Deepgram o Nativa. Activa a Emma cuando estés listo.",
                        fontSize = 11.sp, color = accent.copy(alpha = 0.8f)
                    )
                }
            }

            // ═══════════════════════════════════════
            // MEDIA GENERATION (Phase 23)
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("MEDIA IA (Imagenes + Video)")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Genera imagenes y videos con IA desde cualquier chat", fontSize = 12.sp, color = textSecondary,
                        modifier = Modifier.weight(1f))
                    Surface(color = Color(0xFFFF9800).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("PRÓXIMAMENTE", fontSize = 9.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                var falKey by remember { mutableStateOf(securePrefs.getString("fal_api_key", "") ?: "") }
                var showFalKey by remember { mutableStateOf(false) }
                var togetherKey by remember { mutableStateOf(securePrefs.getString("together_api_key", "") ?: "") }
                var showTogetherKey by remember { mutableStateOf(false) }

                // Status
                val hasAnyProvider = falKey.isNotBlank() || togetherKey.isNotBlank() ||
                    (openRouterKey.isNotBlank() && selectedProvider == "openrouter")
                Surface(
                    color = if (hasAnyProvider) Color(0xFF4CAF50).copy(alpha = 0.12f) else Color(0xFFF44336).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasAnyProvider) Icons.Filled.Palette else Icons.Filled.Warning,
                            "Status",
                            tint = if (hasAnyProvider) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                if (hasAnyProvider) "Listo para generar" else "Sin provider configurado",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = Color(0xFFE0E0E0)
                            )
                            Text(
                                buildString {
                                    val providers = mutableListOf<String>()
                                    if (falKey.isNotBlank()) providers.add("fal.ai (Flux)")
                                    if (togetherKey.isNotBlank()) providers.add("Together (Flux)")
                                    if (openRouterKey.isNotBlank()) providers.add("OpenRouter (DALL-E)")
                                    append(if (providers.isNotEmpty()) providers.joinToString(" · ") else "Configura al menos un provider")
                                },
                                fontSize = 11.sp, color = textSecondary
                            )
                            // Show image count
                            val imgDir = java.io.File(
                                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                                "BeeMovil/images"
                            )
                            val imgCount = if (imgDir.exists()) imgDir.listFiles()?.size ?: 0 else 0
                            if (imgCount > 0) {
                                Text("$imgCount imagenes generadas", fontSize = 10.sp, color = accent)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // fal.ai key
                OutlinedTextField(
                    value = falKey,
                    onValueChange = { falKey = it },
                    label = { Text("fal.ai API Key") },
                    placeholder = { Text("fal_xxxxxxxxxxxx", color = textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showFalKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showFalKey = !showFalKey }) {
                            Icon(if (showFalKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = textSecondary)
                        }
                    },
                    colors = fieldColors()
                )
                Text("Flux Schnell/Pro — el mas rapido. fal.ai/dashboard", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.7f))

                Spacer(modifier = Modifier.height(6.dp))

                // Together AI key
                OutlinedTextField(
                    value = togetherKey,
                    onValueChange = { togetherKey = it },
                    label = { Text("Together AI API Key") },
                    placeholder = { Text("tok_xxxxxxxxxxxx", color = textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showTogetherKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showTogetherKey = !showTogetherKey }) {
                            Icon(if (showTogetherKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = textSecondary)
                        }
                    },
                    colors = fieldColors()
                )
                Text("Flux.1 Schnell — creditos iniciales gratis. together.ai", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.7f))

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        securePrefs.edit()
                            .putString("fal_api_key", falKey.trim())
                            .putString("together_api_key", togetherKey.trim())
                            .apply()
                        Toast.makeText(context, "Media generation configurado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC), contentColor = textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Palette, "Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar Media Keys", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Cost warning
                Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, "Info", tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "La generacion de imagenes y videos puede tener costo dependiendo del provider que uses. " +
                            "fal.ai y Together AI ofrecen creditos iniciales gratis. " +
                            "Revisa los precios en la pagina del provider que prefieras antes de usar.\n\n" +
                            "Prioridad: fal.ai → Together AI → OpenRouter\n" +
                            "Di 'genera una imagen de...' o 'genera un video de...' en cualquier chat.",
                            fontSize = 11.sp, color = Color(0xFFE0E0E0), lineHeight = 16.sp
                        )
                    }
                }
            }

            // ═══════════════════════════════════════
            // DEVELOPER / GIT / BROWSER
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("DEVELOPER & GIT")
                Text("Configura GitHub y herramientas de desarrollo", fontSize = 12.sp, color = textSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                var githubToken by remember { mutableStateOf(securePrefs.getString("github_token", "") ?: "") }
                var showGithubToken by remember { mutableStateOf(false) }
                var browserHomepage by remember { mutableStateOf(prefs.getString("browser_homepage", "https://www.google.com") ?: "https://www.google.com") }

                OutlinedTextField(
                    value = githubToken,
                    onValueChange = { githubToken = it },
                    label = { Text("GitHub Personal Access Token") },
                    placeholder = { Text("ghp_xxxxxxxxxxxx", color = textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showGithubToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGithubToken = !showGithubToken }) {
                            Icon(
                                if (showGithubToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                "Toggle", tint = textSecondary
                            )
                        }
                    },
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = browserHomepage,
                    onValueChange = { browserHomepage = it },
                    label = { Text("Browser Homepage") },
                    placeholder = { Text("https://www.google.com", color = textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        securePrefs.edit().putString("github_token", githubToken.trim()).apply()
                        prefs.edit().putString("browser_homepage", browserHomepage.trim()).apply()
                        Toast.makeText(context, "Developer settings guardados", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = textSecondary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Guardar Developer Settings", color = textPrimary, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "1. GitHub → Settings → Developer Settings → Personal Access Tokens\n2. Generate new token (classic) → permisos: repo\n3. Copia el token → pega aqui",
                    fontSize = 11.sp, color = textSecondary
                )
            }

            // ═══════════════════════════════════════
            // GOOGLE WORKSPACE
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("GOOGLE WORKSPACE")
                Text("Un solo login para Drive, Gmail y Calendar", fontSize = 12.sp, color = textSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                val googleAuth = remember { com.beemovil.google.GoogleAuthManager(context) }
                var googleSignedIn by remember { mutableStateOf(googleAuth.isSignedIn()) }
                var googleEmail by remember { mutableStateOf(googleAuth.getEmail() ?: "") }
                var googleName by remember { mutableStateOf(googleAuth.getDisplayName() ?: "") }
                var googleLoading by remember { mutableStateOf(false) }

                if (googleSignedIn) {
                    // ── Connected State ──
                    Surface(
                        color = Color(0xFF1A2E1A),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar placeholder
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                color = Color(0xFF4285F4)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        googleName.firstOrNull()?.uppercase() ?: "G",
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(googleName, color = textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(googleEmail, color = textSecondary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val hasDrive = googleAuth.hasDriveScope()
                                    val hasGmail = googleAuth.hasGmailScope()
                                    val hasCal = googleAuth.hasCalendarScope()
                                    ServiceBadge("Drive", hasDrive)
                                    ServiceBadge("Gmail", hasGmail)
                                    ServiceBadge("Calendar", hasCal)
                                }
                            }
                            Icon(Icons.Filled.CheckCircle, "Connected", tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Disconnect button
                    TextButton(
                        onClick = {
                            scope.launch {
                                googleAuth.signOut()
                                googleSignedIn = false
                                googleEmail = ""
                                googleName = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Desconectar cuenta Google", color = Color(0xFFFF6B6B), fontSize = 13.sp)
                    }
                } else {
                    // ── Not Connected ──
                    var googleError by remember { mutableStateOf<String?>(null) }
                    
                    Button(
                        onClick = {
                            googleError = null
                            scope.launch {
                                googleLoading = true
                                try {
                                    // Find the actual Activity (Compose wraps context)
                                    val activity = context as? android.app.Activity
                                        ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                                    
                                    if (activity == null) {
                                        googleError = "Error interno: No se encontró Activity context"
                                        googleLoading = false
                                        return@launch
                                    }
                                    
                                    android.util.Log.i("GoogleSettings", "Attempting signIn with WEB_CLIENT_ID: ${com.beemovil.google.GoogleAuthManager.WEB_CLIENT_ID.take(20)}...")
                                    
                                    val user = googleAuth.signIn(activity)
                                    if (user != null) {
                                        googleSignedIn = true
                                        googleEmail = user.email
                                        googleName = user.displayName
                                        googleError = null
                                        // Auto-populate soul
                                        try {
                                            val memDb = com.beemovil.memory.BeeMemoryDB(context)
                                            if (memDb.getSoul("name").isNullOrBlank()) {
                                                memDb.setSoul("name", user.displayName)
                                            }
                                            if (memDb.getSoul("email").isNullOrBlank()) {
                                                memDb.setSoul("email", user.email)
                                            }
                                        } catch (_: Exception) {}
                                        
                                        // Request OAuth2 scopes
                                        try {
                                            val accessToken = googleAuth.requestScopes(activity)
                                            if (accessToken != null) {
                                                Toast.makeText(context, "Google conectado con permisos ✅", Toast.LENGTH_SHORT).show()
                                            } else {
                                                googleError = "Cuenta conectada, pero sin permisos de API. Toca de nuevo para reintentar."
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("GoogleSettings", "Scope request failed: ${e.message}", e)
                                            googleError = "Permisos: ${e.message}"
                                        }
                                    } else {
                                        googleError = "Google no respondió. Posibles causas:\n• No hay cuenta Google en este dispositivo\n• El SHA-1 no está registrado en Cloud Console\n• El WEB_CLIENT_ID no coincide con el proyecto"
                                        android.util.Log.w("GoogleSettings", "signIn returned null")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("GoogleSettings", "Sign-in exception: ${e.message}", e)
                                    val msg = e.message ?: "Error desconocido"
                                    googleError = when {
                                        msg.contains("No credentials available", true) || msg.contains("no viable", true) ->
                                            "No hay credenciales disponibles.\n\n" +
                                            "Para arreglar esto:\n" +
                                            "1. Ve a console.cloud.google.com → Credentials\n" +
                                            "2. Crea un OAuth Client ID tipo 'Android'\n" +
                                            "3. Package: com.beemovil\n" +
                                            "4. SHA-1: ${getDebugSha1()}\n" +
                                            "5. También necesitas un Client ID tipo 'Web'\n" +
                                            "6. El WEB_CLIENT_ID actual es:\n${com.beemovil.google.GoogleAuthManager.WEB_CLIENT_ID.take(30)}..."
                                        msg.contains("sign_in_cancelled", true) ->
                                            "Sign-in cancelado por el usuario"
                                        else -> "Excepción: $msg"
                                    }
                                }
                                googleLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !googleLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F1F1F)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        if (googleLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF4285F4))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conectando...", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        } else {
                            Text("G", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conectar con Google", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                    
                    // Persistent error display
                    if (googleError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFF2E1A1A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("❌ Error de Google Sign-In", fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp, color = Color(0xFFF44336))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(googleError!!, fontSize = 11.sp, color = Color(0xFFFF8A80), lineHeight = 15.sp)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Accede a Drive, Gmail y Calendar con un solo login.\nNecesitas configurar un proyecto en Google Cloud Console.",
                        fontSize = 11.sp, color = textSecondary, lineHeight = 14.sp
                    )
                }
            }

            // ═══════════════════════════════════════
            // EMAIL CONFIG
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("CORREO ELECTRONICO")
                Text("Configura tu email para usar la bandeja de entrada", fontSize = 12.sp, color = textSecondary)
                Spacer(modifier = Modifier.height(8.dp))

                var emailAddr by remember { mutableStateOf(securePrefs.getString("email_address", "") ?: "") }
                var emailPasswd by remember { mutableStateOf(securePrefs.getString("email_password", "") ?: "") }
                var showEmailPass by remember { mutableStateOf(false) }
                var emailImapHost by remember { mutableStateOf(prefs.getString("email_imap_host", "imap.gmail.com") ?: "imap.gmail.com") }
                var emailImapPort by remember { mutableStateOf(prefs.getInt("email_imap_port", 993).toString()) }
                var emailSmtpHost by remember { mutableStateOf(prefs.getString("email_smtp_host", "smtp.gmail.com") ?: "smtp.gmail.com") }
                var emailSmtpPort by remember { mutableStateOf(prefs.getInt("email_smtp_port", 587).toString()) }
                var emailTestResult by remember { mutableStateOf("") }
                var showServerFields by remember { mutableStateOf(false) }

                // Presets – auto-fill servers
                Text("PROVEEDOR", fontSize = 10.sp, color = textSecondary, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Gmail", "Outlook", "Dominio propio").forEach { name ->
                        FilterChip(
                            selected = when (name) {
                                "Gmail" -> emailImapHost.contains("gmail")
                                "Outlook" -> emailImapHost.contains("office365") || emailImapHost.contains("outlook")
                                else -> !emailImapHost.contains("gmail") && !emailImapHost.contains("office365") && !emailImapHost.contains("outlook")
                            },
                            onClick = {
                                if (name == "Dominio propio") {
                                    showServerFields = true
                                    emailImapHost = ""
                                    emailSmtpHost = ""
                                    emailTestResult = "Configura los servidores IMAP/SMTP de tu dominio"
                                } else {
                                    val preset = com.beemovil.email.EmailService.PRESETS[name] ?: return@FilterChip
                                    emailImapHost = preset.imapHost
                                    emailImapPort = preset.imapPort.toString()
                                    emailSmtpHost = preset.smtpHost
                                    emailSmtpPort = preset.smtpPort.toString()
                                    showServerFields = false
                                    emailTestResult = ""
                                }
                            },
                            label = { Text(name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent,
                                selectedLabelColor = if (isDark) BeeBlack else Color.White,
                                containerColor = textSecondary.copy(alpha = 0.3f),
                                labelColor = Color(0xFFE0E0E0)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = emailAddr,
                    onValueChange = {
                        emailAddr = it
                        // Auto-detect provider from email
                        val lower = it.lowercase()
                        when {
                            lower.contains("@gmail") -> {
                                val p = com.beemovil.email.EmailService.PRESETS["Gmail"]!!
                                emailImapHost = p.imapHost; emailSmtpHost = p.smtpHost
                                emailImapPort = p.imapPort.toString(); emailSmtpPort = p.smtpPort.toString()
                                showServerFields = false
                            }
                            lower.contains("@outlook") || lower.contains("@hotmail") || lower.contains("@live") -> {
                                val p = com.beemovil.email.EmailService.PRESETS["Outlook"]!!
                                emailImapHost = p.imapHost; emailSmtpHost = p.smtpHost
                                emailImapPort = p.imapPort.toString(); emailSmtpPort = p.smtpPort.toString()
                                showServerFields = false
                            }
                            lower.contains("@") && lower.substringAfter("@").contains(".") -> {
                                // Custom domain — auto-try mail.domain.com
                                val domain = lower.substringAfter("@")
                                emailImapHost = "mail.$domain"
                                emailSmtpHost = "mail.$domain"
                                emailImapPort = "993"; emailSmtpPort = "587"
                                showServerFields = true
                            }
                        }
                    },
                    label = { Text("Correo electrónico") },
                    placeholder = { Text("usuario@gmail.com", color = textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )

                // Gmail App Password hint
                if (emailImapHost.contains("gmail")) {
                    Text(
                        "Gmail requiere App Password (no tu contrasena normal).\nVe a myaccount.google.com > Seguridad > Contrasenas de apps",
                        fontSize = 10.sp, color = accent.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = emailPasswd,
                    onValueChange = { emailPasswd = it },
                    label = { Text("Contraseña / App Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showEmailPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showEmailPass = !showEmailPass }) {
                            Icon(if (showEmailPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle", tint = textSecondary)
                        }
                    },
                    colors = fieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Server fields — expandable
                Surface(
                    onClick = { showServerFields = !showServerFields },
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Settings, "Server", tint = BeeGray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Servidores", fontSize = 13.sp, color = Color(0xFFE0E0E0))
                            Text("IMAP: $emailImapHost:$emailImapPort · SMTP: $emailSmtpHost:$emailSmtpPort",
                                fontSize = 10.sp, color = textSecondary)
                        }
                        Text(if (showServerFields) "▲" else "▼", color = textSecondary)
                    }
                }

                if (showServerFields) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = emailImapHost,
                            onValueChange = { emailImapHost = it },
                            label = { Text("IMAP Host") },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            colors = fieldColors()
                        )
                        OutlinedTextField(
                            value = emailImapPort,
                            onValueChange = { emailImapPort = it },
                            label = { Text("Puerto") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = fieldColors()
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = emailSmtpHost,
                            onValueChange = { emailSmtpHost = it },
                            label = { Text("SMTP Host") },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            colors = fieldColors()
                        )
                        OutlinedTextField(
                            value = emailSmtpPort,
                            onValueChange = { emailSmtpPort = it },
                            label = { Text("Puerto") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = fieldColors()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            securePrefs.edit()
                                .putString("email_address", emailAddr.trim())
                                .putString("email_password", emailPasswd)
                                .apply()
                            prefs.edit()
                                .putString("email_imap_host", emailImapHost)
                                .putInt("email_imap_port", emailImapPort.toIntOrNull() ?: 993)
                                .putString("email_smtp_host", emailSmtpHost)
                                .putInt("email_smtp_port", emailSmtpPort.toIntOrNull() ?: 587)
                                .apply()
                            Toast.makeText(context, "Email configurado", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = textSecondary.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Guardar", color = textPrimary, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            emailTestResult = "Conectando a $emailImapHost..."
                            // Also save before testing
                            securePrefs.edit()
                                .putString("email_address", emailAddr.trim())
                                .putString("email_password", emailPasswd)
                                .apply()
                            prefs.edit()
                                .putString("email_imap_host", emailImapHost)
                                .putInt("email_imap_port", emailImapPort.toIntOrNull() ?: 993)
                                .putString("email_smtp_host", emailSmtpHost)
                                .putInt("email_smtp_port", emailSmtpPort.toIntOrNull() ?: 587)
                                .apply()
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val config = com.beemovil.email.EmailService.EmailConfig(
                                        emailImapHost, emailImapPort.toIntOrNull() ?: 993,
                                        emailSmtpHost, emailSmtpPort.toIntOrNull() ?: 587
                                    )
                                    val result = com.beemovil.email.EmailService(context)
                                        .testConnection(emailAddr.trim(), emailPasswd, config)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        emailTestResult = "OK Conectado - $result"
                                    }
                                } catch (e: Exception) {
                                    val msg = e.message ?: "Error desconocido"
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        emailTestResult = when {
                                            msg.contains("Authentication", true) || msg.contains("AUTHENTICATE", true) ->
                                                "Error: Credenciales incorrectas. Gmail necesita App Password"
                                            msg.contains("connect", true) || msg.contains("timeout", true) ->
                                                "Error: No pudo conectar a $emailImapHost. Verifica el servidor"
                                            msg.contains("SSL", true) || msg.contains("TLS", true) ->
                                                "Error: SSL. Prueba otro puerto (993 para IMAP)"
                                            else -> "Error: ${msg.take(80)}"
                                        }
                                    }
                                }
                            }
                        },
                        enabled = emailAddr.isNotBlank() && emailPasswd.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Probar", color = textPrimary, fontSize = 13.sp)
                    }
                }

                if (emailTestResult.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(emailTestResult, fontSize = 12.sp,
                        color = when {
                            emailTestResult.startsWith("OK") -> Color(0xFF4CAF50)
                            emailTestResult.startsWith("Error") -> Color(0xFFF44336)
                            else -> textSecondary
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFFC107).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Gmail requiere App Password", fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, color = Color(0xFFFFC107))
                        Text("1. Ve a myaccount.google.com\n2. Seguridad > Verificación en 2 pasos (activar)\n3. App Passwords > genera una y pégala aquí\n\nOutlook/Hotmail: usa tu contraseña normal",
                            fontSize = 10.sp, color = textSecondary)
                    }
                }
            }

            // ═══════════════════════════════════════
            // DATOS Y ALMACENAMIENTO
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("DATOS Y ALMACENAMIENTO")
                Spacer(modifier = Modifier.height(8.dp))

                // S-15: Contadores reales desde la base de datos
                val threadCount by androidx.compose.runtime.produceState(initialValue = 0) {
                    value = viewModel.chatHistoryDB.chatHistoryDao().getAllThreads().size
                }
                val agentCount = viewModel.availableAgents.size

                DataRow("Hilos de chat", "$threadCount")
                DataRow("Agentes", "$agentCount")

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearAllHistory()
                            Toast.makeText(context, "Historial limpiado", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Limpiar chats", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "Exportacion proximamente", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Exportar", fontSize = 12.sp)
                    }
                }
            }

            // ═══════════════════════════════════════
            // SKILLS
            // ═══════════════════════════════════════
            SectionCard {
                val skillGroups = listOf(
                    "Core" to listOf("datetime", "web_search", "memory", "flashlight", "volume", "telemetry"),
                    "Código" to listOf("code_sandbox"),
                    "Web + Docs" to listOf("web_scraper", "analyze_image", "read_document"),
                    "Generadores" to listOf("export_pdf", "export_csv", "html_forge"),
                    "OS + Contacts" to listOf("contacts", "calendar", "god_mode"),
                    "Comunicación" to listOf("email", "whatsapp", "web_api_fetch")
                )
                val totalSkills = skillGroups.sumOf { it.second.size }
                SectionTitle("SKILLS NATIVOS ($totalSkills)")
                Spacer(modifier = Modifier.height(6.dp))

                skillGroups.forEach { (group, skills) ->
                    Text(group, fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold)
                    Text(skills.joinToString("  "), fontSize = 12.sp, color = if (isDark) Color(0xFFB0B0B0) else TextGrayDark)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // ═══════════════════════════════════════
            // ACERCA DE
            // ═══════════════════════════════════════
            SectionCard {
                SectionTitle("ACERCA DE")
                Spacer(modifier = Modifier.height(6.dp))
                Text("E.M.M.A. AI", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textPrimary)
                Text("v4.2.1 · Kotlin nativo · Edge AI · Multi-Agent", fontSize = 12.sp, color = textSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Enhanced Multi-Modal Mobile Assistant.\nTu asistente AI personal que vive en tu teléfono.\nSin servidores externos. Sin tracking. 100% tuyo.",
                    fontSize = 12.sp, color = if (isDark) Color(0xFFB0B0B0) else TextGrayDark)
                Spacer(modifier = Modifier.height(12.dp))

                // Documentation & Tutorial link
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://helloemma.ai/docs"))
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.MenuBook, "Docs", tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Documentación y Tutorial", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Guía completa de uso y configuración", color = textSecondary, fontSize = 11.sp)
                        }
                        Icon(Icons.Filled.ChevronRight, "→", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF2A2A3D) else LightBorder)

                // Licenses link
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://helloemma.ai/licenses"))
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Policy, "Licencias", tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Licencias y Términos", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Open source licenses y términos de uso", color = textSecondary, fontSize = 11.sp)
                        }
                        Icon(Icons.Filled.ChevronRight, "→", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
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
    val isDark = isDarkTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1A1A2E) else LightSurface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    val accent = if (isDarkTheme()) BeeYellow else BrandBlue
    Text(text, fontSize = 11.sp, color = accent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun DataRow(label: String, value: String) {
    val isDark = isDarkTheme()
    val accent = if (isDark) BeeYellow else BrandBlue
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = if (isDark) Color(0xFFE0E0E0) else TextDark, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProviderChip(label: String, id: String, selected: String, onClick: (String) -> Unit) {
    val isDark = isDarkTheme()
    val accent = if (isDark) BeeYellow else BrandBlue
    FilterChip(
        selected = selected == id,
        onClick = { onClick(id) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent,
            selectedLabelColor = if (isDark) BeeBlack else Color.White,
            containerColor = if (isDark) BeeGray.copy(alpha = 0.3f) else LightCard,
            labelColor = if (isDark) Color(0xFFE0E0E0) else TextDark
        )
    )
}

@Composable
private fun ServiceBadge(label: String, active: Boolean) {
    val isDark = isDarkTheme()
    val muted = if (isDark) BeeGray else TextGrayDark
    Surface(
        color = if (active) Color(0xFF4CAF50).copy(alpha = 0.2f) else muted.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                if (active) Icons.Filled.Check else Icons.Filled.Lock,
                label,
                tint = if (active) Color(0xFF4CAF50) else muted,
                modifier = Modifier.size(10.dp)
            )
            Text(label, fontSize = 9.sp, color = if (active) Color(0xFF4CAF50) else muted)
        }
    }
}

@Composable
private fun fieldColors(): androidx.compose.material3.TextFieldColors {
    val isDark = isDarkTheme()
    val accent = if (isDark) BeeYellow else BrandBlue
    val textColor = if (isDark) BeeWhite else TextDark
    val labelColor = if (isDark) BeeGrayLight else TextGrayDark
    val borderColor = if (isDark) Color(0xFF333355) else LightBorder
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = borderColor,
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        focusedLabelColor = accent,
        unfocusedLabelColor = labelColor,
        cursorColor = accent
    )
}

/** Helper to get debug keystore SHA-1 for Google Cloud Console setup */
private fun getDebugSha1(): String {
    return try {
        val debugKeystore = java.io.File(
            System.getProperty("user.home"), ".android/debug.keystore"
        )
        if (!debugKeystore.exists()) return "(keystore no encontrado)"
        val ks = java.security.KeyStore.getInstance("JKS")
        debugKeystore.inputStream().use { ks.load(it, "android".toCharArray()) }
        val cert = ks.getCertificate("androiddebugkey")
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val digest = md.digest(cert.encoded)
        digest.joinToString(":") { "%02X".format(it) }
    } catch (e: Exception) {
        "(no se pudo leer: ${e.message})"
    }
}
