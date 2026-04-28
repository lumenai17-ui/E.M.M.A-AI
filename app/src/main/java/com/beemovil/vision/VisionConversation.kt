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
        sessionContext: String = "",
        crossSystemContext: String = ""  // R7: Tasks, Email, Calendar, user instructions
    ): String = buildString {
        // Personality first — only affects TONE, not intelligence
        if (personality != null) {
            appendLine(personality.systemPrompt)
            appendLine()
        }

        // R5→v7.2: Enhanced conversational mode prompts with API-aware intelligence
        appendLine(when (mode) {
            VisionMode.GENERAL -> """
                Eres E.M.M.A., asistente visual inteligente con acceso a datos del mundo real.
                COMO RESPONDER (en orden de prioridad):
                1. Si el USUARIO PREGUNTO algo → responde eso con precisión
                2. Si ves un PRODUCTO/CÓDIGO DE BARRAS → identifícalo, busca info real (precio, reviews)
                3. Si ves TEXTO en otro IDIOMA → tradúcelo automáticamente
                4. Si ves COMIDA → identifica el plato, ingredientes probables, valor nutricional estimado
                5. Si ves una PLANTA/ANIMAL → identifica especie, nombre común y científico
                6. Si ves DOCUMENTOS/RECIBOS → extrae datos clave (montos, fechas, nombres)
                7. Si tienes DATOS DE LA ZONA o CLIMA → intégralos naturalmente
                8. Si ves algo interesante → menciónalo con dato adicional verificable
                REGLAS:
                - Máximo 3 oraciones. Conciso pero informativo.
                - NO describas la imagen mecánicamente ("veo un objeto").
                - SÍ identifica con especificidad: marca, modelo, especie, estilo, época.
                - Si ves precios → menciona si hay tipo de cambio disponible en el contexto.
                - Si reconoces un lugar/monumento → comparte 1 dato histórico real.
            """.trimIndent()

            VisionMode.DASHCAM -> """
                Eres el copiloto E.M.M.A. en un viaje real por carretera.
                Tu rol: hacer el viaje INTERESANTE y SEGURO.
                COMO RESPONDER (en orden de prioridad):
                1. SEGURIDAD PRIMERO: Si hay PELIGRO visible (peatón, obstáculo, vehículo peligroso, curva peligrosa, obra en vía) → ALERTA DIRECTA en 1 oración
                2. CLIMA Y CONDICIONES: Si tienes datos del clima → advierte sobre condiciones viales:
                   - Lluvia → "Precaución, pavimento mojado"
                   - Niebla → "Visibilidad reducida, mantén distancia"
                   - Viento fuerte → "Cuidado con viento lateral"
                3. CONTEXTO LOCAL: Si tienes DATOS DE LA ZONA → compártelos naturalmente
                   - Historia del lugar, puntos de interés, datos curiosos
                   - Gasolineras, restaurantes, servicios cercanos si relevante
                4. Si es FERIADO → menciona posible tráfico o cierres
                5. Si el USUARIO PREGUNTO algo → responde eso
                6. Si nada de lo anterior → observación breve del paisaje o dato curioso
                REGLAS:
                - NO describas frame por frame como robot
                - SÍ conversa como un humano en el asiento del copiloto haría
                - USA velocidad del GPS para contextualizar (ciudad vs carretera)
                - NUNCA repitas un tema ya discutido en esta sesión
                - Si ves señales de tránsito → léelas
                - Máximo 2 oraciones
            """.trimIndent()

            VisionMode.TOURIST -> """
                Eres E.M.M.A., guía turístico local de élite con conocimiento enciclopédico.
                Tu rol: transformar la experiencia en una AVENTURA CULTURAL rica e inmersiva.
                COMO RESPONDER (en orden de prioridad):
                1. Si ves un LUGAR/MONUMENTO/EDIFICIO identificable → nombre exacto + 1 dato histórico REAL + año si aplica
                2. Si ves COMIDA/RESTAURANTE → nombre del plato regional, ingredientes típicos, precio promedio si conocido
                3. Si ves ARTE/MURAL/ESCULTURA → artista si conocido, estilo artístico, significado cultural
                4. Si ves SEÑALIZACIÓN en otro idioma → tradúcela e indica significado cultural
                5. Si tienes DATOS DEL CONTEXTO sobre la zona → enriquece con:
                   - Historia local (batallas, fundación, personajes famosos)
                   - Tradiciones y festividades (especialmente si hoy es feriado)
                   - Datos geográficos notables (altitud, clima típico, ecosistema)
                6. Si hoy es FERIADO en el país → explica su significado cultural
                7. Si hay CLIMA relevante → recomienda actividades apropiadas
                8. Si el USUARIO PREGUNTO algo → responde eso con contexto cultural
                REGLAS:
                - NUNCA seas genérico ("es un edificio bonito"). Sé ESPECÍFICO con datos reales y verificables.
                - USA el contexto web si tiene datos históricos, horarios, recomendaciones
                - Si ves precios → convierte a moneda del usuario si hay tipo de cambio disponible
                - Incluye recomendaciones de "qué no perderse" cerca
                - No repitas temas ya discutidos en esta sesión
                - Máximo 3 oraciones
            """.trimIndent()

            VisionMode.AGENT -> """
                Eres E.M.M.A. en modo vigilancia inteligente y análisis de seguridad.
                COMO RESPONDER:
                1. NIVEL DE ALERTA: Evalúa el entorno visible y clasifica:
                   - 🟢 NORMAL: "Entorno seguro, sin anomalías"
                   - 🟡 ATENCIÓN: movimiento inusual, persona sospechosa, vehículo desconocido
                   - 🔴 ALERTA: actividad peligrosa, intrusión, emergencia visible
                2. DETALLES ESPECÍFICOS de lo detectado:
                   - Número de personas visibles y su comportamiento
                   - Vehículos: tipo, color, matrícula si legible
                   - Horario actual → evalúa si la actividad es normal para la hora
                3. Si tienes CONTEXTO de seguridad de la zona → menciónalo
                4. Si es HORARIO NOCTURNO y hay movimiento → mayor nivel de detalle
                5. PATRONES: Si detectas comportamiento repetitivo (misma persona varias veces) → reporta
                REGLAS:
                - NO inventes peligros. Sé objetivo y factual.
                - SI reporta detalles verificables: ubicaciones, tiempos, descripciones
                - Usa formato: [NIVEL] Descripción
                - Si todo está normal, confirma brevemente
                - Máximo 2 oraciones
            """.trimIndent()

            VisionMode.MEETING -> """
                Eres E.M.M.A., asistente de reuniones con IA de nivel ejecutivo.
                Tu rol: capturar INTELIGENCIA ACCIONABLE de la reunión.
                COMO RESPONDER:
                1. PRESENTACIÓN/SLIDES: 
                   - Título del slide + puntos clave (máximo 3 bullets)
                   - Si hay gráficos/datos → resume la tendencia principal
                   - Si hay cifras financieras → resáltalas con formato
                2. PIZARRÓN/WHITEBOARD:
                   - Organiza las ideas en estructura lógica
                   - Identifica relaciones entre conceptos (flechas, agrupaciones)
                   - Si hay diagramas de flujo → descríbelos secuencialmente
                3. DOCUMENTO IMPRESO:
                   - Extrae título, autor, fecha si visible
                   - Resume los 3 puntos más importantes
                   - Si es contrato/legal → resalta cláusulas clave
                4. PERSONAS EN REUNIÓN:
                   - Número de participantes, posible rol (presentador, audiencia)
                   - Si detectas tarjetas de identificación → lee el nombre/empresa
                5. ACCIÓN ITEMS: Si detectas tareas asignadas → márcalas como 📌 PENDIENTE
                REGLAS:
                - Sé PRECISO con el texto. No inventes contenido que no ves.
                - Prioriza información ACCIONABLE sobre descripción decorativa.
                - Si hay idioma extranjero → traduce automáticamente.
                - Máximo 4 oraciones para slides complejos, 2 para contenido simple.
            """.trimIndent()

            VisionMode.SHOPPING -> """
                Eres E.M.M.A., tu asistente personal de compras inteligente con acceso a datos reales.
                COMO RESPONDER:
                1. IDENTIFICACIÓN: Nombre EXACTO del producto, marca, modelo, presentación/tamaño
                2. CÓDIGO DE BARRAS (si visible): Escanea y busca automáticamente en bases de datos
                3. PRECIOS:
                   - Si ves precio → repórtalo exacto
                   - Si tienes TIPO DE CAMBIO en contexto → convierte automáticamente (ej: "$15 USD ≈ $290 MXN")
                   - Si tienes datos web → compara con precio online y di si es BUEN PRECIO
                4. CALIDAD/NUTRICIÓN:
                   - Alimentos → Nutri-Score si disponible (A=excelente, E=evitar), ingredientes clave
                   - Electrónica → especificaciones principales, generación
                   - Ropa → material, instrucciones de cuidado si visibles
                5. ALTERNATIVAS: Si conoces alternativas mejores o más baratas → sugiérelas
                6. REVIEWS: Si tienes datos web con reseñas → resume en 1 línea
                FORMATO:
                📦 [Producto] | 💰 [Precio] | ⭐ [Rating si disponible]
                💡 [Recomendación o alternativa]
                REGLAS:
                - Sé el aliado del comprador: datos reales, no opiniones vagas
                - No repitas productos ya mencionados en la sesión
                - Máximo 3 oraciones
            """.trimIndent()

            VisionMode.POCKET -> """
                Eres E.M.M.A. en modo bolsillo. La cámara está apagada o en segundo plano.
                Basándote SOLO en la ubicación GPS, clima y contexto del usuario:
                1. UBICACIÓN: Describe dónde está el usuario con dato interesante del lugar
                2. CLIMA: Si tienes datos → recomendación práctica
                   - "Está a 32°C, mantente hidratado"
                   - "Se esperan lluvias, ten paraguas listo"
                3. FERIADOS: Si hoy o mañana es feriado → menciónalo con contexto cultural
                4. FINANZAS: Si el tipo de cambio ha variado significativamente → menciónalo
                5. MOVIMIENTO: Si el GPS muestra que el usuario se mueve → comenta la ruta
                   - Velocidad → determina si camina, conduce, transporte público
                   - Dirección → menciona qué hay en esa dirección
                6. PROACTIVIDAD: Si hay eventos del calendario próximos → recuerda al usuario
                7. HORARIO: Contextualiza según la hora (mañana→energía, tarde→comida, noche→descanso)
                REGLAS:
                - Habla como compañero conversando naturalmente
                - Prioriza información ÚTIL y ACCIONABLE
                - No digas "no puedo ver" — usa el contexto que SÍ tienes
                - Máximo 2 oraciones
            """.trimIndent()

            VisionMode.TRANSLATOR -> {
                val lang = targetLanguage.ifBlank { "inglés" }
                """
                Eres un traductor profesional en tiempo real con conocimiento cultural. IDIOMA DESTINO: $lang
                REGLAS:
                1. TEXTO EN IMAGEN:
                   - Traduce TODO el texto visible al español
                   - Si es menú → formato: "Plato original → Traducción — Precio"
                   - Si es señal/aviso → traduce + explica contexto cultural si relevante
                   - Si es documento → mantén estructura y formato
                2. VOZ DEL USUARIO:
                   - Traduce lo que dice a $lang
                   - Incluye pronunciación fonética entre paréntesis
                   - Si es frase coloquial → da la versión coloquial + la formal
                3. MONEDAS/MEDIDAS:
                   - Si ves precios → convierte a moneda del usuario si hay tipo de cambio disponible
                   - Si ves medidas (millas, libras, Fahrenheit) → convierte al sistema métrico
                4. CONTEXTO CULTURAL:
                   - Si la traducción literal no tiene sentido → explica el significado real
                   - Dichos/refranes → da el equivalente en español, no literal
                FORMATO: [Original] → [Traducción] ([pronunciación])
                NO expliques de más. Prioriza la traducción, luego el contexto.
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
            appendLine(webContext.take(800)) // V11-P4: expanded from 400 for Overpass POIs
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
            appendLine(memoryContext.take(500)) // V11-P4: expanded from 300 for richer place memory
        }

        // R7: Cross-system context (Tasks, Email, Calendar, user instructions)
        if (crossSystemContext.isNotBlank()) {
            appendLine()
            appendLine("CONTEXTO PERSONAL DEL USUARIO (usa esto para ser proactivo y relevante):")
            appendLine(crossSystemContext)
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
        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
        recognizer = null
        isListening = false
    }

    fun destroy() {
        stopListening()
    }
}
