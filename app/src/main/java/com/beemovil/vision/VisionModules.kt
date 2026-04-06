package com.beemovil.vision

/**
 * VisionProState — Configuration state for all 7 Vision Pro modules.
 * Each module is independently toggleable by the user.
 */
data class VisionProState(
    val gpsOverlay: Boolean = false,
    val voiceNarration: Boolean = false,
    val tapToFocus: Boolean = true,
    val arTextOverlay: Boolean = true,
    val videoRecording: Boolean = false,
    val dashcamMode: Boolean = false,
    val backgroundAgent: Boolean = false,
    val backgroundAgentPrompt: String = "Si detectas algo peligroso o inusual, avisame.",
    val touristGuide: Boolean = false,
    val touristDestination: String = ""
)

/**
 * VisionModule — Metadata for each toggleable module in the Vision Pro panel.
 */
data class VisionModule(
    val key: String,
    val label: String,
    val description: String,
    val iconName: String   // Material icon name reference
)

/** All available modules for the UI panel. */
val VISION_MODULES = listOf(
    VisionModule("gps", "GPS Overlay", "Ubicacion en pantalla + en prompt", "LocationOn"),
    VisionModule("voice", "Voz Narracion", "El agente habla lo que ve", "RecordVoiceOver"),
    VisionModule("focus", "Tap-to-Focus", "Toca para enfocar + zoom", "CenterFocusStrong"),
    VisionModule("ar", "AR Overlay", "Texto sobre la camara", "Visibility"),
    VisionModule("video", "Grabar Video", "Graba mientras analiza", "Videocam"),
    VisionModule("dashcam", "Dashcam Mode", "Grabacion + log GPS + timestamps", "DirectionsCar"),
    VisionModule("agent", "Agente Fondo", "Ejecuta acciones segun lo que ve", "SmartToy")
)

/**
 * GpsData — Current GPS reading with derived data.
 */
data class GpsData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speed: Float = 0f,          // m/s
    val bearing: Float = 0f,        // degrees
    val accuracy: Float = 0f,       // meters
    val address: String = "",       // Geocoded address
    val timestamp: Long = 0L
) {
    val speedKmh: Float get() = speed * 3.6f
    val bearingCardinal: String get() = when {
        bearing < 22.5 || bearing >= 337.5 -> "N"
        bearing < 67.5 -> "NE"
        bearing < 112.5 -> "E"
        bearing < 157.5 -> "SE"
        bearing < 202.5 -> "S"
        bearing < 247.5 -> "SO"
        bearing < 292.5 -> "O"
        else -> "NO"
    }
    val coordsShort: String get() = "%.4f, %.4f".format(latitude, longitude)

    fun toPromptContext(): String {
        val parts = mutableListOf<String>()
        parts.add("Lat: ${"%.5f".format(latitude)}, Lng: ${"%.5f".format(longitude)}")
        if (altitude > 0) parts.add("Alt: ${"%.0f".format(altitude)}m")
        if (speed > 0.5f) parts.add("Vel: ${"%.1f".format(speedKmh)} km/h, Dir: $bearingCardinal")
        if (address.isNotBlank()) parts.add("Dir: $address")
        return "[UBICACION: ${parts.joinToString(" | ")}]"
    }
}
