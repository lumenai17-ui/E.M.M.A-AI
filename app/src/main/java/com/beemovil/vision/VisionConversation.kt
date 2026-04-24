package com.beemovil.vision

import android.content.Context
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.os.Bundle
import android.util.Log
import java.util.Locale

/**
 * VisionConversation — Manages conversational context for LiveVision.
 *
 * Solves:
 * - "Repite mucho lo mismo" → tracks previous results, asks for novelty
 * - "No se siente inteligente" → rich system prompts per mode
 * - "Conversación se queda sin responder" → conversation history
 * - "Micrófono no responde" → fallback SpeechRecognizer (no Deepgram dependency)
 *
 * Usage:
 *   val conversation = VisionConversation()
 *   conversation.addFrame(result) // after each analysis
 *   conversation.buildSystemPrompt(mode, personality) // for LLM call
 *   conversation.getNoveltyHint() // "Ya mencionaste X, di algo nuevo"
 */
class VisionConversation {

    companion object {
        private const val TAG = "VisionConversation"
        private const val MAX_HISTORY = 8
        private const val MAX_PREVIOUS_RESULTS = 5
        private const val REPETITION_THRESHOLD = 0.6f // 60% similarity = repetition
    }

    // Conversation history (user questions + AI responses)
    private val history = mutableListOf<ConversationEntry>()

    // Previous frame analysis results (for repetition detection)
    private val previousResults = mutableListOf<String>()

    /** V7: Expose previous results for VisionAssessor and TemporalPatternDetector */
    fun getPreviousResults(): List<String> = previousResults.toList()

    /** V7: Expose last result for VisionBridge */
    var lastResult: String = ""
        private set

    // Frame counter
    var frameNumber = 0
        private set

    // Current user question (from mic)
    var pendingQuestion: String? = null

