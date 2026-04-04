package com.beemovil.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * ContactsSkill — search contacts and initiate calls/messages.
 */
class ContactsSkill(private val context: Context) : BeeSkill {
    override val name = "contacts"
    override val description = "Search phone contacts and initiate calls/messages. Actions: 'search' (requires 'query'), 'call' (requires 'number'), 'sms' (requires 'number', optional 'message')"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["search","call","sms"]},
            "query":{"type":"string","description":"Name to search in contacts"},
            "number":{"type":"string","description":"Phone number to call/SMS"},
            "message":{"type":"string","description":"SMS message text"}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "search")

        return when (action) {
            "search" -> searchContacts(params.optString("query", ""))
            "call" -> makeCall(params.optString("number", ""))
            "sms" -> sendSms(params.optString("number", ""), params.optString("message", ""))
            else -> JSONObject().put("error", "Action not supported")
        }
    }

    private fun searchContacts(query: String): JSONObject {
        if (query.isBlank()) return JSONObject().put("error", "No search query")

        val results = JSONArray()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$query%"),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            cursor?.use {
                while (it.moveToNext() && results.length() < 10) {
                    results.put(JSONObject().apply {
                        put("name", it.getString(0))
                        put("number", it.getString(1))
                    })
                }
            }
        } catch (e: SecurityException) {
            return JSONObject().put("error", "Sin permiso para leer contactos. Otorga el permiso en Configuración.")
        } catch (e: Exception) {
            return JSONObject().put("error", "Error: ${e.message}")
        }

        return JSONObject().put("contacts", results).put("count", results.length())
    }

    private fun makeCall(number: String): JSONObject {
        if (number.isBlank()) return JSONObject().put("error", "No phone number")
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            JSONObject().put("success", true).put("message", "📞 Marcando $number")
        } catch (e: Exception) {
            JSONObject().put("error", "Error: ${e.message}")
        }
    }

    private fun sendSms(number: String, message: String): JSONObject {
        if (number.isBlank()) return JSONObject().put("error", "No phone number")
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            JSONObject().put("success", true).put("message", "💬 Abriendo SMS para $number")
        } catch (e: Exception) {
            JSONObject().put("error", "Error: ${e.message}")
        }
    }
}
