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
import androidx.compose.foundation.ExperimentalFoundationApi
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

    // 22-A: Native speech input (works without Deepgram API key)
    val nativeSpeech = remember { NativeSpeechInput(context) }

    // 22-F: Session recording and export
    val sessionManager = remember { VisionSessionManager(context) }
    var showSessionDialog by remember { mutableStateOf(false) }

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

    // Voice manager for narration (TTS output)
    val dgVoice = viewModel.deepgramVoiceManager

    // Live context: web search for real-time info
    val liveContext = remember { LiveContextProvider() }

    // Tourist guide: destination coords (resolved by AI)
    var touristBearing by remember { mutableStateOf(0f) }
    var touristDistance by remember { mutableStateOf(0f) }
    var touristDestCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    var showCameraPermDialog by remember { mutableStateOf(false) }

    if (showCameraPermDialog && !hasCameraPermission) {
        PermissionDialog(
            permission = BeePermission.CAMERA,
            onGranted = { hasCameraPermission = true; showCameraPermDialog = false },
            onDenied = { showCameraPermDialog = false },
            onDismiss = { showCameraPermDialog = false }
        )
    }

    // GPS lifecycle
    LaunchedEffect(visionState.gpsOverlay || visionState.dashcamMode) {
        if (visionState.gpsOverlay || visionState.dashcamMode) {
            gpsModule.onLocationUpdate = { data -> gpsData = data }
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
    LaunchedEffect(isLiveActive, intervalSeconds) {
        if (!isLiveActive) return@LaunchedEffect

        while (isLiveActive) {
            kotlinx.coroutines.delay(intervalSeconds * 1000L)
            if (!isLiveActive || isProcessing) continue

            val capture = imageCapture ?: continue
            isProcessing = true

            capture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        try {
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
                                try {
                                    val currentProviderType = viewModel.currentProvider.value
                                    val apiKey = when (currentProviderType) {
                                        "openrouter" -> com.beemovil.security.SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                                        "ollama" -> com.beemovil.security.SecurePrefs.get(context).getString("ollama_api_key", "") ?: ""
                                        else -> ""
                                    }
                                    if (apiKey.isBlank() && currentProviderType != "local") {
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
                                    )

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
                                        model = selectedModel
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

                                    // Voice narration
                                    if (visionState.voiceNarration && liveResult.isNotBlank()) {
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

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            isLiveActive = false
            gpsModule.stop()
            if (dashcamLogger.isActive) dashcamLogger.stopSession()
            cameraExecutor.shutdown()
            nativeSpeech.destroy()
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier.fillMaxSize().background(BeeBlack),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.CameraAlt, "Camera", tint = BeeGray, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Permiso de camara necesario", fontWeight = FontWeight.Bold,
                fontSize = 18.sp, color = BeeWhite)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Vision Pro necesita acceso a la camara", fontSize = 13.sp, color = BeeGray)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showCameraPermDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = BeeYellow, contentColor = BeeBlack)
            ) {
                Text("Permitir camara", fontWeight = FontWeight.Bold)
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

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imgCapture
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
        // GPS OVERLAY (full width, prominent)
        // ═══════════════════════════════════════
        if (visionState.gpsOverlay && gpsData.latitude != 0.0) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 68.dp, start = 10.dp, end = 10.dp),
                colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LocationOn, "GPS", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(gpsData.coordsShort, fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            if (gpsData.address.isNotBlank()) {
                                Text(gpsData.address, fontSize = 12.sp, color = BeeWhite,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (gpsData.speed > 0.5f) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${"%,.0f".format(gpsData.speedKmh)}", fontSize = 22.sp,
                                    color = BeeWhite, fontWeight = FontWeight.Bold)
                                Text("km/h ${gpsData.bearingCardinal}", fontSize = 11.sp, color = BeeGray)
                            }
                        }
                        if (gpsData.altitude > 0) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${"%,.0f".format(gpsData.altitude)}m", fontSize = 14.sp, color = BeeGray)
                                Text("alt", fontSize = 9.sp, color = BeeGray)
                            }
                        }
                    }
                }
            }
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
        if (visionState.touristGuide && touristDestCoords != null && gpsData.latitude != 0.0) {
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

        // ═══════════════════════════════════════
        // MODULES PANEL (slide-in right)
        // ═══════════════════════════════════════
        AnimatedVisibility(
            visible = showModules,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Card(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .padding(vertical = 56.dp),
                colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.92f)),
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
                                if (isSelected) Icon(Icons.Filled.CheckCircle, "Sel", tint = BeeYellow, modifier = Modifier.size(14.dp))
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
        // RESULT OVERLAY (bottom)
        // ═══════════════════════════════════════
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            // AR overlay mode: text on camera — PROMINENT
            if (visionState.arTextOverlay && liveResult.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF00BCD4).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00BCD4).copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Visibility, "AR", tint = Color(0xFF00BCD4), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VISION AI", fontSize = 11.sp, color = Color(0xFF00BCD4),
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
                            fontSize = 15.sp,
                            color = Color.White,
                            lineHeight = 22.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (!visionState.arTextOverlay && liveResult.isNotBlank()) {
                // Classic card mode
                Card(
                    colors = CardDefaults.cardColors(containerColor = BeeBlack.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SmartToy, "Bee", tint = BeeYellow, modifier = Modifier.size(14.dp))
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
                        Text(liveResult, fontSize = 14.sp, color = Color(0xFFE0E0E0),
                            lineHeight = 20.sp, maxLines = 6)
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
                            selectedModel, customPrompt, visionState, gpsData, dgVoice, dashcamLogger,
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

                // Mic — voice question (22-A: uses NativeSpeechInput, no API key needed)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            if (isVoiceListening) {
                                nativeSpeech.stopListening()
                                isVoiceListening = false
                                partialSpeech = ""
                            } else {
                                isVoiceListening = true
                                partialSpeech = ""
                                nativeSpeech.startListening(
                                    onResult = { text ->
                                        isVoiceListening = false
                                        partialSpeech = ""
                                        // Add to conversation context
                                        conversation.addUserQuestion(text)
                                        // Trigger single capture with the voice question
                                        triggerSmartCapture(
                                            imageCapture, cameraExecutor, viewModel, context,
                                            selectedModel, text, visionState, gpsData,
                                            dgVoice, dashcamLogger, conversation, selectedPersonality,
                                            onResult = { liveResult = it; frameCount++ },
                                            onProcessing = { isProcessing = it }
                                        )
                                    },
                                    onPartial = { partialSpeech = it },
                                    onError = { err ->
                                        isVoiceListening = false
                                        partialSpeech = ""
                                        // Fallback: try DeepgramVoiceManager
                                        dgVoice?.startListening(
                                            onResult = { text ->
                                                conversation.addUserQuestion(text)
                                                triggerSmartCapture(
                                                    imageCapture, cameraExecutor, viewModel, context,
                                                    selectedModel, text, visionState, gpsData,
                                                    dgVoice, dashcamLogger, conversation, selectedPersonality,
                                                    onResult = { liveResult = it; frameCount++ },
                                                    onProcessing = { isProcessing = it }
                                                )
                                            },
                                            onError = { Toast.makeText(context, "Mic: $err", Toast.LENGTH_SHORT).show() }
                                        )
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
                        onClick = { isLiveActive = !isLiveActive },
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
                            )

                            val provider = LlmFactory.createProvider(currentProviderType, apiKey, model)
                            val msgs = listOf(
                                ChatMessage(role = "system", content = systemPrompt),
                                ChatMessage(role = "user", content = userQuestion, images = listOf(b64))
                            )
                            val response = provider.complete(msgs, emptyList())
                            val result = response.text ?: ""
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
