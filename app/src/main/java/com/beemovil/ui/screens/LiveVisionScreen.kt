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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.content.ContextCompat
import com.beemovil.llm.ChatMessage
import com.beemovil.llm.LlmFactory
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.components.BeePermission
import com.beemovil.ui.components.PermissionDialog
import com.beemovil.ui.components.isPermissionGranted
import com.beemovil.ui.theme.*
import com.beemovil.vision.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beemovil.llm.local.LocalGemmaProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVisionScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("beemovil", 0) }

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isLiveActive by remember { mutableStateOf(false) }
    var liveResult by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var frameCount by remember { mutableStateOf(0) }
    var selectedModel by remember {
        mutableStateOf(prefs.getString("vision_model", "gemma4:31b-cloud") ?: "gemma4:31b-cloud")
    }
    var intervalSeconds by remember { mutableStateOf(4) }
    var showSettings by remember { mutableStateOf(false) }
    var showModules by remember { mutableStateOf(false) }
    var customPrompt by remember { mutableStateOf("Describe lo que ves en esta imagen en espanol. Se conciso.") }
    var isVoiceListening by remember { mutableStateOf(false) }
    var partialSpeech by remember { mutableStateOf("") }

    // Vision Pro state — 7 toggleable modules
    var visionState by remember { mutableStateOf(VisionProState()) }

    // 22-A: Conversation engine (history, repetition detection, smart prompts)
    val conversation = remember { VisionConversation() }
    var selectedPersonality by remember { mutableStateOf<NarratorPersonality?>(null) }
    
    // 25: Local LLM Loading State
    val isLLMLoading by LocalGemmaProvider.engineLoadingState.collectAsState()

    // 22-A: Native speech input (works without Deepgram API key)
    val nativeSpeech = remember { NativeSpeechInput(context) }

    // 22-F: Session recording and export
    val sessionManager = remember { VisionSessionManager(context) }
    var showSessionDialog by remember { mutableStateOf(false) }

    // 22-B: GPS Navigator + Mini Map
    val gpsNavigator = remember { GpsNavigator() }
    var showNavInput by remember { mutableStateOf(false) }
    var navDestinationText by remember { mutableStateOf("") }
    var navUpdate by remember { mutableStateOf(NavigationUpdate.idle()) }
    var poiSuggestions by remember { mutableStateOf<List<PoiSuggestion>>(emptyList()) }

    // Camera
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomLevel by remember { mutableStateOf(1f) }

    // GPS module
    val gpsModule = remember { GpsModule(context) }
    var gpsData by remember { mutableStateOf(GpsData()) }

    // Dashcam logger
    val dashcamLogger = remember { DashcamLogger(context) }

    // 22-I: Face Tracking UI State
    var detectedFaces by remember { mutableStateOf<List<FaceResult>>(emptyList()) }
    var imageWidth by remember { mutableStateOf(1) }
    var imageHeight by remember { mutableStateOf(1) }

    // Voice manager for narration (TTS output)
    val dgVoice = viewModel.deepgramVoiceManager

    // Live context: web search for real-time info
    val liveContext = remember { LiveContextProvider() }
    
    // Live Session Tracker (BUG-12: Prevent stale threads from speaking after restart)
    var currentLiveSessionId by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tourist guide: destination coords (resolved by AI)
    var touristBearing by remember { mutableStateOf(0f) }
    var touristDistance by remember { mutableStateOf(0f) }
    var touristDestCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        val cam = map[Manifest.permission.CAMERA] == true
        val mic = map[Manifest.permission.RECORD_AUDIO] == true
        hasPermissions = cam && mic
    }
    var showPermDialog by remember { mutableStateOf(false) }

    if (showPermDialog && !hasPermissions) {
        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        showPermDialog = false
    }

    // BUG-11: GPS lifecycle — use individual keys so any toggle change re-triggers
    LaunchedEffect(visionState.gpsOverlay, visionState.dashcamMode, visionState.touristGuide) {
        val needsGps = visionState.gpsOverlay || visionState.dashcamMode || visionState.touristGuide
        if (needsGps) {
            gpsModule.onLocationUpdate = { data ->
                gpsData = data
                // 22-B: Update navigator on every GPS tick
                if (gpsNavigator.isNavigating) {
                    navUpdate = gpsNavigator.update(data)
                    poiSuggestions = gpsNavigator.getContextualSuggestions(data)
                }
            }
            gpsModule.start()
        } else {
            gpsModule.stop()
        }
    }

    // Dashcam lifecycle
    LaunchedEffect(visionState.dashcamMode) {
        if (visionState.dashcamMode && !dashcamLogger.isActive) {
            dashcamLogger.startSession()
        } else if (!visionState.dashcamMode && dashcamLogger.isActive) {
            dashcamLogger.stopSession()
        }
    }

    // Auto-capture loop
    // BUG-01/09/12: Proper lifecycle — use session track to kill stale threads
    LaunchedEffect(isLiveActive, intervalSeconds) {
        if (!isLiveActive) {
            // Stop TTS on exit
            dgVoice?.stopSpeaking()
            return@LaunchedEffect
        }
        
        val localSessionId = currentLiveSessionId

        while (isLiveActive && currentLiveSessionId == localSessionId) {
            kotlinx.coroutines.delay(intervalSeconds * 1000L)
            if (!isLiveActive || currentLiveSessionId != localSessionId || isProcessing) continue

            // BUG-14: Variable Cycle - If TTS is speaking, skip this tick avoiding voice overlap and overload.
            if (dgVoice?.isSpeaking == true) continue

            val capture = imageCapture ?: continue
            isProcessing = true

            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        try {
                            if (!isLiveActive || currentLiveSessionId != localSessionId) {
                                isProcessing = false
                                imageProxy.close()
                                return
                            }
                            val bitmap = imageProxy.toBitmap()
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

                            Thread {
                                val threadSessionId = currentLiveSessionId
                                try {
                                    if (!isLiveActive || currentLiveSessionId != threadSessionId) {
                                        isProcessing = false
                                        return@Thread
                                    }
                                    // 22-G: Smart provider routing — detect local vs cloud
                                    val modelEntry = com.beemovil.llm.ModelRegistry.findModel(selectedModel)
                                    val isLocalModel = modelEntry?.provider == "local"
                                    
                                    // Detect network availability for auto-fallback
                                    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                                    val hasNetwork = cm?.activeNetwork != null
                                    
                                    val currentProviderType = when {
                                        isLocalModel -> "local"
                                        !hasNetwork -> {
                                            val localModel = com.beemovil.llm.local.LocalModelManager.getDownloadedModels().firstOrNull()
                                            if (localModel != null) "local" else viewModel.currentProvider.value
                                        }
                                        else -> viewModel.currentProvider.value
                                    }
                                    
                                    val effectiveModel = if (!isLocalModel && currentProviderType == "local") {
                                        com.beemovil.llm.local.LocalModelManager.getDownloadedModels().firstOrNull()?.id ?: selectedModel
                                    } else selectedModel

                                    val apiKey = when (currentProviderType) {
                                        "openrouter" -> com.beemovil.security.SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                                        "ollama" -> com.beemovil.security.SecurePrefs.get(context).getString("ollama_api_key", "") ?: ""
                                        else -> ""
                                    }
                                    if (apiKey.isBlank() && currentProviderType != "local") {
                                        if (!hasNetwork || com.beemovil.llm.local.LocalModelManager.getDownloadedModels().isEmpty()) {
                                            liveResult = "[WARN] Sin red y sin modelo local. Descarga Gemma 4 en Settings."
                                            isProcessing = false
                                            return@Thread
                                        }
                                        liveResult = "[WARN] Configura API key de ${currentProviderType}"
                                        isProcessing = false
                                        return@Thread
                                    }

                                    // 22-A: Smart prompt with conversation context
                                    val visionMode = when {
                                        visionState.dashcamMode -> VisionMode.DASHCAM
                                        visionState.touristGuide -> VisionMode.TOURIST
                                        visionState.backgroundAgent -> VisionMode.AGENT
                                        else -> VisionMode.GENERAL
                                    }
                                    val gpsCtx = if ((visionState.gpsOverlay || visionState.dashcamMode) && gpsData.latitude != 0.0)
                                        gpsData.toPromptContext() else ""

                                    val userQ = conversation.consumeQuestion()
                                    val systemPrompt = conversation.buildSystemPrompt(
                                        mode = visionMode,
                                        personality = selectedPersonality,
                                        gpsContext = gpsCtx,
                                        userQuestion = userQ
                                    ) + if (detectedFaces.isNotEmpty()) {
                                        "\n[SISTEMA INTERNO: El sensor biométrico detecta ${detectedFaces.size} rostros en vivo.]"
                                    } else ""

                                    // Build user prompt with web context
                                    val userPrompt = buildString {
                                        val contextMode = when {
                                            visionState.touristGuide -> LiveContextProvider.ContextMode.TOURIST
                                            visionState.dashcamMode -> LiveContextProvider.ContextMode.DASHCAM
                                            visionState.gpsOverlay -> LiveContextProvider.ContextMode.GPS
                                            else -> null
                                        }
                                        if (contextMode != null && gpsData.latitude != 0.0) {
                                            val webContext = liveContext.fetchContext(
                                                gpsData.address, gpsData.coordsShort, contextMode
                                            )
                                            if (webContext.isNotBlank()) {
                                                appendLine(webContext)
                                            }
                                        }
                                        if (userQ != null) {
                                            append(userQ)
                                        } else {
                                            append(customPrompt)
                                        }
                                    }

                                    val provider = LlmFactory.createProvider(
                                        providerType = currentProviderType,
                                        apiKey = apiKey,
                                        model = effectiveModel
                                    )
                                    val msgs = listOf(
                                        ChatMessage(role = "system", content = systemPrompt),
                                        ChatMessage(role = "user", content = userPrompt, images = listOf(b64))
                                    )
                                    val response = provider.complete(msgs, emptyList())
                                    liveResult = response.text ?: ""
                                    frameCount++

                                    // Track in conversation
                                    conversation.addFrame(liveResult)

                                    // 22-F: Session logging
                                    if (sessionManager.isRecording) {
                                        sessionManager.logFrame(
                                            result = liveResult,
                                            gpsCoords = if (gpsData.latitude != 0.0) gpsData.coordsShort else null,
                                            gpsAddress = gpsData.address.ifBlank { null },
                                            gpsSpeed = if (gpsData.speed > 0) gpsData.speed else null
                                        )
                                    }

                                    // Voice narration — BUG-12: check session ID before TTS
                                    if (isLiveActive && currentLiveSessionId == threadSessionId && visionState.voiceNarration && liveResult.isNotBlank()) {
                                        dgVoice?.speak(text = liveResult)
                                    }

                                    // Dashcam logging
                                    if (visionState.dashcamMode) {
                                        dashcamLogger.logFrame(
                                            frameNumber = frameCount,
                                            gpsData = if (gpsData.latitude != 0.0) gpsData else null,
                                            analysisResult = liveResult,
                                            prompt = userPrompt
                                        )
                                    }

                                    // Background agent evaluation
                                    if (visionState.backgroundAgent && visionState.backgroundAgentPrompt.isNotBlank()) {
                                        evaluateBackgroundAgent(
                                            context, viewModel, liveResult,
                                            visionState.backgroundAgentPrompt
                                        )
                                    }
                                } catch (e: Exception) {
                                    liveResult = "[ERR] ${e.message?.take(80)}"
                                }
                                isProcessing = false
                            }.start()
                        } catch (e: Exception) {
                            liveResult = "[ERR] Frame: ${e.message}"
                            isProcessing = false
                        }
                        imageProxy.close()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        liveResult = "[ERR] Capture: ${exception.message}"
                        isProcessing = false
                    }
                }
            )
        }
    }

    // BUG-02/08: Complete cleanup — stop TTS, speech, GPS, camera
    DisposableEffect(Unit) {
        onDispose {
            isLiveActive = false
            dgVoice?.stopSpeaking()  // BUG-02: Stop TTS on exit
            gpsModule.stop()
            if (dashcamLogger.isActive) dashcamLogger.stopSession()
            cameraExecutor.shutdown()
            nativeSpeech.destroy()
        }
    }

    if (!hasPermissions) {
        Column(
            modifier = Modifier.fillMaxSize().background(BeeBlack),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.CameraAlt, "Camera/Mic", tint = BeeGray, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Permisos necesarios", fontWeight = FontWeight.Bold,
                fontSize = 18.sp, color = BeeWhite)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Vision Pro necesita acceso a cámara y micrófono", fontSize = 13.sp, color = BeeGray)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                },
                colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack)
            ) {
                Text("Otorgar permisos", fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(BeeBlack)) {
        // Camera preview (full screen) with tap-to-focus and pinch-to-zoom
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

                    val faceAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor, FaceAnalyzer { faces, w, h ->
                                detectedFaces = faces
                                imageWidth = w
                                imageHeight = h
                            })
                        }

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imgCapture,
                            faceAnalyzer
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visionState.tapToFocus) {
                    if (visionState.tapToFocus) {
                        detectTapGestures { offset ->
                            val factory = camera?.cameraInfo?.let {
                                SurfaceOrientedMeteringPointFactory(
                                    size.width.toFloat(), size.height.toFloat()
                                )
                            }
                            factory?.let {
                                val point = it.createPoint(offset.x / size.width, offset.y / size.height)
                                val action = FocusMeteringAction.Builder(point).build()
                                camera?.cameraControl?.startFocusAndMetering(action)
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        zoomLevel = (zoomLevel * zoom).coerceIn(1f, 5f)
                        camera?.cameraControl?.setZoomRatio(zoomLevel)
                    }
                }
        )

        // ═══════════════════════════════════════
        // FACE TRACKING OVERLAY (Canvas)
        // ═══════════════════════════════════════
        if (detectedFaces.isNotEmpty()) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                // Adjust scale using Center-Crop logic to match PreviewView scaleType=FILL_CENTER
                val scale = maxOf(size.width / imageWidth.toFloat(), size.height / imageHeight.toFloat())
                val scaledWidth = imageWidth * scale
                val scaledHeight = imageHeight * scale
                val offsetX = (size.width - scaledWidth) / 2f
                val offsetY = (size.height - scaledHeight) / 2f
                
                detectedFaces.forEach { face ->
                    // Map bounding box to screen coordinates using scale and offset
                    val left = face.boundingBox.left * scale + offsetX
                    val top = face.boundingBox.top * scale + offsetY
                    val right = face.boundingBox.right * scale + offsetX
                    val bottom = face.boundingBox.bottom * scale + offsetY
                    
                    val rectWidth = right - left
                    val rectHeight = bottom - top

                    // Draw High-Tech Bracket Boxes
                    drawRect(
                        color = Color(0xFF00E5FF).copy(alpha = 0.3f), // Cyan fill
                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight)
                    )
                    drawRect(
                        color = Color(0xFF00E5FF), // Cyan stroke
                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                    )
                    
                    // Draw Face ID & Emotion
                    val label = buildString {
                        append("FACE_ID[${face.trackingId ?: "?"}]")
                        if ((face.smilingProbability ?: 0f) > 0.6f) append(" 😊")
                    }
                    
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.CYAN
                        textSize = 36f
                        isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        left,
                        top - 10f,
                        paint
                    )
                }
            }
        }

        // ═══════════════════════════════════════
        // TOP BAR
        // ═══════════════════════════════════════
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = BeeWhite)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Vision Pro", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BeeWhite)
                    val modelName = LlmFactory.VISION_MODELS.find { it.id == selectedModel }?.name ?: selectedModel
                    Text("$modelName · ${intervalSeconds}s", fontSize = 11.sp, color = BeeGray)
                }

                // Active module indicators
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (selectedModel.startsWith("gemma4-e")) ModuleChip("📱OFF", Color(0xFF4CAF50))
                    if (visionState.gpsOverlay) ModuleChip("GPS", Color(0xFF4CAF50))
                    if (visionState.voiceNarration) ModuleChip("VOZ", Color(0xFF2196F3))
                    if (visionState.dashcamMode) ModuleChip("REC", Color(0xFFF44336))
                    if (visionState.backgroundAgent) ModuleChip("AGT", Color(0xFFFF9800))
                    if (selectedPersonality != null) ModuleChip(selectedPersonality!!.emoji, Color(0xFFE040FB))
                }

                if (isLiveActive) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(color = Color(0xFFF44336).copy(alpha = 0.8f), shape = CircleShape) {
                        Text("$frameCount", fontSize = 11.sp, color = BeeWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                IconButton(onClick = { showSettings = !showSettings; showModules = false }) {
                    Icon(Icons.Filled.Tune, "Settings", tint = BeeYellow)
                }
                IconButton(onClick = { showModules = !showModules; showSettings = false }) {
                    Icon(Icons.Filled.Extension, "Modules", tint = BeeYellow)
                }
            }
        }

        // ═══════════════════════════════════════
        // GPS OVERLAY — BUG-07: leave right side free for minimap
        // ═══════════════════════════════════════
        if (visionState.gpsOverlay && gpsData.latitude != 0.0) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(0.65f)
                    .padding(top = 68.dp, start = 10.dp),
                colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LocationOn, "GPS", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(gpsData.coordsShort, fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            if (gpsData.address.isNotBlank()) {
                                Text(gpsData.address, fontSize = 11.sp, color = BeeWhite,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (gpsData.speed > 0.5f) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${"%.0f".format(gpsData.speedKmh)} km/h ${gpsData.bearingCardinal}",
                            fontSize = 13.sp, color = BeeWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // MINI MAP PIP — BUG-07: top-right, NOT overlapping GPS
        // ═══════════════════════════════════════
        if ((visionState.gpsOverlay || visionState.touristGuide) && gpsData.latitude != 0.0) {
            MiniMapPIP(
                gpsData = gpsData,
                navigator = gpsNavigator,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 68.dp, end = 8.dp)
            )
        }

        // ═══════════════════════════════════════
        // DASHCAM HUD (top-left under header)
        // ═══════════════════════════════════════
        if (visionState.dashcamMode) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 72.dp, start = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.7f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(8.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("DASHCAM", fontSize = 10.sp, color = BeeWhite, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${dashcamLogger.entryCount} frames", fontSize = 9.sp, color = BeeWhite.copy(alpha = 0.8f))
                }
            }
        }

        // ═══════════════════════════════════════
        // TOURIST GUIDE OVERLAY (center)
        // ═══════════════════════════════════════
        // BUG-13: Show tourist arrow ONLY when NavigationHUD is NOT active
        if (visionState.touristGuide && !gpsNavigator.isNavigating && touristDestCoords != null && gpsData.latitude != 0.0) {
            // Calculate bearing and distance to destination
            val destLat = touristDestCoords!!.first
            val destLng = touristDestCoords!!.second
            val results = FloatArray(3)
            android.location.Location.distanceBetween(
                gpsData.latitude, gpsData.longitude, destLat, destLng, results
            )
            touristDistance = results[0]
            touristBearing = if (results.size > 1) results[1] else 0f

            // Direction arrow relative to phone bearing
            val relativeBearing = touristBearing - gpsData.bearing
            val distText = if (touristDistance > 1000) "${"%.1f".format(touristDistance / 1000)} km"
                          else "${"%.0f".format(touristDistance)} m"

            Card(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF8BC34A).copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Arrow pointing toward destination
                    Text(
                        when {
                            relativeBearing > -22.5 && relativeBearing <= 22.5 -> "^"
                            relativeBearing > 22.5 && relativeBearing <= 67.5 -> "/"
                            relativeBearing > 67.5 && relativeBearing <= 112.5 -> ">"
                            relativeBearing > 112.5 && relativeBearing <= 157.5 -> "\\"
                            relativeBearing > 157.5 || relativeBearing <= -157.5 -> "v"
                            relativeBearing > -157.5 && relativeBearing <= -112.5 -> "/"
                            relativeBearing > -112.5 && relativeBearing <= -67.5 -> "<"
                            else -> "\\"
                        },
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = BeeBlack
                    )
                    Text(distText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BeeBlack)
                    Text(visionState.touristDestination.take(20), fontSize = 11.sp, color = BeeBlack.copy(alpha = 0.7f))
                }
            }
        }

        // 22-C: Tourist quick action buttons
        if (visionState.touristGuide && gpsData.latitude != 0.0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 180.dp, start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val touristActions = listOf(
                    "🔍" to "¿Qué es lo que veo en esta imagen? Descríbelo con detalle y datos curiosos.",
                    "📖" to "Cuéntame más sobre la historia de este lugar y la zona donde estoy.",
                    "🍽️" to "¿Dónde puedo comer algo rico cerca de aquí? Recomienda platos locales."
                )
                touristActions.forEach { (emoji, question) ->
                    Surface(
                        onClick = {
                            conversation.addUserQuestion(question)
                            triggerSmartCapture(
                                imageCapture, cameraExecutor, viewModel, context,
                                selectedModel, question, visionState, gpsData,
                                dgVoice, dashcamLogger, conversation, selectedPersonality, detectedFaces,
                                onResult = { liveResult = it; frameCount++ },
                                onProcessing = { isProcessing = it }
                            )
                        },
                        color = Color(0xFF8BC34A).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(emoji, fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // MODULES PANEL — BUG-06: scrim + constrained height
        // ═══════════════════════════════════════
        // Scrim behind modules panel
        if (showModules) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showModules = false }
            )
        }
        AnimatedVisibility(
            visible = showModules,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Card(
                modifier = Modifier
                    .width(240.dp)
                    .padding(top = 60.dp, bottom = 120.dp, end = 4.dp), // BUG-06: Leave room for header + controls
                colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("MODULOS", fontSize = 12.sp, color = BeeYellow,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Activa/desactiva cada modulo", fontSize = 10.sp, color = BeeGray)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle rows
                    ModuleToggle("GPS Overlay", Icons.Filled.LocationOn,
                        visionState.gpsOverlay, Color(0xFF4CAF50)) {
                        visionState = visionState.copy(gpsOverlay = it)
                    }
                    ModuleToggle("Voz Narracion", Icons.Filled.RecordVoiceOver,
                        visionState.voiceNarration, Color(0xFF2196F3)) {
                        visionState = visionState.copy(voiceNarration = it)
                    }
                    ModuleToggle("Tap-to-Focus", Icons.Filled.CenterFocusStrong,
                        visionState.tapToFocus, Color(0xFF9C27B0)) {
                        visionState = visionState.copy(tapToFocus = it)
                    }
                    ModuleToggle("AR Overlay", Icons.Filled.Visibility,
                        visionState.arTextOverlay, Color(0xFF00BCD4)) {
                        visionState = visionState.copy(arTextOverlay = it)
                    }
                    ModuleToggle("Grabar Video", Icons.Filled.Videocam,
                        visionState.videoRecording, Color(0xFFE91E63)) {
                        visionState = visionState.copy(videoRecording = it)
                        if (it) Toast.makeText(context, "Video: proximamente", Toast.LENGTH_SHORT).show()
                    }
                    ModuleToggle("Dashcam", Icons.Filled.DirectionsCar,
                        visionState.dashcamMode, Color(0xFFF44336)) {
                        visionState = visionState.copy(dashcamMode = it)
                        // Also enable GPS if dashcam
                        if (it) visionState = visionState.copy(gpsOverlay = true)
                    }
                    ModuleToggle("Agente Fondo", Icons.Filled.SmartToy,
                        visionState.backgroundAgent, Color(0xFFFF9800)) {
                        visionState = visionState.copy(backgroundAgent = it)
                    }
                    ModuleToggle("Guia Turista", Icons.Filled.Explore,
                        visionState.touristGuide, Color(0xFF8BC34A)) {
                        visionState = visionState.copy(touristGuide = it)
                        if (it) visionState = visionState.copy(gpsOverlay = true, voiceNarration = true)
                    }

                    // ── 22-B: GPS NAVIGATION ──
                    if (visionState.gpsOverlay || visionState.touristGuide) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFF4CAF50).copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("NAVEGACIÓN", fontSize = 10.sp, color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Ingresa destino para navegar", fontSize = 9.sp, color = BeeGray)
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = navDestinationText,
                            onValueChange = { navDestinationText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ej: Parque Central, OXXO, Aeropuerto...", fontSize = 10.sp, color = BeeGray) },
                            maxLines = 1,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = BeeWhite),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                                cursorColor = Color(0xFF4CAF50)
                            ),
                            trailingIcon = {
                                if (gpsNavigator.isNavigating) {
                                    IconButton(onClick = {
                                        gpsNavigator.stopNavigation()
                                        navUpdate = NavigationUpdate.idle()
                                    }) {
                                        Icon(Icons.Filled.Close, "Stop", tint = Color(0xFFF44336), modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    IconButton(onClick = {
                                        if (navDestinationText.isNotBlank()) {
                                            // Try geocoder first
                                            Thread {
                                                try {
                                                    val geocoder = android.location.Geocoder(context, java.util.Locale("es"))
                                                    val results = geocoder.getFromLocationName(navDestinationText, 1)
                                                    if (!results.isNullOrEmpty()) {
                                                        val addr = results[0]
                                                        val dest = NavigationDestination(
                                                            name = navDestinationText,
                                                            latitude = addr.latitude,
                                                            longitude = addr.longitude,
                                                            resolvedBy = "geocoder"
                                                        )
                                                        gpsNavigator.startNavigation(dest)
                                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                            navUpdate = gpsNavigator.update(gpsData)
                                                            poiSuggestions = gpsNavigator.getContextualSuggestions(gpsData)
                                                            Toast.makeText(context, "🗺️ Navegando a: ${navDestinationText}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                            Toast.makeText(context, "No se encontró: $navDestinationText", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                        Toast.makeText(context, "Error: ${e.message?.take(40)}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }.start()
                                        }
                                    }) {
                                        Icon(Icons.Filled.Navigation, "Go", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )

                        // Nav status inline
                        if (gpsNavigator.isNavigating) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(navUpdate.arrow, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(navUpdate.distance, fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(navUpdate.eta, fontSize = 11.sp, color = BeeGray)
                            }
                        }
                    }

                    // ── 22-E: NARRATOR PERSONALITY ──
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = BeeGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PERSONALIDAD", fontSize = 10.sp, color = Color(0xFFE040FB),
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Cambia el estilo del narrador", fontSize = 9.sp, color = BeeGray)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Personality chips in a wrap-style layout
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NARRATOR_PERSONALITIES.forEach { p ->
                            val isSelected = selectedPersonality?.id == p.id ||
                                (p.id == "default" && selectedPersonality == null)
                            Surface(
                                onClick = {
                                    selectedPersonality = if (p.id == "default") null else p
                                },
                                color = if (isSelected) Color(0xFFE040FB).copy(alpha = 0.2f)
                                    else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected)
                                    androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE040FB))
                                else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(p.emoji, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(p.name, fontSize = 10.sp,
                                        color = if (isSelected) Color(0xFFE040FB) else BeeGray)
                                }
                            }
                        }
                    }

                    // Background agent prompt
                    if (visionState.backgroundAgent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = BeeGray.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("INSTRUCCION AGENTE", fontSize = 10.sp, color = BeeYellow,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = visionState.backgroundAgentPrompt,
                            onValueChange = { visionState = visionState.copy(backgroundAgentPrompt = it) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = BeeWhite),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BeeYellow,
                                unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                                cursorColor = BeeYellow
                            )
                        )
                    }

                    // Tourist destination input
                    if (visionState.touristGuide) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = BeeGray.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("GUIA TURISTA", fontSize = 10.sp, color = Color(0xFF8BC34A),
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Destino + navegacion + traduccion", fontSize = 9.sp, color = BeeGray)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = visionState.touristDestination,
                            onValueChange = { visionState = visionState.copy(touristDestination = it) },
                            placeholder = { Text("Ej: Torre Eiffel, Starbucks", fontSize = 10.sp, color = BeeGray) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = BeeWhite),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF8BC34A),
                                unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                                cursorColor = Color(0xFF8BC34A)
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Navigate button
                        Button(
                            onClick = {
                                if (visionState.touristDestination.isNotBlank() && gpsData.latitude != 0.0) {
                                    Thread {
                                        try {
                                            val currentProviderType = viewModel.currentProvider.value
                                            val apiKey = when (currentProviderType) {
                                                "openrouter" -> com.beemovil.security.SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                                                "ollama" -> com.beemovil.security.SecurePrefs.get(context).getString("ollama_api_key", "") ?: ""
                                                else -> ""
                                            }
                                            val nav = """Eres un guia turistico experto. Mi ubicacion actual:
                                                |Coordenadas: ${gpsData.coordsShort}
                                                |Direccion: ${gpsData.address}
                                                |Mirando hacia: ${gpsData.bearingCardinal}
                                                |
                                                |Quiero llegar a: ${visionState.touristDestination}
                                                |
                                                |Instrucciones:
                                                |1. COORDS:lat,lng (coordenadas exactas del destino)
                                                |2. Instrucciones paso a paso para CAMINAR (breves, maximo 5 pasos)
                                                |3. Distancia estimada y tiempo caminando
                                                |4. Si el destino esta en un pais donde se habla otro idioma, incluye frases utiles traducidas para preguntar direcciones a locales (ej: "Excuse me, how do I get to...?")
                                                |
                                                |Responde en espanol.""".trimMargin()
                                            val provider = LlmFactory.createProvider(currentProviderType, apiKey, viewModel.currentModel.value)
                                            val msgs = listOf(com.beemovil.llm.ChatMessage(role = "user", content = nav))
                                            val response = provider.complete(msgs, emptyList())
                                            val text = response.text ?: ""
                                            val coordsMatch = Regex("COORDS:\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)").find(text)
                                            if (coordsMatch != null) {
                                                val lat = coordsMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                                                val lng = coordsMatch.groupValues[2].toDoubleOrNull() ?: 0.0
                                                touristDestCoords = Pair(lat, lng)
                                            }
                                            liveResult = text.replace(Regex("COORDS:\\s*-?[\\d.]+\\s*,\\s*-?[\\d.]+"), "").trim()
                                            if (visionState.voiceNarration) {
                                                dgVoice?.speak(text = liveResult.take(400))
                                            }
                                        } catch (e: Exception) {
                                            liveResult = "[ERR] ${e.message?.take(80)}"
                                        }
                                    }.start()
                                } else {
                                    Toast.makeText(context, "Activa GPS y escribe un destino", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8BC34A), contentColor = BeeBlack),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Navigation, "Nav", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Navegar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Translate / ask for directions button
                        OutlinedButton(
                            onClick = {
                                Thread {
                                    try {
                                        val currentProviderType = viewModel.currentProvider.value
                                        val apiKey = when (currentProviderType) {
                                            "openrouter" -> com.beemovil.security.SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                                            "ollama" -> com.beemovil.security.SecurePrefs.get(context).getString("ollama_api_key", "") ?: ""
                                            else -> ""
                                        }
                                        val dest = visionState.touristDestination.ifBlank { "un lugar cercano" }
                                        val translatePrompt = """Estoy de turista y necesito pedir direcciones a alguien local.
                                            |Quiero llegar a: $dest
                                            |Mi ubicacion: ${gpsData.address.ifBlank { gpsData.coordsShort }}
                                            |
                                            |Dame las frases traducidas en el idioma local del pais donde estoy para:
                                            |1. "Disculpe, como llego a $dest?"
                                            |2. "Esta lejos para caminar?"
                                            |3. "Muchas gracias por su ayuda"
                                            |4. "Donde esta la parada de autobus/metro mas cercana?"
                                            |
                                            |Incluye pronunciacion fonetica si el idioma no usa alfabeto latino.
                                            |Responde primero en espanol con la traduccion al lado.""".trimMargin()
                                        val provider = LlmFactory.createProvider(currentProviderType, apiKey, viewModel.currentModel.value)
                                        val msgs = listOf(com.beemovil.llm.ChatMessage(role = "user", content = translatePrompt))
                                        val response = provider.complete(msgs, emptyList())
                                        liveResult = response.text ?: ""
                                        if (visionState.voiceNarration) {
                                            dgVoice?.speak(text = liveResult.take(300))
                                        }
                                    } catch (e: Exception) {
                                        liveResult = "[ERR] ${e.message?.take(80)}"
                                    }
                                }.start()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8BC34A)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8BC34A).copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Filled.Translate, "Translate", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Traducir / Pedir Direcciones", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════
        // SETTINGS PANEL (slide-in left)
        // ═══════════════════════════════════════
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Card(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .padding(vertical = 60.dp),
                colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("CONFIGURACION", fontSize = 12.sp, color = BeeYellow,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Model selector
                    Text("Modelo", fontSize = 11.sp, color = BeeGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    LlmFactory.VISION_MODELS.forEach { model ->
                        val isSelected = selectedModel == model.id
                        val isLocal = model.id.startsWith("gemma4-e")
                        Surface(
                            onClick = {
                                selectedModel = model.id
                                prefs.edit().putString("vision_model", model.id).apply()
                            },
                            color = when {
                                isSelected && isLocal -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                isSelected -> BeeYellow.copy(alpha = 0.15f)
                                else -> Color.Transparent
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isLocal) {
                                    Text("📱 ", fontSize = 12.sp)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.name, fontSize = 12.sp,
                                        color = when {
                                            isSelected && isLocal -> Color(0xFF4CAF50)
                                            isSelected -> BeeYellow
                                            else -> Color(0xFFE0E0E0)
                                        })
                                    if (isLocal) {
                                        Text("OFFLINE · Sin internet", fontSize = 9.sp, color = Color(0xFF4CAF50).copy(alpha = 0.7f))
                                    }
                                }
                                if (isSelected) Icon(Icons.Filled.CheckCircle, "Sel",
                                    tint = if (isLocal) Color(0xFF4CAF50) else BeeYellow,
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Interval
                    Text("Intervalo: ${intervalSeconds}s", fontSize = 11.sp, color = BeeGray)
                    Slider(
                        value = intervalSeconds.toFloat(),
                        onValueChange = { intervalSeconds = it.toInt() },
                        valueRange = 2f..10f,
                        steps = 7,
                        colors = SliderDefaults.colors(thumbColor = BeeYellow, activeTrackColor = BeeYellow)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom prompt
                    Text("Instruccion al modelo", fontSize = 11.sp, color = BeeGray)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = BeeWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BeeYellow,
                            unfocusedBorderColor = BeeGray.copy(alpha = 0.3f),
                            cursorColor = BeeYellow
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Zoom indicator
                    Text("Zoom: ${"%.1f".format(zoomLevel)}x", fontSize = 11.sp, color = BeeGray)
                    Slider(
                        value = zoomLevel,
                        onValueChange = {
                            zoomLevel = it
                            camera?.cameraControl?.setZoomRatio(it)
                        },
                        valueRange = 1f..5f,
                        colors = SliderDefaults.colors(thumbColor = BeeYellow, activeTrackColor = BeeYellow)
                    )
                }
            }
        }

        // ═══════════════════════════════════════
        // RESULT OVERLAY (bottom) — 22-D + 22-H
        // ═══════════════════════════════════════
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {


            // Result overlay — BUG-04: constrained height + scroll + dismiss
            if (liveResult.isNotBlank()) {
                val accentColor = if (visionState.arTextOverlay) Color(0xFF00BCD4) else BeeYellow
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (visionState.arTextOverlay)
                            Color(0xFF00BCD4).copy(alpha = 0.15f)
                        else BeeBlack.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp) // BUG-04: Don't let it take over the screen
                        .padding(horizontal = 10.dp),
                    border = if (visionState.arTextOverlay)
                        androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00BCD4).copy(alpha = 0.5f))
                    else null
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (visionState.arTextOverlay) Icons.Filled.Visibility else Icons.Filled.SmartToy,
                                "AI", tint = accentColor, modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (visionState.arTextOverlay) "VISION AI" else "RECONOCIMIENTO",
                                fontSize = 10.sp, color = accentColor,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = accentColor, strokeWidth = 2.dp
                                )
                            }
                            // BUG-04: Dismiss button
                            IconButton(
                                onClick = { liveResult = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.Close, "Cerrar", tint = BeeGray, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            liveResult,
                            fontSize = 13.sp,
                            color = if (visionState.arTextOverlay) Color.White else Color(0xFFE0E0E0),
                            lineHeight = 19.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState()) // BUG-04: scrollable
                        )
                    }
                }
            }

            // Control buttons — BIG and well spaced
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BeeBlack.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Snap photo
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { triggerSingleCapture(imageCapture, cameraExecutor, viewModel, context,
                            selectedModel, customPrompt, visionState, gpsData, dgVoice, dashcamLogger, detectedFaces,
                            onResult = { liveResult = it; frameCount++ },
                            onProcessing = { isProcessing = it }) },
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333355)),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, "Foto", tint = BeeWhite, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Foto", fontSize = 10.sp, color = BeeGray)
                }

                // Mic — voice question (BUG-03 fix: stop TTS, guard concurrency)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            if (isVoiceListening) {
                                nativeSpeech.stopListening()
                                isVoiceListening = false
                                partialSpeech = ""
                            } else if (!isProcessing) {
                                // BUG-03: Stop TTS first so mic doesn't hear its own output
                                dgVoice?.stopSpeaking()
                                isVoiceListening = true
                                partialSpeech = ""
                                nativeSpeech.startListening(
                                    onResult = { text ->
                                        isVoiceListening = false
                                        partialSpeech = ""
                                        if (text.isNotBlank() && !isProcessing) {
                                            conversation.addUserQuestion(text)
                                            triggerSmartCapture(
                                                imageCapture, cameraExecutor, viewModel, context,
                                                selectedModel, text, visionState, gpsData,
                                                dgVoice, dashcamLogger, conversation, selectedPersonality, detectedFaces,
                                                onResult = { liveResult = it; frameCount++ },
                                                onProcessing = { isProcessing = it }
                                            )
                                        }
                                    },
                                    onPartial = { partialSpeech = it },
                                    onError = { err ->
                                        isVoiceListening = false
                                        partialSpeech = ""
                                        Toast.makeText(context, "Mic: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVoiceListening) Color(0xFF4CAF50) else Color(0xFF333355)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            if (isVoiceListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                            "Mic", tint = BeeWhite, modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        when {
                            partialSpeech.isNotBlank() -> partialSpeech.takeLast(20)
                            isVoiceListening -> "Escuchando..."
                            else -> "Preguntar"
                        },
                        fontSize = 10.sp,
                        color = if (isVoiceListening) Color(0xFF4CAF50) else BeeGray,
                        maxLines = 1
                    )
                }

                // Live toggle (BIGGEST button)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { 
                            isLiveActive = !isLiveActive
                            if (isLiveActive) {
                                currentLiveSessionId = System.currentTimeMillis() // Start new clean tracking session
                            } else {
                                dgVoice?.stopSpeaking()
                                isProcessing = false
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLiveActive) Color(0xFFF44336) else BeeYellow
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            if (isLiveActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            "Live",
                            tint = if (isLiveActive) BeeWhite else BeeBlack,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(if (isLiveActive) "Parar" else "Live", fontSize = 10.sp,
                        color = if (isLiveActive) Color(0xFFF44336) else BeeYellow)
                }

                // 22-F: Session / Copy
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    @OptIn(ExperimentalFoundationApi::class)
                    Surface(
                        modifier = Modifier
                            .size(52.dp)
                            .combinedClickable(
                                onClick = {
                                    if (sessionManager.isRecording) {
                                        val summary = sessionManager.stopSession()
                                        liveResult = summary
                                        showSessionDialog = true
                                    } else if (liveResult.isNotBlank()) {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("vision", liveResult))
                                        Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onLongClick = {
                                    if (!sessionManager.isRecording) {
                                        sessionManager.startSession(
                                            when {
                                                visionState.dashcamMode -> VisionSessionManager.SessionType.DASHCAM
                                                visionState.touristGuide -> VisionSessionManager.SessionType.TOURISM
                                                else -> VisionSessionManager.SessionType.GENERAL
                                            }
                                        )
                                        Toast.makeText(context, "📝 Sesión iniciada", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ),
                        shape = CircleShape,
                        color = if (sessionManager.isRecording) Color(0xFFE91E63) else Color(0xFF333355)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                if (sessionManager.isRecording) Icons.Filled.Stop else Icons.Filled.ContentCopy,
                                "Session", tint = BeeWhite, modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (sessionManager.isRecording) "Sesión ●" else "Copiar",
                        fontSize = 10.sp,
                        color = if (sessionManager.isRecording) Color(0xFFE91E63) else BeeGray
                    )
                }
            }
        }

        // 22-F: Session export dialog
        if (showSessionDialog) {
            AlertDialog(
                onDismissRequest = { showSessionDialog = false },
                containerColor = BeeBlack.copy(alpha = 0.95f),
                title = {
                    Text("📝 Sesión Finalizada", color = BeeYellow, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            "${sessionManager.sessionType.emoji} ${sessionManager.sessionType.label}",
                            color = BeeWhite, fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${sessionManager.entryCount} entradas",
                            color = BeeGray, fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val file = sessionManager.saveToFile()
                                    if (file != null) {
                                        Toast.makeText(context, "Guardado: ${file.name}", Toast.LENGTH_SHORT).show()
                                    }
                                    showSessionDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeYellow),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BeeYellow.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Filled.Save, "Save", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guardar", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    sessionManager.shareSession()
                                    showSessionDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CAF50)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Filled.Share, "Share", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Compartir", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Copy full log
                        OutlinedButton(
                            onClick = {
                                val log = sessionManager.generateFullLog()
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("session", log))
                                Toast.makeText(context, "Log copiado", Toast.LENGTH_SHORT).show()
                                showSessionDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeGray),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BeeGray.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Filled.ContentCopy, "Copy", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar log completo", fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSessionDialog = false }) {
                        Text("Cerrar", color = BeeYellow)
                    }
                }
            )
        }
    } // end dialog

    // 25: Local LLM Server Loading Dialog
    if (isLLMLoading) {
        Dialog(
            onDismissRequest = { /* Cannot dismiss by tapping outside */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = BeeBlackLight,
                border = BorderStroke(1.dp, BeeGray),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = BeeYellow, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Encendiendo Motor...",
                        color = BeeWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Cargando modelo de Visión en la memoria RAM.\n\nEsto asume mucho ancho de banda. Por favor, espera antes de usar la cámara.",
                        color = BeeGray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { LocalGemmaProvider.releaseEngine() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("Detener / Cancelar", color = BeeWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// COMPOSABLES
// ═══════════════════════════════════════

@Composable
private fun ModuleChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, fontSize = 8.sp, color = color, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

@Composable
private fun ModuleToggle(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    color: Color,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, label, tint = if (checked) color else BeeGray, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = if (checked) BeeWhite else BeeGray,
            modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            modifier = Modifier.height(28.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = BeeBlack,
                checkedTrackColor = color,
                uncheckedTrackColor = BeeGray.copy(alpha = 0.3f)
            )
        )
    }
}

// ═══════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════

private fun triggerSingleCapture(
    imageCapture: ImageCapture?,
    executor: java.util.concurrent.ExecutorService,
    viewModel: ChatViewModel,
    context: android.content.Context,
    model: String,
    prompt: String,
    state: VisionProState,
    gpsData: GpsData,
    dgVoice: com.beemovil.voice.DeepgramVoiceManager?,
    logger: DashcamLogger,
    detectedFaces: List<FaceResult>,
    onResult: (String) -> Unit,
    onProcessing: (Boolean) -> Unit
) {
    val capture = imageCapture ?: return
    onProcessing(true)

    capture.takePicture(
        executor,
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
                            val currentProviderType = viewModel.currentProvider.value
                            val apiKey = when (currentProviderType) {
                                "openrouter" -> com.beemovil.security.SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                                "ollama" -> com.beemovil.security.SecurePrefs.get(context).getString("ollama_api_key", "") ?: ""
                                else -> ""
                            }
                            val fullPrompt = buildString {
                                if ((state.gpsOverlay || state.dashcamMode) && gpsData.latitude != 0.0) {
                                    appendLine(gpsData.toPromptContext())
                                }
                                append(prompt)
                            }
                            val provider = LlmFactory.createProvider(currentProviderType, apiKey, model)
                            val msgs = listOf(ChatMessage(role = "user", content = fullPrompt, images = listOf(b64)))
                            val response = provider.complete(msgs, emptyList())
                            val result = response.text ?: ""
                            onResult(result)

                            if (state.voiceNarration && result.isNotBlank()) {
                                dgVoice?.speak(text = result)
                            }
                        } catch (e: Exception) {
                            onResult("[ERR] ${e.message?.take(80)}")
                        }
                        onProcessing(false)
                    }.start()
                } catch (e: Exception) {
                    onResult("[ERR] ${e.message}")
                    onProcessing(false)
                }
                imageProxy.close()
            }

            override fun onError(exception: ImageCaptureException) {
                onResult("[ERR] ${exception.message}")
                onProcessing(false)
            }
        }
    )
}

