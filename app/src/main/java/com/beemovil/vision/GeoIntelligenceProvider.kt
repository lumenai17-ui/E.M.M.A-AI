package com.beemovil.vision

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * GeoIntelligenceProvider — R5 Phase 3
 *
 * Free API integrations for rich location intelligence:
 * 1. Overpass API (OpenStreetMap) — POIs within radius (gas, food, hotels)
 * 2. Wikipedia API — Historical/cultural data about localities
 * 3. Nominatim (OSM) — Detailed reverse geocoding (road name, type)
 *
 * All APIs are free, no key required. Rate-limited to be respectful.
 * Results are cached via OfflineContextCache for offline fallback.
 */
class GeoIntelligenceProvider(private val offlineCache: OfflineContextCache? = null) {

    companion object {
        private const val TAG = "GeoIntel"
        private const val CONNECT_TIMEOUT = 5000
        private const val READ_TIMEOUT = 8000
        private const val USER_AGENT = "EMMA-Vision/1.0"
    }

    // Rate limiter: max 1 call per API per 60s
    private var lastOverpassCall = 0L
    private var lastWikipediaCall = 0L
    private var lastNominatimCall = 0L
    private val API_COOLDOWN = 60_000L

    // ═══════════════════════════════════════════════════════
    // 1. OVERPASS API — POIs within radius
    // ═══════════════════════════════════════════════════════

    /**
     * Find nearby points of interest using OpenStreetMap Overpass API.
     * Returns a formatted string of POIs grouped by category.
     *
     * @param lat GPS latitude
     * @param lng GPS longitude
     * @param radiusMeters Search radius (default 3km)
     * @param mode Vision mode (affects which POI types to search)
     */
    suspend fun fetchNearbyPOIs(
        lat: Double, lng: Double,
        radiusMeters: Int = 3000,
        mode: VisionMode = VisionMode.GENERAL
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastOverpassCall < API_COOLDOWN) return@withContext ""

        // Check offline cache first
        val cacheKey = "overpass_${mode.name.lowercase()}"
        val cached = offlineCache?.get(lat, lng, type = cacheKey)
        if (!cached.isNullOrBlank()) return@withContext cached

        val amenities = when (mode) {
            VisionMode.DASHCAM -> "fuel|parking|car_repair|car_wash"
            VisionMode.TOURIST -> "restaurant|cafe|museum|attraction|viewpoint|hotel"
            VisionMode.SHOPPING -> "supermarket|marketplace|mall|bank|atm"
            VisionMode.AGENT -> "police|hospital|fire_station|pharmacy"
            else -> "restaurant|fuel|hospital|atm|pharmacy"
        }

        val query = """
            [out:json][timeout:8];
            (
              node["amenity"~"$amenities"](around:$radiusMeters,$lat,$lng);
              node["tourism"~"hotel|museum|attraction|viewpoint"](around:$radiusMeters,$lat,$lng);
            );
            out body 20;
        """.trimIndent()

        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://overpass-api.de/api/interpreter?data=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            lastOverpassCall = now
            conn.disconnect()

