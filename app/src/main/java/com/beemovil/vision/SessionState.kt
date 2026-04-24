package com.beemovil.vision

import android.util.Log
import com.beemovil.llm.ChatMessage
import com.beemovil.llm.LlmProvider

/**
 * SessionState — R5 Phase 1: Session Intelligence
 *
 * Tracks conversational state across an entire vision session.
 * Instead of truncated 4-exchange history (80 chars each), maintains:
 * - Topics discussed (avoid repetition)
 * - Insights already shared (don't re-share)
 * - Progressive narrative ("Viaje Santiago→Chitré")
 * - Compressed summary every 15 min via LLM
 *
 * The prompt receives a rich, compact session context instead of raw exchanges.
 */
class SessionState {

    companion object {
        private const val TAG = "SessionState"
        private const val MAX_TOPICS = 20
        private const val MAX_INSIGHTS = 15
        private const val MAX_ZONES = 10
        private const val COMPRESSION_INTERVAL_MS = 15 * 60 * 1000L // 15 min
        private const val FRAMES_PER_LOCAL_UPDATE = 5
    }

    // Core tracking
    private val topicsDiscussed = mutableListOf<String>()
    private val insightsShared = mutableListOf<String>()
    private val queriesUsed = mutableSetOf<String>()
    private val zonesVisited = mutableListOf<String>()
    private val userQuestions = mutableListOf<String>()

    // Narrative
    private var currentNarrative = ""
    private var sessionSummary = ""
    private var startAddress = ""
    private var lastAddress = ""

    // Timing
    private var sessionStartTime = System.currentTimeMillis()
    private var lastCompressionTime = System.currentTimeMillis()
    private var framesSinceCompression = 0
    private var totalFrames = 0

    // Mode tracking
    private var primaryMode: VisionMode = VisionMode.GENERAL

    /**
     * Called after each LLM response. Extracts topics and updates state.
     */
    fun addFrame(result: String, address: String, mode: VisionMode) {
        if (result.isBlank()) return
        totalFrames++
        framesSinceCompression++
        primaryMode = mode

        // Track address progression
        if (startAddress.isBlank() && address.isNotBlank()) {
            startAddress = address
        }
        if (address.isNotBlank() && address != lastAddress) {
            lastAddress = address
            val shortZone = extractZone(address)
            if (shortZone.isNotBlank() && shortZone !in zonesVisited) {
                zonesVisited.add(shortZone)
                if (zonesVisited.size > MAX_ZONES) zonesVisited.removeAt(0)
            }
        }

        // Extract and store topics from LLM result
        val newTopics = extractTopics(result, mode)
        newTopics.forEach { topic ->
            if (topic !in topicsDiscussed) {
                topicsDiscussed.add(topic)
                if (topicsDiscussed.size > MAX_TOPICS) topicsDiscussed.removeAt(0)
            }
        }

        // Track insights (data/facts the LLM shared, not visual descriptions)
        val insight = extractInsight(result)
        if (insight != null && insight !in insightsShared) {
            insightsShared.add(insight)
            if (insightsShared.size > MAX_INSIGHTS) insightsShared.removeAt(0)
        }

        // Update narrative locally every N frames
        if (framesSinceCompression % FRAMES_PER_LOCAL_UPDATE == 0) {
            updateNarrativeLocal()
        }
    }

    /**
     * Track user questions for context
     */
    fun addUserQuestion(question: String) {
        if (question.isNotBlank()) {
            userQuestions.add(question.take(80))
            if (userQuestions.size > 5) userQuestions.removeAt(0)
        }
    }

    /**
     * Mark a web query as used (don't repeat it)
     */
    fun markQueryUsed(query: String) {
        queriesUsed.add(query.lowercase().take(60))
    }

    fun wasQueryUsed(query: String): Boolean = query.lowercase().take(60) in queriesUsed

    /**
     * Check if it's time for LLM-powered compression (every 15 min)
     */
    fun needsCompression(): Boolean {
        return System.currentTimeMillis() - lastCompressionTime > COMPRESSION_INTERVAL_MS
                && framesSinceCompression > 10
    }

