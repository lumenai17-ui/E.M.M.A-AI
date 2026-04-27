package com.beemovil.plugins.builtins

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import org.json.JSONObject

/**
 * PersonalEmailPlugin — Redacta y prepara correos electrónicos.
 * En lugar de enviarlos internamente (overkill), abre la aplicación de correo nativa
 * del usuario (Gmail, Outlook) pre-cargada con el asunto, destinatario y mensaje
 * para que el usuario pueda revisarlo y enviarlo manualmente con un solo toque.
 */
class PersonalEmailPlugin(private val context: Context) : EmmaPlugin {
    override val id = "personal_email"
    private val TAG = "PersonalEmailPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Redacta y prepara un correo electrónico abriendo la aplicación nativa de correo del usuario (Ej. Gmail). Úsalo cuando el usuario te pida 'envía un correo', 'manda un email', etc. Tú armas el mensaje y el usuario lo envía con un toque.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("to", JSONObject().apply {
                        put("type", "string")
                        put("description", "Dirección de correo electrónico del destinatario (Ej. juan@gmail.com). Obligatorio.")
                    })
                    put("subject", JSONObject().apply {
                        put("type", "string")
                        put("description", "Asunto del correo.")
                    })
                    put("body", JSONObject().apply {
                        put("type", "string")
                        put("description", "Cuerpo del correo. El mensaje que quieres enviar.")
                    })
                })
                put("required", org.json.JSONArray().put("to").put("subject").put("body"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        return try {
            val to = (args["to"] as? String)?.trim() ?: ""
            val subject = args["subject"] as? String ?: "Mensaje"
            val body = args["body"] as? String ?: ""

            if (to.isBlank()) {
                return "Error: Necesito la dirección de correo electrónico del destinatario."
            }

            // Create Intent to open Email app
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Verify if there is an app that can handle the intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                "Éxito: He redactado el correo para $to y he abierto tu aplicación de correo. Solo revisa el mensaje y presiona enviar."
            } else {
                "Error: No se encontró ninguna aplicación de correo instalada en el dispositivo para enviar correos."
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error executing email intent", e)
            "Error inesperado al preparar el correo: ${e.message}"
        }
    }
}
