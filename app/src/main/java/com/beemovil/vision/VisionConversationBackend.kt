package com.beemovil.vision

import android.content.Context
import android.util.Log
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.llm.*
import com.beemovil.security.SecurePrefs
import com.beemovil.voice.ConversationBackend
import com.beemovil.voice.ConversationConfig
import com.beemovil.voice.DeepgramVoiceManager
import com.beemovil.voice.ProactiveBackend

/**
 * VisionConversationBackend — V11 Phase 1
 *
 * A ConversationBackend that extends the standard pipeline with:
 * 1. Reading the passive VisionContextStream (Vigía's RAM buffer)
 * 2. Reading the ContextOrchestrator's 7-layer context block (Investigador)
 * 3. Building a vision-enriched system prompt per mode/personality
 * 4. Handling [SNAPSHOT_REQUIRED] for on-demand high-res capture
 *
 * This backend makes the ConversationEngine "vision-aware" without
 * modifying ConversationEngine itself.
 */
class VisionConversationBackend(
    private val context: Context,
    private val voiceManager: DeepgramVoiceManager,
    private val engine: EmmaEngine
) : ConversationBackend, ProactiveBackend {

    companion object {
        private const val TAG = "VisionConvBackend"
    }

    override val id = "vision_pipeline"
    override val displayName = "Vision Pipeline (Vigía + Voz)"
    override val requiresInternet = true
    override val requiredKeys = listOf("openrouter_api_key")

    private var config: ConversationConfig? = null
    private var isActive = false

    // --- Vision context providers (injected by LiveVisionScreen) ---
    var conversation: VisionConversation? = null
    var contextOrchestrator: ContextOrchestrator? = null
    var sessionState: SessionState? = null
    var memoryManager: VisionMemoryManager? = null
    var temporalDetector: TemporalPatternDetector? = null
    var selectedMode: VisionMode = VisionMode.GENERAL
    var selectedPersonality: NarratorPersonality = NARRATOR_PERSONALITIES.first()
    var currentGpsData: GpsData = GpsData()
    var weatherInfo: String = ""
    var webContext: String = ""
    var navUpdate: NavigationUpdate? = null
    var targetLanguage: String = ""
    var currentFaceHint: String = ""
    var intervalSeconds: Int = 5

    // V11 Phase 4: Proactivity dependencies
    var visionAssessor: VisionAssessor? = null

    // --- Snapshot support (injected by LiveVisionScreen) ---
    var captureLoop: VisionCaptureLoop? = null
    var imageCapture: androidx.camera.core.ImageCapture? = null
    var cameraExecutor: java.util.concurrent.Executor? = null

    // Callback to update UI with intermediate state
    var onIntermediateResult: ((String) -> Unit)? = null

    override fun isAvailable(): Boolean {
        val prefs = SecurePrefs.get(context)
        val orKey = prefs.getString("openrouter_api_key", "") ?: ""
        val ollamaUrl = prefs.getString("ollama_base_url", "") ?: ""
        return orKey.isNotBlank() || ollamaUrl.isNotBlank()
    }

    override suspend fun startSession(config: ConversationConfig) {
        this.config = config
        isActive = true
        Log.i(TAG, "Vision session started (${config.llmProvider}:${config.llmModel})")
    }

    override suspend fun processTranscript(transcript: String): String {
        if (!isActive) return ""
        val cfg = config ?: return ""
        val conv = conversation ?: return engine.processUserMessage(
            transcript, cfg.llmProvider, cfg.llmModel,
            threadId = cfg.threadId, senderId = cfg.agentId
        )

        return try {
            // 1. Track the user question in the vision conversation
            conv.addUserQuestion(transcript)
            sessionState?.addUserQuestion(transcript)

            // 2. Build the enriched context from the Investigador (7 layers)
            val ctxBlock = contextOrchestrator?.buildContextBlock(
                gpsData = currentGpsData,
                mode = selectedMode,
                sessionState = sessionState ?: SessionState(),
                memoryManager = memoryManager ?: VisionMemoryManager(context),
                temporalDetector = temporalDetector ?: TemporalPatternDetector(),
                previousResults = conv.getPreviousResults(),
                intervalSeconds = intervalSeconds,
                weatherInfo = weatherInfo
            ) ?: ContextBlock()

            // 3. Build the mode-specific system prompt
            val basePrompt = conv.buildSystemPrompt(
                mode = selectedMode,
                personality = if (selectedPersonality.id == "default") null else selectedPersonality,
                gpsData = currentGpsData,
                weatherInfo = weatherInfo,
                webContext = webContext.take(800), // V11-P4: expanded from 500
                navUpdate = navUpdate,
                targetLanguage = targetLanguage,
                memoryContext = ctxBlock.memoryContext,
                temporalAlerts = ctxBlock.temporalContext,
                faceHint = currentFaceHint,
                sessionContext = ctxBlock.sessionContext,
                crossSystemContext = ctxBlock.crossSystemContext
            )

            // 4. Inject the Vigía's visual log from RAM
            val visionStreamText = VisionContextStream.buildContextForVoiceEngine()

            // 5. Add Snapshot rule
            val snapshotRule = "\nREGLA ESPECIAL V11: Si el usuario te pide detalles visuales específicos que NO están en la bitácora (ej. leer una placa, un cartel pequeño, describir algo detallado), responde ÚNICAMENTE con la palabra: [SNAPSHOT_REQUIRED]."

            // 6. V11 FIX: Route through EmmaEngine (has smart key resolution + fallback)
            // Inject vision context as a user message prefix instead of replacing system prompt
            val visionPrefix = buildString {
                append("[CONTEXTO VISUAL ACTIVO — Modo: ${selectedMode.name}]\n")
                append(basePrompt.take(2500)) // V11-P4: expanded from 1500 to preserve geo/memory/cross-system data
                append("\n\n")
                if (visionStreamText.isNotBlank()) {
                    append(visionStreamText.take(2000)) // V11-P4: expanded from 1000 for more visual history
                    append("\n\n")
                }
                append(snapshotRule)
                append("\n\n--- PREGUNTA DEL USUARIO ---\n")
                append(transcript)
            }

            var resultText = engine.processUserMessage(
                visionPrefix,
                cfg.llmProvider,
                cfg.llmModel,
                threadId = cfg.threadId,
                senderId = cfg.agentId
            )

            // 7. Handle SNAPSHOT_REQUIRED — take a high-res photo and re-query
            if (resultText.contains("[SNAPSHOT_REQUIRED]")) {
                Log.i(TAG, "Snapshot requested by LLM — capturing high-res image")
                onIntermediateResult?.invoke("📸 Capturando en alta resolución...")

                val capture = imageCapture
                val executor = cameraExecutor
                val loop = captureLoop
                if (capture != null && executor != null && loop != null) {
                    val b64Image = loop.takeSnapshotBase64(capture, executor)
                    if (b64Image != null) {
                        val snapMessages = listOf(
                            ChatMessage("system", "Eres E.M.M.A. El usuario te acaba de hacer una pregunta. Responde EXCLUSIVAMENTE basándote en la foto de alta resolución adjunta, que fue tomada en este preciso instante."),
                            ChatMessage("user", transcript, images = listOf(b64Image))
                        )
                        // For snapshot, use the vision model (Vigía's model)
                        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                        val visionModelId = prefs.getString("vision_model", null)
                        val visionModel = if (visionModelId != null) ModelRegistry.findModel(visionModelId) else null
                        
                        val snapProvider = if (visionModel != null && visionModel.hasVision) {
                            val snapApiKey = getApiKeyForProvider(context, visionModel.provider)
                            LlmFactory.createProvider(visionModel.provider, snapApiKey, visionModel.id)
                        } else {
                            // Fallback: find best available vision model from ANY provider
                            val bestVision = ModelRegistry.getVisionModels().firstOrNull()
                            if (bestVision != null) {
                                val snapKey = getApiKeyForProvider(context, bestVision.provider)
                                LlmFactory.createProvider(bestVision.provider, snapKey, bestVision.id)
                            } else {
                                val orKey = SecurePrefs.get(context).getString("openrouter_api_key", "") ?: ""
                                LlmFactory.createProvider("openrouter", orKey, "openai/gpt-4o")
                            }
                        }
                        
                        val response = snapProvider.complete(snapMessages, emptyList())
                        resultText = response.text ?: ""
                    } else {
                        resultText = "Perdón, tuve un problema enfocando la cámara."
                    }
                } else {
                    resultText = "La cámara no está disponible en este momento."
                }
            }

            // 8. Track the response in the vision conversation
            if (resultText.isNotBlank()) {
                conv.addFrame(resultText)
            }

            resultText
        } catch (e: Exception) {
            Log.e(TAG, "processTranscript error: ${e.message}")
            "Error procesando tu mensaje: ${e.message?.take(100)}"
        }
    }

    // ══════════════════════════════════════════
    // V11 Phase 4: ProactiveBackend implementation
    // ══════════════════════════════════════════

    override suspend fun evaluateProactivity(): String? {
        if (!isActive) return null
        val cfg = config ?: return null
        val assessor = visionAssessor ?: return null
        val conv = conversation ?: return null

        val latestEntry = VisionContextStream.getLatestEntry() ?: return null

        val assessment = assessor.assess(
            result = latestEntry.description,
            previousResults = conv.getPreviousResults(),
            mode = selectedMode,
            speedKmh = latestEntry.speedKmh
        )

        if (!assessment.shouldNarrate) return null

        return try {
            // Build enriched context (same 7-layer pipeline as processTranscript)
            val ctxBlock = contextOrchestrator?.buildContextBlock(
                gpsData = currentGpsData,
                mode = selectedMode,
                sessionState = sessionState ?: SessionState(),
                memoryManager = memoryManager ?: VisionMemoryManager(context),
                temporalDetector = temporalDetector ?: TemporalPatternDetector(),
                previousResults = conv.getPreviousResults(),
                intervalSeconds = intervalSeconds,
                weatherInfo = weatherInfo
            ) ?: ContextBlock()

            val urgencyContext = if (assessment.category == VisionAssessor.Category.URGENT) "¡URGENTE!" else "Nota interesante:"

            val proactivePrompt = buildString {
                append("[MODO PROACTIVO — ${selectedMode.name}]\n")
                append("El usuario ha estado en silencio. Tu Vigía de cámara reporta:\n")
                append("[$urgencyContext ${latestEntry.description}]\n\n")
                if (ctxBlock.locationContext.isNotBlank()) {
                    append("DATOS DE LA ZONA: ${ctxBlock.locationContext.take(400)}\n")
                }
                if (ctxBlock.sensorContext.isNotBlank()) {
                    append("SENSORES: ${ctxBlock.sensorContext}\n")
                }
                if (ctxBlock.crossSystemContext.isNotBlank()) {
                    append("CONTEXTO PERSONAL: ${ctxBlock.crossSystemContext.take(300)}\n")
                }
                append("\nEres E.M.M.A. Advierte o comenta esto PROACTIVAMENTE al usuario en UNA SOLA frase natural. ")
                append("NO saludes. Ve directo al punto. Mantén el tono del modo: ${selectedMode.name}.\n\n")
                append("Dime si hay algo importante.")
            }

            val resultText = engine.processUserMessage(
                proactivePrompt,
                cfg.llmProvider,
                cfg.llmModel,
                threadId = "vision_proactive",
                senderId = "vision_heartbeat"
            )

            if (resultText.isNotBlank()) {
                conv.addFrame(resultText)
                assessor.markNarrated()
                resultText
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "evaluateProactivity error: ${e.message}")
            null
        }
    }

    override fun stopSession() {
        isActive = false
        config = null
        Log.i(TAG, "Vision session stopped")
    }

    override fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    override fun isSpeaking(): Boolean = false

    /**
     * Utility: Get API key for a given provider.
     */
    private fun getApiKeyForProvider(context: Context, provider: String): String {
        val prefs = SecurePrefs.get(context)
        return when (provider) {
            "openrouter" -> prefs.getString("openrouter_api_key", "") ?: ""
            "ollama" -> prefs.getString("ollama_base_url", "") ?: ""
            "local" -> "local"
            else -> prefs.getString("openrouter_api_key", "") ?: ""
        }
    }
}
