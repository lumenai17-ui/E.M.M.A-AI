package com.beemovil.skills

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * DateTimeSkill — current date, time, timezone, calculations.
 */
class DateTimeSkill : BeeSkill {
    override val name = "datetime"
    override val description = "Get current date/time info. Actions: 'now' (current date/time), 'calendar' (day of week, week number, etc)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["now","calendar"]},
            "timezone":{"type":"string","description":"Timezone like America/Mexico_City"}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "now")
        val tzStr = params.optString("timezone", "")
        val tz = if (tzStr.isNotBlank()) TimeZone.getTimeZone(tzStr) else TimeZone.getDefault()
        val cal = Calendar.getInstance(tz)

        return when (action) {
            "now" -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                sdf.timeZone = tz
                JSONObject()
                    .put("datetime", sdf.format(cal.time))
                    .put("date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = tz }.format(cal.time))
                    .put("time", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply { timeZone = tz }.format(cal.time))
                    .put("timezone", tz.id)
                    .put("utc_offset", tz.getOffset(cal.timeInMillis) / 3600000)
                    .put("timestamp", cal.timeInMillis)
            }
            "calendar" -> {
                val dayNames = arrayOf("", "Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado")
                val monthNames = arrayOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

                JSONObject()
                    .put("day_of_week", dayNames[cal.get(Calendar.DAY_OF_WEEK)])
                    .put("day", cal.get(Calendar.DAY_OF_MONTH))
                    .put("month", monthNames[cal.get(Calendar.MONTH)])
                    .put("month_number", cal.get(Calendar.MONTH) + 1)
                    .put("year", cal.get(Calendar.YEAR))
                    .put("week_of_year", cal.get(Calendar.WEEK_OF_YEAR))
                    .put("day_of_year", cal.get(Calendar.DAY_OF_YEAR))
                    .put("is_weekend", cal.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY))
                    .put("days_remaining_year", cal.getActualMaximum(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR))
            }
            else -> JSONObject().put("error", "Action not supported")
        }
    }
}
