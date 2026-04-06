package com.beemovil.skills

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * CalendarSkill — read/create events in the native Android calendar.
 */
class CalendarSkill(private val context: Context) : BeeSkill {
    override val name = "calendar"
    override val description = "Manage calendar events. Actions: 'create' (requires 'title', 'start_time' as 'YYYY-MM-DD HH:mm', optional 'end_time', 'location', 'description'), 'today' (list today's events), 'week' (list this week's events)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["create","today","week"]},
            "title":{"type":"string","description":"Event title"},
            "start_time":{"type":"string","description":"Start time YYYY-MM-DD HH:mm"},
            "end_time":{"type":"string","description":"End time YYYY-MM-DD HH:mm"},
            "location":{"type":"string","description":"Event location"},
            "description":{"type":"string","description":"Event description"}
        },"required":["action"]}
    """.trimIndent())

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "today")

        return when (action) {
            "create" -> createEvent(params)
            "today" -> listEvents(0)
            "week" -> listEvents(7)
            else -> JSONObject().put("error", "Action not supported")
        }
    }

    private fun createEvent(params: JSONObject): JSONObject {
        val title = params.optString("title", "")
        if (title.isBlank()) return JSONObject().put("error", "No title provided")

        val startStr = params.optString("start_time", "")
        val endStr = params.optString("end_time", "")
        val location = params.optString("location", "")
        val description = params.optString("description", "")

        return try {
            val startMs = if (startStr.isNotBlank()) sdf.parse(startStr)?.time ?: System.currentTimeMillis()
                          else System.currentTimeMillis()
            val endMs = if (endStr.isNotBlank()) sdf.parse(endStr)?.time ?: (startMs + 3600000)
                        else startMs + 3600000 // default 1 hour

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
                if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                if (description.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, description)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            JSONObject()
                .put("success", true)
                .put("message", "[CAL] Creando evento: $title")
                .put("start", startStr)
                .put("end", endStr.ifBlank { "1 hora después" })
        } catch (e: Exception) {
            JSONObject().put("error", "Error: ${e.message}")
        }
    }

    private fun listEvents(daysAhead: Int): JSONObject {
        val results = JSONArray()
        try {
            val cal = Calendar.getInstance()
            val startMs = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, if (daysAhead == 0) 1 else daysAhead)
            val endMs = cal.timeInMillis

            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION
            )

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf("$startMs", "$endMs"),
                "${CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use {
                while (it.moveToNext() && results.length() < 20) {
                    val dtStart = it.getLong(1)
                    results.put(JSONObject().apply {
                        put("title", it.getString(0) ?: "Sin título")
                        put("start", sdf.format(Date(dtStart)))
                        put("end", if (!it.isNull(2)) sdf.format(Date(it.getLong(2))) else "")
                        put("location", it.getString(3) ?: "")
                        put("description", it.getString(4) ?: "")
                    })
                }
            }
        } catch (e: SecurityException) {
            return JSONObject().put("error", "Sin permiso de calendario. Otórgalo en Configuración.")
        } catch (e: Exception) {
            return JSONObject().put("error", "Error: ${e.message}")
        }

        val label = if (daysAhead == 0) "hoy" else "esta semana"
        return JSONObject()
            .put("events", results)
            .put("count", results.length())
            .put("period", label)
            .put("message", if (results.length() == 0) "No hay eventos $label" else "${results.length()} eventos $label")
    }
}
