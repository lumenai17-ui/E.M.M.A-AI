package com.beemovil.vision

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DashcamLogger — JSON session logger for dashcam mode.
 *
 * Logs each analysis frame with: timestamp, GPS, speed, heading, AI result.
 * Creates one file per session in /sdcard/BeeMovil/dashcam/
 */
class DashcamLogger(private val context: Context) {

    companion object {
        private const val TAG = "DashcamLogger"
    }

    private var sessionFile: File? = null
    private var entries = JSONArray()
    private var sessionStartTime = 0L

    val isActive: Boolean get() = sessionFile != null
    val entryCount: Int get() = entries.length()

    fun startSession() {
        val dir = File(context.getExternalFilesDir(null), "dashcam")
        dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        sessionFile = File(dir, "dashcam_$timestamp.json")
        entries = JSONArray()
        sessionStartTime = System.currentTimeMillis()

        // Write initial header
        val header = JSONObject().apply {
            put("session_start", timestamp)
            put("device", android.os.Build.MODEL)
            put("entries", entries)
        }
        sessionFile?.writeText(header.toString(2))
        Log.i(TAG, "Dashcam session started: ${sessionFile?.name}")
    }

    fun logFrame(
        frameNumber: Int,
        gpsData: GpsData?,
        analysisResult: String,
        prompt: String = ""
    ) {
        if (sessionFile == null) return

        val entry = JSONObject().apply {
            put("frame", frameNumber)
            put("timestamp", SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()))
            put("elapsed_s", (System.currentTimeMillis() - sessionStartTime) / 1000)

            if (gpsData != null) {
                put("lat", gpsData.latitude)
                put("lng", gpsData.longitude)
                put("speed_kmh", "%.1f".format(gpsData.speedKmh))
                put("heading", gpsData.bearingCardinal)
                put("altitude", "%.0f".format(gpsData.altitude))
                if (gpsData.address.isNotBlank()) put("address", gpsData.address)
            }

            put("analysis", analysisResult.take(500))
            if (prompt.isNotBlank()) put("prompt", prompt)
        }

        entries.put(entry)

        // Save every 5 frames to avoid data loss
        if (entries.length() % 5 == 0) {
            save()
        }
    }

    fun stopSession() {
        save()
        val count = entries.length()
        // R4-3: Copy to public Downloads/EMMA/
        sessionFile?.let { file ->
            if (file.exists()) {
                com.beemovil.files.PublicFileWriter.copyToPublicDownloads(
                    context, file, "application/json"
                )
            }
        }
        sessionFile = null
        entries = JSONArray()
        Log.i(TAG, "Dashcam session stopped ($count entries) — saved to Downloads/EMMA/")
    }

    private fun save() {
        try {
            val wrapper = JSONObject().apply {
                put("session_start", SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
                    .format(Date(sessionStartTime)))
                put("total_frames", entries.length())
                put("duration_s", (System.currentTimeMillis() - sessionStartTime) / 1000)
                put("entries", entries)
            }
            sessionFile?.writeText(wrapper.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Save error: ${e.message}")
        }
    }

    fun getSessionFilePath(): String? = sessionFile?.absolutePath
}
