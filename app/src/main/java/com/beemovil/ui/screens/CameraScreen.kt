package com.beemovil.ui.screens

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.beemovil.llm.*
import com.beemovil.ui.ChatViewModel
import com.beemovil.vision.GpsModule
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * CameraScreen — E.M.M.A. Vision Phase V1: Los Ojos
 *
 * Static image analysis with 7 quick prompts and contextual GPS enrichment.
 * Supports: take photo (CameraX), pick from gallery, auto-select best vision provider.
 * Theme-aware: uses MaterialTheme.colorScheme for Light/Dark.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CameraScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("beemovil", 0) }
    val colorScheme = MaterialTheme.colorScheme

    // ── State ──
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageBase64 by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var selectedVisionModel by remember {
        mutableStateOf(prefs.getString("vision_model", "") ?: "")
    }
    var showModelPicker by remember { mutableStateOf(false) }

    // ── GPS for context enrichment ──
    val gpsModule = remember { GpsModule(context) }
    var gpsAddress by remember { mutableStateOf("") }
    var gpsCoords by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (gpsModule.hasPermission) {
            gpsModule.onLocationUpdate = { data ->
                gpsAddress = data.address
                gpsCoords = data.coordsShort
            }
            gpsModule.start()
        }
    }
    DisposableEffect(Unit) { onDispose { gpsModule.stop() } }

    // ── Auto-select best vision model ──
    LaunchedEffect(Unit) {
        if (selectedVisionModel.isBlank()) {
            selectedVisionModel = autoSelectVisionModel(context, viewModel)
            prefs.edit().putString("vision_model", selectedVisionModel).apply()
        }
    }

    // ── Camera capture ──
    val photoFile = remember { File(context.cacheDir, "emma_camera_${System.currentTimeMillis()}.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile.exists()) {
            processImageFile(photoFile)?.let { (bmp, b64) ->
                capturedBitmap = bmp; imageBase64 = b64; analysisResult = ""
            } ?: Toast.makeText(context, "Error procesando imagen", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Gallery picker ──
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            processImageUri(context, it)?.let { (bmp, b64) ->
                capturedBitmap = bmp; imageBase64 = b64; analysisResult = ""
            } ?: Toast.makeText(context, "Error procesando imagen", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Quick prompts (7) ──
    val quickPrompts = listOf(
        Triple(Icons.Filled.Description, "Describir", "Describe en detalle lo que ves en esta imagen."),
        Triple(Icons.Filled.TextFields, "OCR", "Extrae todo el texto visible en esta imagen (OCR)."),
        Triple(Icons.Filled.Receipt, "Factura", "Analiza esta factura o recibo. Extrae monto, fecha, concepto, emisor."),
        Triple(Icons.Filled.BarChart, "Datos", "Analiza los datos, tablas o gráficos en esta imagen."),
        Triple(Icons.Filled.Sell, "Marca", "Identifica la marca, producto o modelo que ves."),
        Triple(Icons.Filled.Park, "Natural", "Identifica esta planta, animal o elemento natural."),
        Triple(Icons.Filled.Place, "¿Qué lugar?", buildPlacePrompt(gpsAddress, gpsCoords))
    )

    // ── Analysis function ──
    fun analyzeImage(question: String) {
        if (imageBase64.isBlank()) return
        isAnalyzing = true; analysisResult = ""

        Thread {
            try {
                val enrichedPrompt = enrichWithGps(question, gpsAddress, gpsCoords)
                val modelEntry = ModelRegistry.findModel(selectedVisionModel)
                val providerType = modelEntry?.provider ?: viewModel.currentProvider.value
                val apiKey = getApiKeyForProvider(context, providerType)

                if (apiKey.isBlank() && providerType != "local") {
                    analysisResult = "Configura tu API key de $providerType en Settings"
                    isAnalyzing = false; return@Thread
                }

                val provider = LlmFactory.createProvider(providerType, apiKey, selectedVisionModel)
                val messages = listOf(
                    ChatMessage(role = "user", content = enrichedPrompt, images = listOf(imageBase64))
                )
                val response = provider.complete(messages, emptyList())
                analysisResult = response.text ?: "Sin respuesta del modelo"
            } catch (e: Exception) {
                analysisResult = "Error: ${e.message?.take(150)}"
            }
            isAnalyzing = false
        }.start()
    }

    // ── UI ──
    Column(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        // Header
        TopAppBar(
            title = {
                Column {
                    Text("Visión AI", fontWeight = FontWeight.Bold)
                    val modelName = ModelRegistry.findModel(selectedVisionModel)?.name ?: selectedVisionModel
                    Text(modelName, fontSize = 11.sp, color = colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = colorScheme.primary)
                }
            },
            actions = {
                IconButton(onClick = { showModelPicker = !showModelPicker }) {
                    Icon(Icons.Filled.Tune, "Model", tint = colorScheme.primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surface,
                titleContentColor = colorScheme.onSurface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Model Picker (collapsible) ──
            if (showModelPicker) {
                VisionModelPicker(
                    selected = selectedVisionModel,
                    onSelect = { id ->
                        selectedVisionModel = id
                        prefs.edit().putString("vision_model", id).apply()
                        showModelPicker = false
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── GPS context indicator ──
            if (gpsAddress.isNotBlank()) {
                Surface(
                    color = colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MyLocation, "GPS", tint = colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(gpsAddress, fontSize = 12.sp, color = colorScheme.onSecondaryContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Image preview or empty state ──
            if (capturedBitmap != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box {
                        capturedBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Captured",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        IconButton(
                            onClick = { capturedBitmap = null; imageBase64 = ""; analysisResult = "" },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                .background(colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, "Clear", tint = colorScheme.onSurface)
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.CameraAlt, "Camera", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Captura o selecciona una imagen", fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = colorScheme.onSurface)
                        Text("E.M.M.A. analizará lo que vea", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Capture buttons ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { cameraLauncher.launch(photoUri) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, "Camera", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Foto", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, "Gallery", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Galería", fontSize = 13.sp)
                }
                Button(
                    onClick = { viewModel.currentScreen.value = "live_vision" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.FiberManualRecord, "Live", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Live", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // ── Quick prompts (only when image loaded) ──
            if (capturedBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("ANÁLISIS RÁPIDO", fontSize = 11.sp, color = colorScheme.primary,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPrompts.forEach { (icon, label, p) ->
                        AssistChip(
                            onClick = { prompt = p; analyzeImage(p) },
                            label = { Text(label, fontSize = 12.sp) },
                            leadingIcon = { Icon(icon, label, modifier = Modifier.size(16.dp)) },
                            enabled = !isAnalyzing,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Custom prompt
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Pregunta personalizada") },
                    placeholder = { Text("¿Qué quieres saber de esta imagen?") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface,
                        cursorColor = colorScheme.primary,
                        focusedLabelColor = colorScheme.primary
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizando...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, "Analyze", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analizar imagen", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Analysis loading indicator ──
            if (isAnalyzing) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant
                )
            }

            // ── Result ──
            if (analysisResult.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, "AI", tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RESULTADO", fontSize = 11.sp, color = colorScheme.primary,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            val modelName = ModelRegistry.findModel(selectedVisionModel)?.name ?: selectedVisionModel
                            Text("via $modelName", fontSize = 9.sp, color = colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(analysisResult, fontSize = 14.sp, color = colorScheme.onSurface, lineHeight = 22.sp)

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.currentScreen.value = "chat"
                                    viewModel.sendMessage("Resultado de análisis de imagen: $analysisResult\n\n¿Puedes elaborar más?")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.primary),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Al Chat", fontSize = 12.sp) }

                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("result", analysisResult))
                                    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurfaceVariant),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Copiar", fontSize = 12.sp) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ═══════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════

/** Auto-select the best available vision model */
internal fun autoSelectVisionModel(context: android.content.Context, viewModel: ChatViewModel): String {
    val secPrefs = com.beemovil.security.SecurePrefs.get(context)

    // 1. Current provider has vision model?
    val currentProvider = viewModel.currentProvider.value
    val currentModel = viewModel.currentModel.value
    val currentEntry = ModelRegistry.findModel(currentModel)
    if (currentEntry?.hasVision == true) return currentModel

    // 2. Provider's best vision model
    val providerVision = ModelRegistry.getModelsForProvider(currentProvider)
        .firstOrNull { it.hasVision }
    if (providerVision != null) return providerVision.id

    // 3. Ollama Cloud with key?
    val ollamaKey = secPrefs.getString("ollama_api_key", "") ?: ""
    if (ollamaKey.isNotBlank()) return "gemma4:cloud"

    // 4. OpenRouter with key?
    val orKey = secPrefs.getString("openrouter_api_key", "") ?: ""
    if (orKey.isNotBlank()) return "openai/gpt-4o"

    // 5. Local model with vision?
    val localVision = ModelRegistry.LOCAL.firstOrNull {
        it.hasVision && com.beemovil.llm.local.LocalModelManager.isModelDownloaded(it.id)
    }
    if (localVision != null) return localVision.id

    // Fallback
    return "gemma4:cloud"
}

/** Get API key for a provider */
internal fun getApiKeyForProvider(context: android.content.Context, provider: String): String {
    val secPrefs = com.beemovil.security.SecurePrefs.get(context)
    return when (provider) {
        "openrouter" -> secPrefs.getString("openrouter_api_key", "") ?: ""
        "ollama" -> secPrefs.getString("ollama_api_key", "") ?: ""
        "local" -> ""
        else -> ""
    }
}

/** Enrich a prompt with GPS context if available */
private fun enrichWithGps(question: String, address: String, coords: String): String {
    if (address.isBlank() && coords.isBlank()) return question
    val gpsCtx = buildString {
        append("[Contexto GPS del usuario: ")
        if (address.isNotBlank()) append("ubicación: $address")
        if (coords.isNotBlank()) append(" ($coords)")
        append(". Usa esta información para enriquecer tu análisis si es relevante.]")
    }
    return "$gpsCtx\n\n$question"
}

/** Build the "What place is this?" prompt with GPS data */
private fun buildPlacePrompt(address: String, coords: String): String {
    val base = "¿Qué lugar, monumento, edificio o punto de interés es este? Describe su historia y datos relevantes."
    if (address.isBlank() && coords.isBlank()) return base
    return "$base\n[GPS: ${address.ifBlank { coords }}. Usa esta ubicación para identificar el lugar con mayor precisión.]"
}

/** Process a camera photo file into Bitmap + Base64 */
private fun processImageFile(file: File): Pair<Bitmap, String>? {
    return try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        resizeAndEncode(bitmap)
    } catch (_: Exception) { null }
}

/** Process a gallery URI into Bitmap + Base64 */
private fun processImageUri(context: android.content.Context, uri: Uri): Pair<Bitmap, String>? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(input)
        input.close()
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
        resizeAndEncode(bitmap)
    } catch (_: Exception) { null }
}

/** Resize to max 800px and encode to base64 JPEG */
private fun resizeAndEncode(bitmap: Bitmap): Pair<Bitmap, String> {
    val scale = minOf(800f / bitmap.width, 800f / bitmap.height, 1f)
    val resized = Bitmap.createScaledBitmap(
        bitmap,
        maxOf(1, (bitmap.width * scale).toInt()),
        maxOf(1, (bitmap.height * scale).toInt()),
        true
    )
    if (resized !== bitmap) bitmap.recycle() // Recycle original if a new one was created
    val baos = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, 85, baos)
    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    return Pair(resized, b64)
}

// ═══════════════════════════════════════════════════
// VISION MODEL PICKER
// ═══════════════════════════════════════════════════

@Composable
private fun VisionModelPicker(selected: String, onSelect: (String) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val visionModels = remember { ModelRegistry.getVisionModels() }

    Card(
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("MODELO DE VISIÓN", fontSize = 10.sp, color = colorScheme.primary,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            visionModels.forEach { model ->
                val isSelected = selected == model.id
                Surface(
                    onClick = { onSelect(model.id) },
                    color = if (isSelected) colorScheme.primaryContainer else colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Visibility, "Vision", tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, fontSize = 13.sp,
                                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            Text("${model.provider} · ${model.sizeLabel}", fontSize = 10.sp,
                                color = colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) Icon(Icons.Filled.CheckCircle, "Selected", tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
