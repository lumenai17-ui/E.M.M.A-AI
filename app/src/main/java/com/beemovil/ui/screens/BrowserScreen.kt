package com.beemovil.ui.screens

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.beemovil.agent.*
import com.beemovil.llm.LlmFactory
import com.beemovil.llm.ModelRegistry
import com.beemovil.memory.BrowserActivityLog
import com.beemovil.skills.BrowserSkill
import com.beemovil.ui.ChatViewModel
import com.beemovil.ui.components.*
import com.beemovil.ui.theme.*

// ── Color tokens ──
private val StatusBarBg = Color(0xFF0D0D1A)
private val ToastBg = Color(0xFF1E1E3F)
private val ToastSuccess = Color(0xFF4CAF50)
private val ToastError = Color(0xFFF44336)
private val BeeYellowAccent = Color(0xFFD4A843)
private val FabBg = Color(0xFF1A1A2E)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    browserSkill: BrowserSkill?,
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // ── URL state ──
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var urlInput by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("Browser") }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var lastLoadedUrl by remember { mutableStateOf("") }

    // ── Chat panel state ──
    var showChatPanel by remember { mutableStateOf(false) }
    val chatMessages = remember { mutableStateListOf<BrowserChatMessage>() }

    // ── Agent state ──
    var agentStatus by remember { mutableStateOf(TaskStatus.IDLE) }
    var agentStatusText by remember { mutableStateOf("Listo") }
    val activityLog = remember { BrowserActivityLog(context) }
    val agentLoop = remember {
        browserSkill?.let { BrowserAgentLoop(context, it, activityLog) }
    }

    // ── Toast state ──
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var toastIsError by remember { mutableStateOf(false) }

    // ── Modal (human help) state ──
    var showHelpModal by remember { mutableStateOf(false) }
    var helpModalText by remember { mutableStateOf("") }

    // ── Model selection ──
    val selectedModel = viewModel.currentModel.value
    val availableModels = remember {
        (ModelRegistry.OPENROUTER + ModelRegistry.LOCAL).map { it.id }.take(10)
    }

    // ── Cookie persistence ──
    LaunchedEffect(Unit) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(null, true)
    }

    // ── Configure agent loop callbacks ──
    LaunchedEffect(agentLoop) {
        agentLoop?.onStepComplete = { step, result ->
            val emoji = if (result.contains("error", true)) "X" else "OK"
            toastMessage = "$emoji ${step.action}: ${result.take(40)}"
            toastIsError = result.contains("error", true)
        }
        agentLoop?.onStatusChange = { status, text ->
            agentStatus = status
            agentStatusText = text ?: "Listo"
            when (status) {
                TaskStatus.PAUSED_NEED_HELP -> {
                    helpModalText = text ?: "Necesito tu ayuda"
                    showHelpModal = true
                }
                TaskStatus.PAUSED_LOOP -> {
                    helpModalText = text ?: "Loop detectado"
                    showHelpModal = true
                }
                TaskStatus.COMPLETED -> {
                    toastMessage = "Tarea completada"
                    toastIsError = false
                }
                TaskStatus.FAILED -> {
                    toastMessage = "Tarea fallida"
                    toastIsError = true
                }
                else -> {}
            }
        }
        agentLoop?.onAgentMessage = { msg ->
            chatMessages.add(BrowserChatMessage(msg, MessageSender.AGENT))
        }
    }

    // ── Auto-dismiss toast ──
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2500)
            toastMessage = null
        }
    }

    // ── Helper: send message to agent ──
    fun sendToAgent(message: String) {
        if (browserSkill == null) {
            chatMessages.add(BrowserChatMessage("Browser no inicializado", MessageSender.SYSTEM))
            return
        }

        chatMessages.add(BrowserChatMessage(message, MessageSender.USER))

        // Start agent task in background
        Thread {
            try {
                val modelEntry = ModelRegistry.findModel(selectedModel)
                val isLocal = modelEntry?.provider == "local"
                val providerType = if (isLocal) "local" else viewModel.currentProvider.value
                val apiKey = when (providerType) {
                    "openrouter" -> com.beemovil.security.SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                    "ollama" -> com.beemovil.security.SecurePrefs.get(context).getString("ollama_api_key", "") ?: ""
                    else -> ""
                }
                val provider = LlmFactory.createProvider(providerType, apiKey, selectedModel)

                if (agentStatus == TaskStatus.PAUSED_NEED_HELP || agentStatus == TaskStatus.PAUSED_LOOP) {
                    // Resume with user's message as context
                    chatMessages.add(BrowserChatMessage("Reanudando...", MessageSender.SYSTEM))
                    agentLoop?.resumeTask(provider, selectedModel)
                } else {
                    agentLoop?.startTask(message, provider, selectedModel)
                }
            } catch (e: Throwable) {
                chatMessages.add(BrowserChatMessage("Error: ${e.message?.take(80)}", MessageSender.SYSTEM))
                agentStatus = TaskStatus.FAILED
                agentStatusText = "Error"
            }
        }.start()
    }

    // ── Quick action handler ──
    fun handleQuickAction(action: String) {
        if (browserSkill == null) return
        Thread {
            val result = browserSkill.execute(org.json.JSONObject().put("action", action))
            val msg = result.optString("message", result.toString().take(200))
            chatMessages.add(BrowserChatMessage(msg, MessageSender.AGENT))
        }.start()
    }

    // ═══════════════════════════════════════════
    // UI Layout
    // ═══════════════════════════════════════════

    Box(modifier = Modifier.fillMaxSize().background(BeeBlack)) {

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top: URL Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StatusBarBg)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = BeeYellowAccent, modifier = Modifier.size(20.dp))
                }

                // URL input
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("URL o buscar...", color = BeeGray, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = BeeWhite),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeeYellowAccent,
                        unfocusedBorderColor = BeeGray.copy(alpha = 0.2f),
                        cursorColor = BeeYellowAccent,
                        focusedContainerColor = Color(0xFF0A0A18),
                        unfocusedContainerColor = Color(0xFF0A0A18)
                    ),
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val url = urlInput.trim()
                        currentUrl = when {
                            url.startsWith("http") -> url
                            url.startsWith("file://") -> url
                            url.contains(".") && !url.contains(" ") -> "https://$url"
                            else -> "https://www.google.com/search?q=${url.replace(" ", "+")}"
                        }
                    })
                )

                // Go button
                IconButton(onClick = {
                    val url = urlInput.trim()
                    currentUrl = when {
                        url.startsWith("http") -> url
                        url.startsWith("file://") -> url
                        url.contains(".") && !url.contains(" ") -> "https://$url"
                        else -> "https://www.google.com/search?q=${url.replace(" ", "+")}"
                    }
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Search, "Go", tint = BeeYellowAccent, modifier = Modifier.size(18.dp))
                }
            }

            // ── Status bar (agent state) ──
            AnimatedVisibility(visible = agentStatus != TaskStatus.IDLE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when (agentStatus) {
                                TaskStatus.RUNNING -> ToastSuccess.copy(alpha = 0.1f)
                                TaskStatus.PAUSED_NEED_HELP, TaskStatus.PAUSED_LOOP -> BeeYellowAccent.copy(alpha = 0.1f)
                                TaskStatus.COMPLETED -> Color(0xFF2196F3).copy(alpha = 0.1f)
                                else -> ToastError.copy(alpha = 0.1f)
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status dot
                    val dotColor = when (agentStatus) {
                        TaskStatus.RUNNING -> ToastSuccess
                        TaskStatus.PAUSED_NEED_HELP, TaskStatus.PAUSED_LOOP -> BeeYellowAccent
                        TaskStatus.COMPLETED -> Color(0xFF2196F3)
                        else -> ToastError
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        agentStatusText.take(50),
                        fontSize = 11.sp,
                        color = BeeWhite.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // STOP button
                    if (agentStatus == TaskStatus.RUNNING) {
                        TextButton(
                            onClick = { agentLoop?.cancelTask() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("STOP", fontSize = 10.sp, color = ToastError, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Loading bar ──
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = BeeYellowAccent,
                    trackColor = StatusBarBg
                )
            }

            // ── WebView ──
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                pageTitle = view?.title ?: ""
                                urlInput = url ?: ""
                                // Persist cookies
                                CookieManager.getInstance().flush()
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isLoading = true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 100) isLoading = false
                            }
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title ?: ""
                            }
                        }

                        // Download listener — files go to Downloads folder
                        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                            try {
                                val request = DownloadManager.Request(Uri.parse(url)).apply {
                                    setMimeType(mimetype)
                                    addRequestHeader("User-Agent", userAgent)
                                    val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    setTitle(filename)
                                    setDescription("Descargando desde Browser Agent")
                                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                                }
                                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.enqueue(request)
                                toastMessage = "Descargando archivo..."
                                toastIsError = false
                            } catch (e: Exception) {
                                toastMessage = "Error al descargar: ${e.message?.take(40)}"
                                toastIsError = true
                            }
                        }

                        // Register with BrowserSkill
                        browserSkill?.initWebView(this)
                        browserSkill?.onNavigate = { url -> currentUrl = url }

                        loadUrl(currentUrl)
                    }
                },
                update = { webView ->
                    if (currentUrl != lastLoadedUrl && currentUrl.isNotBlank()) {
                        lastLoadedUrl = currentUrl
                        webView.loadUrl(currentUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ═══════════════════════════════════════════
        // Overlay elements (on top of WebView)
        // ═══════════════════════════════════════════

        // ── Toast overlay (2s auto-dismiss) ──
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (toastIsError) ToastError.copy(alpha = 0.9f) else ToastBg.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    toastMessage ?: "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    color = BeeWhite,
                    maxLines = 2
                )
            }
        }

        // ── FAB (open chat panel) ──
        FloatingActionButton(
            onClick = { showChatPanel = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(48.dp),
            containerColor = FabBg,
            contentColor = BeeYellowAccent,
            shape = CircleShape
        ) {
            Icon(Icons.Filled.SmartToy, "Agent", modifier = Modifier.size(22.dp))
        }

        // ── Chat panel (bottom sheet) ──
        AnimatedVisibility(
            visible = showChatPanel,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
        ) {
            BrowserChatPanel(
                messages = chatMessages,
                agentStatus = agentStatus,
                statusText = agentStatusText,
                currentModel = selectedModel,
                availableModels = availableModels,
                onSendMessage = { msg -> sendToAgent(msg) },
                onQuickAction = { action -> handleQuickAction(action) },
                onStopAgent = { agentLoop?.cancelTask() },
                onResumeAgent = { sendToAgent("Listo, ya resolvi el problema. Continua.") },
                onModelChange = { /* TODO: model switch */ },
                onDismiss = { showChatPanel = false }
            )
        }

        // ── Help modal (agent needs human) ──
        if (showHelpModal) {
            Dialog(onDismissRequest = { showHelpModal = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.PanTool,
                            "Help needed",
                            tint = BeeYellowAccent,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Agente Pausado",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = BeeWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            helpModalText,
                            fontSize = 14.sp,
                            color = BeeGray,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Action buttons
                        Button(
                            onClick = {
                                showHelpModal = false
                                sendToAgent("Listo, ya resolvi el problema. Continua.")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ToastSuccess.copy(alpha = 0.8f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Check, "done", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Listo, continua")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                showHelpModal = false
                                showChatPanel = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BeeYellowAccent)
                        ) {
                            Icon(Icons.Filled.Chat, "chat", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abrir chat")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                showHelpModal = false
                                agentLoop?.cancelTask()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar tarea", color = ToastError.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
