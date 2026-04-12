package com.beemovil.plugins.builtins

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarOperatorPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "calendar_os_operations"
    private val TAG = "CalendarOperatorPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Operador del Calendario nativo. Permite LEER tu agenda o ESCRIBIR una junta. Úsalo si el usuario dice '¿Qué tengo hoy?', 'Agendame una cita mañana a las 5', etc.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("operation", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("read_agenda").put("insert_event"))
                        put("description", "Operación. read_agenda para leer los próximos 7 días. insert_event para escribir/crear una reunión en el calendario.")
                    })
                    put("event_title", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Solo insert_event) El nombre de la junta o evento.")
                    })
                    put("event_description", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Solo insert_event) Descripción o notas de la reunión.")
                    })
                    put("epoch_start", JSONObject().apply {
                        put("type", "number")
                        put("description", "(Solo insert_event) Timestamp Unix epoch MS exacto donde inicia la reunión, calculado matemáticamente basado en lo que pide el usuario.")
                    })
                })
                put("required", JSONArray().put("operation"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val operation = args["operation"] as? String ?: return "Falta 'operation'."

        return withContext(Dispatchers.IO) {
            try {
                if (operation == "read_agenda") {
                    readAgenda()
                } else if (operation == "insert_event") {
                    val title = args["event_title"] as? String ?: "Evento E.M.M.A."
                    val desc = args["event_description"] as? String ?: ""
                    val startMsDouble = args["epoch_start"] as? Number
                    val startMs = startMsDouble?.toLong() ?: System.currentTimeMillis() + (1000 * 60 * 60) // En 1 hora por defecto

                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, title)
                        putExtra(CalendarContract.Events.DESCRIPTION, desc)
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMs + (1000 * 60 * 60)) // Dura 1 hora aprox
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    "Disparé la pantalla visual del calendario insertando el evento '$title'. El usuario solamente le tiene que dar a Guardar porque yo ya rellené los datos."
                } else {
                    "Operación desconocida"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción Calendar", e)
                "Se intentó, pero el OS denegó el acceso al calendario: ${e.message}"
            }
        }
    }

    private fun readAgenda(): String {
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )

        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.add(Calendar.DAY_OF_YEAR, 7) // Próximos 7 días
        val nextWeek = c.timeInMillis

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(now.toString(), nextWeek.toString())

        val sb = StringBuilder()
        var eventsCount = 0
        val sdf = SimpleDateFormat("dd/MMM/yyyy HH:mm", Locale.getDefault())

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)

            sb.append("Extracto crudo de Agenda de Google Calendar (Siguientes 7 Días):\n")
            while (cursor.moveToNext() && eventsCount < 20) {
                val title = cursor.getString(titleIdx)
                val start = cursor.getLong(startIdx)
                val dateStr = sdf.format(Date(start))
                sb.append("- [ $dateStr ] $title\n")
                eventsCount++
            }
        }

        return if (eventsCount == 0) {
            "El sistema reporta CERO juntas o eventos en el calendario para los próximos 7 días."
        } else sb.toString()
    }
}
