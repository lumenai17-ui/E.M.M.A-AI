package com.beemovil.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.beemovil.llm.DynamicModelFetcher
import com.beemovil.llm.ModelRegistry
import com.beemovil.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentFactorySheet(
    onDismiss: () -> Unit,
    onForgeAgent: (name: String, icon: String, prompt: String, model: String) -> Unit,
    onForgeAgentWithAvatar: ((name: String, icon: String, prompt: String, model: String, avatarUri: String?) -> Unit)? = null
) {
    val isDark = isDarkTheme()
    val sheetBg = if (isDark) Color(0xFF1E1E2C) else LightSurface
    val textPrimary = if (isDark) BeeWhite else TextDark
    val textSecondary = if (isDark) BeeGray else TextGrayDark
    val accent = if (isDark) BeeYellow else BrandBlue
    val dropdownBg = if (isDark) Color(0xFF2A2A3D) else LightCard

    var agentName by remember { mutableStateOf("") }
    var agentIcon by remember { mutableStateOf("🤖") }
    var systemPrompt by remember { mutableStateOf("") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var savedAvatarPath by remember { mutableStateOf<String?>(null) }

    // Two-dropdown state
    var selectedProvider by remember { mutableStateOf("default") }
    var selectedModelId by remember { mutableStateOf("") }
    var availableModels by remember { mutableStateOf<List<ModelRegistry.ModelEntry>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var expandedProvider by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Provider options
    val providerOptions = remember {
        listOf(
            "default" to "⚙️ Usar Config Global (Default)",
            "openrouter" to "🌐 OpenRouter",
            "ollama" to "☁️ Ollama Cloud",
            "local" to "📱 Local (On-Device)",
            "hermes-a2a" to "🔗 Hermes A2A (Túnel Remoto)"
        )
    }

    // Load models when provider changes
    LaunchedEffect(selectedProvider) {
        if (selectedProvider in listOf("default", "hermes-a2a")) {
            availableModels = emptyList()
            selectedModelId = ""
            return@LaunchedEffect
        }
        isLoadingModels = true
        selectedModelId = ""
        try {
            val models = DynamicModelFetcher.fetchForProvider(context, selectedProvider)
            availableModels = models
            // Auto-select first model if available
            if (models.isNotEmpty()) {
                selectedModelId = models.first().id
            }
        } catch (e: Exception) {
            // Fallback to static registry
            availableModels = ModelRegistry.getModelsForProvider(selectedProvider)
            if (availableModels.isNotEmpty()) {
                selectedModelId = availableModels.first().id
            }
        }
        isLoadingModels = false
    }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val agentsDir = File(context.filesDir, "agent_avatars")
                agentsDir.mkdirs()
                val destFile = File(agentsDir, "avatar_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                savedAvatarPath = destFile.absolutePath
                selectedAvatarUri = Uri.fromFile(destFile)
            } catch (e: Exception) {
                selectedAvatarUri = uri
                savedAvatarPath = uri.toString()
            }
        }
    }

    // Build the final model string for onForgeAgent
    fun buildModelString(): String = when (selectedProvider) {
        "default" -> "koog-engine"
        "hermes-a2a" -> "hermes-a2a"
        "openrouter" -> "openrouter:$selectedModelId"
        "ollama" -> "ollama:$selectedModelId"
        "local" -> "local:$selectedModelId"
        else -> "koog-engine"
    }

    // Format model display with badges
    fun ModelRegistry.ModelEntry.badgedName(): String {
        val badges = buildString {
            if (free) append("🆓 ")
            else append("💎 ")
            if (hasTools) append("🔧")
            if (hasVision) append("👁️")
            if (hasThinking) append("🧠")
        }
        return "$badges $name"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "La Forja (Agent Factory)",
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar photo + icon selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clickable avatar circle
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF2A2A3D) else Color(0xFFEEEEEE))
                        .border(2.dp, accent.copy(alpha = 0.5f), CircleShape)
                        .clickable { photoPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedAvatarUri != null) {
                        AsyncImage(
                            model = selectedAvatarUri,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(agentIcon, fontSize = 28.sp)
                            Icon(Icons.Filled.CameraAlt, "Foto", tint = textSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Name + Icon fields
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = agentName,
                        onValueChange = { agentName = it },
                        label = { Text("Nombre Operativo", color = textSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                            focusedBorderColor = accent, unfocusedBorderColor = textSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("🤖", "🧠", "📊", "💼", "🎨", "🔬", "⚡", "🛡️").forEach { emoji ->
                            Surface(
                                color = if (agentIcon == emoji) accent.copy(alpha = 0.3f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { agentIcon = emoji }
                            ) {
                                Text(emoji, fontSize = 18.sp, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ADN Prompt
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt (El ADN del experto)", color = textSecondary) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                    focusedBorderColor = accent, unfocusedBorderColor = textSecondary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ═══════════════════════════════════════
            // DROPDOWN 1: PROVIDER SELECTION
            // ═══════════════════════════════════════
            Text("Motor de Inteligencia", color = textSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))

            ExposedDropdownMenuBox(
                expanded = expandedProvider,
                onExpandedChange = { expandedProvider = !expandedProvider }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = providerOptions.find { it.first == selectedProvider }?.second ?: "",
                    onValueChange = {},
                    label = { Text("Proveedor", color = textSecondary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                        focusedBorderColor = accent, unfocusedBorderColor = textSecondary
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedProvider,
                    onDismissRequest = { expandedProvider = false },
                    modifier = Modifier.background(dropdownBg)
                ) {
                    providerOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = textPrimary) },
                            onClick = {
                                selectedProvider = key
                                expandedProvider = false
                            }
                        )
                    }
                }
            }

            // ═══════════════════════════════════════
            // DROPDOWN 2: MODEL SELECTION (dynamic)
            // ═══════════════════════════════════════
            if (selectedProvider !in listOf("default", "hermes-a2a")) {
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoadingModels) {
                    // Loading state
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = accent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Cargando modelos disponibles...", color = textSecondary, fontSize = 13.sp)
                    }
                } else {
                    // Model count indicator
                    Text(
                        "✅ ${availableModels.size} modelos disponibles",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    ExposedDropdownMenuBox(
                        expanded = expandedModel,
                        onExpandedChange = { expandedModel = !expandedModel }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = availableModels.find { it.id == selectedModelId }?.badgedName() ?: "Selecciona un modelo",
                            onValueChange = {},
                            label = { Text("Modelo", color = textSecondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                                focusedBorderColor = accent, unfocusedBorderColor = textSecondary
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedModel,
                            onDismissRequest = { expandedModel = false },
                            modifier = Modifier
                                .background(dropdownBg)
                                .heightIn(max = 300.dp)
                        ) {
                            // Group by category
                            val grouped = availableModels.groupBy { it.category }
                            grouped.forEach { (category, models) ->
                                // Category header
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "── ${category.icon} ${category.label} ──",
                                            color = accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {},
                                    enabled = false
                                )
                                // Models in this category
                                models.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(model.badgedName(), color = textPrimary, fontSize = 14.sp)
                                                if (model.description.isNotBlank()) {
                                                    Text(
                                                        "${model.sizeLabel} • ${model.description}",
                                                        color = textSecondary,
                                                        fontSize = 11.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedModelId = model.id
                                            expandedModel = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // FORGE ACTION
            Button(
                onClick = {
                    if (agentName.isNotBlank() && systemPrompt.isNotBlank()) {
                        val modelString = buildModelString()
                        if (onForgeAgentWithAvatar != null) {
                            onForgeAgentWithAvatar(agentName, agentIcon, systemPrompt, modelString, savedAvatarPath)
                        } else {
                            onForgeAgent(agentName, agentIcon, systemPrompt, modelString)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (isDark) BeeBlack else Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("FORJAR AGENTE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