/**
 * 22-A: Smart capture with conversation context, personality, and system prompts.
 */
private fun triggerSmartCapture(
    imageCapture: ImageCapture?,
    executor: java.util.concurrent.ExecutorService,
    viewModel: ChatViewModel,
    context: android.content.Context,
    model: String,
    userQuestion: String,
    state: VisionProState,
    gpsData: GpsData,
    dgVoice: com.beemovil.voice.DeepgramVoiceManager?,
    logger: DashcamLogger,
    conversation: VisionConversation,
    personality: NarratorPersonality?,
    detectedFaces: List<FaceResult>,
    onResult: (String) -> Unit,
    onProcessing: (Boolean) -> Unit
) {
    val capture = imageCapture ?: return
    onProcessing(true)

    capture.takePicture(
        executor,
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
                            val currentProviderType = viewModel.currentProvider.value
                            val apiKey = when (currentProviderType) {
                                "openrouter" -> com.beemovil.security.SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                                "ollama" -> com.beemovil.security.SecurePrefs.get(context).getString("ollama_api_key", "") ?: ""
                                else -> ""
                            }

                            val visionMode = when {
                                state.dashcamMode -> VisionMode.DASHCAM
                                state.touristGuide -> VisionMode.TOURIST
                                state.backgroundAgent -> VisionMode.AGENT
                                else -> VisionMode.GENERAL
                            }
                            val gpsCtx = if ((state.gpsOverlay || state.dashcamMode) && gpsData.latitude != 0.0)
                                gpsData.toPromptContext() else ""

                            val systemPrompt = conversation.buildSystemPrompt(
                                mode = visionMode,
                                personality = personality,
                                gpsContext = gpsCtx,
                                userQuestion = userQuestion
                            ) + if (detectedFaces.isNotEmpty()) {
                                "\n[SISTEMA INTERNO: El sensor biométrico detecta ${detectedFaces.size} rostros en vivo.]"
                            } else ""

                            val msgs = listOf(
                                ChatMessage(role = "system", content = systemPrompt),
                                ChatMessage(role = "user", content = userQuestion, images = listOf(b64))
                            )

                            // 22-G: Try primary provider, fallback to alternate
                            val result = try {
                                val provider = LlmFactory.createProvider(currentProviderType, apiKey, model)
                                val response = provider.complete(msgs, emptyList())
                                response.text ?: ""
                            } catch (primaryErr: Exception) {
                                // BUG-13: Do NOT silently fallback to local model if cloud fails. It freezes the app suddenly.
                                // ONLY fallback to cloud if local model fails.
                                if (currentProviderType == "local") {
                                    try {
                                        val fallbackKey = com.beemovil.security.SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                                        val fallbackModel = model
                                        val fallbackProvider = LlmFactory.createProvider("openrouter", fallbackKey, fallbackModel)
                                        val fallbackResponse = fallbackProvider.complete(msgs, emptyList())
                                        "[CLOUD FALLBACK] ${fallbackResponse.text ?: ""}"
                                    } catch (_: Exception) {
                                        throw primaryErr // Both failed
                                    }
                                } else {
                                    throw primaryErr // Cloud failed. Don't secretly try local!
                                }
                            }

                            conversation.addFrame(result)
                            onResult(result)

                            if (state.voiceNarration && result.isNotBlank()) {
                                dgVoice?.speak(text = result)
                            }
                        } catch (e: Exception) {
                            onResult("[ERR] ${e.message?.take(80)}")
                        }
                        onProcessing(false)
                    }.start()
                } catch (e: Exception) {
                    onResult("[ERR] ${e.message}")
                    onProcessing(false)
                }
                imageProxy.close()
            }

            override fun onError(exception: ImageCaptureException) {
                onResult("[ERR] ${exception.message}")
                onProcessing(false)
            }
        }
    )
}

