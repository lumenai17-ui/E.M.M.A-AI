package com.beemovil.vision

import android.util.Log
import com.beemovil.plugins.builtins.WebSearchPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * StructuredWebSearch — R5 Phase 2
 *
 * Replaces generic DuckDuckGo queries with categorized, mode-specific searches.
 * Rate-limited: max 2/min DASHCAM, 3/min TOURIST, 2/min others.
 * Tracks used queries via SessionState to avoid repeats.
 */
class StructuredWebSearch {

    companion object {
        private const val TAG = "StructuredWebSearch"
    }

    private val searchPlugin = WebSearchPlugin()

    // Rate limiter
    private var searchCount = 0
    private var lastMinuteReset = System.currentTimeMillis()

    /**
     * Rate limit check per mode.
     */
    private fun canSearch(mode: VisionMode): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastMinuteReset > 60_000L) {
            searchCount = 0
            lastMinuteReset = now
        }
        val limit = when (mode) {
            VisionMode.TOURIST -> 3
            VisionMode.DASHCAM -> 2
            VisionMode.SHOPPING -> 2
            else -> 2
        }
        return searchCount < limit
    }

    /**
     * Search for contextual data about the current zone.
     * Returns a LocationIntel with categorized results.
     *
     * @param address Current GPS address
     * @param mode Current vision mode
     * @param sessionState Used to check/mark queries as used
     * @param speedKmh Current speed (affects query strategy)
     */
    suspend fun searchForZone(
        address: String,
        mode: VisionMode,
        sessionState: SessionState,
        speedKmh: Float = 0f
    ): LocationIntel = withContext(Dispatchers.IO) {
        if (address.isBlank() || !canSearch(mode)) return@withContext LocationIntel.EMPTY

        val zone = extractSearchableZone(address)
        if (zone.isBlank()) return@withContext LocationIntel.EMPTY

        // Build categorized queries for this mode
        val queries = buildCategorizedQueries(zone, mode, speedKmh)
            .filter { !sessionState.wasQueryUsed(it.query) }
            .take(2) // Max 2 queries per cycle

        if (queries.isEmpty()) return@withContext LocationIntel.EMPTY

        val intel = LocationIntel()

        for (cq in queries) {
            try {
                val result = searchPlugin.execute(mapOf("query" to cq.query))
                if (result.isNotBlank() && !result.startsWith("Error") && !result.startsWith("No se encontraron")) {
                    searchCount++
                    sessionState.markQueryUsed(cq.query)

                    // Assign result to the correct category
                    val snippet = result.take(200)
                    when (cq.category) {
                        SearchCategory.ROAD -> intel.roadInfo = snippet
                        SearchCategory.HISTORY -> intel.historicalFact = snippet
                        SearchCategory.POI -> intel.nearbyPOI = snippet
                        SearchCategory.SERVICES -> intel.nearbyServices = snippet
                        SearchCategory.WARNINGS -> intel.warnings = snippet
                        SearchCategory.CULTURE -> intel.culturalNote = snippet
                        SearchCategory.PRICES -> intel.priceInfo = snippet
                        SearchCategory.SECURITY -> intel.securityInfo = snippet
                    }
                    Log.d(TAG, "${cq.category}: '${cq.query}' -> ${snippet.length} chars")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search failed for '${cq.query}': ${e.message}")
            }
        }

        return@withContext intel
    }

    /**
     * Build categorized queries based on mode and context.
     */
    private fun buildCategorizedQueries(
        zone: String,
        mode: VisionMode,
        speedKmh: Float
    ): List<CategorizedQuery> {
        val queries = mutableListOf<CategorizedQuery>()

        when (mode) {
            VisionMode.DASHCAM -> {
                // Priority 1: Road info (always useful while driving)
                if (speedKmh > 30) {
                    queries.add(CategorizedQuery(
                        "$zone carretera estado vial condiciones",
                        SearchCategory.ROAD
                    ))
                }
                // Priority 2: Services nearby
                queries.add(CategorizedQuery(
                    "$zone gasolinera restaurante servicios cerca",
                    SearchCategory.SERVICES
                ))
                // Priority 3: History/culture of the area
                queries.add(CategorizedQuery(
                    "$zone datos curiosos historia informacion",
                    SearchCategory.HISTORY
                ))
            }

            VisionMode.TOURIST -> {
                // Priority 1: Historical/cultural data
                queries.add(CategorizedQuery(
                    "$zone historia datos curiosos cultura tradiciones",
                    SearchCategory.HISTORY
                ))
                // Priority 2: POIs and recommendations
                queries.add(CategorizedQuery(
                    "$zone que visitar puntos interes recomendaciones",
                    SearchCategory.POI
                ))
                // Priority 3: Food
                queries.add(CategorizedQuery(
                    "$zone comida tipica restaurantes donde comer",
                    SearchCategory.CULTURE
                ))
            }

            VisionMode.SHOPPING -> {
                // Priority 1: Prices and deals
                queries.add(CategorizedQuery(
                    "$zone tiendas ofertas precios",
                    SearchCategory.PRICES
                ))
                // Priority 2: Reviews
                queries.add(CategorizedQuery(
                    "$zone mejores tiendas resenas opiniones",
                    SearchCategory.POI
                ))
            }

            VisionMode.AGENT -> {
                // Priority 1: Security news
                queries.add(CategorizedQuery(
                    "$zone noticias seguridad alertas recientes",
                    SearchCategory.SECURITY
                ))
                // Priority 2: Area info
                queries.add(CategorizedQuery(
                    "$zone informacion zona barrio datos",
                    SearchCategory.HISTORY
                ))
            }

            VisionMode.POCKET -> {
                // Priority 1: General area info
                queries.add(CategorizedQuery(
                    "$zone informacion datos curiosos que hay",
                    SearchCategory.HISTORY
                ))
                queries.add(CategorizedQuery(
                    "$zone lugares interes cerca actividades",
                    SearchCategory.POI
                ))
            }

            else -> {
                // General/Meeting/Translator — minimal search
                queries.add(CategorizedQuery(
                    "$zone informacion relevante datos",
                    SearchCategory.HISTORY
                ))
            }
        }

        return queries
    }

    /**
     * Extract a searchable zone name from a full address.
     * "Calle 5, Asamajana, Herrera, Panama" -> "Asamajana Herrera Panama"
     */
    private fun extractSearchableZone(address: String): String {
        val parts = address.split(",").map { it.trim() }.filter { it.length > 2 }
        return when {
            parts.size >= 3 -> "${parts[1]} ${parts[2]}" // Town + Province
            parts.size == 2 -> parts.joinToString(" ")
            else -> address.take(40)
        }
    }

    fun getSearchCount(): Int = searchCount
}

