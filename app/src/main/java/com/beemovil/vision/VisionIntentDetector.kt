package com.beemovil.vision

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.beemovil.plugins.builtins.WebSearchPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * VisionIntentDetector — Phase V4: El GPS
 *
 * Simple keyword-based intent detection for voice commands during LiveVision.
 * No ML needed — pattern matching on Spanish + English keywords.
 */
class VisionIntentDetector(private val context: Context) {

    companion object {
        private const val TAG = "VisionIntentDetector"
    }

    private val geocoder: Geocoder? = if (Geocoder.isPresent()) Geocoder(context, Locale("es")) else null
    private val searchPlugin = WebSearchPlugin()

    enum class IntentType { NAVIGATION, STOP_NAV, POI_SEARCH, ACTION, QUESTION }

    data class DetectedIntent(
        val type: IntentType,
        val destination: String = "",
        val rawText: String = "",
        val actionHint: String = ""  // V7: for VisionBridge routing
    )

    /**
     * Detect the intent from user speech text.
     */
    fun detect(text: String): DetectedIntent {
        val lower = text.lowercase().trim()

        // Stop navigation
        val stopWords = listOf("para navegación", "cancela", "ya llegué", "detente", "stop", "cancel", "para la guía")
        if (stopWords.any { lower.contains(it) }) {
            return DetectedIntent(IntentType.STOP_NAV, rawText = text)
        }

        // Navigation request
        val navPatterns = listOf(
            "llévame a", "llevame a", "guíame a", "guiame a", "cómo llego a", "como llego a",
            "navega a", "dirígeme a", "dirigeme a", "quiero ir a", "vamos a",
            "take me to", "navigate to", "guide me to", "how do i get to"
        )
        for (pattern in navPatterns) {
            if (lower.contains(pattern)) {
                val dest = lower.substringAfter(pattern).trim()
                    .replace("por favor", "").replace("please", "").trim()
                if (dest.isNotBlank()) {
                    return DetectedIntent(IntentType.NAVIGATION, destination = dest, rawText = text)
                }
            }
        }

        // POI search
        val poiWords = listOf("qué hay cerca", "que hay cerca", "dónde puedo comer", "donde puedo comer",
            "recomienda", "restaurantes", "gasolinera", "farmacia", "hospital", "hotel",
            "what's nearby", "where can i eat")
        if (poiWords.any { lower.contains(it) }) {
            return DetectedIntent(IntentType.POI_SEARCH, destination = text, rawText = text)
        }

        // V7: Action intents (VisionBridge)
        val actionKeywords = listOf(
            "whatsapp", "manda", "envía", "enviar",
            "pdf", "reporte", "resumen", "notas de la reunión",
            "agenda", "cita", "evento",
            "linterna", "flashlight", "luz",
            "busca", "search", "investiga",
            "guarda la foto", "guarda esta foto", "guarda imagen",
            "alarma", "timer", "temporizador",
            "correo", "email", "mail"
        )
        if (actionKeywords.any { lower.contains(it) }) {
            return DetectedIntent(IntentType.ACTION, rawText = text, actionHint = "bridge")
        }

        // Default: question
        return DetectedIntent(IntentType.QUESTION, rawText = text)
    }

    /**
     * Resolve a destination name to coordinates via Geocoder + web search fallback.
     * Returns NavigationDestination or null if not found.
     */
    suspend fun resolveDestination(destinationName: String, nearLat: Double, nearLng: Double): NavigationDestination? =
        withContext(Dispatchers.IO) {
            // Try Geocoder first (faster, offline-capable)
            try {
                val results = geocoder?.getFromLocationName(destinationName, 1, 
                    nearLat - 0.5, nearLng - 0.5, nearLat + 0.5, nearLng + 0.5)
                if (!results.isNullOrEmpty()) {
                    val addr = results[0]
                    Log.i(TAG, "Geocoded '$destinationName' → ${addr.latitude}, ${addr.longitude}")
                    return@withContext NavigationDestination(
                        name = destinationName.replaceFirstChar { it.uppercase() },
                        latitude = addr.latitude,
                        longitude = addr.longitude,
                        resolvedBy = "geocoder"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder failed: ${e.message}")
            }

            // Fallback: web search for coordinates
            try {
                val query = "$destinationName ubicación coordenadas cerca de ${nearLat.toInt()}"
                val webResult = searchPlugin.execute(mapOf("query" to query))
                // Try to extract coords from web result (best effort)
                val coordRegex = Regex("(-?\\d{1,3}\\.\\d{3,}),\\s*(-?\\d{1,3}\\.\\d{3,})")
                val match = coordRegex.find(webResult)
                if (match != null) {
                    val lat = match.groupValues[1].toDoubleOrNull()
                    val lng = match.groupValues[2].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        Log.i(TAG, "Web resolved '$destinationName' → $lat, $lng")
                        return@withContext NavigationDestination(
                            name = destinationName.replaceFirstChar { it.uppercase() },
                            latitude = lat, longitude = lng,
                            resolvedBy = "web"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Web resolve failed: ${e.message}")
            }

            Log.w(TAG, "Could not resolve: $destinationName")
            null
        }
}
