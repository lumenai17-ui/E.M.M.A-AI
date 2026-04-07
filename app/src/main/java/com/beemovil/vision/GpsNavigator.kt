package com.beemovil.vision

import android.content.Context
import android.location.Location
import android.util.Log
import java.util.Calendar

/**
 * GpsNavigator — Intelligent GPS navigation with POI suggestions.
 *
 * Features:
 * - Destination tracking (bearing, distance, ETA)
 * - Turn-by-turn style guidance text
 * - Contextual POI suggestions (food at lunch, attractions by interest)
 * - Speed-aware navigation (walking vs driving)
 * - Direction arrow calculation
 */
class GpsNavigator {

    companion object {
        private const val TAG = "GpsNavigator"
        private const val ARRIVAL_THRESHOLD_M = 30f  // Consider "arrived" at 30m
    }

    // Navigation state
    var isNavigating = false
        private set
    var destination: NavigationDestination? = null
        private set

    // Calculated values (updated each tick)
    var distanceMeters: Float = 0f
        private set
    var bearingToDest: Float = 0f
        private set
    var relativeBearing: Float = 0f    // relative to phone heading
        private set
    var estimatedMinutes: Float = 0f
        private set
    var hasArrived: Boolean = false
        private set

    // Route history for speed averaging
    private val speedHistory = mutableListOf<Float>()
    private var lastUpdateTime = 0L

    /**
     * Start navigating to a destination.
     */
    fun startNavigation(dest: NavigationDestination) {
        destination = dest
        isNavigating = true
        hasArrived = false
        speedHistory.clear()
        lastUpdateTime = System.currentTimeMillis()
        Log.i(TAG, "Navigation started to: ${dest.name} (${dest.latitude}, ${dest.longitude})")
    }

    /**
     * Stop navigation.
     */
    fun stopNavigation() {
        isNavigating = false
        destination = null
        hasArrived = false
        speedHistory.clear()
    }

    /**
     * Update navigation with current GPS position.
     * Returns navigation instruction text.
     */
    fun update(currentGps: GpsData): NavigationUpdate {
        val dest = destination ?: return NavigationUpdate.idle()
        if (!isNavigating) return NavigationUpdate.idle()

        // Calculate distance and bearing
        val results = FloatArray(3)
        Location.distanceBetween(
            currentGps.latitude, currentGps.longitude,
            dest.latitude, dest.longitude,
            results
        )
        distanceMeters = results[0]
        bearingToDest = if (results.size > 1) results[1] else 0f

        // Normalize bearing to 0-360
        if (bearingToDest < 0) bearingToDest += 360f

        // Relative bearing (for arrow display)
        relativeBearing = bearingToDest - currentGps.bearing
        if (relativeBearing > 180) relativeBearing -= 360
        if (relativeBearing < -180) relativeBearing += 360

        // Track speed for ETA
        if (currentGps.speed > 0.3f) {
            speedHistory.add(currentGps.speed)
            if (speedHistory.size > 20) speedHistory.removeAt(0)
        }

        // Calculate ETA
        val avgSpeed = if (speedHistory.isNotEmpty()) {
            speedHistory.average().toFloat()
        } else {
            1.4f  // Default walking speed ~5 km/h
        }
        estimatedMinutes = (distanceMeters / avgSpeed) / 60f

        // Check arrival
        if (distanceMeters < ARRIVAL_THRESHOLD_M) {
            hasArrived = true
            return NavigationUpdate(
                instruction = "🎉 ¡Llegaste a ${dest.name}!",
                distance = formatDistance(distanceMeters),
                eta = "0m",
                arrow = getArrowEmoji(relativeBearing),
                phase = NavPhase.ARRIVED,
                speedKmh = currentGps.speedKmh
            )
        }

        // Generate instruction based on relative bearing and distance
        val instruction = generateInstruction(relativeBearing, distanceMeters, dest.name, currentGps)

        return NavigationUpdate(
            instruction = instruction,
            distance = formatDistance(distanceMeters),
            eta = formatEta(estimatedMinutes),
            arrow = getArrowEmoji(relativeBearing),
            phase = when {
                distanceMeters < 100 -> NavPhase.ARRIVING
                distanceMeters < 500 -> NavPhase.CLOSE
                else -> NavPhase.EN_ROUTE
            },
            speedKmh = currentGps.speedKmh
        )
    }

