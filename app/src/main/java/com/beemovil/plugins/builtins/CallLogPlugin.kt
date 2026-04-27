package com.beemovil.plugins.builtins

import android.content.Context
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "check_call_logs"
    private val TAG = "CallLogPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Escanea el registro de llamadas del teléfono (Call Logs). Úsala cuando el usuario pregunte '¿quién me llamó?', '¿tengo llamadas perdidas?' o 'revisa mis últimas llamadas'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("limit", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Número máximo de llamadas recientes a extraer. Por defecto 5.")
                    })
                    put("type_filter", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("all").put("missed").put("incoming").put("outgoing"))
                        put("description", "Filtro de llamadas. missed = Solo perdidas, incoming = Recibidas, outgoing = Salientes, all = Todas.")
                    })
                })
                put("required", JSONArray().put("type_filter"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        // Check Permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return "TOOL_CALL::request_permission::CALL_LOGS"
        }

        val limit = (args["limit"] as? Number)?.toInt() ?: 5
        val filter = args["type_filter"] as? String ?: "all"

        return withContext(Dispatchers.IO) {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )
            
            var selection: String? = null
            var selectionArgs: Array<String>? = null
            
            when (filter) {
                "missed" -> {
                    selection = "${CallLog.Calls.TYPE} = ?"
                    selectionArgs = arrayOf(CallLog.Calls.MISSED_TYPE.toString())
                }
                "incoming" -> {
                    selection = "${CallLog.Calls.TYPE} = ?"
                    selectionArgs = arrayOf(CallLog.Calls.INCOMING_TYPE.toString())
                }
                "outgoing" -> {
                    selection = "${CallLog.Calls.TYPE} = ?"
                    selectionArgs = arrayOf(CallLog.Calls.OUTGOING_TYPE.toString())
                }
            }

            val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT $limit"
            val sb = StringBuilder()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

            try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                    
                    if (numberIdx < 0 || typeIdx < 0 || dateIdx < 0) return@withContext "Error del proveedor nativo de OS."

                    sb.append("📋 **Registro de Llamadas Nativas Android** (Filtro: $filter, Límite: $limit)\n\n")
                    var matches = 0

                    while (cursor.moveToNext()) {
                        val number = cursor.getString(numberIdx) ?: "Desconocido"
                        val name = cursor.getString(nameIdx) ?: "Sin Registrar"
                        val typeCode = cursor.getInt(typeIdx)
                        val dateLong = cursor.getLong(dateIdx)
                        val duration = cursor.getString(durIdx) ?: "0"
                        
                        val dateStr = dateFormat.format(Date(dateLong))
                        val typeStr = when (typeCode) {
                            CallLog.Calls.INCOMING_TYPE -> "📥 Recibida"
                            CallLog.Calls.OUTGOING_TYPE -> "📤 Saliente"
                            CallLog.Calls.MISSED_TYPE -> "❌ Perdida"
                            CallLog.Calls.VOICEMAIL_TYPE -> "🎧 Buzón de voz"
                            CallLog.Calls.REJECTED_TYPE -> "⛔ Rechazada"
                            CallLog.Calls.BLOCKED_TYPE -> "🛑 Bloqueada"
                            else -> "Desconocida ($typeCode)"
                        }

                        sb.append("- [$typeStr] **$name** ($number)\n  Fecha: $dateStr | Duración: $duration seg\n\n")
                        matches++
                    }
                    
                    if (matches == 0) {
                        sb.append("No hay registros que coincidan con el filtro.")
                    }
                }
                sb.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo CallLog", e)
                "Excepción OS al extraer registro de llamadas. Error: ${e.message}"
            }
        }
    }
}
