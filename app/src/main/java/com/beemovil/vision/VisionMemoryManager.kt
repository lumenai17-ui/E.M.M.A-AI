package com.beemovil.vision

import android.content.Context
import android.util.Log

/**
 * VisionMemoryManager — Phase V7: La Inteligencia
 *
 * RAG coordinator: uses OfflineContextCache (SQLite) + BeeMemoryDB (SharedPrefs)
 * to create a persistent memory of what E.M.M.A. has seen.
 *
 * SAVE: after each frame (if assessor says shouldSave)
 * QUERY: before each frame (inject into system prompt)
 */
class VisionMemoryManager(private val context: Context) {

    companion object {
        private const val TAG = "VisionMemory"
        private const val MAX_MEMORY_CONTEXT = 300 // chars injected into prompt
    }

    private val offlineCache = OfflineContextCache.getInstance(context)
    private val memoryDB = com.beemovil.memory.BeeMemoryDB(context)
    val placeProfileManager = PlaceProfileManager(context) // V8: expose for lifecycle

    // Track what we've already injected to avoid repeats
    private var lastInjectedMemory = ""

    /**
     * Query memories relevant to current location and mode.
     * Returns a context string for prompt injection.
     */
    fun queryMemories(lat: Double, lng: Double, mode: VisionMode): String {
        if (lat == 0.0 && lng == 0.0) return ""

        val parts = mutableListOf<String>()

        // V8: Place profile (visited before? how many times? notes?)
        val placeCtx = placeProfileManager.getPromptContext(lat, lng)
        if (placeCtx.isNotBlank()) {
            parts.add(placeCtx.take(200))
        }

        // V8: Past session summaries for this location
        val sessionSummary = offlineCache.get(lat, lng, type = "session_summary")
        if (sessionSummary.isNotBlank()) {
            parts.add("Sesion anterior: ${sessionSummary.take(150)}")
        }

        // 1. Offline cache: any mode's data for this location (cross-mode!)
        val cachedContext = offlineCache.get(lat, lng, type = "vision_memory")
        if (cachedContext.isNotBlank() && cachedContext != lastInjectedMemory) {
            parts.add(cachedContext.take(200))
        }

        // 2. Location-specific web data from ANY past mode
        val crossMode = offlineCache.get(lat, lng) // No mode filter = cross-mode
        if (crossMode.isNotBlank() && crossMode != cachedContext) {
            parts.add(crossMode.take(150))
        }

        // 3. BeeMemoryDB keyword search (user's saved memories)
        val allMemories = memoryDB.getAllMemories()
        // Simple keyword match from location address
        val addressKey = offlineCache.get(lat, lng, type = "geocode").take(50).lowercase()
        if (addressKey.isNotBlank()) {
            val relevant = allMemories.filter { memory ->
                val memLower = memory.lowercase()
                addressKey.split(" ").any { word ->
                    word.length > 4 && memLower.contains(word)
                }
            }
            if (relevant.isNotEmpty()) {
                parts.add(relevant.takeLast(2).joinToString("; "))
            }
        }

        val result = parts.joinToString("\n").take(MAX_MEMORY_CONTEXT)
        lastInjectedMemory = result
        if (result.isNotBlank()) {
            Log.d(TAG, "Memory query: ${result.length} chars for (${lat.toInt()}, ${lng.toInt()})")
        }
        return result
    }

    /**
     * Save a vision frame result to memory.
     * Called when VisionAssessor.shouldSave = true.
     */
    fun saveVisionMemory(
        lat: Double, lng: Double,
        result: String,
        mode: VisionMode,
        address: String = ""
    ) {
        if (result.isBlank() || (lat == 0.0 && lng == 0.0)) return

        // Save to offline cache as vision_memory type
        offlineCache.save(
            lat = lat, lng = lng,
            type = "vision_memory",
            content = result.take(300),
            source = "vision_v7",
            mode = mode.name.lowercase(),
            address = address,
            ttlHours = 24 * 30 // 30 days
        )

        // Also save notable observations to BeeMemoryDB for cross-app access
        val notable = isNotable(result, mode)
        if (notable) {
            val memoryFragment = buildString {
                if (address.isNotBlank()) append("$address: ")
                append(result.take(150))
                append(" [${mode.name}]")
            }
            memoryDB.saveMemory(memoryFragment)
            Log.i(TAG, "Saved notable memory: ${memoryFragment.take(60)}...")
        }
    }

    /**
     * Save geocode data for later location recall.
     */
    fun saveGeocode(lat: Double, lng: Double, address: String) {
        if (address.isBlank()) return
        offlineCache.save(
            lat = lat, lng = lng,
            type = "geocode",
            content = address,
            source = "geocoder",
            ttlHours = 24 * 7 // 7 days
        )
    }

    /**
     * Determine if a result is "notable" enough to persist in BeeMemoryDB.
     */
    private fun isNotable(result: String, mode: VisionMode): Boolean {
        val lower = result.lowercase()
        return when (mode) {
            VisionMode.SHOPPING -> lower.contains("$") || lower.contains("precio")
            VisionMode.TOURIST -> lower.contains("museo") || lower.contains("monumento") ||
                    lower.contains("restaurante") || lower.contains("hotel")
            VisionMode.AGENT -> lower.contains("alerta") || lower.contains("sospechoso")
            VisionMode.DASHCAM -> lower.contains("accidente") || lower.contains("peligro")
            VisionMode.MEETING -> lower.contains("acción") || lower.contains("pendiente") ||
                    lower.contains("tarea") || lower.contains("deadline")
            else -> false // General/Pocket/Translator don't auto-save
        }
    }

    /** Get stats for UI display */
    fun getStats(): String {
        val cacheCount = offlineCache.entryCount()
        val memCount = memoryDB.getMemoryCount()
        return "$cacheCount cached | $memCount memories"
    }

    /** V9: Get stats as map for dashboard */
    fun getStatsMap(): Map<String, Int> {
        return mapOf(
            "cached" to offlineCache.entryCount(),
            "memories" to memoryDB.getMemoryCount()
        )
    }

    /** V9: Save a vision note to BeeMemoryDB (for chat integration) */
    fun saveVisionNote(note: String) {
        if (note.isBlank()) return
        memoryDB.saveMemory(note.take(300))
        Log.i(TAG, "Vision note saved: ${note.take(50)}...")
    }
}