    /**
     * Compress session via LLM. Call on background thread.
     * Takes the full state and produces a 2-sentence summary.
     */
    suspend fun compress(provider: LlmProvider) {
        val raw = buildRawState()
        if (raw.length < 50) return

        try {
            val prompt = buildString {
                appendLine("Resume esta sesion de vision en MAXIMO 2 oraciones naturales.")
                appendLine("Incluye: donde estuvo, que zonas recorrio, temas principales.")
                appendLine("NO incluyas datos tecnicos. Se conciso.")
                appendLine()
                appendLine(raw)
            }

            val messages = listOf(
                ChatMessage("system", prompt),
                ChatMessage("user", "Genera el resumen comprimido.")
            )
            val response = provider.complete(messages, emptyList())
            val compressed = response.text?.take(200)

            if (!compressed.isNullOrBlank()) {
                sessionSummary = compressed
                framesSinceCompression = 0
                lastCompressionTime = System.currentTimeMillis()
                Log.i(TAG, "Session compressed: ${compressed.take(80)}...")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Compression failed: ${e.message}")
            // Fallback: local compression
            sessionSummary = buildLocalSummary()
            framesSinceCompression = 0
            lastCompressionTime = System.currentTimeMillis()
        }
    }

    /**
     * Build the context block for prompt injection.
     * This replaces the old 4-exchange truncated history.
     */
    fun buildPromptContext(): String {
        val durationMin = ((System.currentTimeMillis() - sessionStartTime) / 60_000).toInt()
        if (durationMin < 1 && topicsDiscussed.isEmpty()) return ""

        return buildString {
            appendLine("[SESION ACTIVA: ${durationMin} min]")

            // Narrative or summary
            val narrative = sessionSummary.ifBlank { currentNarrative }
            if (narrative.isNotBlank()) {
                appendLine(narrative)
            }

            // Zones
            if (zonesVisited.size > 1) {
                appendLine("Ruta: ${zonesVisited.joinToString(" > ")}")
            }

            // Topics already discussed (anti-repetition)
            if (topicsDiscussed.isNotEmpty()) {
                val recentTopics = topicsDiscussed.takeLast(6).joinToString(", ")
                appendLine("Ya discutimos: $recentTopics")
            }

            // Recent user questions
            if (userQuestions.isNotEmpty()) {
                appendLine("El usuario pregunto: ${userQuestions.last()}")
            }

            // Directive
            appendLine("INSTRUCCION: Comparte algo NUEVO y relevante. No repitas temas anteriores.")
        }
    }

    /**
     * Check if a topic was already discussed.
     */
    fun wasTopicDiscussed(topic: String): Boolean {
        val lower = topic.lowercase()
        return topicsDiscussed.any { it.lowercase().contains(lower) || lower.contains(it.lowercase()) }
    }

    /**
     * Get session duration in minutes
     */
    fun getDurationMinutes(): Int = ((System.currentTimeMillis() - sessionStartTime) / 60_000).toInt()

    fun getTopicCount(): Int = topicsDiscussed.size
    fun getZonesVisited(): List<String> = zonesVisited.toList()
    fun getTotalFrames(): Int = totalFrames

    /**
     * Reset for new session
     */
    fun reset() {
        topicsDiscussed.clear()
        insightsShared.clear()
        queriesUsed.clear()
        zonesVisited.clear()
        userQuestions.clear()
        currentNarrative = ""
        sessionSummary = ""
        startAddress = ""
        lastAddress = ""
        sessionStartTime = System.currentTimeMillis()
        lastCompressionTime = System.currentTimeMillis()
        framesSinceCompression = 0
        totalFrames = 0
    }

    // ── Private helpers ──

    /**
     * Extract short zone name from a full address.
     * "Calle 5, Asamajana, Herrera, Panama" → "Asamajana"
     */
    private fun extractZone(address: String): String {
        val parts = address.split(",").map { it.trim() }
        // Take the 2nd part (usually town/district) or 1st if short address
        return when {
            parts.size >= 3 -> parts[1]
            parts.size == 2 -> parts[0]
            else -> address.take(30)
        }
    }

    /**
     * Extract topic keywords from LLM result.
     * Focuses on named entities, not generic descriptions.
     */
    private fun extractTopics(result: String, mode: VisionMode): List<String> {
        val topics = mutableListOf<String>()
        val lower = result.lowercase()

        // Mode-specific entity extraction
        when (mode) {
            VisionMode.DASHCAM -> {
                // Road names, landmarks, hazards
                val roadPatterns = listOf("carretera", "autopista", "calle", "avenida", "puente", "peaje", "rotonda")
                roadPatterns.forEach { pattern ->
                    val idx = lower.indexOf(pattern)
                    if (idx >= 0) {
                        val snippet = result.substring(idx, minOf(idx + 30, result.length))
                            .split(Regex("[.,;!?]")).firstOrNull()?.trim()
                        if (snippet != null && snippet.length > 5) topics.add(snippet)
                    }
                }
                // Hazard entities
                if (lower.contains("peaton") || lower.contains("peat")) topics.add("peatones")
                if (lower.contains("vehiculo") || lower.contains("camion")) topics.add("trafico vehicular")
                if (lower.contains("semaforo") || lower.contains("senal")) topics.add("senales de transito")
                if (lower.contains("ganado") || lower.contains("animal")) topics.add("animales en via")
            }
            VisionMode.TOURIST -> {
                val poiPatterns = listOf("iglesia", "museo", "parque", "plaza", "monumento", "restaurante", "hotel", "mercado")
                poiPatterns.forEach { pattern ->
                    if (lower.contains(pattern)) {
                        val idx = lower.indexOf(pattern)
                        val snippet = result.substring(maxOf(0, idx - 10), minOf(idx + 25, result.length))
                            .split(Regex("[.,;!?]")).firstOrNull()?.trim()
                        if (snippet != null) topics.add(snippet)
                    }
                }
            }
            VisionMode.SHOPPING -> {
                // Product names, prices
                val priceRegex = Regex("\\$[\\d,.]+|B/\\.?[\\d,.]+")
                priceRegex.findAll(result).forEach { topics.add("precio ${it.value}") }
            }
            else -> {
                // Generic: extract capitalized proper nouns
                val properNouns = Regex("[A-Z][a-záéíóúñ]+(?:\\s[A-Z][a-záéíóúñ]+)*")
                properNouns.findAll(result).take(2).forEach { topics.add(it.value) }
            }
        }

        return topics.filter { it.length > 3 }.distinct().take(3)
    }

    /**
     * Extract factual insight (not visual description) from result.
     */
    private fun extractInsight(result: String): String? {
        // Insights usually contain factual markers
        val markers = listOf("fundad", "construi", "poblacion", "historia", "dato", "km de", "a solo", "cerca de")
        val lower = result.lowercase()
        val hasInsight = markers.any { lower.contains(it) }
        return if (hasInsight) result.take(100) else null
    }

    /**
     * Update narrative locally without LLM (fast, every 5 frames)
     */
    private fun updateNarrativeLocal() {
        val durationMin = getDurationMinutes()
        val modeStr = when (primaryMode) {
            VisionMode.DASHCAM -> "Conduciendo"
            VisionMode.TOURIST -> "Explorando"
            VisionMode.SHOPPING -> "Comprando"
            VisionMode.AGENT -> "Vigilando"
            VisionMode.MEETING -> "En reunion"
            else -> "Sesion"
        }

        currentNarrative = buildString {
            append("$modeStr ${durationMin}min")
            if (startAddress.isNotBlank() && lastAddress.isNotBlank() && startAddress != lastAddress) {
                val startZone = extractZone(startAddress)
                val endZone = extractZone(lastAddress)
                if (startZone != endZone) append(" desde $startZone hacia $endZone")
                else append(" en $startZone")
            } else if (lastAddress.isNotBlank()) {
                append(" en ${extractZone(lastAddress)}")
            }
            append(".")
        }
    }

    /**
     * Build full raw state for LLM compression
     */
    private fun buildRawState(): String = buildString {
        appendLine("Duracion: ${getDurationMinutes()} min")
        appendLine("Modo: ${primaryMode.name}")
        if (zonesVisited.isNotEmpty()) appendLine("Zonas: ${zonesVisited.joinToString(", ")}")
        if (topicsDiscussed.isNotEmpty()) appendLine("Temas: ${topicsDiscussed.joinToString(", ")}")
        if (insightsShared.isNotEmpty()) appendLine("Datos compartidos: ${insightsShared.joinToString("; ")}")
        if (userQuestions.isNotEmpty()) appendLine("Preguntas del usuario: ${userQuestions.joinToString("; ")}")
        if (sessionSummary.isNotBlank()) appendLine("Resumen previo: $sessionSummary")
    }

    /**
     * Fallback local summary when LLM compression fails
     */
    private fun buildLocalSummary(): String {
        val durationMin = getDurationMinutes()
        val zones = if (zonesVisited.size > 1) {
            "${zonesVisited.first()} a ${zonesVisited.last()}"
        } else zonesVisited.firstOrNull() ?: lastAddress.take(30)

        val topicStr = topicsDiscussed.takeLast(4).joinToString(", ")
        return "Sesion de ${durationMin}min en $zones. Temas: $topicStr."
    }
}
