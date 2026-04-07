package com.beemovil.vision

import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * VisionSessionManager — Manages recording and export of vision sessions.
 *
 * Session types:
 * - GENERAL: General vision analysis log
 * - MEETING: Meeting minutes (summary, participants, action items)
 * - DASHCAM: Dashcam log (GPS, speed, timestamps)
 * - TOURISM: Tourism log (places visited, descriptions)
 *
 * Export options:
 * - PDF (using GeneratePdfSkill)
 * - Text file
 * - Share
 * - Email
 */
class VisionSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "VisionSessionManager"
    }

    enum class SessionType(val label: String, val emoji: String) {
        GENERAL("General", "📋"),
        MEETING("Reunión", "📹"),
        DASHCAM("Dashcam", "🚗"),
        TOURISM("Turismo", "🏛️")
    }

    data class SessionEntry(
        val timestamp: Long,
        val frameNumber: Int,
        val role: String,         // "user", "assistant", "system"
        val content: String,
        val gpsCoords: String? = null,
        val gpsAddress: String? = null,
        val gpsSpeed: Float? = null
    )

    // Session state
    var isRecording = false
        private set
    var sessionType = SessionType.GENERAL
        private set
    var sessionStartTime: Long = 0
        private set
    private val entries = mutableListOf<SessionEntry>()
    private var frameCount = 0

    /**
     * Start a new session.
     */
    fun startSession(type: SessionType = SessionType.GENERAL) {
        isRecording = true
        sessionType = type
        sessionStartTime = System.currentTimeMillis()
        entries.clear()
        frameCount = 0

        entries.add(SessionEntry(
            timestamp = sessionStartTime,
            frameNumber = 0,
            role = "system",
            content = "Sesión ${type.emoji} ${type.label} iniciada"
        ))
    }

    /**
     * Add a frame analysis entry.
     */
    fun logFrame(
        result: String,
        gpsCoords: String? = null,
        gpsAddress: String? = null,
        gpsSpeed: Float? = null
    ) {
        if (!isRecording) return
        frameCount++
        entries.add(SessionEntry(
            timestamp = System.currentTimeMillis(),
            frameNumber = frameCount,
            role = "assistant",
            content = result,
            gpsCoords = gpsCoords,
            gpsAddress = gpsAddress,
            gpsSpeed = gpsSpeed
        ))
    }

    /**
     * Add a user interaction.
     */
    fun logUserQuestion(question: String) {
        if (!isRecording) return
        entries.add(SessionEntry(
            timestamp = System.currentTimeMillis(),
            frameNumber = frameCount,
            role = "user",
            content = question
        ))
    }

    /**
     * Stop the session and return the session summary.
     */
    fun stopSession(): String {
        isRecording = false
        val endTime = System.currentTimeMillis()
        val duration = (endTime - sessionStartTime) / 1000

        entries.add(SessionEntry(
            timestamp = endTime,
            frameNumber = frameCount,
            role = "system",
            content = "Sesión finalizada. Duración: ${formatDuration(duration)}. Frames: $frameCount"
        ))

        return generateSummary()
    }

    /**
     * Generate full session text for export.
     */
    fun generateFullLog(): String = buildString {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        appendLine("═══════════════════════════════════════")
        appendLine("${sessionType.emoji} SESIÓN DE VISIÓN PRO — ${sessionType.label.uppercase()}")
        appendLine("═══════════════════════════════════════")
        appendLine()
        appendLine("Inicio: ${dateFormat.format(Date(sessionStartTime))}")
        if (!isRecording) {
            val lastEntry = entries.lastOrNull()
            if (lastEntry != null) {
                appendLine("Fin: ${dateFormat.format(Date(lastEntry.timestamp))}")
            }
        }
        appendLine("Total frames analizados: $frameCount")
        appendLine("Total interacciones: ${entries.count { it.role == "user" }}")
        appendLine()
        appendLine("───────────────────────────────────────")
        appendLine("REGISTRO COMPLETO")
        appendLine("───────────────────────────────────────")
        appendLine()

        entries.forEach { entry ->
            val time = timeFormat.format(Date(entry.timestamp))
            val prefix = when (entry.role) {
                "user" -> "🎤 USUARIO"
                "assistant" -> "🤖 AI [Frame #${entry.frameNumber}]"
                "system" -> "⚙️ SISTEMA"
                else -> "?"
            }

            appendLine("[$time] $prefix")

            // GPS data if available
            if (entry.gpsCoords != null) {
                append("  📍 ${entry.gpsCoords}")
                if (entry.gpsAddress != null) append(" — ${entry.gpsAddress}")
                if (entry.gpsSpeed != null && entry.gpsSpeed > 0.5f) {
                    append(" · ${"%.1f".format(entry.gpsSpeed * 3.6f)} km/h")
                }
                appendLine()
            }

            appendLine("  ${entry.content}")
            appendLine()
        }
    }

    /**
     * Generate a concise summary (for inline display).
     */
    fun generateSummary(): String = buildString {
        val duration = if (entries.size > 1) {
            (entries.last().timestamp - sessionStartTime) / 1000
        } else 0

        appendLine("${sessionType.emoji} ${sessionType.label} · ${formatDuration(duration)}")
        appendLine("📊 $frameCount frames · ${entries.count { it.role == "user" }} preguntas")

        // Key highlights: first 3 unique assistant entries
        val highlights = entries
            .filter { it.role == "assistant" && it.content.length > 20 }
            .take(3)
            .map { "• ${it.content.take(80)}..." }
        if (highlights.isNotEmpty()) {
            appendLine()
            appendLine("Puntos clave:")
            highlights.forEach { appendLine(it) }
        }
    }

    /**
     * Generate a meeting-style minutes document.
     */
    fun generateMeetingMinutes(): String = buildString {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        appendLine("📋 MINUTA DE REUNIÓN")
        appendLine("════════════════════════════════")
        appendLine()
        appendLine("Fecha: ${dateFormat.format(Date(sessionStartTime))}")
        appendLine("Hora: ${timeFormat.format(Date(sessionStartTime))} - ${
            if (entries.size > 1) timeFormat.format(Date(entries.last().timestamp)) else "en curso"
        }")
        appendLine("Duración: ${formatDuration(
            if (entries.size > 1) (entries.last().timestamp - sessionStartTime) / 1000 else 0
        )}")
        appendLine("Frames analizados: $frameCount")
        appendLine()
        appendLine("────────────────────────────────")
        appendLine("OBSERVACIONES DEL AI")
        appendLine("────────────────────────────────")
        appendLine()

        entries.filter { it.role == "assistant" }.forEachIndexed { idx, entry ->
            appendLine("${idx + 1}. ${entry.content}")
            appendLine()
        }

        val userQuestions = entries.filter { it.role == "user" }
        if (userQuestions.isNotEmpty()) {
            appendLine("────────────────────────────────")
            appendLine("PREGUNTAS / INTERVENCIONES")
            appendLine("────────────────────────────────")
            appendLine()
            userQuestions.forEachIndexed { idx, entry ->
                appendLine("Q${idx + 1}: ${entry.content}")
                appendLine()
            }
        }

        appendLine("────────────────────────────────")
        appendLine("RESUMEN")
        appendLine("────────────────────────────────")
        appendLine()
        appendLine("[Este resumen debe ser generado por el AI al finalizar la sesión]")
    }

    /**
     * Save session to file.
     */
    fun saveToFile(): File? {
        try {
            val dir = File(context.getExternalFilesDir(null), "BeeMovil/sessions")
            if (!dir.exists()) dir.mkdirs()

            val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                .format(Date(sessionStartTime))
            val filename = "vision_${sessionType.name.lowercase()}_$dateStr.txt"
            val file = File(dir, filename)

            val content = when (sessionType) {
                SessionType.MEETING -> generateMeetingMinutes()
                else -> generateFullLog()
            }

            file.writeText(content)
            return file
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Share session via Android share intent.
     */
    fun shareSession() {
        val content = when (sessionType) {
            SessionType.MEETING -> generateMeetingMinutes()
            else -> generateFullLog()
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${sessionType.emoji} Sesión Vision Pro — ${sessionType.label}")
            putExtra(Intent.EXTRA_TEXT, content)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(Intent.createChooser(intent, "Compartir sesión").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    /**
     * Get entry count.
     */
    val entryCount: Int get() = entries.size
    val sessionDuration: String get() {
        val duration = if (isRecording) {
            (System.currentTimeMillis() - sessionStartTime) / 1000
        } else 0
        return formatDuration(duration)
    }

    /**
     * Export session as JSON (for PDF generation via skill).
     */
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", sessionType.name)
        put("startTime", sessionStartTime)
        put("frames", frameCount)
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("t", e.timestamp)
                put("f", e.frameNumber)
                put("role", e.role)
                put("content", e.content)
                if (e.gpsCoords != null) put("gps", e.gpsCoords)
                if (e.gpsAddress != null) put("addr", e.gpsAddress)
            })
        }
        put("entries", arr)
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
