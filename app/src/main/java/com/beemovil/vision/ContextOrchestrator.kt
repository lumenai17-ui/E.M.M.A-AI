package com.beemovil.vision

import android.content.Context
import android.util.Log

/**
 * ContextOrchestrator — R5 Phase 2
 *
 * Central coordinator for all vision context layers.
 * Replaces scattered context logic in LiveVisionScreen with a single
 * buildContextBlock() call that respects a token budget.
 *
 * 6 Layers:
 * 1. Sensor Fusion (GPS speed, heading, altitude, time, battery)
 * 2. Location Intelligence (structured web search)
 * 3. Route Intelligence (zone changes, speed transitions)
 * 4. Temporal Patterns (already exists, just integrated)
 * 5. Session State (topics, narrative, compression)
 * 6. Memory (place profiles, past sessions)
 */
class ContextOrchestrator(private val context: Context) {

    companion object {
        private const val TAG = "ContextOrchestrator"
        private const val MAX_CONTEXT_CHARS = 1200 // ~800 tokens budget for all context
        private const val ZONE_CHANGE_THRESHOLD_METERS = 2000.0 // ~2km = new zone
    }

    // Sub-systems
    val structuredSearch = StructuredWebSearch()
    private val offlineCache = OfflineContextCache.getInstance(context)

    // Zone tracking
    private var lastZoneAddress = ""
    private var lastZoneLat = 0.0
    private var lastZoneLng = 0.0
    private var lastZoneSearchTime = 0L
    private var cachedIntel = LocationIntel.EMPTY
    private val ZONE_SEARCH_COOLDOWN = 45_000L // Don't search same zone more than every 45s

    // Speed transition tracking
    private var lastSpeedBracket = SpeedBracket.STOPPED
    private var speedTransition: String? = null

