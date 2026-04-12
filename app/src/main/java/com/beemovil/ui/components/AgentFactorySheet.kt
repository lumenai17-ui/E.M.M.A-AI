package com.beemovil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemovil.ui.theme.BeeBlack
import com.beemovil.ui.theme.BeeGray
import com.beemovil.ui.theme.BeeWhite
import com.beemovil.ui.theme.BeeYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentFactorySheet(
    onDismiss: () -> Unit,
    onForgeAgent: (name: String, icon: String, prompt: String, model: String) -> Unit
) {
    var agentName by remember { mutableStateOf("") }
    var agentIcon by remember { mutableStateOf("🤖") }
    var systemPrompt by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("koog-engine") }

    val modelOptions = remember {
        val options = mutableListOf<Pair<String, String>>()
        options.add("koog-engine" to "K.O.O.G Engine (Default)")
        options.add("hermes-a2a" to "Hermes A2A (Remoto)")
        
        com.beemovil.llm.ModelRegistry.OPENROUTER.forEach { model ->
            options.add(model.id to "Cloud: ${model.name}")
        }
        com.beemovil.llm.ModelRegistry.OLLAMA_CLOUD.forEach { model ->
            options.add(model.id to "Ollama: ${model.name}")
        }
        com.beemovil.llm.ModelRegistry.LOCAL.forEach { model ->
            options.add(model.id to "Native Offline: ${model.name}")
        }
        options
    }

    var expandedMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2C), // Glassmorphism Dark
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "La Forja (Agent Factory)",
                color = BeeWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar & Name
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = agentIcon,
                    onValueChange = { if (it.length <= 2) agentIcon = it },
                    label = { Text("Icono", color = BeeGray) },
                    modifier = Modifier.weight(0.3f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                        focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray
                    )
                )
                OutlinedTextField(
                    value = agentName,
                    onValueChange = { agentName = it },
                    label = { Text("Nombre Operativo", color = BeeGray) },
                    modifier = Modifier.weight(0.7f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                        focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ADN Prompt
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt (El ADN del experto)", color = BeeGray) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                    focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray
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
                    label = { Text("Motor de Inteligencia", color = BeeGray) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMenu) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BeeWhite, unfocusedTextColor = BeeWhite,
                        focusedBorderColor = BeeYellow, unfocusedBorderColor = BeeGray
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false },
                    modifier = Modifier.background(Color(0xFF2A2A3D))
                ) {
                    modelOptions.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption.second, color = BeeWhite) },
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
                        onForgeAgent(agentName, agentIcon, systemPrompt, selectedModel)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("FORJAR AGENTE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
