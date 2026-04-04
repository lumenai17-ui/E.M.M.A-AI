package com.beemovil.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.beemovil.ui.theme.*

@Composable
fun SettingsScreen(
    onApiKeySaved: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
    var apiKey by remember { mutableStateOf(prefs.getString("openrouter_api_key", "") ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellow)
            }
            Text(
                text = "⚙️ Configuración",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BeeWhite
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // API Key section
        Card(
            colors = CardDefaults.cardColors(containerColor = BeeBlackLight),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "🔑 OpenRouter API Key",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BeeYellow
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Para usar modelos como Qwen, GPT-4o, Claude, etc.\nObtén tu key gratis en openrouter.ai",
                    fontSize = 13.sp,
                    color = BeeGrayLight
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    placeholder = { Text("sk-or-v1-...") },
                    visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                "Toggle visibility",
                                tint = BeeGrayLight
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeeYellow,
                        unfocusedBorderColor = BeeGray,
                        cursorColor = BeeYellow,
                        focusedTextColor = BeeWhite,
                        unfocusedTextColor = BeeWhite,
                        focusedLabelColor = BeeYellow,
                        unfocusedLabelColor = BeeGrayLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        prefs.edit().putString("openrouter_api_key", apiKey.trim()).apply()
                        onApiKeySaved(apiKey.trim())
                        saved = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BeeYellow,
                        contentColor = BeeBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Save, "Save")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }

                if (saved) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✅ Key guardada correctamente",
                        color = BeeGreen,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Model info
        Card(
            colors = CardDefaults.cardColors(containerColor = BeeBlackLight),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "🤖 Modelos disponibles",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BeeYellow
                )
                Spacer(modifier = Modifier.height(12.dp))

                ModelRow("🆓", "Qwen 3.6 Plus", "Gratis")
                ModelRow("🆓", "Llama 3.3 70B", "Gratis")
                ModelRow("💰", "Gemini 2.5 Flash", "~$0.10/1M tokens")
                ModelRow("💎", "GPT-4o", "~$2.50/1M tokens")
                ModelRow("💎", "Claude 3.5 Sonnet", "~$3/1M tokens")
            }
        }
    }
}

@Composable
private fun ModelRow(icon: String, name: String, price: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = name, color = BeeWhite, fontSize = 14.sp)
        }
        Text(text = price, color = BeeGrayLight, fontSize = 12.sp)
    }
}