    /**
     * Build the complete context block for prompt injection.
     * Orchestrates all layers and respects the token budget.
     */
    suspend fun buildContextBlock(
        gpsData: GpsData,
        mode: VisionMode,
        sessionState: SessionState,
        memoryManager: VisionMemoryManager,
        temporalDetector: TemporalPatternDetector,
        previousResults: List<String>,
        intervalSeconds: Int,
        weatherInfo: String = ""
    ): ContextBlock {
        val block = ContextBlock()

        // ── Layer 1: Sensor Fusion (always, ~80 chars) ──
        block.sensorContext = buildSensorContext(gpsData, weatherInfo)

        // ── Layer 2: Route Intelligence (zone changes, ~60 chars) ──
        val zoneChanged = detectZoneChange(gpsData)
        detectSpeedTransition(gpsData)
        if (speedTransition != null) {
            block.routeContext = speedTransition ?: ""
            speedTransition = null
        }

        // ── Layer 3: Location Intelligence — structured web search ──
        if (gpsData.address.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (zoneChanged || (now - lastZoneSearchTime > ZONE_SEARCH_COOLDOWN && cachedIntel.isEmpty())) {
                // New zone or cooldown passed — do a structured search
                try {
                    val intel = structuredSearch.searchForZone(
                        address = gpsData.address,
                        mode = mode,
                        sessionState = sessionState,
                        speedKmh = gpsData.speedKmh
                    )
                    if (!intel.isEmpty()) {
                        cachedIntel = intel
                        lastZoneSearchTime = now

                        // Cache to offline storage
                        val intelText = intel.toPromptText(400)
                        if (intelText.isNotBlank()) {
                            offlineCache.save(
                                lat = gpsData.latitude, lng = gpsData.longitude,
                                type = "structured_intel", content = intelText,
                                source = "structured_search", mode = mode.name.lowercase(),
                                address = gpsData.address, ttlHours = 12
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Structured search failed: ${e.message}")
                }
            }

            // Use cached intel (from search or offline cache)
            val intelText = cachedIntel.toPromptText(350)
            if (intelText.isNotBlank()) {
                block.locationContext = intelText
            } else {
                // Fallback to offline cache
                val cached = offlineCache.get(gpsData.latitude, gpsData.longitude, mode.name.lowercase(), "structured_intel")
                if (cached.isNotBlank()) {
                    block.locationContext = cached.take(350)
                }
            }
        }

        // ── Layer 4: Temporal Patterns ──
        val patterns = temporalDetector.detectPatterns(previousResults, mode, intervalSeconds)
        if (patterns.isNotEmpty()) {
            block.temporalContext = patterns.joinToString("\n") { it.alert }
        }

        // ── Layer 5: Session State (always, ~120 chars) ──
        block.sessionContext = sessionState.buildPromptContext()

        // ── Layer 6: Memory (place profiles, past sessions) ──
        if (gpsData.latitude != 0.0) {
            val memCtx = memoryManager.queryMemories(gpsData.latitude, gpsData.longitude, mode)
            if (memCtx.isNotBlank()) {
                block.memoryContext = memCtx
            }
        }

        // ── Budget enforcement ──
        block.enforeBudget(MAX_CONTEXT_CHARS)

        Log.d(TAG, "Context: ${block.totalChars()} chars | zone=${if (zoneChanged) "NEW" else "same"} | searches=${structuredSearch.getSearchCount()}")
        return block
    }

    /**
     * Build sensor context from GPS data — always available, no internet needed.
     */
    private fun buildSensorContext(gpsData: GpsData, weatherInfo: String): String {
        if (gpsData.latitude == 0.0) return ""

        return buildString {
            // Movement state
            val movement = when {
                gpsData.speedKmh > 80 -> "Autopista ${gpsData.speedKmh.toInt()}km/h ${gpsData.bearingCardinal}"
                gpsData.speedKmh > 40 -> "Carretera ${gpsData.speedKmh.toInt()}km/h ${gpsData.bearingCardinal}"
                gpsData.speedKmh > 8 -> "Zona urbana ${gpsData.speedKmh.toInt()}km/h"
                gpsData.speedKmh > 1 -> "Caminando ${gpsData.bearingCardinal}"
                else -> "Detenido"
            }
            append(movement)

            // Time of day context
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val timeCtx = when {
                hour < 6 -> ", madrugada"
                hour < 12 -> ", manana"
                hour < 14 -> ", mediodia"
                hour < 18 -> ", tarde"
                hour < 21 -> ", atardecer"
                else -> ", noche"
            }
            append(timeCtx)

            // Weather
            if (weatherInfo.isNotBlank()) append(", $weatherInfo")
        }
    }

    /**
     * Detect if we've entered a new zone (moved >2km from last zone center).
     */
    private fun detectZoneChange(gpsData: GpsData): Boolean {
        if (gpsData.latitude == 0.0) return false
        if (lastZoneLat == 0.0) {
            // First zone
            lastZoneLat = gpsData.latitude
            lastZoneLng = gpsData.longitude
            lastZoneAddress = gpsData.address
            return true
        }

        val distance = haversineDistance(lastZoneLat, lastZoneLng, gpsData.latitude, gpsData.longitude)
        if (distance > ZONE_CHANGE_THRESHOLD_METERS) {
            Log.i(TAG, "Zone change: ${lastZoneAddress.take(20)} -> ${gpsData.address.take(20)} (${distance.toInt()}m)")
            lastZoneLat = gpsData.latitude
            lastZoneLng = gpsData.longitude
            lastZoneAddress = gpsData.address
            cachedIntel = LocationIntel.EMPTY // Invalidate cache
            return true
        }
        return false
    }

    /**
     * Detect speed transitions (highway -> urban, moving -> stopped, etc.)
     */
    private fun detectSpeedTransition(gpsData: GpsData) {
        val newBracket = when {
            gpsData.speedKmh > 80 -> SpeedBracket.HIGHWAY
            gpsData.speedKmh > 30 -> SpeedBracket.ROAD
            gpsData.speedKmh > 5 -> SpeedBracket.URBAN
            else -> SpeedBracket.STOPPED
        }

        if (newBracket != lastSpeedBracket) {
            speedTransition = when {
                lastSpeedBracket == SpeedBracket.HIGHWAY && newBracket == SpeedBracket.URBAN ->
                    "Transicion: autopista a zona urbana, reducir velocidad"
                lastSpeedBracket == SpeedBracket.HIGHWAY && newBracket == SpeedBracket.STOPPED ->
                    "Detenido despues de autopista"
                lastSpeedBracket == SpeedBracket.STOPPED && newBracket == SpeedBracket.ROAD ->
                    "En movimiento, saliendo de zona"
                lastSpeedBracket == SpeedBracket.URBAN && newBracket == SpeedBracket.HIGHWAY ->
                    "Transicion: zona urbana a autopista"
                else -> null
            }
            lastSpeedBracket = newBracket
        }
    }

    /**
     * Haversine distance between two GPS points in meters.
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    /**
     * Reset for new session.
     */
    fun reset() {
        lastZoneAddress = ""
        lastZoneLat = 0.0
        lastZoneLng = 0.0
        lastZoneSearchTime = 0L
        cachedIntel = LocationIntel.EMPTY
        lastSpeedBracket = SpeedBracket.STOPPED
        speedTransition = null
    }

    private enum class SpeedBracket { STOPPED, URBAN, ROAD, HIGHWAY }
}

/**
 * ContextBlock — all context layers assembled, ready for prompt injection.
 * Each field can be independently truncated to respect the budget.
 */
data class ContextBlock(
    var sensorContext: String = "",
    var locationContext: String = "",
    var routeContext: String = "",
    var temporalContext: String = "",
    var sessionContext: String = "",
    var memoryContext: String = ""
) {
    fun totalChars(): Int = sensorContext.length + locationContext.length +
            routeContext.length + temporalContext.length +
            sessionContext.length + memoryContext.length

    /**
     * Enforce budget by trimming lower-priority layers.
     * Priority: sensor > session > location > temporal > memory > route
     */
    fun enforeBudget(maxChars: Int) {
        var remaining = maxChars

        // 1. Sensor (always keep, small)
        sensorContext = sensorContext.take(remaining)
        remaining -= sensorContext.length

        // 2. Session (always keep)
        sessionContext = sessionContext.take(remaining.coerceAtLeast(0))
        remaining -= sessionContext.length

        // 3. Location (main value-add)
        locationContext = locationContext.take(remaining.coerceAtLeast(0))
        remaining -= locationContext.length

        // 4. Temporal
        temporalContext = temporalContext.take(remaining.coerceAtLeast(0))
        remaining -= temporalContext.length

        // 5. Memory
        memoryContext = memoryContext.take(remaining.coerceAtLeast(0))
        remaining -= memoryContext.length

        // 6. Route (least priority)
        routeContext = routeContext.take(remaining.coerceAtLeast(0))
    }
}
