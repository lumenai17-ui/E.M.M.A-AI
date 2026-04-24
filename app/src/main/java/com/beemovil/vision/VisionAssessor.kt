package com.beemovil.vision

import android.util.Log

/**
 * VisionAssessor — Phase V7: La Inteligencia
 *
 * Urgency scoring gate: decides IF a frame result is worth narrating.
 * Pure Kotlin — no LLM calls, zero latency, zero API cost.
 *
 * Scoring factors:
 * 1. Novelty (similarity to previous results)
 * 2. Urgent keywords (danger, accident, alert)
 * 3. Mode-specific relevance
 * 4. Silence pressure (haven't spoken in a while)
 * 5. Location change (GPS speed)
 */
class VisionAssessor {

    companion object {
        private const val TAG = "VisionAssessor"
    }

    data class Assessment(
        val score: Int,
        val category: Category,
        val shouldNarrate: Boolean,
        val shouldSave: Boolean,
        val action: Action
    )

    enum class Category { BORING, LOW, MEDIUM, URGENT }
    enum class Action { NONE, LOG_EVENT, SAVE_PHOTO, SEND_ALERT }

    private var lastNarrationTime = 0L

    fun markNarrated() {
        lastNarrationTime = System.currentTimeMillis()
    }

    /**
     * Assess a frame result and return a decision.
     */
    fun assess(
        result: String,
        previousResults: List<String>,
        mode: VisionMode,
        speedKmh: Float = 0f
    ): Assessment {
        var score = 50

        // ── Factor 1: Novelty ──
        if (previousResults.isNotEmpty()) {
            val maxSim = previousResults.takeLast(5).maxOf { similarity(result, it) }
            score += when {
                maxSim > 0.75f -> -35  // Nearly identical → boring
                maxSim > 0.55f -> -20  // Similar
                maxSim > 0.35f -> 0    // Somewhat different
                maxSim < 0.15f -> +20  // Very new
                else -> +10
            }
        } else {
            score += 15 // First frame → always interesting
        }

        // ── Factor 2: Urgent keywords ──
        val lower = result.lowercase()
        val urgentWords = listOf(
            "peligro", "accidente", "emergencia", "fuego", "alerta",
            "cuidado", "persona caída", "robo", "arma", "incendio",
            "danger", "accident", "emergency", "fire", "warning",
            "⚠️", "🚨", "❗"
        )
        val urgentHits = urgentWords.count { lower.contains(it) }
        if (urgentHits > 0) score += 30 + (urgentHits * 10)

        // ── Factor 3: Mode-specific relevance ──
        score += modeRelevance(lower, mode)

        // ── Factor 4: Silence pressure ──
        val silenceMs = System.currentTimeMillis() - lastNarrationTime
        if (silenceMs > 30_000) score += 10
        if (silenceMs > 60_000) score += 20
        if (silenceMs > 120_000) score += 30

        // ── Factor 5: Movement (location change) ──
        if (speedKmh > 10f) score += 5
        if (speedKmh > 40f) score += 10

        // ── Factor 6: User question context ──
        // If the result mentions answering a question, always narrate
        if (lower.contains("respuesta") || lower.contains("preguntaste") ||
            lower.contains("me pides") || lower.contains("en cuanto a")) {
            score += 25
        }

        score = score.coerceIn(0, 100)

        val category = when {
            score >= 80 -> Category.URGENT
            score >= 50 -> Category.MEDIUM
            score >= 25 -> Category.LOW
            else -> Category.BORING
        }

        val shouldNarrate = when (category) {
            Category.URGENT -> true
            Category.MEDIUM -> true
            Category.LOW -> silenceMs > 25_000 // Only narrate low if silent a while
            Category.BORING -> silenceMs > 60_000 // Only if desperate for content
        }

        val action = when {
            urgentHits > 0 && mode == VisionMode.AGENT -> Action.SEND_ALERT
            score >= 70 -> Action.LOG_EVENT
            score >= 55 && mode == VisionMode.TOURIST -> Action.SAVE_PHOTO
            else -> Action.NONE
        }

        Log.d(TAG, "Assessment: score=$score cat=$category narrate=$shouldNarrate " +
              "silence=${silenceMs/1000}s mode=$mode")

        return Assessment(
            score = score,
            category = category,
            shouldNarrate = shouldNarrate,
            shouldSave = score >= 55,
            action = action
        )
    }

    private fun modeRelevance(text: String, mode: VisionMode): Int = when (mode) {
        VisionMode.DASHCAM -> {
            var bonus = 0
            if (text.contains("semáforo") || text.contains("señal")) bonus += 8
            if (text.contains("peatón") || text.contains("bicicleta") || text.contains("moto")) bonus += 12
            if (text.contains("policía") || text.contains("ambulancia")) bonus += 20
            bonus
        }
        VisionMode.SHOPPING -> {
            var bonus = 0
            if (text.contains("precio") || text.contains("$") || text.contains("oferta")) bonus += 12
            if (text.contains("descuento") || text.contains("promo")) bonus += 15
            bonus
        }
        VisionMode.AGENT -> {
            var bonus = -10 // Default: stay silent unless something notable
            if (text.contains("persona") || text.contains("movimiento")) bonus += 15
            if (text.contains("sospechoso") || text.contains("alerta")) bonus += 25
            bonus
        }
        VisionMode.TOURIST -> {
            var bonus = 0
            if (text.contains("monumento") || text.contains("histórico") || text.contains("museo")) bonus += 10
            if (text.contains("restaurante") || text.contains("hotel")) bonus += 8
            bonus
        }
        VisionMode.MEETING -> {
            var bonus = 5 // Meeting should narrate more (OCR is valuable)
            if (text.contains("punto") || text.contains("agenda") || text.contains("acción")) bonus += 10
            bonus
        }
        else -> 0
    }

    /**
     * Simple word-overlap similarity (0.0 to 1.0).
     * Fast — no external deps.
     */
    private fun similarity(a: String, b: String): Float {
        val wordsA = a.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        val wordsB = b.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0f
        val intersection = wordsA.intersect(wordsB).size
        return intersection.toFloat() / maxOf(wordsA.size, wordsB.size)
    }
}
