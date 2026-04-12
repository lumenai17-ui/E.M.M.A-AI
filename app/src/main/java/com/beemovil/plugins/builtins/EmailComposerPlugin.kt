package com.beemovil.plugins.builtins

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class EmailComposerPlugin(private val context: Context) : EmmaPlugin {
    override val id: String = "compose_email_intent"
    private val TAG = "EmailComposerPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un compositor automático de Correos Electrónicos directos. Úsalo SIEMPRE que el humano te pida 'mandarle un email a X', redactando el correo a nivel profesional.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("to_email", JSONObject().apply {
                        put("type", "string")
                        put("description", "Dirección del destinatario. (Ej: rh@empresa.com)")
                    })
                    put("subject", JSONObject().apply {
                        put("type", "string")
                        put("description", "Asunto del correo.")
                    })
                    put("bodyText", JSONObject().apply {
                        put("type", "string")
                        put("description", "Cuerpo del correo gigante con las peticiones redactadas apropiadamente (saltos de línea incluidos).")
                    })
                })
                put("required", JSONArray().put("to_email").put("subject").put("bodyText"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val targetEmail = args["to_email"] as? String ?: return "Falta el destinatario."
        val subject = args["subject"] as? String ?: "Mensaje de E.M.M.A."
        val body = args["bodyText"] as? String ?: ""

        Log.i(TAG, "Lanzando compositor Email para: $targetEmail")

        return withContext(Dispatchers.Main) {
            try {
                // Preparamos esquema Nativo MailTo
                val uriText = "mailto:$targetEmail" +
                        "?subject=${Uri.encode(subject)}" +
                        "&body=${Uri.encode(body)}"
                
                val uri = Uri.parse(uriText)
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = uri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                context.startActivity(intent)
                "Ensamblé, redacté e inyecté la plantilla de Email hacia '$targetEmail'. Solamente corrobóralo y presiona el botón 'Enviar' en tu app de correos."
            } catch (e: Exception) {
                Log.e(TAG, "Error Composer", e)
                "Fallo al abrir el cliente de Emails del Teléfono: ${e.message}"
            }
        }
    }
}
