package com.beemovil.vision

import org.junit.Assert.*
import org.junit.Test

/**
 * VisionIntegrationTest — Phase V10: La Madurez
 *
 * Unit tests for pure-Kotlin Vision components.
 * These tests do NOT require Android context.
 */
class VisionIntegrationTest {

    // ═══════════════════════════════════════
    // VisionAssessor Tests
    // ═══════════════════════════════════════

    @Test
    fun `assessor filters boring frames`() {
        val assessor = VisionAssessor()
        val result = assessor.assess(
            "No veo nada interesante en esta imagen",
            VisionMode.GENERAL,
            emptyList()
        )
        assertEquals(VisionAssessor.Action.SKIP, result.action)
        assertFalse(result.shouldSave)
    }

    @Test
    fun `assessor narrates urgent results`() {
        val assessor = VisionAssessor()
        val result = assessor.assess(
            "¡PELIGRO! Hay un accidente en la carretera con un vehículo volcado",
            VisionMode.DASHCAM,
            emptyList()
        )
        assertTrue(result.score >= 60)
        assertTrue(result.action == VisionAssessor.Action.NARRATE ||
                   result.action == VisionAssessor.Action.SEND_ALERT)
    }

    @Test
    fun `assessor detects prices in shopping mode`() {
        val assessor = VisionAssessor()
        val result = assessor.assess(
            "Veo una zapatilla Nike Air Max por $89.99, parece buena oferta",
            VisionMode.SHOPPING,
            emptyList()
        )
        assertTrue(result.score >= 40) // Should be at least MEDIUM
    }

    @Test
    fun `assessor filters repeated content`() {
        val assessor = VisionAssessor()
        val previous = listOf("Veo una calle vacía", "Veo una calle vacía", "Veo una calle vacía")
        val result = assessor.assess(
            "Veo una calle vacía",
            VisionMode.GENERAL,
            previous
        )
        assertTrue(result.score < 40) // Should be LOW or BORING due to repetition
    }

    // ═══════════════════════════════════════
    // TemporalPatternDetector Tests
    // ═══════════════════════════════════════

    @Test
    fun `temporal detects persistent entity`() {
        val detector = TemporalPatternDetector()
        val results = List(5) { "Veo un auto azul estacionado en la esquina" }
        val patterns = detector.detectPatterns(results, VisionMode.AGENT, 5)
        assertTrue(patterns.any {
            it.type == TemporalPatternDetector.PatternType.PERSISTENT_ENTITY
        })
    }

    @Test
    fun `temporal detects no patterns with varied input`() {
        val detector = TemporalPatternDetector()
        val results = listOf(
            "Veo un parque bonito",
            "Hay un restaurante italiano",
            "Una tienda de electrónica",
            "Un museo de arte moderno"
        )
        val patterns = detector.detectPatterns(results, VisionMode.TOURIST, 5)
        // With varied content, persistent entity should NOT be detected
        assertFalse(patterns.any {
            it.type == TemporalPatternDetector.PatternType.PERSISTENT_ENTITY
        })
    }

    @Test
    fun `temporal handles empty input gracefully`() {
        val detector = TemporalPatternDetector()
        val patterns = detector.detectPatterns(emptyList(), VisionMode.GENERAL, 5)
        assertTrue(patterns.isEmpty())
    }

    // ═══════════════════════════════════════
    // VisionCaptureLoop Tests
    // ═══════════════════════════════════════

    @Test
    fun `adaptive interval increases when battery is low`() {
        // Can't instantiate VisionCaptureLoop without Context, test the math directly
        val baseInterval = 5

        // Normal battery (100%): 5 * 1.0 * 1.0 * 1000 = 5000
        val normal = calculateAdaptiveInterval(baseInterval, 100, false)
        assertEquals(5000L, normal)

        // Low battery (25%): 5 * 2.0 * 1.0 * 1000 = 10000
        val low = calculateAdaptiveInterval(baseInterval, 25, false)
        assertEquals(10000L, low)

        // Critical + throttled: 5 * 3.0 * 2.0 * 1000 = 30000
        val critical = calculateAdaptiveInterval(baseInterval, 10, true)
        assertEquals(30000L, critical)
    }

    // Helper that mirrors VisionCaptureLoop.getAdaptiveInterval logic
    private fun calculateAdaptiveInterval(baseInterval: Int, batteryPct: Int, isThrottled: Boolean): Long {
        val multiplier = when {
            batteryPct < 15 -> 3.0
            batteryPct < 30 -> 2.0
            batteryPct < 50 -> 1.5
            else -> 1.0
        }
        val errorMultiplier = if (isThrottled) 2.0 else 1.0
        return (baseInterval * multiplier * errorMultiplier * 1000).toLong()
    }

    // ═══════════════════════════════════════
    // VisionConversation Tests
    // ═══════════════════════════════════════

    @Test
    fun `face hint injected into system prompt`() {
        val conv = VisionConversation()
        val prompt = conv.buildSystemPrompt(
            mode = VisionMode.GENERAL,
            faceHint = "Hay 2 personas sonriendo"
        )
        assertTrue(prompt.contains("DETECCIÓN FACIAL"))
        assertTrue(prompt.contains("2 personas sonriendo"))
    }

    @Test
    fun `empty face hint not injected`() {
        val conv = VisionConversation()
        val prompt = conv.buildSystemPrompt(
            mode = VisionMode.GENERAL,
            faceHint = ""
        )
        assertFalse(prompt.contains("DETECCIÓN FACIAL"))
    }

    @Test
    fun `memory context injected when present`() {
        val conv = VisionConversation()
        val prompt = conv.buildSystemPrompt(
            mode = VisionMode.TOURIST,
            memoryContext = "Has visitado este lugar 3 veces"
        )
        assertTrue(prompt.contains("MEMORIAS RELEVANTES"))
    }

    @Test
    fun `session duration tracking works`() {
        val conv = VisionConversation()
        conv.markSessionStart()
        Thread.sleep(100)
        val duration = conv.getSessionDurationMs()
        assertTrue(duration >= 100)
    }
}