    /**
     * Get contextual POI suggestions based on time, location, and context.
     */
    fun getContextualSuggestions(gpsData: GpsData): List<PoiSuggestion> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val suggestions = mutableListOf<PoiSuggestion>()

        // Time-based suggestions
        when (hour) {
            in 7..9 -> suggestions.add(PoiSuggestion("☕", "Desayuno", "Buscar cafeterías y panaderías cerca"))
            in 12..14 -> suggestions.add(PoiSuggestion("🍽️", "Almuerzo", "Restaurantes cerca para comer"))
            in 18..20 -> suggestions.add(PoiSuggestion("🍕", "Cena", "Opciones para cenar en la zona"))
            in 21..23 -> suggestions.add(PoiSuggestion("🍺", "Noche", "Bares y vida nocturna"))
        }

        // Speed-based suggestions
        if (gpsData.speedKmh > 30) {
            suggestions.add(PoiSuggestion("⛽", "Gasolina", "Gasolineras en el camino"))
        }

        // General always-available
        suggestions.add(PoiSuggestion("📍", "Interés", "Puntos de interés cercanos"))
        suggestions.add(PoiSuggestion("🏥", "Urgencia", "Hospitales y farmacias"))

        return suggestions.take(4) // Max 4 suggestions
    }

    // ── Helper functions ─────────────────────

    private fun generateInstruction(
        relBearing: Float,
        distance: Float,
        destName: String,
        gps: GpsData
    ): String {
        val cardinal = when {
            relBearing > -22.5 && relBearing <= 22.5 -> "adelante"
            relBearing > 22.5 && relBearing <= 67.5 -> "adelante a la derecha"
            relBearing > 67.5 && relBearing <= 112.5 -> "a la derecha"
            relBearing > 112.5 && relBearing <= 157.5 -> "atrás a la derecha"
            relBearing > 157.5 || relBearing <= -157.5 -> "atrás"
            relBearing > -157.5 && relBearing <= -112.5 -> "atrás a la izquierda"
            relBearing > -112.5 && relBearing <= -67.5 -> "a la izquierda"
            else -> "adelante a la izquierda"
        }

        return when {
            distance > 1000 -> "Continúa $cardinal hacia $destName (${formatDistance(distance)})"
            distance > 200 -> "Sigue $cardinal, $destName está a ${formatDistance(distance)}"
            distance > 50 -> "Casi llegas — $destName está $cardinal a ${formatDistance(distance)}"
            else -> "¡$destName está justo $cardinal!"
        }
    }

    private fun getArrowEmoji(relBearing: Float): String = when {
        relBearing > -22.5 && relBearing <= 22.5 -> "⬆️"
        relBearing > 22.5 && relBearing <= 67.5 -> "↗️"
        relBearing > 67.5 && relBearing <= 112.5 -> "➡️"
        relBearing > 112.5 && relBearing <= 157.5 -> "↘️"
        relBearing > 157.5 || relBearing <= -157.5 -> "⬇️"
        relBearing > -157.5 && relBearing <= -112.5 -> "↙️"
        relBearing > -112.5 && relBearing <= -67.5 -> "⬅️"
        else -> "↖️"
    }

    private fun formatDistance(meters: Float): String = when {
        meters >= 1000 -> "${"%.1f".format(meters / 1000)} km"
        else -> "${"%.0f".format(meters)} m"
    }

    private fun formatEta(minutes: Float): String = when {
        minutes >= 60 -> "${(minutes / 60).toInt()}h ${(minutes % 60).toInt()}m"
        minutes >= 1 -> "${minutes.toInt()} min"
        else -> "<1 min"
    }
}

// ── Data classes ─────────────────────────────

data class NavigationDestination(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val resolvedBy: String = "ai"  // "ai", "geocoder", "manual"
)

data class NavigationUpdate(
    val instruction: String,
    val distance: String,
    val eta: String,
    val arrow: String,
    val phase: NavPhase,
    val speedKmh: Float = 0f
) {
    companion object {
        fun idle() = NavigationUpdate("", "", "", "", NavPhase.IDLE)
    }
}

enum class NavPhase {
    IDLE, EN_ROUTE, CLOSE, ARRIVING, ARRIVED
}

data class PoiSuggestion(
    val emoji: String,
    val label: String,
    val searchQuery: String
)