/**
 * Categorized search query with semantic label.
 */
private data class CategorizedQuery(
    val query: String,
    val category: SearchCategory
)

private enum class SearchCategory {
    ROAD, HISTORY, POI, SERVICES, WARNINGS, CULTURE, PRICES, SECURITY
}

/**
 * Structured location intelligence — categorized web search results.
 */
data class LocationIntel(
    var roadInfo: String? = null,
    var historicalFact: String? = null,
    var nearbyPOI: String? = null,
    var nearbyServices: String? = null,
    var warnings: String? = null,
    var culturalNote: String? = null,
    var priceInfo: String? = null,
    var securityInfo: String? = null
) {
    companion object {
        val EMPTY = LocationIntel()
    }

    fun isEmpty(): Boolean = roadInfo == null && historicalFact == null && nearbyPOI == null
            && nearbyServices == null && warnings == null && culturalNote == null
            && priceInfo == null && securityInfo == null

    /**
     * Format for prompt injection. Only includes non-null categories.
     * Respects a token budget (character limit).
     */
    fun toPromptText(maxChars: Int = 400): String {
        val parts = mutableListOf<String>()

        // Order by priority
        warnings?.let { parts.add("Alertas: $it") }
        roadInfo?.let { parts.add("Via: $it") }
        historicalFact?.let { parts.add("Dato: $it") }
        nearbyPOI?.let { parts.add("Cerca: $it") }
        nearbyServices?.let { parts.add("Servicios: $it") }
        culturalNote?.let { parts.add("Cultura: $it") }
        priceInfo?.let { parts.add("Precios: $it") }
        securityInfo?.let { parts.add("Seguridad: $it") }

        if (parts.isEmpty()) return ""

        // Build within budget
        val result = StringBuilder()
        for (part in parts) {
            if (result.length + part.length + 1 > maxChars) break
            if (result.isNotEmpty()) result.append("\n")
            result.append(part)
        }
        return result.toString()
    }
}