    /**
     * Add a frame analysis result. Tracks for repetition detection.
     */
    fun addFrame(result: String) {
        if (result.isBlank() || result.startsWith("[")) return
        frameNumber++
        lastResult = result

        // Add to conversation history
        history.add(ConversationEntry(
            role = "assistant",
            content = result,
            frameNumber = frameNumber,
            timestamp = System.currentTimeMillis()
        ))

        // Track for repetition
        previousResults.add(result.take(200))
        if (previousResults.size > MAX_PREVIOUS_RESULTS) {
            previousResults.removeAt(0)
        }

        // Trim history
        if (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
    }

    /**
     * Add a user question (from mic or text input).
     */
    fun addUserQuestion(question: String) {
        history.add(ConversationEntry(
            role = "user",
            content = question,
            frameNumber = frameNumber,
            timestamp = System.currentTimeMillis()
        ))
        pendingQuestion = question
        if (history.size > MAX_HISTORY) {
            history.removeAt(0)
        }
    }

    /**
     * Clear the pending question (after it's been used in a prompt).
     */
    fun consumeQuestion(): String? {
        val q = pendingQuestion
        pendingQuestion = null
        return q
    }

    /**
     * Check if the latest result is likely a repetition.
     */
    fun isRepetitive(newResult: String): Boolean {
        if (previousResults.size < 2) return false
        val latest = newResult.take(100).lowercase()
        return previousResults.dropLast(1).any { prev ->
            val similarity = calculateSimilarity(latest, prev.take(100).lowercase())
            similarity > REPETITION_THRESHOLD
        }
    }

    /**
     * Get a hint for the LLM to avoid repetition.
     */
    fun getNoveltyHint(): String {
        if (previousResults.size < 2) return ""
        val recentTopics = previousResults.takeLast(3).joinToString("; ") { it.take(50) }
        return "\n[IMPORTANTE: Ya mencionaste estos temas recientemente: \"$recentTopics\". " +
               "Observa algo DIFERENTE o proporciona información NUEVA. No repitas.]"
    }

    /**
     * Get conversation context for the LLM (last N exchanges).
     */
    fun getConversationContext(): String {
        if (history.isEmpty()) return ""
        val recent = history.takeLast(4)
        return recent.joinToString("\n") { entry ->
            when (entry.role) {
                "user" -> "[USUARIO preguntó: \"${entry.content.take(100)}\"]"
                "assistant" -> "[Frame #${entry.frameNumber}: \"${entry.content.take(80)}...\"]"
                else -> ""
            }
        }
    }

    /**
     * Build a rich system prompt based on the current mode, personality, and V4 context.
     */
    fun buildSystemPrompt(
        mode: VisionMode,
        personality: NarratorPersonality? = null,
        gpsContext: String = "",
        userQuestion: String? = null,
        // V4: Enriched context
        gpsData: GpsData? = null,
        weatherInfo: String = "",
        webContext: String = "",
        navUpdate: NavigationUpdate? = null,
        // V6: Translator
        targetLanguage: String = "",
        // V7: Intelligence
        memoryContext: String = "",
        temporalAlerts: String = "",
        // V9: Face detection hint
        faceHint: String = ""
    ): String = buildString {
        // Personality first (if set)
        if (personality != null) {
            appendLine(personality.systemPrompt)
            appendLine()
        }

        // Mode-specific prompt
        appendLine(when (mode) {
            VisionMode.GENERAL -> """
                Eres un asistente visual inteligente. 
                REGLA SUPREMA: Responde con MÁXIMO 2 ORACIONES muy cortas y directas. 
                No des explicaciones largas. Ve al grano, mantén tu personalidad.
            """.trimIndent()

            VisionMode.DASHCAM -> """
                Eres un copiloto inteligente de conducción. Analizas el camino.
                Menciona: señales de tránsito, condiciones del camino, vehículos, peatones, peligros.
                Sé breve y claro. Si hay algo urgente, empieza con ⚠️.
            """.trimIndent()

            VisionMode.TOURIST -> """
                Eres un guía turístico local caminante.
                REGLA SUPREMA: MÁXIMO 2 ORACIONES por cada respuesta.
                1. Identifica qué lugar, comida o cosa ves.
                2. Añade UN (1) dato cultural o recomendación rápida. 
                No seas genérico. Busca algo NUEVO en la escena si ya hablaste de lo anterior.
            """.trimIndent()

            VisionMode.AGENT -> """
                Eres un agente de vigilancia inteligente. Observas continuamente.
                Si detectas algo inusual, peligroso o relevante, repórtalo con ALERTA.
                Si todo está normal, describe brevemente el entorno actual.
            """.trimIndent()

            VisionMode.MEETING -> """
                Eres un asistente de reuniones inteligente.
                REGLA: Identifica texto visible (pizarras, slides, documentos).
                1. Lee y transcribe el contenido visible con precisión
                2. Si es una presentación, resume los puntos clave
                3. Si es un pizarrón, organiza las ideas en bullet points
                Sé preciso con el texto. No inventes contenido que no ves.
            """.trimIndent()

            VisionMode.SHOPPING -> """
                Eres un asistente de compras inteligente.
                1. Identifica productos visibles: nombre exacto, marca, presentación
                2. Si ves precios, repórtalos con precisión
                3. Si ves etiquetas nutricionales, resume calorías y macros
                4. FORMATO: "[PRODUCTO: nombre] [PRECIO: si visible]"
                Si tienes CONTEXTO WEB con precios de referencia:
                - Compara: "En tienda vs Online" e indica si es buen precio
                - Menciona rating/reviews si están disponibles
                Máximo 2 oraciones.
            """.trimIndent()

            VisionMode.POCKET -> """
                Eres E.M.M.A. en modo bolsillo. La cámara está apagada.
                Basándote SOLO en la ubicación GPS y el contexto web:
                1. Describe dónde está el usuario
                2. Narra el entorno basándote en datos del GPS
                3. Si el usuario camina, comenta sobre la ruta
                Habla como su acompañante conversando naturalmente.
                No menciones que no puedes ver. Narra el entorno.
            """.trimIndent()

            VisionMode.TRANSLATOR -> {
                val lang = targetLanguage.ifBlank { "inglés" }
                """
                Eres un traductor en tiempo real. IDIOMA DESTINO: $lang
                REGLAS:
                1. Si ves texto en la imagen, tradúcelo TODO al español
                2. Si el usuario dice algo, tradúcelo a $lang
                3. Incluye la pronunciación fonética entre paréntesis
                4. Si ves un menú, formatea como lista: "Plato - Precio"
                5. No expliques — solo traduce
                FORMATO: [Original] → [Traducción] ([pronunciación])
                """.trimIndent()
            }
        })

        // V4: Semantic GPS block (no raw coordinates)
        if (gpsData != null && gpsData.address.isNotBlank()) {
            appendLine()
            appendLine("📍 UBICACIÓN: ${gpsData.address}")
            val movement = when {
                gpsData.speedKmh > 60 -> "🚗 En vehículo a ${gpsData.speedKmh.toInt()} km/h, dirección ${gpsData.bearingCardinal}"
                gpsData.speedKmh > 8 -> "🚴 Moviéndose a ${gpsData.speedKmh.toInt()} km/h, dirección ${gpsData.bearingCardinal}"
                gpsData.speedKmh > 1 -> "🚶 Caminando al ${gpsData.bearingCardinal}"
                else -> "📌 Estacionario"
            }
            appendLine(movement)
            if (weatherInfo.isNotBlank()) appendLine("🌤️ $weatherInfo")
        } else if (gpsContext.isNotBlank()) {
            // Fallback to legacy gpsContext string
            appendLine()
            appendLine(gpsContext)
        }

        // V4: Active navigation block
        if (navUpdate != null && navUpdate.phase != NavPhase.IDLE) {
            appendLine()
            appendLine("🎯 NAVEGACIÓN ACTIVA → ${navUpdate.instruction}")
            appendLine("${navUpdate.arrow} ${navUpdate.distance} · ETA: ${navUpdate.eta} · ${navUpdate.speedKmh.toInt()} km/h")
            if (navUpdate.phase == NavPhase.ARRIVED) {
                appendLine("🎉 ¡DESTINO ALCANZADO!")
            } else {
                appendLine("⚡ PRIORIDAD: Guía al usuario usando PUNTOS DE REFERENCIA VISIBLES en la imagen. No recites distancias ni coordenadas, describe lo que el usuario VE para orientarse.")
            }
        }

        // V4: Web enrichment context
        if (webContext.isNotBlank() && !webContext.startsWith("Error") && !webContext.startsWith("No se encontraron")) {
            appendLine()
            appendLine("📰 CONTEXTO LOCAL:")
            appendLine(webContext.take(400)) // Cap to avoid blowing context window
        }

        // Conversation context (avoid repetition)
        val ctx = getConversationContext()
        if (ctx.isNotBlank()) {
            appendLine()
            appendLine("[CONTEXTO DE ESTA SESIÓN:]")
            appendLine(ctx)
        }

        // Novelty hint
        val hint = getNoveltyHint()
        if (hint.isNotBlank()) {
            append(hint)
        }

        // V7: Memory context (RAG from past sessions)
        if (memoryContext.isNotBlank()) {
            appendLine()
            appendLine("🧠 MEMORIAS RELEVANTES:")
            appendLine(memoryContext.take(300))
            appendLine("Usa esta información para enriquecer tu respuesta sin repetirla textualmente.")
        }

        // V7: Temporal pattern alerts
        if (temporalAlerts.isNotBlank()) {
            appendLine()
            appendLine("⏱️ PATRONES TEMPORALES DETECTADOS:")
            appendLine(temporalAlerts)
            appendLine("Incorpora estas observaciones temporales de forma natural.")
        }

        // V9: Face detection hint
        if (faceHint.isNotBlank()) {
            appendLine()
            appendLine("👤 DETECCIÓN FACIAL: $faceHint")
            appendLine("Usa esta información para describir las personas con precisión.")
        }

        // User question takes priority
        if (userQuestion != null) {
            appendLine()
            appendLine("[EL USUARIO TE PREGUNTA: \"$userQuestion\"]")
            appendLine("Responde específicamente a su pregunta basándote en lo que ves en la imagen.")
        }

        // Language
        appendLine()
        append("Responde siempre en español.")
    }

    /**
     * Reset conversation (new session).
     */
    fun reset() {
        history.clear()
        previousResults.clear()
        frameNumber = 0
        pendingQuestion = null
    }

    /**
     * Get full session log for export.
     */
    fun getSessionLog(): String = buildString {
        appendLine("=== Sesión de Visión Pro ===")
        appendLine("Total frames: $frameNumber")
        appendLine()
        history.forEach { entry ->
            val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(java.util.Date(entry.timestamp))
            appendLine("[$time] ${entry.role.uppercase()}: ${entry.content}")
            appendLine()
        }
    }

    // Simple word-overlap similarity
    private fun calculateSimilarity(a: String, b: String): Float {
        val wordsA = a.split(" ").filter { it.length > 3 }.toSet()
        val wordsB = b.split(" ").filter { it.length > 3 }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0f
        val common = wordsA.intersect(wordsB).size
        return common.toFloat() / maxOf(wordsA.size, wordsB.size)
    }

    // V8: Session timing
    var sessionStartTime: Long = System.currentTimeMillis()
        private set

    fun markSessionStart() {
        sessionStartTime = System.currentTimeMillis()
    }

    /** V8: Get session duration in ms */
    fun getSessionDurationMs(): Long = System.currentTimeMillis() - sessionStartTime
}

data class ConversationEntry(
    val role: String,       // "user" or "assistant"
    val content: String,
    val frameNumber: Int,
    val timestamp: Long
)

enum class VisionMode {
    GENERAL, DASHCAM, TOURIST, AGENT, MEETING, SHOPPING, POCKET, TRANSLATOR
}

/**
 * NarratorPersonality — Defines the style of the vision narrator.
 */
data class NarratorPersonality(
    val id: String,
    val emoji: String,
    val name: String,
    val systemPrompt: String
)

/** Pre-built personalities */
val NARRATOR_PERSONALITIES = listOf(
    NarratorPersonality("default", "🤖", "Default", ""),
    NarratorPersonality("professional", "🎯", "Profesional",
        "Habla de forma profesional, objetiva y factual. Sin emociones, solo hechos."),
    NarratorPersonality("sarcastic", "😏", "Sarcástico",
        "Habla con sarcasmo e ironía. Haz comentarios graciosos y agudos sobre lo que ves. Sé ingenioso pero no ofensivo."),
    NarratorPersonality("analytical", "🧪", "Analítico",
        "Habla como un científico: detallista, preciso, técnico. Analiza materiales, estructuras, proporciones."),
    NarratorPersonality("showman", "🎪", "Showman",
        "¡Habla como un presentador de TV emocionado! ¡Todo es INCREÍBLE! ¡ESPECTACULAR! Usa exclamaciones y energía."),
    NarratorPersonality("grandpa", "👴", "Abuelo",
        "Habla como un abuelo sabio y nostálgico. Cuenta anécdotas del pasado. 'Esto me recuerda cuando yo era joven...'"),
    NarratorPersonality("detective", "🕵️", "Detective",
        "Habla como un detective suspicaz. Analiza pistas, detalles sospechosos. 'Hmm, interesante... noto que...'"),
    NarratorPersonality("chef", "🧑‍🍳", "Chef",
        "Todo lo relacionas con comida y cocina. Si ves ingredientes, propones recetas. Si ves un lugar, sugieres dónde comer."),
    NarratorPersonality("coach", "🏋️", "Coach",
        "Eres un coach motivacional y deportivo. Motiva al usuario. Sugiere actividades físicas, tips de salud.")
)

/**
 * NativeSpeechInput — Standalone Android SpeechRecognizer for LiveVision.
 * Unlike DeepgramVoiceManager, this works WITHOUT API key.
 */
class NativeSpeechInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        language: String = "es-MX",
        onResult: (String) -> Unit,
        onPartial: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onListening: ((Boolean) -> Unit)? = null
    ) {
        if (isListening) {
            stopListening()
        }

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onListening?.invoke(true)
                    Log.i("NativeSpeechInput", "Ready for speech")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    onListening?.invoke(false)
                }
                override fun onError(error: Int) {
                    isListening = false
                    onListening?.invoke(false)
                    val errMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                        SpeechRecognizer.ERROR_NETWORK -> "Sin red"
                        else -> "Error $error"
                    }
                    Log.w("NativeSpeechInput", "Speech error: $errMsg")
                    onError?.invoke(errMsg)
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    onListening?.invoke(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        Log.i("NativeSpeechInput", "Speech result: $text")
                        onResult(text)
                    } else {
                        onError?.invoke("No se entendió")
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    partial?.firstOrNull()?.let { onPartial?.invoke(it) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("NativeSpeechInput", "Failed to start speech: ${e.message}")
            onError?.invoke("No disponible: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
        isListening = false
    }

    fun destroy() {
        stopListening()
    }
}