            val result = parseOverpassResponse(response, lat, lng)
            if (result.isNotBlank()) {
                offlineCache?.save(lat, lng, cacheKey, result, "overpass",
                    mode.name.lowercase(), ttlHours = 6)
                Log.d(TAG, "Overpass: ${result.length} chars for (${"%.3f".format(lat)}, ${"%.3f".format(lng)})")
            }
            return@withContext result
        } catch (e: Exception) {
            Log.w(TAG, "Overpass failed: ${e.message}")
            return@withContext ""
        }
    }

    private fun parseOverpassResponse(json: String, userLat: Double, userLng: Double): String {
        return try {
            val obj = JSONObject(json)
            val elements = obj.optJSONArray("elements") ?: return ""

            val pois = mutableListOf<String>()
            for (i in 0 until minOf(elements.length(), 15)) { // V11-P4: expanded from 8
                val el = elements.getJSONObject(i)
                val tags = el.optJSONObject("tags") ?: continue
                val name = tags.optString("name", "")
                if (name.isBlank()) continue
                val amenity = tags.optString("amenity", tags.optString("tourism", ""))
                val poiLat = el.optDouble("lat", 0.0)
                val poiLng = el.optDouble("lon", 0.0)

                val distKm = if (poiLat != 0.0) {
                    haversineKm(userLat, userLng, poiLat, poiLng)
                } else null

                val distStr = if (distKm != null && distKm < 10) {
                    " (${"%.1f".format(distKm)}km)"
                } else ""

                val typeStr = translateAmenity(amenity)
                pois.add("$typeStr: $name$distStr")
            }

            if (pois.isEmpty()) "" else pois.joinToString("; ")
        } catch (e: Exception) {
            Log.w(TAG, "Parse overpass error: ${e.message}")
            ""
        }
    }

    private fun translateAmenity(amenity: String): String = when (amenity) {
        "fuel" -> "Gasolinera"
        "restaurant" -> "Restaurante"
        "cafe" -> "Cafe"
        "museum" -> "Museo"
        "attraction" -> "Atraccion"
        "viewpoint" -> "Mirador"
        "hotel" -> "Hotel"
        "supermarket" -> "Supermercado"
        "marketplace" -> "Mercado"
        "mall" -> "Centro comercial"
        "bank" -> "Banco"
        "atm" -> "Cajero"
        "police" -> "Policia"
        "hospital" -> "Hospital"
        "fire_station" -> "Bomberos"
        "pharmacy" -> "Farmacia"
        "parking" -> "Estacionamiento"
        "car_repair" -> "Taller mecanico"
        "car_wash" -> "Lavado"
        else -> amenity.replaceFirstChar { it.uppercase() }
    }

    // ═══════════════════════════════════════════════════════
    // 2. WIKIPEDIA API — Historical/cultural data
    // ═══════════════════════════════════════════════════════

    /**
     * Search Wikipedia for data about a locality.
     * Returns a summary extract (1-2 paragraphs).
     *
     * @param placeName Name of the place/locality to search
     */
    suspend fun fetchWikipediaData(placeName: String): String = withContext(Dispatchers.IO) {
        if (placeName.isBlank()) return@withContext ""
        val now = System.currentTimeMillis()
        if (now - lastWikipediaCall < API_COOLDOWN) return@withContext ""

        // Check offline cache
        val cached = offlineCache?.get(0.0, 0.0, type = "wikipedia_$placeName")
        if (!cached.isNullOrBlank()) return@withContext cached

        try {
            val encoded = URLEncoder.encode(placeName, "UTF-8")
            // Use Spanish Wikipedia for better local results
            val url = URL("https://es.wikipedia.org/api/rest_v1/page/summary/$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode != 200) {
                conn.disconnect()
                // Try search API as fallback
                return@withContext searchWikipedia(placeName)
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            lastWikipediaCall = now
            conn.disconnect()

            val obj = JSONObject(response)
            val extract = obj.optString("extract", "").take(300)
            val title = obj.optString("title", placeName)

            if (extract.isBlank() || extract.length < 20) return@withContext ""

            val result = "$title: $extract"
            offlineCache?.save(0.0, 0.0, "wikipedia_$placeName", result, "wikipedia",
                ttlHours = 24 * 7) // 7 days
            Log.d(TAG, "Wikipedia: '$title' -> ${extract.length} chars")
            return@withContext result
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia failed for '$placeName': ${e.message}")
            return@withContext ""
        }
    }

    /**
     * Fallback: search Wikipedia when direct page lookup fails.
     */
    private suspend fun searchWikipedia(query: String): String {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=1&utf8=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            lastWikipediaCall = System.currentTimeMillis()
            conn.disconnect()

            val obj = JSONObject(response)
            val search = obj.optJSONObject("query")?.optJSONArray("search")
            if (search == null || search.length() == 0) return ""

            val first = search.getJSONObject(0)
            val title = first.optString("title", "")
            val snippet = first.optString("snippet", "")
                .replace(Regex("<[^>]*>"), "") // Strip HTML tags
                .take(200)

            if (snippet.isBlank()) return ""

            val result = "$title: $snippet"
            offlineCache?.save(0.0, 0.0, "wikipedia_$query", result, "wikipedia",
                ttlHours = 24 * 7)
            result
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia search failed: ${e.message}")
            ""
        }
    }

    // ═══════════════════════════════════════════════════════
    // 3. NOMINATIM — Detailed reverse geocoding
    // ═══════════════════════════════════════════════════════

    /**
     * Get detailed road/area information from OSM Nominatim.
     * Returns road name, type, suburb, county-level info.
     */
    suspend fun fetchRoadInfo(lat: Double, lng: Double): RoadInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastNominatimCall < API_COOLDOWN) return@withContext null

        // Check cache
        val cached = offlineCache?.get(lat, lng, type = "nominatim")
        if (!cached.isNullOrBlank()) {
            return@withContext parseRoadInfoFromCache(cached)
        }

        try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lng&zoom=16&addressdetails=1&extratags=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Language", "es")
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            lastNominatimCall = now
            conn.disconnect()

            val obj = JSONObject(response)
            val address = obj.optJSONObject("address")
            val extratags = obj.optJSONObject("extratags")

            val roadName = address?.optString("road", "")
                ?: obj.optString("name", "")
            val roadType = obj.optString("type", "")
            val suburb = address?.optString("suburb",
                address.optString("neighbourhood", "")) ?: ""
            val city = address?.optString("city",
                address.optString("town",
                    address.optString("village", ""))) ?: ""
            val county = address?.optString("county", "") ?: ""
            val state = address?.optString("state", "") ?: ""

            // Extra tags: speed limit, surface, lanes
            val speedLimit = extratags?.optString("maxspeed", "") ?: ""
            val surface = extratags?.optString("surface", "") ?: ""
            val lanes = extratags?.optString("lanes", "") ?: ""

            val info = RoadInfo(
                roadName = roadName ?: "",
                roadType = translateRoadType(roadType),
                suburb = suburb,
                city = city,
                county = county,
                state = state,
                speedLimit = speedLimit,
                surface = translateSurface(surface),
                lanes = lanes
            )

            // Cache result
            val cacheStr = info.toStorageString()
            offlineCache?.save(lat, lng, "nominatim", cacheStr, "nominatim", ttlHours = 4)
            Log.d(TAG, "Nominatim: ${info.roadName}, ${info.city}")

            return@withContext info
        } catch (e: Exception) {
            Log.w(TAG, "Nominatim failed: ${e.message}")
            return@withContext null
        }
    }

    private fun translateRoadType(type: String): String = when (type) {
        "motorway" -> "Autopista"
        "trunk" -> "Via principal"
        "primary" -> "Carretera primaria"
        "secondary" -> "Carretera secundaria"
        "tertiary" -> "Via terciaria"
        "residential" -> "Zona residencial"
        "service" -> "Via de servicio"
        "unclassified" -> "Via local"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    private fun translateSurface(surface: String): String = when (surface) {
        "asphalt" -> "asfalto"
        "concrete" -> "concreto"
        "gravel" -> "grava"
        "dirt" -> "tierra"
        "paved" -> "pavimentado"
        "unpaved" -> "sin pavimentar"
        else -> surface
    }

    private fun parseRoadInfoFromCache(cached: String): RoadInfo? {
        return try {
            val parts = cached.split("|")
            if (parts.size < 6) return null
            RoadInfo(
                roadName = parts[0],
                roadType = parts[1],
                suburb = parts[2],
                city = parts[3],
                county = parts[4],
                state = parts[5],
                speedLimit = parts.getOrElse(6) { "" },
                surface = parts.getOrElse(7) { "" },
                lanes = parts.getOrElse(8) { "" }
            )
        } catch (_: Exception) { null }
    }

    // ═══════════════════════════════════════════════════════
    // UTILS
    // ═══════════════════════════════════════════════════════

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}

/**
 * Detailed road/area information from OSM Nominatim.
 */
data class RoadInfo(
    val roadName: String = "",
    val roadType: String = "",
    val suburb: String = "",
    val city: String = "",
    val county: String = "",
    val state: String = "",
    val speedLimit: String = "",
    val surface: String = "",
    val lanes: String = ""
) {
    /**
     * Format for prompt injection.
     */
    fun toPromptText(): String = buildString {
        if (roadName.isNotBlank()) append("Via: $roadName")
        if (roadType.isNotBlank()) append(" ($roadType)")
        if (speedLimit.isNotBlank()) append(", limite $speedLimit km/h")
        if (surface.isNotBlank()) append(", $surface")
        if (lanes.isNotBlank()) append(", $lanes carriles")
        if (city.isNotBlank()) append(". Zona: $city")
        if (county.isNotBlank() && county != city) append(", $county")
    }

    fun toStorageString(): String = listOf(
        roadName, roadType, suburb, city, county, state, speedLimit, surface, lanes
    ).joinToString("|")
}
