package com.beemovil.plugins.builtins

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ContactManagerPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "search_android_contacts"
    private val TAG = "ContactManagerPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un escáner directo a la libreta de contactos privada del teléfono del usuario. Ejecuta esta herramienta siempre que el usuario mencione un nombre, apodo o local pidiendo que le brindes su teléfono, le marques o lo busques.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("search_query", JSONObject().apply {
                        put("type", "string")
                        put("description", "El nombre parcial o total a buscar en la agenda (Ej: 'mama', 'Hugo', 'oficina'). Trata de que sea genérico para asegurar coincidencias.")
                    })
                })
                put("required", JSONArray().put("search_query"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val query = args["search_query"] as? String ?: return "Error: Falta el parámetro de búsqueda"
        Log.i(TAG, "Infiltrando agenda nativa de Android buscando: $query")

        return withContext(Dispatchers.IO) {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "\${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            
            val sb = StringBuilder()
            var matches = 0

            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    if (nameIdx < 0 || numIdx < 0) return@withContext "Error del proveedor nativo de OS."

                    sb.append("Resultados de libreta telefónica nativa para '$query':\n")
                    while (cursor.moveToNext() && matches < 15) { // Cap a 15 resultados
                        val name = cursor.getString(nameIdx)
                        val number = cursor.getString(numIdx)
                        sb.append("- Nombre de Contacto: $name | Teléfono: $number\n")
                        matches++
                    }
                }

                if (matches == 0) {
                    "No encontré a nadie que se parezca remotamente a '$query' en los Contactos SQLite del Celular."
                } else {
                    sb.toString()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Permiso denegado o fallo de Contract", e)
                "Excepción OS al extraer contactos. Posible falta de permisos READ_CONTACTS. Error: ${e.message}"
            }
        }
    }
}
