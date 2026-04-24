package com.beemovil.vision

import android.content.Context
import android.util.Log
import com.beemovil.llm.ChatMessage
import com.beemovil.llm.LlmFactory
import com.beemovil.memory.BeeMemoryDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SessionSummaryGenerator — Phase V8: El Perfil
 *
 * When a vision session ends, generates a natural language summary
 * via LLM and persists it to memory for future reference.
 */
class SessionSummaryGenerator(private val context: Context) {

    companion object {
        private const val TAG = "SessionSummary"
    }

    data class SessionSummary(
        val text: String,
        val timestamp: Long,
        val durationMinutes: Int,
        val frameCount: Int,
        val mode: VisionMode,
        val mainAddress: String
    )

    /**
     * Generate a LLM-powered session summary.
     * Falls back to a simple stats-based summary if LLM fails.
     */
    suspend fun generate(
        sessionLog: String,
        frameCount: Int,
        durationMs: Long,
        mode: VisionMode,
        mainAddress: String,
        providerType: String,
        apiKey: String,
        modelId: String
    ): SessionSummary = withContext(Dispatchers.IO) {
        val durationMin = (durationMs / 60_000).toInt().coerceAtLeast(1)

        val summary = try {
            val prompt = buildString {
                appendLine("Resume esta sesión de visión en 2-3 oraciones naturales y concisas.")
                appendLine("Incluye: dónde estuvo el usuario, qué vio de interesante, duración.")
                appendLine("NO incluyas datos técnicos, coordenadas ni JSON.")
                appendLine()
                appendLine("DATOS:")
                appendLine("- Duración: $durationMin min | Frames: $frameCount | Modo: ${mode.name}")
                appendLine("- Ubicación: ${mainAddress.ifBlank { "Sin ubicación" }}")
                appendLine()
                appendLine("LOG DE SESIÓN (últimas entradas):")
                appendLine(sessionLog.takeLast(800))
            }

            val messages = listOf(
                ChatMessage("system", prompt),
                ChatMessage("user", "Genera el resumen.")
            )
            val provider = LlmFactory.createProvider(providerType, apiKey, modelId)
            val response = provider.complete(messages, emptyList())
            response.text?.take(300) ?: fallbackSummary(durationMin, frameCount, mode, mainAddress)
        } catch (e: Exception) {
            Log.w(TAG, "LLM summary failed: ${e.message}")
            fallbackSummary(durationMin, frameCount, mode, mainAddress)
        }

        Log.i(TAG, "Summary: ${summary.take(80)}...")
        SessionSummary(summary, System.currentTimeMillis(), durationMin, frameCount, mode, mainAddress)
    }

    /**
     * Persist the summary to memory systems.
     */
    fun persist(summary: SessionSummary, lat: Double, lng: Double) {
        try {
            // 1. BeeMemoryDB (cross-app, shown to main E.M.M.A. chat)
            val memoryDB = BeeMemoryDB(context)
            memoryDB.saveMemory("Vision (${summary.mode.name}): ${summary.text}")

            // 2. OfflineContextCache (GPS-indexed, 90 days)
            val cache = OfflineContextCache.getInstance(context)
            cache.save(lat, lng, "session_summary", summary.text, "vision_v8",
                summary.mode.name.lowercase(), summary.mainAddress, ttlHours = 24 * 90)

            Log.i(TAG, "Summary persisted (memory + cache)")
        } catch (e: Exception) {
            Log.w(TAG, "Persist failed: ${e.message}")
        }
    }

    private fun fallbackSummary(
        durationMin: Int, frameCount: Int, mode: VisionMode, address: String
    ): String {
        val loc = if (address.isNotBlank()) " en $address" else ""
        return "Sesión de ${durationMin} min$loc. " +
               "Se analizaron $frameCount frames en modo ${mode.name.lowercase()}."
    }
}
