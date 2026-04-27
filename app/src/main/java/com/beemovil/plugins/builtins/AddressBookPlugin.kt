package com.beemovil.plugins.builtins

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AddressBookPlugin(private val context: Context) : EmmaPlugin {

    override val id = "search_contact"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Busca el número de teléfono o correo de un contacto en la libreta de direcciones física (Agenda) del usuario. " +
                    "Úsalo ANTES de enviar un WhatsApp, SMS o correo si el usuario solo te da el nombre (ej: 'Mándale a Juan').",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply {
                        put("type", "string")
                        put("description", "Nombre o alias del contacto a buscar (ej: 'Lumen', 'Juan Pérez').")
                    })
                })
                put("required", JSONArray(listOf("name")))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val name = args["name"] as? String ?: return@withContext "ERROR: El parámetro 'name' es requerido."

        // Check permission (Permission Gate Just-In-Time)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext "TOOL_CALL::request_permission::CONTACTS"
        }

        try {
            val results = searchContactInAgenda(name)
            if (results.isEmpty()) {
                "No se encontró ningún contacto que coincida con '$name' en la agenda del dispositivo. Pídele al usuario el número directamente o que revise cómo está guardado."
            } else {
                val formatted = results.joinToString("\n") { "Nombre: ${it.name} | Teléfonos: ${it.phones.joinToString(", ")} | Emails: ${it.emails.joinToString(", ")}" }
                "Contactos encontrados en la agenda para '$name':\n$formatted\n\nUsa estos datos exactos para tu próxima acción (WhatsApp/Email)."
            }
        } catch (e: Exception) {
            "ERROR_TOOL_FAILED: Falló la búsqueda de contactos (${e.message})."
        }
    }

    private fun searchContactInAgenda(query: String): List<ContactResult> {
        val results = mutableListOf<ContactResult>()
        
        // Search by Display Name
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.HAS_PHONE_NUMBER),
            selection,
            selectionArgs,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val contactName = cursor.getString(nameIndex) ?: "Desconocido"
                val hasPhone = cursor.getInt(hasPhoneIndex) > 0
                
                val phones = if (hasPhone) getPhonesForContact(id) else emptyList()
                val emails = getEmailsForContact(id)
                
                results.add(ContactResult(contactName, phones, emails))
                
                // Limit to top 5 matches to avoid blowing up the prompt
                if (results.size >= 5) break
            }
        }
        return results
    }

    private fun getPhonesForContact(contactId: String): List<String> {
        val phones = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val num = cursor.getString(numIndex)?.replace(Regex("[^0-9+]"), "")
                if (num != null && !phones.contains(num)) {
                    phones.add(num)
                }
            }
        }
        return phones
    }

    private fun getEmailsForContact(contactId: String): List<String> {
        val emails = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val emailIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                val email = cursor.getString(emailIndex)
                if (email != null && !emails.contains(email)) {
                    emails.add(email)
                }
            }
        }
        return emails
    }

    private data class ContactResult(val name: String, val phones: List<String>, val emails: List<String>)
}
