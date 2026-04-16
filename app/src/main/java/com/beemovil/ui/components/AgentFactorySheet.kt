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
import com.beemovil.ui.theme.*
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
    var selectedModel by remember { mutableStateOf("koog-engine") }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var savedAvatarPath by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Copy to app's internal storage so it persists
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

    val modelOptions = remember {
        val options = mutableListOf<Pair<String, String>>()
        options.add("koog-engine" to "⚙️ Usar config global (Default)")
        options.add("hermes-a2a" to "🔗 Hermes A2A (Túnel Remoto)")
        
        // OpenRouter models with free/premium badges
        com.beemovil.llm.ModelRegistry.OPENROUTER.forEach { model ->
            val badge = if (model.free) "🆓" else "💎"
            options.add("openrouter:${model.id}" to "$badge ${model.name}")
        }
        // Ollama Cloud
        com.beemovil.llm.ModelRegistry.OLLAMA_CLOUD.forEach { model ->
            options.add("ollama:${model.id}" to "☁️ ${model.name}")
        }
        // Local on-device
        com.beemovil.llm.ModelRegistry.LOCAL.forEach { model ->
            options.add("local:${model.id}" to "📱 ${model.name}")
        }
        options
    }

    var expandedMenu by remember { mutableStateOf(false) }

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

            // AI Engine Selection
            ExposedDropdownMenuBox(
                expanded = expandedMenu,
                onExpandedChange = { expandedMenu = !expandedMenu }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = modelOptions.find { it.first == selectedModel }?.second ?: selectedModel,
                    onValueChange = {},
                    label = { Text("Motor de Inteligencia", color = textSecondary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMenu) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary, unfocusedTextColor = textPrimary,
                        focusedBorderColor = accent, unfocusedBorderColor = textSecondary
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false },
                    modifier = Modifier.background(dropdownBg)
                ) {
                    modelOptions.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.second, color = textPrimary) },
                            onClick = {
                                selectedModel = selectionOption.first
                                expandedMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // FORGE ACTION
            Button(
                onClick = {
                    if (agentName.isNotBlank() && systemPrompt.isNotBlank()) {
                        if (onForgeAgentWithAvatar != null) {
                            onForgeAgentWithAvatar(agentName, agentIcon, systemPrompt, selectedModel, savedAvatarPath)
                        } else {
                            onForgeAgent(agentName, agentIcon, systemPrompt, selectedModel)
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
