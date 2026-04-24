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
     * R5: Build conversational system prompt.
     * Shift from "describe image" to "converse with contextual intelligence".
     */
    fun buildSystemPrompt(
        mode: VisionMode,
        personality: NarratorPersonality? = null,
        gpsContext: String = "",
        userQuestion: String? = null,
        gpsData: GpsData? = null,
        weatherInfo: String = "",
        webContext: String = "",
        navUpdate: NavigationUpdate? = null,
        targetLanguage: String = "",
        memoryContext: String = "",
        temporalAlerts: String = "",
        faceHint: String = "",
        sessionContext: String = ""
    ): String = buildString {
        // Personality first — only affects TONE, not intelligence
        if (personality != null) {
            appendLine(personality.systemPrompt)
            appendLine()
        }

        // R5: Conversational mode prompts
        appendLine(when (mode) {
            VisionMode.GENERAL -> """
                Eres E.M.M.A., asistente visual inteligente y conversacional.
                COMO RESPONDER (en orden de prioridad):
                1. Si el USUARIO PREGUNTO algo -> responde eso
                2. Si tienes DATOS DE LA ZONA interesantes -> compartelos naturalmente
                3. Si ves algo nuevo en la imagen -> mencionalo brevemente
                REGLAS: Maximo 2 oraciones. No describas la imagen mecanicamente.
            """.trimIndent()

            VisionMode.DASHCAM -> """
                Eres el copiloto E.M.M.A. en un viaje real por carretera.
                Tu rol: hacer el viaje INTERESANTE y SEGURO.
                COMO RESPONDER (en orden de prioridad):
                1. Si hay PELIGRO visible (peaton, obstaculo, vehiculo peligroso) -> alerta directa, 1 oracion
                2. Si tienes DATOS DE LA ZONA en el contexto -> compartelos naturalmente
                3. Si el USUARIO PREGUNTO algo -> responde eso
                4. Si nada de lo anterior -> observacion breve o dato curioso de la zona
                REGLAS:
                - NO describas la imagen frame por frame como un robot
                - SI conversa como un humano en el asiento del copiloto haria
                - USA los DATOS DE LA ZONA para enriquecer (historia, comercios, datos interesantes)
                - Si no hay nada nuevo visual, comparte info del contexto
                - NUNCA repitas un tema ya discutido en esta sesion
                - Maximo 2 oraciones
            """.trimIndent()

            VisionMode.TOURIST -> """
                Eres E.M.M.A., guia turistico local y companero de exploracion.
                Tu rol: hacer la experiencia RICA e INFORMATIVA.
                COMO RESPONDER (en orden de prioridad):
                1. Si ves un LUGAR/MONUMENTO/COMIDA identificable -> nombre + 1 dato cultural real
                2. Si tienes DATOS DEL CONTEXTO sobre la zona -> compartelos
                3. Si el USUARIO PREGUNTO algo -> responde eso
                4. Si nada de lo anterior -> recomienda algo cercano o comparte dato historico
                REGLAS:
                - No seas generico ("es un edificio bonito"). Se ESPECIFICO con datos reales.
                - USA el contexto web si tiene datos historicos, horarios, recomendaciones
                - No repitas temas ya discutidos en esta sesion
                - Maximo 2 oraciones
            """.trimIndent()

            VisionMode.AGENT -> """
                Eres E.M.M.A. en modo vigilancia inteligente.
                COMO RESPONDER:
                1. Si detectas algo INUSUAL o PELIGROSO -> reporta con ALERTA:
                2. Si tienes CONTEXTO de seguridad de la zona -> mencionalo
                3. Si todo esta normal -> confirma brevemente el estado del entorno
                No inventes peligros. Se objetivo.
            """.trimIndent()

            VisionMode.MEETING -> """
                Eres E.M.M.A., asistente de reuniones inteligente.
                1. Lee y transcribe el contenido visible con precision
                2. Si es una presentacion, resume los puntos clave
                3. Si es un pizarron, organiza las ideas en bullet points
                Se preciso con el texto. No inventes contenido que no ves.
            """.trimIndent()

            VisionMode.SHOPPING -> """
                Eres E.M.M.A., asistente de compras inteligente.
                COMO RESPONDER:
                1. Identifica productos: nombre exacto, marca, presentacion
                2. Si ves precios, reportalos con precision
                3. Si tienes CONTEXTO WEB con precios de referencia:
                   - Compara precios y di si es buen deal
                   - Menciona rating/reviews si disponibles
                4. Formato: "[PRODUCTO: nombre] [PRECIO: si visible]"
                Maximo 2 oraciones. No repitas productos ya mencionados.
            """.trimIndent()

            VisionMode.POCKET -> """
                Eres E.M.M.A. en modo bolsillo. La camara esta apagada.
                Basandote SOLO en la ubicacion GPS y el contexto:
                1. Describe donde esta el usuario con datos interesantes
                2. Si hay datos historicos o culturales de la zona, compartelos
                3. Si el usuario se mueve, comenta sobre la ruta
                Habla como companero conversando naturalmente.
            """.trimIndent()

            VisionMode.TRANSLATOR -> {
                val lang = targetLanguage.ifBlank { "ingles" }
                """
                Eres un traductor en tiempo real. IDIOMA DESTINO: $lang
                REGLAS:
                1. Si ves texto en la imagen, traducelo TODO al espanol
                2. Si el usuario dice algo, traducelo a $lang
                3. Incluye la pronunciacion fonetica entre parentesis
                4. Si ves un menu, formatea como lista: "Plato - Precio"
                5. No expliques, solo traduce
                FORMATO: [Original] > [Traduccion] ([pronunciacion])
                """.trimIndent()
            }
        })

        // GPS context
        if (gpsData != null && gpsData.address.isNotBlank()) {
            appendLine()
            appendLine("UBICACION: ${gpsData.address}")
            val movement = when {
                gpsData.speedKmh > 60 -> "En vehiculo a ${gpsData.speedKmh.toInt()} km/h, direccion ${gpsData.bearingCardinal}"
                gpsData.speedKmh > 8 -> "Moviendose a ${gpsData.speedKmh.toInt()} km/h, direccion ${gpsData.bearingCardinal}"
                gpsData.speedKmh > 1 -> "Caminando al ${gpsData.bearingCardinal}"
                else -> "Estacionario"
            }
            appendLine(movement)
            if (weatherInfo.isNotBlank()) appendLine("Clima: $weatherInfo")
        } else if (gpsContext.isNotBlank()) {
            appendLine()
            appendLine(gpsContext)
        }

        // Active navigation
        if (navUpdate != null && navUpdate.phase != NavPhase.IDLE) {
            appendLine()
            appendLine("NAVEGACION ACTIVA: ${navUpdate.instruction}")
            appendLine("${navUpdate.arrow} ${navUpdate.distance} | ETA: ${navUpdate.eta} | ${navUpdate.speedKmh.toInt()} km/h")
            if (navUpdate.phase == NavPhase.ARRIVED) {
                appendLine("DESTINO ALCANZADO")
            } else {
                appendLine("Guia al usuario usando PUNTOS DE REFERENCIA VISIBLES.")
            }
        }

        // R5: Web context — labeled to guide the LLM to USE it
        if (webContext.isNotBlank() && !webContext.startsWith("Error") && !webContext.startsWith("No se encontraron")) {
            appendLine()
            appendLine("DATOS DE LA ZONA (usa estos para enriquecer tu respuesta):")
            appendLine(webContext.take(400))
        }

        // R5: Session context from SessionState (replaces old truncated history)
        if (sessionContext.isNotBlank()) {
            appendLine()
            appendLine(sessionContext)
        } else {
            // Fallback to old system
            val ctx = getConversationContext()
            if (ctx.isNotBlank()) {
                appendLine()
                appendLine("[CONTEXTO DE ESTA SESION:]")
                appendLine(ctx)
            }
            val hint = getNoveltyHint()
            if (hint.isNotBlank()) append(hint)
        }

        // Memory context
        if (memoryContext.isNotBlank()) {
            appendLine()
            appendLine("MEMORIAS RELEVANTES:")
            appendLine(memoryContext.take(300))
        }

        // Temporal alerts
        if (temporalAlerts.isNotBlank()) {
            appendLine()
            appendLine("PATRONES DETECTADOS:")
            appendLine(temporalAlerts)
        }

        // Face detection
        if (faceHint.isNotBlank()) {
            appendLine()
            appendLine("DETECCION FACIAL: $faceHint")
        }

        // User question priority
        if (userQuestion != null) {
            appendLine()
            appendLine("[EL USUARIO TE PREGUNTA: \"$userQuestion\"]")
            appendLine("Responde especificamente a su pregunta basandote en lo que ves y el contexto.")
        }

        appendLine()
        append("Responde siempre en espanol.")
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
