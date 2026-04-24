package com.beemovil.vision

import android.util.Log

/**
 * TemporalPatternDetector — Phase V7: La Inteligencia
 *
 * Multi-frame reasoning: detects entities that persist across frames.
 * "That black car has been behind you for 5 minutes" (Agent)
 * "The price went up $4 since you entered" (Shopping)
 */
class TemporalPatternDetector {

    companion object {
        private const val TAG = "TemporalPattern"
        private const val MIN_OCCURRENCES_RATIO = 0.4f // entity in >40% of frames
    }

    data class TemporalPattern(
        val entity: String,
        val occurrences: Int,
        val totalFrames: Int,
        val durationSeconds: Int,
        val alert: String
    )

    /**
     * Detect persistent entities across recent frames.
     */
    fun detectPatterns(
        results: List<String>,
        mode: VisionMode,
        intervalSeconds: Int
    ): List<TemporalPattern> {
        if (results.size < 3) return emptyList()

        val patterns = mutableListOf<TemporalPattern>()
        val entityCounts = mutableMapOf<String, Int>()

        // Extract and count entities
        results.forEach { result ->
            extractEntities(result, mode).forEach { entity ->
                entityCounts[entity] = (entityCounts[entity] ?: 0) + 1
            }
        }

        // Filter: entity must appear in >40% of frames
        val threshold = (results.size * MIN_OCCURRENCES_RATIO).toInt().coerceAtLeast(2)
        entityCounts.filter { it.value >= threshold }.forEach { (entity, count) ->
            val durationSec = count * intervalSeconds
            val alert = buildAlert(entity, count, durationSec, mode)
            if (alert.isNotBlank()) {
                patterns.add(TemporalPattern(
                    entity = entity,
                    occurrences = count,
                    totalFrames = results.size,
                    durationSeconds = durationSec,
                    alert = alert
                ))
                Log.d(TAG, "Pattern: '$entity' × $count (${durationSec}s)")
            }
        }

        // Price tracking (Shopping mode)
        if (mode == VisionMode.SHOPPING) {
            detectPriceChange(results)?.let { patterns.add(it) }
        }

        return patterns
    }

    private fun extractEntities(text: String, mode: VisionMode): List<String> {
        val entities = mutableListOf<String>()
        val lower = text.lowercase()

        // Vehicles (Dashcam, Agent)
        val vehiclePattern = Regex("(vehículo|carro|auto|camión|camioneta|moto|bicicleta)\\s+(\\w+)")
        vehiclePattern.findAll(lower).forEach { entities.add(it.value) }

        // People (Agent)
        val personPattern = Regex("(persona|hombre|mujer|individuo)\\s+(de\\s+)?(\\w+)")
        personPattern.findAll(lower).forEach { entities.add(it.value) }

        // Signs/signals (Dashcam)
        if (mode == VisionMode.DASHCAM) {
            if (lower.contains("semáforo rojo")) entities.add("semáforo rojo")
            if (lower.contains("semáforo verde")) entities.add("semáforo verde")
            if (lower.contains("tráfico") || lower.contains("congestionamiento")) entities.add("tráfico")
        }

        // Brands/products (Shopping)
        if (mode == VisionMode.SHOPPING) {
            val brandPattern = Regex("[A-Z][a-záéíóú]+(?:\\s[A-Z][a-záéíóú]+)*")
            brandPattern.findAll(text).filter { it.value.length > 3 }.forEach {
                entities.add("producto:${it.value}")
            }
        }

        return entities.distinct()
    }

    private fun buildAlert(entity: String, count: Int, durationSec: Int, mode: VisionMode): String {
        return when (mode) {
            VisionMode.AGENT -> {
                if (entity.contains("persona") || entity.contains("vehículo") || entity.contains("carro")) {
                    "⚠️ \"$entity\" ha estado presente por ${durationSec}s ($count frames)"
                } else ""
            }
            VisionMode.DASHCAM -> {
                if (entity.contains("semáforo rojo") && durationSec > 20) {
                    "🚦 Llevas ${durationSec}s en semáforo rojo"
                } else if (entity.contains("tráfico") && count > 3) {
                    "🚗 Tráfico detectado por ${durationSec}s"
                } else ""
            }
            VisionMode.SHOPPING -> {
                if (entity.startsWith("producto:")) {
                    val product = entity.removePrefix("producto:")
                    "🛒 Has visto \"$product\" ${count} veces"
                } else ""
            }
            else -> {
                if (count > 4) {
                    "📌 \"$entity\" presente por ${durationSec}s"
                } else ""
            }
        }
    }

    private fun detectPriceChange(results: List<String>): TemporalPattern? {
        val pricePattern = Regex("\\$(\\d+\\.?\\d*)")
        val prices = results.mapNotNull { result ->
            pricePattern.find(result)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        }
        if (prices.size < 2) return null

        val first = prices.first()
        val last = prices.last()
        val diff = last - first

        return if (kotlin.math.abs(diff) > 0.5) {
            val direction = if (diff > 0) "subió" else "bajó"
            TemporalPattern(
                entity = "precio",
                occurrences = prices.size,
                totalFrames = results.size,
                durationSeconds = 0,
                alert = "💰 El precio $direction de \$${String.format("%.2f", first)} a \$${String.format("%.2f", last)}"
            )
        } else null
    }
}
