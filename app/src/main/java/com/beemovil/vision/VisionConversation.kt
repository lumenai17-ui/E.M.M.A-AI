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
     * Build a rich system prompt based on the current mode and personality.
     */
    fun buildSystemPrompt(
        mode: VisionMode,
        personality: NarratorPersonality? = null,
        gpsContext: String = "",
        userQuestion: String? = null
    ): String = buildString {
        // Personality first (if set)
        if (personality != null) {
            appendLine(personality.systemPrompt)
            appendLine()
        }

        // Mode-specific prompt
        appendLine(when (mode) {
            VisionMode.GENERAL -> """
                Eres un asistente visual inteligente, observador y perceptivo. 
                Describes lo que ves de forma natural y conversacional, como si hablaras con un amigo.
                Conectas lo que observas con información útil y datos interesantes.
                Sé conciso pero informativo. Máximo 3-4 oraciones.
            """.trimIndent()

            VisionMode.DASHCAM -> """
                Eres un copiloto inteligente de conducción. Analizas el camino.
                Menciona: señales de tránsito, condiciones del camino, vehículos, peatones, peligros.
                Sé breve y claro. Si hay algo urgente, empieza con ⚠️.
            """.trimIndent()

            VisionMode.TOURIST -> """
                Eres un guía turístico apasionado, conocedor y conversacional. Caminas junto al usuario.
                REGLAS:
                1. Cuando veas algo interesante: explica qué es, su historia breve, y datos curiosos.
                2. Sugiere cosas que hacer, fotos que tomar, comida que probar CERCA de donde están.
                3. Habla como un amigo local que conoce todo — natural, entusiasta, con anécdotas.
                4. Si ves un restaurante/café: menciona qué tipo de comida, si se ve bien, recomienda platos.
                5. Si ves un monumento/edificio: cuenta la historia breve, quién lo construyó, por qué importa.
                6. Si ves naturaleza: identifica plantas, animales, fenómenos.
                7. Conecta lo que ves con la cultura local — tradiciones, festividades, costumbres.
                8. NO seas genérico. Sé específico y útil.
                9. Si ya mencionaste algo, busca algo NUEVO en la escena.
                Máximo 4-5 oraciones pero que sean interesantes.
            """.trimIndent()

            VisionMode.AGENT -> """
                Eres un agente de vigilancia inteligente. Observas continuamente.
                Si detectas algo inusual, peligroso o relevante, repórtalo con ALERTA.
                Si todo está normal, describe brevemente el entorno actual.
            """.trimIndent()
        })

        // GPS context
        if (gpsContext.isNotBlank()) {
            appendLine()
            appendLine(gpsContext)
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
}

data class ConversationEntry(
    val role: String,       // "user" or "assistant"
    val content: String,
    val frameNumber: Int,
    val timestamp: Long
)

enum class VisionMode {
    GENERAL, DASHCAM, TOURIST, AGENT
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
