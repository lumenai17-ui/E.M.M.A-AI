package com.beemovil.vision

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * PlaceProfileManager — Phase V8: El Perfil
 *
 * Learns about places the user visits repeatedly.
 * Stored in OfflineContextCache as JSON, queried by GPS proximity.
 */
class PlaceProfileManager(private val context: Context) {

    companion object {
        private const val TAG = "PlaceProfile"
        private const val TYPE = "place_profile"
        private const val TTL_HOURS = 24 * 365 // 1 year
    }

    private val cache = OfflineContextCache.getInstance(context)

    data class PlaceProfile(
        val latitude: Double,
        val longitude: Double,
        val address: String,
        val label: String = "",                // R4-1: User-assigned name (Casa, Trabajo, Gym)
        val visitCount: Int,
        val firstVisit: Long,
        val lastVisit: Long,
        val frequentMode: String?,
        val visitDays: Set<Int>,       // 1=Sun..7=Sat (Calendar.DAY_OF_WEEK)
        val observations: List<String>, // Last 5
        val avgDurationMinutes: Int
    ) {
        /** Generate natural language context for prompt injection */
        fun toPromptContext(): String = buildString {
            val displayName = if (label.isNotBlank()) "$label ($address)" else address
            append("LUGAR CONOCIDO: $displayName\n")
            append("Visitas: $visitCount")
            val daysAgo = ((System.currentTimeMillis() - lastVisit) / 86_400_000).toInt()
            if (daysAgo > 0) append(" (ultima hace ${daysAgo}d)")
            appendLine()
            if (visitDays.isNotEmpty()) {
                val dayNames = listOf("Dom","Lun","Mar","Mie","Jue","Vie","Sab")
                val names = visitDays.sorted().map { dayNames.getOrElse(it - 1) { "?" } }
                appendLine("Dias frecuentes: ${names.joinToString(", ")}")
            }
            if (observations.isNotEmpty()) {
                appendLine("Notas previas: ${observations.takeLast(3).joinToString("; ")}")
            }
            if (frequentMode != null) {
                appendLine("Modo usual: $frequentMode")
            }
        }
    }

    /**
     * Get existing profile or create a new one for this location.
     */
    fun getOrCreate(lat: Double, lng: Double, address: String): PlaceProfile {
        val existing = cache.get(lat, lng, type = TYPE)
        if (existing.isNotBlank()) {
            try {
                return deserialize(existing)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to deserialize profile: ${e.message}")
            }
        }
        return PlaceProfile(
            latitude = lat, longitude = lng, address = address,
            visitCount = 0, firstVisit = System.currentTimeMillis(),
            lastVisit = System.currentTimeMillis(),
            frequentMode = null, visitDays = emptySet(),
            observations = emptyList(), avgDurationMinutes = 0
        )
    }

    /**
     * Record a visit: increment count, add day of week, update mode.
     */
    fun recordVisit(profile: PlaceProfile, mode: VisionMode): PlaceProfile {
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val updated = profile.copy(
            visitCount = profile.visitCount + 1,
            lastVisit = System.currentTimeMillis(),
            frequentMode = mode.name.lowercase(),
            visitDays = profile.visitDays + dayOfWeek
        )
        save(updated)
        Log.d(TAG, "Visit #${updated.visitCount} at ${updated.address}")
        return updated
    }

    /**
     * Add an observation note to the place profile.
     */
    fun addObservation(profile: PlaceProfile, note: String): PlaceProfile {
        val updated = profile.copy(
            observations = (profile.observations + note.take(80)).takeLast(5)
        )
        save(updated)
        return updated
    }

    /**
     * Update session end stats (duration).
     */
    fun updateSessionEnd(profile: PlaceProfile, durationMinutes: Int): PlaceProfile {
        val totalDuration = profile.avgDurationMinutes * (profile.visitCount - 1) + durationMinutes
        val avg = if (profile.visitCount > 0) totalDuration / profile.visitCount else durationMinutes
        val updated = profile.copy(avgDurationMinutes = avg)
        save(updated)
        return updated
    }

    /**
     * Get profile as prompt context (returns empty string if no profile).
     */
    fun getPromptContext(lat: Double, lng: Double): String {
        val data = cache.get(lat, lng, type = TYPE)
        if (data.isBlank()) return ""
        return try {
            deserialize(data).toPromptContext()
        } catch (_: Exception) { "" }
    }

    /**
     * R3-4: Get all saved place profiles (for UI listing).
     */
    fun getAllProfiles(): List<PlaceProfile> {
        val entries = cache.getAllByType(TYPE)
        return entries.mapNotNull { entry ->
            try {
                deserialize(entry.content)
            } catch (_: Exception) { null }
        }.sortedByDescending { it.lastVisit }
    }

    /**
     * R3-4: Delete a place profile by coordinates.
     */
    fun deleteProfile(profile: PlaceProfile) {
        val entries = cache.getAllByType(TYPE)
        entries.filter {
            Math.abs(it.latitude - profile.latitude) < 0.001 &&
            Math.abs(it.longitude - profile.longitude) < 0.001
        }.forEach { cache.deleteById(it.id) }
        Log.d(TAG, "Deleted profile: ${profile.address}")
    }

    /**
     * R4-1: Update the label (user-assigned name) of a place profile.
     */
    fun updateLabel(profile: PlaceProfile, newLabel: String): PlaceProfile {
        val updated = profile.copy(label = newLabel)
        save(updated)
        return updated
    }

    private fun save(profile: PlaceProfile) {
        cache.save(
            profile.latitude, profile.longitude,
            TYPE, serialize(profile), "vision_v8",
            address = profile.address, ttlHours = TTL_HOURS
        )
    }

    private fun serialize(profile: PlaceProfile): String {
        return JSONObject().apply {
            put("lat", profile.latitude)
            put("lng", profile.longitude)
            put("addr", profile.address)
            put("label", profile.label)
            put("visits", profile.visitCount)
            put("first", profile.firstVisit)
            put("last", profile.lastVisit)
            put("mode", profile.frequentMode ?: "")
            put("days", JSONArray(profile.visitDays.toList()))
            put("obs", JSONArray(profile.observations))
            put("avg_min", profile.avgDurationMinutes)
        }.toString()
    }

    private fun deserialize(json: String): PlaceProfile {
        val obj = JSONObject(json)
        val daysArray = obj.optJSONArray("days")
        val days = mutableSetOf<Int>()
        if (daysArray != null) {
            for (i in 0 until daysArray.length()) days.add(daysArray.getInt(i))
        }
        val obsArray = obj.optJSONArray("obs")
        val obs = mutableListOf<String>()
        if (obsArray != null) {
            for (i in 0 until obsArray.length()) obs.add(obsArray.getString(i))
        }
        return PlaceProfile(
            latitude = obj.getDouble("lat"),
            longitude = obj.getDouble("lng"),
            address = obj.optString("addr", ""),
            label = obj.optString("label", ""),
            visitCount = obj.optInt("visits", 1),
            firstVisit = obj.optLong("first", System.currentTimeMillis()),
            lastVisit = obj.optLong("last", System.currentTimeMillis()),
            frequentMode = obj.optString("mode", "").ifBlank { null },
            visitDays = days,
            observations = obs,
            avgDurationMinutes = obj.optInt("avg_min", 0)
        )
    }
}