private fun evaluateBackgroundAgent(
    context: android.content.Context,
    viewModel: ChatViewModel,
    analysisResult: String,
    agentPrompt: String
) {
    if (analysisResult.isBlank() || analysisResult.startsWith("[")) return

    Thread {
        try {
            val currentProviderType = viewModel.currentProvider.value
            val apiKey = when (currentProviderType) {
                "openrouter" -> com.beemovil.security.SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                "ollama" -> com.beemovil.security.SecurePrefs.get(context).getString("ollama_api_key", "") ?: ""
                else -> ""
            }
            if (apiKey.isBlank() && currentProviderType != "local") return@Thread

            val evalPrompt = """Basado en esta observacion de camara: "$analysisResult"
                |
                |Instruccion del usuario: "$agentPrompt"
                |
                |Si la observacion cumple la condicion, responde con ALERTA: [descripcion breve].
                |Si no cumple, responde solo: OK""".trimMargin()

            val provider = LlmFactory.createProvider(currentProviderType, apiKey, viewModel.currentModel.value)
            val msgs = listOf(com.beemovil.llm.ChatMessage(role = "user", content = evalPrompt))
            val response = provider.complete(msgs, emptyList())
            val text = response.text ?: ""

            if (text.contains("ALERTA", ignoreCase = true)) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, text.take(100), Toast.LENGTH_LONG).show()
                }
                // Also speak alert if voice is available
                viewModel.deepgramVoiceManager?.speak(text = text.take(200))
            }
        } catch (_: Exception) {}
    }.start()
}
