package com.beemovil.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.beemovil.llm.*
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.components.VisionModeSelector
import com.beemovil.ui.components.suggestModeBySpeed
import com.beemovil.service.PocketVisionService
import com.beemovil.vision.*
import com.beemovil.voice.DeepgramVoiceManager
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * LiveVisionScreen — Phase V2: La Mirada
 *
 * Real-time camera analysis. Clean UI (~350 lines), logic in VisionCaptureLoop.
 * Controls: Play/Pause, Mute, Interval slider, Back. No tap-to-focus/pinch-to-zoom.
 * Theme-aware via MaterialTheme.colorScheme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVisionScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("beemovil", 0) }
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()

    // ── Permissions ──
    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermissions = granted }
    // R2-9 FIX: Audio permission for STT
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    // ── Vision state ──
    var isLiveActive by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var liveResult by remember { mutableStateOf("") }
    var frameCount by remember { mutableIntStateOf(0) }
    var intervalSeconds by remember { mutableIntStateOf(5) }
    var showSettings by remember { mutableStateOf(false) }
    var customPrompt by remember { mutableStateOf("Describe lo que ves en esta imagen. Sé conciso y en español.") }
    var selectedModel by remember {
        mutableStateOf(prefs.getString("vision_model", "") ?: "")
    }

    // -- V3: Voice state -- R3-1 FIX: Single source of truth (no dual isListening)
    var voiceState by remember { mutableStateOf(VisionVoiceController.VoiceState.IDLE) }
    val isListening = voiceState == VisionVoiceController.VoiceState.LISTENING
    var selectedPersonality by remember { mutableStateOf(NARRATOR_PERSONALITIES.first()) }
    var isNarrationEnabled by remember { mutableStateOf(false) }  // BUG-7 FIX: Don't auto-narrate

    // ── V4: GPS state ──
    var currentGpsData by remember { mutableStateOf(GpsData()) }
    var weatherInfo by remember { mutableStateOf("") }
    var webContext by remember { mutableStateOf("") }
    var navUpdate by remember { mutableStateOf<NavigationUpdate?>(null) }
    var isNavigating by remember { mutableStateOf(false) }

    // ── V5: Mode + Recording state ──
    var selectedMode by remember { mutableStateOf(VisionMode.GENERAL) }
    var suggestedMode by remember { mutableStateOf<VisionMode?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var targetLanguage by remember { mutableStateOf("") } // V6: for translator mode

    // ── Camera ──
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // ── Engines ──
    val captureLoop = remember { VisionCaptureLoop(context) }
    val voiceManager = remember { DeepgramVoiceManager(context).also { it.initialize() } }
    val voiceController = remember { VisionVoiceController(context, voiceManager) }
    val conversation = remember { VisionConversation() }
    val gpsModule = remember { GpsModule(context) }
    val contextProvider = remember { LiveContextProvider(context) }
    val gpsNavigator = remember { GpsNavigator() }
    val intentDetector = remember { VisionIntentDetector(context) }
    val envScanner = remember { com.beemovil.core.EnvironmentScanner(context) }
    val visionRecorder = remember { VisionRecorder(context) }
    val offlineCache = remember { OfflineContextCache.getInstance(context) }
    // V7: Intelligence engines
    val visionAssessor = remember { VisionAssessor() }
    val temporalDetector = remember { TemporalPatternDetector() }
    val memoryManager = remember { VisionMemoryManager(context) }
    val emmaEngine = remember { com.beemovil.core.engine.EmmaEngine(context) }
    val visionBridge = remember { VisionBridge(context, emmaEngine.plugins) }
    // V8: Profile engines
    val emergencyProtocol = remember { EmergencyProtocol(context) }
    val summaryGenerator = remember { SessionSummaryGenerator(context) }
    // R5: Session intelligence
    val sessionState = remember { SessionState() }
    val contextOrchestrator = remember { ContextOrchestrator(context) }
    // V9: Experience engines
    val faceDetector = remember { FaceDetectionModule() }
    var currentFaceHint by remember { mutableStateOf("") }
    var showDashboard by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    // R3-3: Navigation input dialog
    var showNavDialog by remember { mutableStateOf(false) }
    var navDestinationText by remember { mutableStateOf("") }
    // R3-6: DashcamLogger
    val dashcamLogger = remember { DashcamLogger(context) }
    // R3-4: Places sheet
    var showPlacesSheet by remember { mutableStateOf(false) }
    // R3-8: Session exit summary
    var showExitSummary by remember { mutableStateOf(false) }
    var exitSummaryText by remember { mutableStateOf("") }

    // V6: Wire offline cache to context provider
    LaunchedEffect(Unit) {
        contextProvider.setOfflineCache(offlineCache)
        offlineCache.cleanup() // Clean expired entries on startup
    }

    // Auto-select vision model if not set
    LaunchedEffect(Unit) {
        if (selectedModel.isBlank()) {
            selectedModel = autoSelectVisionModel(context, viewModel)
            prefs.edit().putString("vision_model", selectedModel).apply()
        }
    }

    // ── V4: Start GPS + fetch weather once ──
    LaunchedEffect(Unit) {
        if (gpsModule.hasPermission) {
            gpsModule.onLocationUpdate = { data ->
                currentGpsData = data
                // Update navigator if active
                if (gpsNavigator.isNavigating) {
                    val update = gpsNavigator.update(data)
                    navUpdate = update
                    isNavigating = update.phase != NavPhase.IDLE
                    if (update.phase == NavPhase.ARRIVED) {
                        gpsNavigator.stopNavigation()
                    }
                }
            }
            gpsModule.start()
        }
        // Fetch weather once
        val loc = envScanner.getCurrentLocation()
        if (loc != null) {
            weatherInfo = envScanner.fetchWeather(loc.first, loc.second)
        }
    }

    // ── Auto-capture coroutine ──
    LaunchedEffect(isLiveActive, intervalSeconds, selectedModel) {
        if (!isLiveActive) return@LaunchedEffect

        val mySessionId = captureLoop.newSession()
        conversation.markSessionStart() // V8: track session timing
        sessionState.reset() // R5: fresh session state
        contextOrchestrator.reset() // R5: fresh zone tracking

        // V8: Record place visit
        if (currentGpsData.address.isNotBlank()) {
            val profile = memoryManager.placeProfileManager.getOrCreate(
                currentGpsData.latitude, currentGpsData.longitude, currentGpsData.address
            )
            memoryManager.placeProfileManager.recordVisit(profile, selectedMode)
        }

        // Resolve provider
        val modelEntry = ModelRegistry.findModel(selectedModel)
        val providerType = modelEntry?.provider ?: viewModel.currentProvider.value
        val apiKey = getApiKeyForProvider(context, providerType)

        if (apiKey.isBlank() && providerType != "local") {
            liveResult = "⚠️ Configura API key de $providerType en Settings"
            return@LaunchedEffect
        }

        val provider = captureLoop.getOrCreateProvider(providerType, apiKey, selectedModel)
        if (provider == null) {
            liveResult = "⚠️ Error creando provider"
            return@LaunchedEffect
        }

        // Set callbacks — V3: narrate, V5: two-pass + recorder, V7: assessor gate
        captureLoop.onResult = { result ->
            liveResult = result
            conversation.addFrame(result)
            // R5: Feed SessionState for topic tracking
            sessionState.addFrame(result, currentGpsData.address, selectedMode)

            // V7: Assessor — decide IF to narrate
            val assessment = visionAssessor.assess(
                result = result,
                previousResults = conversation.getPreviousResults(),
                mode = selectedMode,
                speedKmh = currentGpsData.speedKmh
            )

            if (assessment.shouldNarrate) {
                voiceController.narrate(result)
                visionAssessor.markNarrated()
            }

            // V7: Memory — save if notable
            if (assessment.shouldSave) {
                memoryManager.saveVisionMemory(
                    lat = currentGpsData.latitude,
                    lng = currentGpsData.longitude,
                    result = result,
                    mode = selectedMode,
                    address = currentGpsData.address
                )
            }

            // R3-6: Log frame to DashcamLogger when in DASHCAM mode
            if (selectedMode == VisionMode.DASHCAM && dashcamLogger.isActive) {
                dashcamLogger.logFrame(frameCount, currentGpsData, result)
            }

            // V8: Auto-emergency alert from assessor
            if (assessment.action == VisionAssessor.Action.SEND_ALERT) {
                if (emergencyProtocol.shouldAutoTrigger()) {
                    coroutineScope.launch {
                        val msg = emergencyProtocol.triggerEmergency(
                            currentGpsData.latitude, currentGpsData.longitude,
                            currentGpsData.address, result
                        )
                        if (msg != null) {
                            liveResult = "ALERTA: Alerta de emergencia enviada"
                        }
                    }
                }
            }

            // V5: Two-pass web enrichment (async, non-blocking)
            coroutineScope.launch {
                contextProvider.extractAndSearch(result, selectedMode)
            }
        }
        captureLoop.onError = { error ->
            liveResult = "⚠️ $error"
        }
        captureLoop.onFrameProcessed = { count ->
            frameCount = count
        }
        // V5: Save frames for timelapse if recording
        captureLoop.onFrameCaptured = { bitmap ->
            // V9+V10: Face detection (throttled to every 3rd frame)
            if (frameCount % 3 == 0) {
                val faceBitmap = bitmap.copy(bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                coroutineScope.launch {
                    val hint = faceDetector.analyze(faceBitmap)
                    currentFaceHint = hint?.promptHint ?: ""
                    faceBitmap.recycle()
                }
            }
            if (isRecording) {
                visionRecorder.saveFrame(bitmap, liveResult, currentGpsData.address)
                bitmap.recycle()
            } else {
                bitmap.recycle()
            }
        }
        // FR-1: Enable barcode scanning in Shopping mode
        captureLoop.barcodeScanEnabled = (selectedMode == VisionMode.SHOPPING)
        captureLoop.onBarcodeDetected = { scan ->
            val info = scan.product
            if (info != null) {
                val productLine = "${info.name}${if (info.brand.isNotBlank()) " (${info.brand})" else ""}"
                liveResult = "PRODUCTO: $productLine\n$liveResult"
            }
        }

        // R3-6: Auto-start DashcamLogger when in DASHCAM mode
        if (selectedMode == VisionMode.DASHCAM) {
            dashcamLogger.startSession()
        }

        // Capture loop
        while (isLiveActive && captureLoop.sessionId.get() == mySessionId) {
            // V10: Adaptive interval based on battery
            val batteryPct = getBatteryLevel(context)
            val adaptiveDelay = captureLoop.getAdaptiveInterval(intervalSeconds, batteryPct)
            kotlinx.coroutines.delay(adaptiveDelay)
            if (!isLiveActive || captureLoop.sessionId.get() != mySessionId) break

            val capture = imageCapture ?: continue

            // V4: Fetch web context (cached, won't hit network every frame)
            if (currentGpsData.address.isNotBlank()) {
                try {
                    webContext = contextProvider.fetchContext(
                        address = currentGpsData.address,
                        mode = selectedMode,
                        coords = currentGpsData.coordsShort
                    )
                } catch (_: Exception) {}
                // V5: Auto-detect mode suggestion by speed
                suggestedMode = suggestModeBySpeed(currentGpsData.speedKmh)
            }

            // R5: ContextOrchestrator assembles all 6 layers with budget
            val userQ = conversation.consumeQuestion()
            val ctxBlock = contextOrchestrator.buildContextBlock(
                gpsData = currentGpsData,
                mode = selectedMode,
                sessionState = sessionState,
                memoryManager = memoryManager,
                temporalDetector = temporalDetector,
                previousResults = conversation.getPreviousResults(),
                intervalSeconds = intervalSeconds,
                weatherInfo = weatherInfo
            )

            // Combine legacy web context with structured location intel
            val twoPassCtx = contextProvider.getTwoPassContext()
            val legacyWeb = if (twoPassCtx.isNotBlank() && webContext.isNotBlank()) {
                "$webContext\n$twoPassCtx"
            } else webContext.ifBlank { twoPassCtx }
            val combinedWebCtx = if (ctxBlock.locationContext.isNotBlank()) {
                ctxBlock.locationContext + if (legacyWeb.isNotBlank()) "\n$legacyWeb" else ""
            } else legacyWeb

            // R3-9: Surface temporal alerts to the user
            if (ctxBlock.temporalContext.isNotBlank() && selectedMode in listOf(VisionMode.AGENT, VisionMode.DASHCAM, VisionMode.SHOPPING)) {
                liveResult = "ALERTA: ${ctxBlock.temporalContext}\n$liveResult"
            }

            // Build prompt with orchestrated context
            val systemPrompt = conversation.buildSystemPrompt(
                mode = selectedMode,
                personality = if (selectedPersonality.id == "default") null else selectedPersonality,
                userQuestion = userQ,
                gpsData = currentGpsData,
                weatherInfo = weatherInfo,
                webContext = combinedWebCtx.take(500),
                navUpdate = navUpdate,
                targetLanguage = targetLanguage,
                memoryContext = ctxBlock.memoryContext,
                temporalAlerts = ctxBlock.temporalContext,
                faceHint = currentFaceHint,
                sessionContext = ctxBlock.sessionContext
            )

            captureLoop.captureAndAnalyze(
                imageCapture = capture,
                executor = cameraExecutor,
                provider = provider,
                systemPrompt = systemPrompt,
                userPrompt = if (userQ != null) userQ else customPrompt,
                mySessionId = mySessionId
            )
        }
    }

    // R5: Progressive session compression every 15 min
    LaunchedEffect(isLiveActive) {
        if (!isLiveActive) return@LaunchedEffect
        while (isLiveActive) {
            kotlinx.coroutines.delay(15 * 60 * 1000L)
            if (!isLiveActive) break
            if (sessionState.needsCompression()) {
                val provider = captureLoop.cachedProvider
                if (provider != null) {
                    try { sessionState.compress(provider) } catch (_: Exception) {}
                }
            }
        }
    }

    // ── V3: Sync voice controller state ──
    LaunchedEffect(isMuted, isNarrationEnabled) {
        voiceController.setMuted(isMuted)
        voiceController.setNarrationEnabled(isNarrationEnabled)  // BUG-5 FIX: Use method that resets state machine
    }
    LaunchedEffect(Unit) {
        voiceController.onStateChange = { state -> voiceState = state }
        // R3-1 FIX: Error callback resets voiceState (which drives isListening)
        voiceController.onError = { error ->
            // voiceState auto-resets via onStateChange -> IDLE
            liveResult = error
        }
        voiceController.onSpeechResult = { spokenText ->
            // R3-1: voiceState auto-transitions via onStateChange -> PROCESSING
            // V4: Intent detection
            val intent = intentDetector.detect(spokenText)
            when (intent.type) {
                VisionIntentDetector.IntentType.STOP_NAV -> {
                    gpsNavigator.stopNavigation()
                    navUpdate = null
                    isNavigating = false
                    voiceController.narrate("Navegación cancelada.")
                }
                VisionIntentDetector.IntentType.NAVIGATION -> {
                    voiceController.narrate("Buscando ${intent.destination}...")
                    coroutineScope.launch {
                        val dest = intentDetector.resolveDestination(
                            intent.destination,
                            currentGpsData.latitude,
                            currentGpsData.longitude
                        )
                        if (dest != null) {
                            gpsNavigator.startNavigation(dest)
                            val firstUpdate = gpsNavigator.update(currentGpsData)
                            navUpdate = firstUpdate
                            isNavigating = true
                            voiceController.narrate("Navegando a ${dest.name}. ${firstUpdate.instruction}")
                        } else {
                            voiceController.narrate("No pude encontrar ${intent.destination}. Intenta con otro nombre.")
                        }
                    }
                }
                VisionIntentDetector.IntentType.POI_SEARCH -> {
                    conversation.addUserQuestion(spokenText)
                    sessionState.addUserQuestion(spokenText)
                }
                // V7: Action intents → VisionBridge
                VisionIntentDetector.IntentType.ACTION -> {
                    val action = visionBridge.parseVoiceAction(spokenText)
                    if (action != null) {
                        coroutineScope.launch {
                            voiceController.narrate("Ejecutando...")
                            val bridgeResult = visionBridge.execute(
                                actionType = action.first,
                                params = action.second,
                                sessionLog = conversation.getSessionLog(),
                                lastResult = conversation.lastResult
                            )
                            voiceController.narrate(bridgeResult.take(150))
                            liveResult = bridgeResult
                        }
                    } else {
                        conversation.addUserQuestion(spokenText)
                        sessionState.addUserQuestion(spokenText)
                    }
                }
                VisionIntentDetector.IntentType.QUESTION -> {
                    conversation.addUserQuestion(spokenText)
                    sessionState.addUserQuestion(spokenText)
                }
            }
        }
    }

    // ── Cleanup + V8: Session summary on dispose ──
    DisposableEffect(Unit) {
        onDispose {
            isLiveActive = false
            captureLoop.stop()
            voiceController.stop()
            voiceManager.destroy()
            gpsModule.stop()
            gpsNavigator.stopNavigation()
            if (visionRecorder.isRecording) visionRecorder.stopRecording()
            if (dashcamLogger.isActive) dashcamLogger.stopSession() // R3-6
            faceDetector.close() // V9
            cameraExecutor.shutdown()

            // BUG-7 FIX: Stop PocketVisionService if running
            if (PocketVisionService.isRunning) {
                val stopIntent = Intent(context, PocketVisionService::class.java).apply {
                    action = PocketVisionService.ACTION_STOP
                }
                context.startService(stopIntent)
            }

            // V8: Generate session summary in background
            if (conversation.frameNumber > 2) {
                coroutineScope.launch {
                    try {
                        val modelEntry = ModelRegistry.findModel(selectedModel)
                        val providerType = modelEntry?.provider ?: "openrouter"
                        val apiKey = getApiKeyForProvider(context, providerType)
                        val modelId = modelEntry?.id ?: selectedModel

                        val summary = summaryGenerator.generate(
                            sessionLog = conversation.getSessionLog(),
                            frameCount = conversation.frameNumber,
                            durationMs = conversation.getSessionDurationMs(),
                            mode = selectedMode,
                            mainAddress = currentGpsData.address,
                            providerType = providerType,
                            apiKey = apiKey,
                            modelId = modelId
                        )
                        summaryGenerator.persist(
                            summary, currentGpsData.latitude, currentGpsData.longitude
                        )

                        // V8: Update place profile with session duration
                        if (currentGpsData.address.isNotBlank()) {
                            val profile = memoryManager.placeProfileManager.getOrCreate(
                                currentGpsData.latitude, currentGpsData.longitude,
                                currentGpsData.address
                            )
                            memoryManager.placeProfileManager.updateSessionEnd(
                                profile, summary.durationMinutes
                            )
                        }
                    } catch (_: Exception) { /* best effort */ }
                }
            }
        }
    }

    // ── Permission gate ──
    if (!hasPermissions) {
        PermissionGateUI(colorScheme, onBack) { permLauncher.launch(Manifest.permission.CAMERA) }
        return
    }

    // ═══ MAIN UI ═══
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // R2-4 FIX: Camera preview OR Pocket placeholder
        if (selectedMode != VisionMode.POCKET) {
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
                                preview, imgCapture
                            )
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                onRelease = {
                    try { ProcessCameraProvider.getInstance(it.context).get().unbindAll() } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Pocket mode: dark placeholder, camera off
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D1A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PhonelinkLock, "Pocket", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Modo Bolsillo Activo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("El servicio analiza en segundo plano", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    Text("Cámara apagada para ahorrar batería", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)
                }
            }
        }

        // ── Top bar overlay ── R2-3 FIX: All elements inside one Row, no absolute positioning
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // R3-8: Back button triggers summary dialog if session had frames
                IconButton(onClick = {
                    isLiveActive = false
                    if (conversation.frameNumber > 3) {
                        coroutineScope.launch {
                            try {
                                val modelEntry = ModelRegistry.findModel(selectedModel)
                                val providerType = modelEntry?.provider ?: "openrouter"
                                val apiKey = getApiKeyForProvider(context, providerType)
                                val modelId = modelEntry?.id ?: selectedModel
                                val summary = summaryGenerator.generate(
                                    sessionLog = conversation.getSessionLog(),
                                    frameCount = conversation.frameNumber,
                                    durationMs = conversation.getSessionDurationMs(),
                                    mode = selectedMode,
                                    mainAddress = currentGpsData.address,
                                    providerType = providerType,
                                    apiKey = apiKey,
                                    modelId = modelId
                                )
                                exitSummaryText = summary.text
                                showExitSummary = true
                            } catch (_: Exception) {
                                onBack()
                            }
                        }
                    } else {
                        onBack()
                    }
                }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live Vision", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    val modelName = ModelRegistry.findModel(selectedModel)?.name ?: selectedModel
                    Text(modelName, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Status badge (compact)
                Surface(
                    color = if (isLiveActive) Color(0xFF00E676).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(6.dp)
                                .clip(CircleShape)
                                .background(if (isLiveActive) Color(0xFF00E676) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            when {
                                isLiveActive && captureLoop.isProcessing.get() -> "Analizando"
                                isLiveActive -> "$frameCount"
                                else -> "Pausado"
                            },
                            fontSize = 10.sp, color = Color.White
                        )
                    }
                }
                // Dashboard badge
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { showDashboard = !showDashboard }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Analytics, "Dashboard", tint = Color(0xFF9b59b6), modifier = Modifier.size(18.dp))
                }
                // R2-3 FIX: Settings gear inside top bar (not absolute positioned)
                IconButton(onClick = { showSettings = !showSettings }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Settings, "Settings", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── V4: Navigation HUD (below top bar) ──
        val nav = navUpdate
        if (isNavigating && nav != null && nav.phase != NavPhase.IDLE) {
            Surface(
                color = if (nav.phase == NavPhase.ARRIVED) Color(0xFF00E676).copy(alpha = 0.85f)
                       else Color(0xFF1565C0).copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(
                        top = 60.dp,
                        // In DASHCAM: narrow to fit between speed badge (left) and minimap (right)
                        start = if (selectedMode == VisionMode.DASHCAM) 80.dp else 16.dp,
                        end = if (selectedMode == VisionMode.DASHCAM) 140.dp else 16.dp
                    )
                    .fillMaxWidth()
            ) {
                val isDashcam = selectedMode == VisionMode.DASHCAM
                Row(
                    modifier = Modifier.padding(if (isDashcam) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isDashcam) {
                        Text(nav.arrow, fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (nav.phase == NavPhase.ARRIVED) "Llegaste!" else "${gpsNavigator.destination?.name ?: ""}",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isDashcam) 11.sp else 14.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${nav.distance} · ${nav.eta}",
                            fontSize = if (isDashcam) 10.sp else 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    if (nav.phase != NavPhase.ARRIVED) {
                        IconButton(
                            onClick = {
                                gpsNavigator.stopNavigation()
                                navUpdate = null
                                isNavigating = false
                            },
                            modifier = Modifier.size(if (isDashcam) 28.dp else 48.dp)
                        ) {
                            Icon(Icons.Filled.Close, "Stop", tint = Color.White, modifier = Modifier.size(if (isDashcam) 16.dp else 24.dp))
                        }
                    }
                }
            }
        } else if (currentGpsData.address.isNotBlank() && selectedMode != VisionMode.DASHCAM) {
            // R2-7 FIX: GPS address badge — hidden in DASHCAM (MiniMap shows location)
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 60.dp, start = 48.dp, end = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.LocationOn, "Location", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        currentGpsData.address,
                        fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    // R3-3: Quick-nav button on address bar
                    IconButton(
                        onClick = { showNavDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Navigation, "Navegar", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // R3-3: Navigation destination input dialog
        if (showNavDialog) {
            AlertDialog(
                onDismissRequest = { showNavDialog = false },
                title = { Text("Navegar a", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Escribe una direccion o lugar:", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = navDestinationText,
                            onValueChange = { navDestinationText = it },
                            placeholder = { Text("Ej: Starbucks, Parque Central...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (navDestinationText.isNotBlank()) {
                            coroutineScope.launch {
                                val dest = intentDetector.resolveDestination(navDestinationText, currentGpsData.latitude, currentGpsData.longitude)
                                if (dest != null) {
                                    gpsNavigator.startNavigation(dest)
                                    isNavigating = true
                                    liveResult = "Navegando a ${dest.name}"
                                } else {
                                    liveResult = "No se pudo encontrar: $navDestinationText"
                                }
                            }
                            showNavDialog = false
                            navDestinationText = ""
                        }
                    }) { Text("Navegar") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNavDialog = false
                        navDestinationText = ""
                    }) { Text("Cancelar") }
                }
            )
        }

        // R3-8: Session exit summary dialog
        if (showExitSummary) {
            AlertDialog(
                onDismissRequest = {
                    showExitSummary = false
                    onBack()
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Summarize, "Summary", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resumen de Sesion", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            "${conversation.frameNumber} frames | ${selectedMode.name} | ${currentGpsData.address.take(40)}",
                            fontSize = 11.sp, color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(exitSummaryText, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExitSummary = false
                        onBack()
                    }) { Text("Cerrar") }
                }
            )
        }

        // R4-1: Interactive Places sheet
        if (showPlacesSheet) {
            var places by remember { mutableStateOf(memoryManager.placeProfileManager.getAllProfiles()) }
            var editingPlace by remember { mutableStateOf<PlaceProfileManager.PlaceProfile?>(null) }
            var editLabelText by remember { mutableStateOf("") }
            var showAddManual by remember { mutableStateOf(false) }
            var manualName by remember { mutableStateOf("") }
            var deleteConfirm by remember { mutableStateOf<PlaceProfileManager.PlaceProfile?>(null) }

            AlertDialog(
                onDismissRequest = { showPlacesSheet = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Place, "Places", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mis Lugares (${places.size})", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            if (currentGpsData.latitude != 0.0) {
                                val profile = memoryManager.placeProfileManager.getOrCreate(
                                    currentGpsData.latitude, currentGpsData.longitude, currentGpsData.address
                                )
                                memoryManager.placeProfileManager.recordVisit(profile, selectedMode)
                                places = memoryManager.placeProfileManager.getAllProfiles()
                                liveResult = "Lugar guardado: ${currentGpsData.address}"
                            }
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.AddLocation, "Agregar actual", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                        // Manual add with label
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            if (showAddManual) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Guardar ubicacion actual como:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = manualName,
                                            onValueChange = { manualName = it },
                                            placeholder = { Text("Casa, Trabajo, Gym...") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(onClick = {
                                            if (manualName.isNotBlank() && currentGpsData.latitude != 0.0) {
                                                val profile = memoryManager.placeProfileManager.getOrCreate(
                                                    currentGpsData.latitude, currentGpsData.longitude, currentGpsData.address
                                                )
                                                memoryManager.placeProfileManager.updateLabel(
                                                    memoryManager.placeProfileManager.recordVisit(profile, selectedMode),
                                                    manualName
                                                )
                                                places = memoryManager.placeProfileManager.getAllProfiles()
                                                manualName = ""
                                                showAddManual = false
                                            }
                                        }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Filled.Check, "Save", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.AddLocation, "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = { showAddManual = true }) {
                                        Text("Guardar ubicacion actual", fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        if (places.isEmpty()) {
                            Text("Aun no hay lugares guardados.", fontSize = 13.sp, color = Color.Gray,
                                modifier = Modifier.padding(vertical = 16.dp))
                        }

                        places.forEach { place ->
                            val daysAgo = ((System.currentTimeMillis() - place.lastVisit) / 86_400_000).toInt()
                            val isEditing = editingPlace == place
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            if (place.label.isNotBlank()) {
                                                Text(place.label, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary)
                                            }
                                            Text(
                                                place.address.ifBlank { "${place.latitude.toInt()}, ${place.longitude.toInt()}" },
                                                fontWeight = if (place.label.isBlank()) FontWeight.SemiBold else FontWeight.Normal,
                                                fontSize = if (place.label.isBlank()) 13.sp else 11.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "${place.visitCount} visitas | hace ${daysAgo}d | ~${place.avgDurationMinutes}min",
                                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            editingPlace = if (isEditing) null else place
                                            editLabelText = place.label
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Filled.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = {
                                            gpsNavigator.startNavigation(NavigationDestination(place.address, place.latitude, place.longitude, "profile"))
                                            isNavigating = true
                                            showPlacesSheet = false
                                            liveResult = "Navegando a ${place.label.ifBlank { place.address }}"
                                        }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Filled.Navigation, "Ir", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { deleteConfirm = place }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Filled.Delete, "Del", tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (isEditing) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                value = editLabelText,
                                                onValueChange = { editLabelText = it },
                                                placeholder = { Text("Casa, Trabajo, Gym...") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(onClick = {
                                                memoryManager.placeProfileManager.updateLabel(place, editLabelText)
                                                places = memoryManager.placeProfileManager.getAllProfiles()
                                                editingPlace = null
                                            }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Filled.Check, "Save", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                    if (place.observations.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Notas: ${place.observations.last()}", fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPlacesSheet = false }) { Text("Cerrar") }
                }
            )

            if (deleteConfirm != null) {
                AlertDialog(
                    onDismissRequest = { deleteConfirm = null },
                    title = { Text("Eliminar lugar?") },
                    text = { Text("Se eliminara ${deleteConfirm?.label?.ifBlank { deleteConfirm?.address } ?: "este lugar"} del historial.") },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteConfirm?.let { memoryManager.placeProfileManager.deleteProfile(it) }
                            places = memoryManager.placeProfileManager.getAllProfiles()
                            deleteConfirm = null
                        }) { Text("Eliminar", color = Color(0xFFFF6B6B)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirm = null }) { Text("Cancelar") }
                    }
                )
            }
        }

        // ── BUG-3 FIX: Dashcam HUD — MiniMap + Speed badge (only in DASHCAM mode) ──
        if (selectedMode == VisionMode.DASHCAM) {
            // MiniMapPIP — already built, just needs to be rendered
            if (currentGpsData.latitude != 0.0) {
                MiniMapPIP(
                    gpsData = currentGpsData,
                    navigator = gpsNavigator,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 60.dp, end = 8.dp)
                )
            }

            // Speed badge
            if (currentGpsData.speedKmh > 1f) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 60.dp, start = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${currentGpsData.speedKmh.toInt()}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentGpsData.speedKmh > 100) Color(0xFFFF5252) else Color.White
                        )
                        Text("km/h", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                        if (currentGpsData.bearingCardinal.isNotBlank()) {
                            Text(
                                currentGpsData.bearingCardinal,
                                fontSize = 11.sp,
                                color = Color(0xFFF5A623),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── Result overlay (glassmorphism, above controls) ── BUG-1 FIX: maxHeight + proper padding
        if (liveResult.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 160.dp)  // BUG-1 FIX: Raised above controls + mode chip
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = 140.dp)  // BUG-1 FIX: Constrain height
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())  // BUG-1 FIX: Scroll if content overflows
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, "AI", tint = Color(0xFFF5A623), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("E.M.M.A.", fontSize = 11.sp, color = Color(0xFFF5A623), fontWeight = FontWeight.Bold)
                            if (selectedPersonality.id != "default") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${selectedPersonality.emoji} ${selectedPersonality.name}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            if (isMuted) {
                                Icon(Icons.Filled.VolumeOff, "Muted", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            liveResult,
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }

        // ── BUG-1 FIX: Mode chip + bottom sheet (replaces inline VisionModeSelector) ──
        var showModeSheet by remember { mutableStateOf(false) }
        val currentModeInfo = remember(selectedMode) {
            com.beemovil.ui.components.VISION_MODES.find { it.mode == selectedMode }
                ?: com.beemovil.ui.components.ModeInfo(VisionMode.GENERAL, Icons.Filled.Visibility, "General")
        }

        // R2-1 FIX: Single compact mode chip — properly cleared above controls
        Surface(
            onClick = { showModeSheet = true },
            color = Color.White.copy(alpha = 0.2f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)  // R2-1 FIX: Clear above 64dp FAB + 12dp padding + 20dp margin
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(currentModeInfo.icon, currentModeInfo.label, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(currentModeInfo.label, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.ExpandMore, "Change mode", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }

        // Mode bottom sheet
        if (showModeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showModeSheet = false },
                containerColor = Color(0xFF1A1A2E)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MODO DE VISIÓN", fontSize = 12.sp, color = Color(0xFFF5A623),
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    com.beemovil.ui.components.VISION_MODES.forEach { info ->
                        val isSelected = selectedMode == info.mode
                        val isSuggested = suggestedMode == info.mode && !isSelected

                        Surface(
                            onClick = {
                                // V6: Start/stop Pocket Service
                                if (info.mode == VisionMode.POCKET && selectedMode != VisionMode.POCKET) {
                                    val pocketIntent = Intent(context, PocketVisionService::class.java).apply {
                                        action = PocketVisionService.ACTION_START
                                        putExtra(PocketVisionService.EXTRA_INTERVAL, intervalSeconds)
                                        putExtra(PocketVisionService.EXTRA_MODE, info.mode.name)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(pocketIntent)
                                    } else {
                                        context.startService(pocketIntent)
                                    }
                                } else if (selectedMode == VisionMode.POCKET && info.mode != VisionMode.POCKET) {
                                    val stopIntent = Intent(context, PocketVisionService::class.java).apply {
                                        action = PocketVisionService.ACTION_STOP
                                    }
                                    context.startService(stopIntent)
                                }
                                selectedMode = info.mode
                                showModeSheet = false
                            },
                            color = when {
                                isSelected -> Color.White.copy(alpha = 0.15f)
                                isSuggested -> Color(0xFFF5A623).copy(alpha = 0.1f)
                                else -> Color.Transparent
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(info.icon, info.label, tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    info.label,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (isSuggested) {
                                    Text("Sugerido", fontSize = 10.sp, color = Color(0xFFF5A623))
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.CheckCircle, "Selected", tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // ── Controls bar (bottom) ── BUG-1 FIX: Reduced to 5 essential buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mic (V3) -- R3-1 FIX: Derived state from voiceController, pulse animation
            Box {
                // Pulse ring behind button when listening
                if (isListening) {
                    val pulseAlpha by rememberInfiniteTransition(label = "micPulse").animateFloat(
                        initialValue = 0.2f,
                        targetValue = 0.6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "micPulseAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color(0xFFFF1744).copy(alpha = pulseAlpha))
                    )
                }
                ControlButton(
                    icon = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (isListening) "Escuchando" else "Mic",
                    isActive = isListening,
                    activeColor = Color(0xFFFF1744),
                    onClick = {
                        if (isListening) {
                            voiceController.stopListening()
                        } else {
                            if (!hasAudioPermission) {
                                audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                liveResult = "Permiso de microfono requerido"
                            } else {
                                val started = voiceController.startListening()
                                if (!started) {
                                    liveResult = "No se pudo iniciar el microfono"
                                }
                            }
                        }
                    }
                )
            }
            // Mute
            ControlButton(
                icon = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                label = if (isMuted) "Muted" else "Audio",
                isActive = !isMuted,
                onClick = {
                    isMuted = voiceController.toggleMute()
                }
            )
            // Play/Pause (main button)
            FloatingActionButton(
                onClick = {
                    if (isLiveActive) {
                        isLiveActive = false
                        captureLoop.stop()
                    } else {
                        isLiveActive = true
                    }
                },
                containerColor = if (isLiveActive) Color(0xFFFF5252) else Color(0xFF00E676),
                contentColor = Color.White,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    if (isLiveActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
            // REC (V5: timelapse) -- R4-2: Post-recording menu with PDF + video
            var showRecMenu by remember { mutableStateOf(false) }
            var recResult by remember { mutableStateOf<VisionRecorder.RecordingResult?>(null) }

            ControlButton(
                icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                label = if (isRecording) "${visionRecorder.getFrameCount()}" else "REC",
                isActive = isRecording,
                activeColor = Color(0xFFFF1744),
                onClick = {
                    if (isRecording) {
                        isRecording = false
                        recResult = visionRecorder.stopRecording()
                        showRecMenu = true
                    } else {
                        visionRecorder.startRecording(selectedMode.name.lowercase())
                        isRecording = true
                        liveResult = "Grabando timelapse..."
                    }
                }
            )

            // Post-recording options dialog
            if (showRecMenu && recResult != null) {
                var isProcessing by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = {
                        if (!isProcessing) {
                            visionRecorder.cleanup()
                            showRecMenu = false
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VideoLibrary, "Rec", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grabacion finalizada", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column {
                            Text("${recResult?.frameCount} frames | ${recResult?.durationSeconds}s | ${(recResult?.sizeBytes ?: 0) / 1024}KB",
                                fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isProcessing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Procesando...", fontSize = 13.sp)
                                }
                            } else {
                                // Video option
                                Surface(
                                    onClick = {
                                        isProcessing = true
                                        coroutineScope.launch {
                                            val mp4 = visionRecorder.encodeToMp4(withOverlay = false)
                                            if (mp4 != null) {
                                                liveResult = "Video guardado en Downloads/EMMA/"
                                            } else {
                                                liveResult = "Error al codificar video"
                                            }
                                            visionRecorder.cleanup()
                                            isProcessing = false
                                            showRecMenu = false
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Videocam, "MP4", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Exportar Video MP4", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("Timelapse en Downloads/EMMA/", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                // PDF option
                                Surface(
                                    onClick = {
                                        isProcessing = true
                                        coroutineScope.launch {
                                            val html = visionRecorder.generateSessionHtml()
                                            if (html != null) {
                                                try {
                                                    val pdfPlugin = com.beemovil.plugins.builtins.PremiumPdfPlugin(context)
                                                    val result = pdfPlugin.execute(mapOf(
                                                        "document_title" to "Vision_Session_${visionRecorder.getFrameCount()}",
                                                        "html_content" to html
                                                    ))
                                                    liveResult = if (result.contains("file_generated")) {
                                                        "PDF guardado en Downloads/EMMA/"
                                                    } else {
                                                        "Error generando PDF"
                                                    }
                                                } catch (e: Exception) {
                                                    liveResult = "Error: ${e.message}"
                                                }
                                            } else {
                                                liveResult = "No hay frames para el PDF"
                                            }
                                            visionRecorder.cleanup()
                                            isProcessing = false
                                            showRecMenu = false
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.PictureAsPdf, "PDF", tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("Exportar PDF Premium", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("Imagenes + analisis AI en PDF", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (!isProcessing) {
                            TextButton(onClick = {
                                visionRecorder.cleanup()
                                showRecMenu = false
                            }) { Text("Descartar") }
                        }
                    }
                )
            }
            // R2-6 FIX: Image analysis button (replaces unused Chat/Share)
            ControlButton(
                icon = Icons.Filled.PhotoCamera,
                label = "Imagen",
                isActive = false,
                onClick = { viewModel.currentScreen.value = "camera" }
            )
        }

        // R2-3 FIX: Gear icon moved inside top bar Row — this absolute one removed

        // ── Settings panel (slide up) ──
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SettingsPanel(
                selectedModel = selectedModel,
                customPrompt = customPrompt,
                intervalSeconds = intervalSeconds,
                isNarrationEnabled = isNarrationEnabled,
                selectedPersonality = selectedPersonality,
                onModelChange = { id ->
                    selectedModel = id
                    prefs.edit().putString("vision_model", id).apply()
                },
                onPromptChange = { customPrompt = it },
                onIntervalChange = { intervalSeconds = it },
                onNarrationToggle = { isNarrationEnabled = it },
                onPersonalityChange = { selectedPersonality = it },
                onDismiss = { showSettings = false },
                emergencyProtocol = emergencyProtocol,
                onPlacesClick = {
                    showSettings = false
                    showPlacesSheet = true
                },
                offlineCache = offlineCache
            )
        }

        // V9: Dashboard overlay
        AnimatedVisibility(
            visible = showDashboard,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
                .statusBarsPadding().padding(top = 60.dp, end = 12.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp).width(180.dp)) {
                    Text("Dashboard", fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    val durationMin = (conversation.getSessionDurationMs() / 60_000).toInt()
                    val stats = memoryManager.getStatsMap()
                    DashRow("Duraci\u00f3n", "${durationMin} min")
                    DashRow("Frames", "$frameCount")
                    DashRow("Memorias", "${stats["memories"] ?: 0}")
                    DashRow("Cache", "${stats["cached"] ?: 0}")
                    if (currentFaceHint.isNotBlank()) {
                        DashRow("Caras", currentFaceHint.substringAfter("Hay ").substringBefore(" persona").trim())
                    }
                    DashRow("Modo", selectedMode.name.lowercase())
                }
            }
        }

        // ── Processing indicator ──
        if (isLiveActive && captureLoop.isProcessing.get()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp),
                color = Color(0xFFF5A623),
                trackColor = Color.Transparent
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// SUB-COMPONENTS
// ═══════════════════════════════════════════════════════

@Composable
private fun DashRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color.White,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor.copy(alpha = 0.2f) else Color.Transparent)
        ) {
            Icon(icon, label, tint = if (isActive) activeColor else Color.Gray, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun PermissionGateUI(
    colorScheme: ColorScheme,
    onBack: () -> Unit,
    onRequest: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.CameraAlt, "Camera", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Permiso de cámara necesario", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Live Vision necesita acceso a la cámara", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(
            containerColor = colorScheme.primary, contentColor = colorScheme.onPrimary
        )) { Text("Otorgar permiso", fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Volver", color = colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPanel(
    selectedModel: String,
    customPrompt: String,
    intervalSeconds: Int,
    isNarrationEnabled: Boolean,
    selectedPersonality: NarratorPersonality,
    onModelChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onNarrationToggle: (Boolean) -> Unit,
    onPersonalityChange: (NarratorPersonality) -> Unit,
    onDismiss: () -> Unit,
    emergencyProtocol: EmergencyProtocol? = null,
    onPlacesClick: (() -> Unit)? = null,
    offlineCache: OfflineContextCache? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentProvider = remember {
        context.getSharedPreferences("beemovil", 0).getString("selected_provider", "openrouter") ?: "openrouter"
    }
    // R2-8 FIX: Dynamic vision model picker with refresh
    var visionModels by remember {
        mutableStateOf(DynamicModelFetcher.getCachedVisionModels(context, currentProvider))
    }
    var isRefreshing by remember { mutableStateOf(false) }
    val displayModels = if (visionModels.isEmpty()) {
        remember { ModelRegistry.getVisionModels() }
    } else {
        visionModels
    }

    Surface(
        color = colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.65f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Configuración", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colorScheme.onSurface)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── V3: Narration toggle ──
            Text("VOZ Y NARRACIÓN", fontSize = 10.sp, color = colorScheme.primary,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.RecordVoiceOver, "Narration", tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Narración automática", fontSize = 14.sp, color = colorScheme.onSurface)
                    Text("E.M.M.A. lee en voz alta lo que ve", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = isNarrationEnabled,
                    onCheckedChange = onNarrationToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = colorScheme.primary)
                )
            }

            // ── V3: Personality selector ──
            if (isNarrationEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("PERSONALIDAD DEL NARRADOR", fontSize = 10.sp, color = colorScheme.primary,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))

                NARRATOR_PERSONALITIES.forEach { p ->
                    val isSelected = selectedPersonality.id == p.id
                    Surface(
                        onClick = { onPersonalityChange(p) },
                        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.emoji, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(p.name, fontSize = 13.sp,
                                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Filled.CheckCircle, "Selected", tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("INTERVALO DE CAPTURA", fontSize = 10.sp, color = colorScheme.primary,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${intervalSeconds}s", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colorScheme.onSurface)
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = intervalSeconds.toFloat(),
                    onValueChange = { onIntervalChange(it.toInt()) },
                    valueRange = 2f..15f,
                    steps = 12,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom prompt
            Text("PROMPT PERSONALIZADO", fontSize = 10.sp, color = colorScheme.primary,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = customPrompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    focusedTextColor = colorScheme.onSurface,
                    unfocusedTextColor = colorScheme.onSurface,
                    cursorColor = colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Vision model selector — R2-8 FIX: Dynamic + Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MODELO DE VISIÓN", fontSize = 10.sp, color = colorScheme.primary,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("${displayModels.size} modelos ($currentProvider)",
                        fontSize = 9.sp, color = colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = {
                        isRefreshing = true
                        scope.launch {
                            val fresh = DynamicModelFetcher.getVisionModels(context, currentProvider)
                            if (fresh.isNotEmpty()) visionModels = fresh
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.Refresh, "Refresh", tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            displayModels.forEach { model ->
                val isSelected = selectedModel == model.id
                Surface(
                    onClick = { onModelChange(model.id) },
                    color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Visibility, "Vision", tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.name, fontSize = 13.sp,
                                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            Text("${model.provider} · ${model.sizeLabel}", fontSize = 10.sp, color = colorScheme.onSurfaceVariant)
                        }
                        if (isSelected) Icon(Icons.Filled.CheckCircle, "Selected", tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // R3-4: Mis Lugares button
            if (onPlacesClick != null) {
                Text("DATOS Y LUGARES", fontSize = 10.sp, color = colorScheme.primary,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    onClick = { onPlacesClick() },
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, "Places", tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mis Lugares", fontSize = 14.sp, color = colorScheme.onSurface)
                            Text("Ver lugares visitados y navegar", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, "Open", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // R3-7: Cache management
            if (offlineCache != null) {
                Spacer(modifier = Modifier.height(12.dp))
                val cacheEntries = remember { offlineCache.entryCount() }
                val cacheSizeKb = remember { offlineCache.dbSizeBytes() / 1024 }
                var showPurgeConfirm by remember { mutableStateOf(false) }

                Surface(
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, "Cache", tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cache Offline", fontSize = 14.sp, color = colorScheme.onSurface)
                            Text("$cacheEntries entradas | ${cacheSizeKb}KB", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showPurgeConfirm = true }) {
                            Text("Limpiar", fontSize = 12.sp, color = Color(0xFFFF6B6B))
                        }
                    }
                }

                if (showPurgeConfirm) {
                    AlertDialog(
                        onDismissRequest = { showPurgeConfirm = false },
                        title = { Text("Limpiar cache?") },
                        text = { Text("Esto borrara $cacheEntries entradas de cache offline. Los datos de memoria (BeeMemory) se mantienen.") },
                        confirmButton = {
                            TextButton(onClick = {
                                offlineCache.purgeAll()
                                showPurgeConfirm = false
                            }) { Text("Limpiar", color = Color(0xFFFF6B6B)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPurgeConfirm = false }) { Text("Cancelar") }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // V9: Emergency config section
            if (emergencyProtocol != null) {
                Text("CONTACTO DE EMERGENCIA", fontSize = 10.sp, color = Color(0xFFFF1744),
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))

                var emergencyConfig by remember {
                    mutableStateOf(emergencyProtocol.loadConfig())
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, "SOS", tint = Color(0xFFFF1744), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Activar emergencia", fontSize = 14.sp, color = colorScheme.onSurface)
                        Text("Botón SOS + auto-detección", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = emergencyConfig.enabled,
                        onCheckedChange = {
                            emergencyConfig = emergencyConfig.copy(enabled = it)
                            emergencyProtocol.saveConfig(emergencyConfig)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF1744))
                    )
                }

                if (emergencyConfig.enabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emergencyConfig.contactName,
                        onValueChange = {
                            emergencyConfig = emergencyConfig.copy(contactName = it)
                            emergencyProtocol.saveConfig(emergencyConfig)
                        },
                        label = { Text("Nombre del contacto") },
                        placeholder = { Text("Ej: Mamá, Esposa") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF1744),
                            unfocusedBorderColor = colorScheme.outline,
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = emergencyConfig.contactNumber,
                        onValueChange = {
                            emergencyConfig = emergencyConfig.copy(contactNumber = it)
                            emergencyProtocol.saveConfig(emergencyConfig)
                        },
                        label = { Text("Teléfono (con código país)") },
                        placeholder = { Text("+507XXXXXXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF1744),
                            unfocusedBorderColor = colorScheme.outline,
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Método: ", fontSize = 13.sp, color = colorScheme.onSurface)
                        EmergencyProtocol.SendMethod.entries.forEach { method ->
                            val isSelected = emergencyConfig.sendMethod == method
                            Surface(
                                onClick = {
                                    emergencyConfig = emergencyConfig.copy(sendMethod = method)
                                    emergencyProtocol.saveConfig(emergencyConfig)
                                },
                                color = if (isSelected) Color(0xFFFF1744).copy(alpha = 0.2f) else colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(method.name, fontSize = 11.sp,
                                    color = if (isSelected) Color(0xFFFF1744) else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** V10: Get current battery level (0-100) */
private fun getBatteryLevel(context: android.content.Context): Int {
    val batteryStatus = context.registerReceiver(null,
        android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100 / scale) else 100
}
