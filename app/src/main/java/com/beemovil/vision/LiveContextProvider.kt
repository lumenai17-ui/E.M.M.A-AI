package com.beemovil.vision

import android.content.Context
import android.util.Log
import com.beemovil.plugins.builtins.WebSearchPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LiveContextProvider — Phase V4: El GPS
 *
 * Fetches contextual web info for the user's current location.
 * Query adapts to VisionMode (tourist, dashcam, agent, general).
 * Cached for 2 minutes to avoid hammering DuckDuckGo every frame.
 */
class LiveContextProvider(private val context: Context) {

    companion object {
        private const val TAG = "LiveContextProvider"
        private const val CACHE_TTL_MS = 120_000L // 2 min cache
        private const val TWO_PASS_TTL_MS = 60_000L // 1 min for two-pass
    }

    private var cachedResult: String = ""
    private var cachedAddress: String = ""
    private var cacheTimestamp: Long = 0L

    // V5: Two-pass cache
    private var twoPassResult: String = ""
    private var twoPassQuery: String = ""
    private var twoPassTimestamp: Long = 0L

    private val searchPlugin = WebSearchPlugin()
    private var offlineCache: OfflineContextCache? = null

    /** Set offline cache for fallback (call from LiveVisionScreen or Service) */
    fun setOfflineCache(cache: OfflineContextCache) {
        offlineCache = cache
    }

    /**
     * Fetch context for the given location. Returns empty if no address.
     * Uses cache if the address hasn't changed significantly and TTL is valid.
     */
    suspend fun fetchContext(
        address: String,
        mode: VisionMode,
        coords: String = ""
    ): String = withContext(Dispatchers.IO) {
        val location = address.ifBlank { coords }
        if (location.isBlank()) return@withContext ""

        // Cache check: same area + within TTL
        val now = System.currentTimeMillis()
        if (cachedResult.isNotBlank()
            && now - cacheTimestamp < CACHE_TTL_MS
            && isSameArea(cachedAddress, address)
        ) {
            return@withContext cachedResult
        }

        // Build mode-specific query
        val query = buildQuery(location, mode)

        try {
            val result = searchPlugin.execute(mapOf("query" to query))
            if (result.isNotBlank() && !result.startsWith("Error")) {
                cachedResult = result
                cachedAddress = address
                cacheTimestamp = now
                Log.d(TAG, "Context fetched for: $location (${result.length} chars)")
                // V6: Save to offline cache
                offlineCache?.save(
                    lat = coords.split(",").getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0,
                    lng = coords.split(",").getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0,
                    type = "web", content = result, source = "duckduckgo",
                    mode = mode.name.lowercase(), address = address
                )
            }
            return@withContext cachedResult
        } catch (e: Exception) {
            Log.w(TAG, "Context fetch failed: ${e.message}")
            // V6: Offline fallback
            if (cachedResult.isBlank()) {
                val lat = coords.split(",").getOrNull(0)?.trim()?.toDoubleOrNull() ?: 0.0
                val lng = coords.split(",").getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0
                val offline = offlineCache?.get(lat, lng, mode.name.lowercase()) ?: ""
                if (offline.isNotBlank()) {
                    Log.d(TAG, "Using offline cache for $location")
                    return@withContext offline
                }
            }
            return@withContext cachedResult
        }
    }

    private fun buildQuery(location: String, mode: VisionMode): String = when (mode) {
        VisionMode.TOURIST -> "$location puntos de interés turismo historia datos"
        VisionMode.DASHCAM -> "$location condiciones carretera tráfico alertas viales"
        VisionMode.AGENT -> "$location noticias seguridad alertas recientes"
        VisionMode.SHOPPING -> "$location tiendas precios ofertas"
        VisionMode.MEETING -> "$location" // Meeting rarely needs web context
        VisionMode.POCKET -> "$location información entorno datos"
        VisionMode.TRANSLATOR -> "$location idioma cultura frases útiles"
        VisionMode.GENERAL -> "$location información relevante puntos interés"
    }

    /** Check if two addresses are roughly the same area (first significant word match) */
    private fun isSameArea(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val wordsA = a.lowercase().split(",", " ").filter { it.length > 3 }.take(2).toSet()
        val wordsB = b.lowercase().split(",", " ").filter { it.length > 3 }.take(2).toSet()
        return wordsA.intersect(wordsB).isNotEmpty()
    }

    fun invalidateCache() {
        cachedResult = ""
        cacheTimestamp = 0L
    }

    // ── V5: Two-Pass Enrichment ──

    /**
     * Takes the LLM result, extracts keywords, and searches for enrichment.
     * Runs async between frames. Result available via getTwoPassContext().
     */
    suspend fun extractAndSearch(llmResult: String, mode: VisionMode): String = withContext(Dispatchers.IO) {
        if (llmResult.length < 15) return@withContext twoPassResult

        val query = when (mode) {
            VisionMode.SHOPPING -> {
                val product = llmResult.take(60).replace(Regex("[^\\w\\s\\$\\.]"), "").trim()
                if (product.isBlank()) return@withContext twoPassResult
                "$product precio review comparar"
            }
            VisionMode.TOURIST -> {
                val place = llmResult.take(50).replace(Regex("[^\\w\\s]"), "").trim()
                if (place.isBlank()) return@withContext twoPassResult
                "$place horario entrada reseñas"
            }
            VisionMode.DASHCAM -> {
                val road = llmResult.take(40).replace(Regex("[^\\w\\s]"), "").trim()
                if (road.isBlank()) return@withContext twoPassResult
                "$road tráfico estado vial"
            }
            else -> return@withContext twoPassResult
        }

        // Cache check: don't repeat same query
        val now = System.currentTimeMillis()
        if (query == twoPassQuery && now - twoPassTimestamp < TWO_PASS_TTL_MS) {
            return@withContext twoPassResult
        }

        try {
            val result = searchPlugin.execute(mapOf("query" to query))
            if (result.isNotBlank() && !result.startsWith("Error")) {
                twoPassResult = result
                twoPassQuery = query
                twoPassTimestamp = now
                Log.d(TAG, "Two-pass: '$query' → ${result.length} chars")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Two-pass failed: ${e.message}")
        }
        return@withContext twoPassResult
    }

    /** Get cached two-pass result (non-blocking) */
    fun getTwoPassContext(): String = twoPassResult
}
