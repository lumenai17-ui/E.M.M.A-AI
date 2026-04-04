package com.beemovil.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.beemovil.llm.ChatMessage
import com.beemovil.llm.LlmFactory
import com.beemovil.llm.OllamaCloudProvider
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.theme.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVisionScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("beemovil", 0) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isLiveActive by remember { mutableStateOf(false) }
    var liveResult by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var frameCount by remember { mutableStateOf(0) }
    var selectedModel by remember {
        mutableStateOf(prefs.getString("vision_model", "gemma4:31b-cloud") ?: "gemma4:31b-cloud")
    }
    var intervalSeconds by remember { mutableStateOf(4) } // Capture interval
    var showSettings by remember { mutableStateOf(false) }
    var customPrompt by remember { mutableStateOf("Describe lo que ves en esta imagen en español. Sé conciso.") }

    // Camera setup
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lastCaptureTime by remember { mutableStateOf(0L) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) Toast.makeText(context, "Permiso de cámara necesario", Toast.LENGTH_SHORT).show()
    }

    // Auto-capture loop
    LaunchedEffect(isLiveActive, intervalSeconds) {
        if (!isLiveActive) return@LaunchedEffect

        while (isLiveActive) {
            kotlinx.coroutines.delay(intervalSeconds * 1000L)
            if (!isLiveActive || isProcessing) continue

            val capture = imageCapture ?: continue
            isProcessing = true

            // Capture frame
            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        try {
                            val bitmap = imageProxy.toBitmap()
                            // Resize to 512px for speed
                            val scale = minOf(512f / bitmap.width, 512f / bitmap.height, 1f)
                            val resized = Bitmap.createScaledBitmap(
                                bitmap,
                                (bitmap.width * scale).toInt(),
                                (bitmap.height * scale).toInt(),
                                true
                            )
                            val baos = ByteArrayOutputStream()
                            resized.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                            // Send to vision model
                            Thread {
                                try {
                                    val apiKey = prefs.getString("ollama_api_key", "") ?: ""
                                    if (apiKey.isBlank()) {
                                        liveResult = "⚠️ Configura API key de Ollama"
                                        isProcessing = false
                                        return@Thread
                                    }
                                    val provider = OllamaCloudProvider(apiKey, selectedModel)
                                    val msgs = listOf(
                                        ChatMessage(role = "user", content = customPrompt, images = listOf(b64))
                                    )
                                    val response = provider.complete(msgs, emptyList())
                                    liveResult = response.text ?: ""
                                    frameCount++
                                } catch (e: Exception) {
                                    liveResult = "❌ ${e.message?.take(80)}"
                                }
                                isProcessing = false
                            }.start()
                        } catch (e: Exception) {
                            liveResult = "❌ Frame: ${e.message}"
                            isProcessing = false
                        }
                        imageProxy.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        liveResult = "❌ Capture: ${exception.message}"
                        isProcessing = false
                    }
                }
            )
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            isLiveActive = false
            cameraExecutor.shutdown()
        }
    }

    if (!hasCameraPermission) {
        // Permission request screen
        Column(
            modifier = Modifier.fillMaxSize().background(BeeBlack),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📸", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Permiso de cámara necesario", fontWeight = FontWeight.Bold,
                fontSize = 18.sp, color = BeeWhite)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack)
            ) {
                Text("Permitir cámara", fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(BeeBlack)) {
        // Camera preview (full screen)
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imgCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = imgCapture

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imgCapture
                        )
                    } catch (e: Exception) {
                        // Camera error
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BeeBlack.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
                .padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = BeeWhite)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("🔴 Live Vision", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeWhite)
                    val modelName = LlmFactory.VISION_MODELS.find { it.id == selectedModel }?.name ?: selectedModel
                    Text("$modelName · ${intervalSeconds}s", fontSize = 11.sp, color = BeeGray)
                }
                // Frame counter
                if (isLiveActive) {
                    Surface(
                        color = Color(0xFFF44336).copy(alpha = 0.8f),
                        shape = CircleShape
                    ) {
                        Text("● $frameCount", fontSize = 11.sp, color = BeeWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(Icons.Filled.Settings, "Settings", tint = BeeYellow)
                }
            }
        }

        // Settings panel (collapsible)
        if (showSettings) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 70.dp, start = 12.dp, end = 12.dp),
                colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("CONFIGURACIÓN LIVE", fontSize = 11.sp, color = BeeYellow,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Model selector
                    Text("Modelo", fontSize = 11.sp, color = BeeGray)
                    LlmFactory.VISION_MODELS.forEach { model ->
                        val isSelected = selectedModel == model.id
                        Surface(
                            onClick = {
                                selectedModel = model.id
                                prefs.edit().putString("vision_model", model.id).apply()
                            },
                            color = if (isSelected) BeeYellow.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(model.name, fontSize = 12.sp,
                                    color = if (isSelected) BeeYellow else Color(0xFFE0E0E0))
                                Spacer(modifier = Modifier.weight(1f))
                                if (isSelected) Text("✓", color = BeeYellow, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Interval
                    Text("Intervalo: ${intervalSeconds}s", fontSize = 11.sp, color = BeeGray)
                    Slider(
                        value = intervalSeconds.toFloat(),
                        onValueChange = { intervalSeconds = it.toInt() },
                        valueRange = 2f..10f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = BeeYellow,
                            activeTrackColor = BeeYellow
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Rápido (2s)", fontSize = 9.sp, color = BeeGray)
                        Text("Lento (10s)", fontSize = 9.sp, color = BeeGray)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom prompt
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        label = { Text("Instrucción al modelo") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
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
                }
            }
        }

        // Result overlay (bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // Live result text
            if (liveResult.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🐝", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RECONOCIMIENTO", fontSize = 10.sp, color = BeeYellow,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = BeeYellow, strokeWidth = 2.dp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            liveResult,
                            fontSize = 14.sp,
                            color = Color(0xFFE0E0E0),
                            lineHeight = 20.sp,
                            maxLines = 6
                        )
                    }
                }
            }

            // Control buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BeeBlack.copy(alpha = 0.9f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Single capture
                OutlinedButton(
                    onClick = {
                        val capture = imageCapture ?: return@OutlinedButton
                        isProcessing = true
                        liveResult = "📸 Capturando..."

                        capture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    try {
                                        val bitmap = imageProxy.toBitmap()
                                        val scale = minOf(800f / bitmap.width, 800f / bitmap.height, 1f)
                                        val resized = Bitmap.createScaledBitmap(bitmap,
                                            (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                                        val baos = ByteArrayOutputStream()
                                        resized.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                                        Thread {
                                            try {
                                                val apiKey = prefs.getString("ollama_api_key", "") ?: ""
                                                val provider = OllamaCloudProvider(apiKey, selectedModel)
                                                val msgs = listOf(ChatMessage(role = "user", content = customPrompt, images = listOf(b64)))
                                                val response = provider.complete(msgs, emptyList())
                                                liveResult = response.text ?: ""
                                                frameCount++
                                            } catch (e: Exception) {
                                                liveResult = "❌ ${e.message?.take(80)}"
                                            }
                                            isProcessing = false
                                        }.start()
                                    } catch (e: Exception) {
                                        liveResult = "❌ ${e.message}"
                                        isProcessing = false
                                    }
                                    imageProxy.close()
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    liveResult = "❌ ${exception.message}"
                                    isProcessing = false
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeWhite),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, "Capture", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Foto", fontSize = 13.sp)
                }

                // Live toggle (big circle)
                Button(
                    onClick = { isLiveActive = !isLiveActive },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLiveActive) Color(0xFFF44336) else BeeYellow
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (isLiveActive) {
                        Icon(Icons.Filled.Stop, "Stop", tint = BeeWhite, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(Icons.Filled.PlayArrow, "Start", tint = BeeBlack, modifier = Modifier.size(28.dp))
                    }
                }

                // Copy result
                OutlinedButton(
                    onClick = {
                        if (liveResult.isNotBlank()) {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("vision", liveResult))
                            Toast.makeText(context, "📋 Copiado", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeWhite),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, "Copy", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar", fontSize = 13.sp)
                }
            }
        }
    }
}
