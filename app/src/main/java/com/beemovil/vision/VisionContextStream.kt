package com.beemovil.vision

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 1 (V11): The Omniscient Observer
 * 
 * VisionContextStream acts as the "Memory Tube" (RAM) connecting the asynchronous
 * sub-agents. 
 * - The "Vision Logger" (Camera) silently pushes text descriptions here.
 * - The "Environment Researcher" (GPS/Web) silently pushes context here.
 * - The "Voice Engine" (The Boss) reads this stream instantly to converse without lag.
 */
object VisionContextStream {

    private const val MAX_LOG_ENTRIES = 30 // Keep the last ~2.5 minutes if logging every 5s

    data class VisionLogEntry(
        val timestamp: Long,
        val formattedTime: String,
        val mode: VisionMode,
        val description: String,
        val gpsContext: String,
        val speedKmh: Float
    ) {
        override fun toString(): String {
            return "[$formattedTime] [Mode: ${mode.name} | Speed: ${speedKmh.toInt()}km/h | Loc: $gpsContext] VISION: $description"
        }
    }

    private val _visionLogs = MutableStateFlow<List<VisionLogEntry>>(emptyList())
    val visionLogs: StateFlow<List<VisionLogEntry>> = _visionLogs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Called by the passive VisionCaptureLoop (Motor A) every X seconds.
     */
    fun addVisionEntry(mode: VisionMode, description: String, gpsContext: String, speedKmh: Float) {
        if (description.isBlank()) return

        val now = System.currentTimeMillis()
        val entry = VisionLogEntry(
            timestamp = now,
            formattedTime = timeFormat.format(Date(now)),
            mode = mode,
            description = description,
            gpsContext = gpsContext.take(30), // Keep it concise
            speedKmh = speedKmh
        )

        val currentList = _visionLogs.value.toMutableList()
        currentList.add(entry)
        
        if (currentList.size > MAX_LOG_ENTRIES) {
            currentList.removeAt(0)
        }
        
        _visionLogs.value = currentList
    }

    /**
     * Extracts the raw text block for the Voice Engine (Motor B) to inject into its prompt.
     */
    fun buildContextForVoiceEngine(): String {
        val currentLogs = _visionLogs.value
        if (currentLogs.isEmpty()) return "El flujo visual está vacío."

        return buildString {
            appendLine("--- INICIO BITÁCORA VISUAL (Últimos ${currentLogs.size} eventos) ---")
            currentLogs.forEach { entry ->
                appendLine(entry.toString())
            }
            appendLine("--- FIN BITÁCORA VISUAL ---")
        }
    }

    /**
     * Get the most recent entry for proactivity evaluation.
     */
    fun getLatestEntry(): VisionLogEntry? {
        return _visionLogs.value.lastOrNull()
    }

    fun clear() {
        _visionLogs.value = emptyList()
    }
}
