package com.beemovil.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.beemovil.llm.ChatMessage
import com.beemovil.llm.LlmFactory
import com.beemovil.llm.OllamaCloudProvider
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("beemovil", 0) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageBase64 by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }

    // Vision model — separate from global chat model
    var selectedVisionModel by remember {
        mutableStateOf(prefs.getString("vision_model", "gemma4:31b-cloud") ?: "gemma4:31b-cloud")
    }
    var showModelPicker by remember { mutableStateOf(false) }

    // Camera capture
    val photoFile = remember { File(context.cacheDir, "bee_camera_${System.currentTimeMillis()}.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            if (bitmap != null) {
                val scale = minOf(800f / bitmap.width, 800f / bitmap.height, 1f)
                val resized = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
                capturedBitmap = resized
                val baos = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val input = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(input)
                input?.close()
                if (bitmap != null) {
                    val scale = minOf(800f / bitmap.width, 800f / bitmap.height, 1f)
                    val resized = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt(),
                        true
                    )
                    capturedBitmap = resized
                    val baos = ByteArrayOutputStream()
                    resized.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission launcher
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(photoUri)
        else Toast.makeText(context, "Permiso de cámara necesario", Toast.LENGTH_SHORT).show()
    }

    // Quick prompts
    val quickPrompts = listOf(
        "🔍" to "¿Qué es esto? Descríbelo en detalle.",
        "📝" to "Extrae todo el texto visible en esta imagen (OCR).",
        "💰" to "Analiza esta factura o recibo. Extrae monto, fecha, concepto.",
        "📊" to "Analiza los datos o gráficos en esta imagen.",
        "🏷️" to "Identifica la marca, producto o modelo que ves.",
        "🌿" to "Identifica esta planta, animal o elemento natural."
    )

    // ── Analysis function using dedicated vision model ──
    fun analyzeImage(question: String) {
        if (imageBase64.isBlank()) return
        isAnalyzing = true
        analysisResult = ""

        Thread {
            try {
                // Get Ollama API key
                val apiKey = prefs.getString("ollama_api_key", "") ?: ""
                if (apiKey.isBlank()) {
                    analysisResult = "⚠️ Configura tu API key de Ollama en Settings"
                    isAnalyzing = false
                    return@Thread
                }

                // Create a DEDICATED vision provider (not the global chat one)
                val visionProvider = OllamaCloudProvider(
                    apiKey = apiKey,
                    model = selectedVisionModel
                )

                // Send image + question directly
                val messages = listOf(
                    ChatMessage(role = "user", content = question, images = listOf(imageBase64))
                )
                val response = visionProvider.complete(messages, emptyList())
                analysisResult = response.text ?: "Sin respuesta del modelo"
            } catch (e: Exception) {
                val msg = e.message ?: "Error desconocido"
                analysisResult = when {
                    msg.contains("404") || msg.contains("not found", true) ->
                        "❌ Modelo '$selectedVisionModel' no encontrado en Ollama Cloud.\nPrueba con 'llava' o 'llama3.2-vision'"
                    msg.contains("timeout", true) ->
                        "❌ Timeout - la imagen puede ser muy grande o el modelo tarda"
                    else -> "❌ ${msg.take(120)}"
                }
            }
            isAnalyzing = false
        }.start()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BeeBlack)
    ) {
        // Header
        TopAppBar(
            title = {
                Column {
                    Text("Visión AI", fontWeight = FontWeight.Bold)
                    Text("👁️ $selectedVisionModel · Analiza imágenes", fontSize = 11.sp, color = BeeGray)
                }
            },
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ═══ Vision Model Selector ═══
            Surface(
                onClick = { showModelPicker = !showModelPicker },
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👁️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MODELO DE VISIÓN", fontSize = 10.sp, color = BeeYellow,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        val displayName = LlmFactory.VISION_MODELS.find { it.id == selectedVisionModel }?.name
                            ?: selectedVisionModel
                        Text(displayName, fontSize = 14.sp, color = BeeWhite)
                        Text(selectedVisionModel, fontSize = 10.sp, color = BeeGray)
                    }
                    Text(if (showModelPicker) "▲" else "▼", color = BeeYellow)
                }
            }

            if (showModelPicker) {
                Spacer(modifier = Modifier.height(4.dp))
                LlmFactory.VISION_MODELS.forEach { model ->
                    val isSelected = selectedVisionModel == model.id
                    Surface(
                        onClick = {
                            selectedVisionModel = model.id
                            prefs.edit().putString("vision_model", model.id).apply()
                            showModelPicker = false
                        },
                        color = if (isSelected) BeeYellow.copy(alpha = 0.15f) else Color(0xFF2A2A3E),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("👁️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.name, fontSize = 14.sp,
                                    color = if (isSelected) BeeYellow else Color(0xFFE0E0E0))
                                Text(model.id, fontSize = 10.sp, color = BeeGray)
                            }
                            if (isSelected) Text("✓", color = BeeYellow, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Image preview or capture prompt
            if (capturedBitmap != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Captured",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                        // Clear button
                        IconButton(
                            onClick = { capturedBitmap = null; imageBase64 = ""; analysisResult = "" },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                .background(BeeBlack.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, "Clear", tint = BeeWhite)
                        }
                    }
                }
            } else {
                // No image — show capture options
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📸", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Captura o selecciona una imagen", fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = BeeWhite)
                        Text("El modelo de visión analizará lo que vea", fontSize = 13.sp, color = BeeGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Capture buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(photoUri)
                        } else {
                            permLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, "Camera", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Foto", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeYellow),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, "Gallery", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Galería", fontSize = 13.sp)
                }
                Button(
                    onClick = { viewModel.currentScreen.value = "live_vision" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336), contentColor = BeeWhite),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🔴", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Live", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Quick prompts
            if (capturedBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("ANÁLISIS RÁPIDO", fontSize = 11.sp, color = BeeYellow,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))

                // 2-column grid of quick actions
                val rows = quickPrompts.chunked(2)
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (icon, p) ->
                            Surface(
                                onClick = {
                                    prompt = p
                                    analyzeImage(p)
                                },
                                color = Color(0xFF2A2A3E),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).padding(vertical = 3.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(icon, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(p.take(25) + if (p.length > 25) "..." else "",
                                        fontSize = 12.sp, color = Color(0xFFE0E0E0))
                                }
                            }
                        }
                    }
                }

                // Custom prompt
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Pregunta personalizada") },
                    placeholder = { Text("¿Qué quieres saber de esta imagen?", color = BeeGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeeYellow,
                        unfocusedBorderColor = Color(0xFF333355),
                        focusedTextColor = BeeWhite,
                        unfocusedTextColor = BeeWhite,
                        cursorColor = BeeYellow,
                        focusedLabelColor = BeeYellow,
                        unfocusedLabelColor = BeeGrayLight
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (prompt.isBlank()) {
                            Toast.makeText(context, "Escribe una pregunta", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        analyzeImage(prompt)
                    },
                    enabled = prompt.isNotBlank() && !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BeeBlack, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizando...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, "Analyze", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analizar imagen", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Result
            if (analysisResult.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🐝 RESULTADO", fontSize = 11.sp, color = BeeYellow,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("via $selectedVisionModel", fontSize = 9.sp, color = BeeGray)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(analysisResult, fontSize = 14.sp, color = Color(0xFFE0E0E0), lineHeight = 22.sp)

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.openAgentChatWithPrompt("main",
                                        "Resultado de análisis de imagen ($selectedVisionModel): $analysisResult\n\n¿Puedes elaborar más?")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeYellow),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("🐝 Chat", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("result", analysisResult))
                                    Toast.makeText(context, "📋 Copiado", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeGray),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("📋 Copiar", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (isAnalyzing) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = BeeYellow, trackColor = Color(0xFF1A1A2E)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("👁️ Analizando con $selectedVisionModel...",
                    fontSize = 13.sp, color = BeeGray,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
