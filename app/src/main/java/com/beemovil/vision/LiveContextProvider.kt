package com.beemovil.vision

import android.util.Log
import com.beemovil.skills.WebSearchSkill
import org.json.JSONObject

/**
 * LiveContextProvider — Fetches real-time information about the user's location
 * and surroundings using web search. Used by Vision Pro modes (Tourist, GPS, Dashcam).
 *
 * Searches for: local news, traffic, interesting places, tips, events, weather alerts.
 * Results are injected into the AI prompt to provide up-to-date context.
 */
class LiveContextProvider {

    companion object {
        private const val TAG = "LiveContext"
        private const val CACHE_DURATION_MS = 120_000L // 2 minutes cache
    }

    private val webSearch = try { WebSearchSkill() } catch (_: Exception) { null }
    private var cachedContext: String = ""
    private var cachedLocation: String = ""
    private var lastFetchTime: Long = 0

    /**
     * Fetch live context for a GPS location. Returns formatted string for prompt injection.
     * Results are cached for 2 minutes to avoid excessive searches.
     */
    fun fetchContext(address: String, coords: String, mode: ContextMode): String {
        val locationKey = "${address}_${mode.name}"

        // Return cache if fresh enough
        if (cachedLocation == locationKey &&
            System.currentTimeMillis() - lastFetchTime < CACHE_DURATION_MS &&
            cachedContext.isNotBlank()) {
            return cachedContext
        }

        val searchArea = address.ifBlank { coords }
        if (searchArea.isBlank()) return ""

        val queries = when (mode) {
            ContextMode.TOURIST -> listOf(
                "$searchArea cosas interesantes para ver hacer turismo",
                "$searchArea noticias locales hoy eventos"
            )
            ContextMode.GPS -> listOf(
                "$searchArea trafico accidentes carretera hoy",
                "$searchArea noticias locales alertas"
            )
            ContextMode.DASHCAM -> listOf(
                "$searchArea condiciones carretera trafico alertas",
                "$searchArea accidentes cierres viales"
            )
        }

        val results = StringBuilder()
        results.appendLine("[INFO EN VIVO - ${mode.label}]")

        for (query in queries) {
            try {
                val params = JSONObject().put("query", query)
                val response = webSearch?.execute(params) ?: continue
                val searchResults = response.optJSONArray("results")

                if (searchResults != null && searchResults.length() > 0) {
                    for (i in 0 until minOf(3, searchResults.length())) {
                        val item = searchResults.optJSONObject(i) ?: continue
                        val title = item.optString("title", "")
                        val snippet = item.optString("snippet", "")
                        if (title.isNotBlank()) {
                            results.appendLine("- $title: ${snippet.take(120)}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search error for '$query': ${e.message}")
            }
        }

        val context = results.toString().trim()
        if (context.lines().size > 1) { // More than just the header
            cachedContext = context
            cachedLocation = locationKey
            lastFetchTime = System.currentTimeMillis()
            Log.i(TAG, "Live context fetched: ${context.length} chars for $searchArea ($mode)")
        }

        return if (context.lines().size > 1) context else ""
    }

    fun clearCache() {
        cachedContext = ""
        cachedLocation = ""
        lastFetchTime = 0
    }

    enum class ContextMode(val label: String) {
        TOURIST("Turismo"),
        GPS("Navegacion"),
        DASHCAM("Dashcam")
    }
}
