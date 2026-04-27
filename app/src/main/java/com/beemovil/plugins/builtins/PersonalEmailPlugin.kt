package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.email.EmailService
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * PersonalEmailPlugin — Reads and sends emails via IMAP/SMTP (personal email).
 * Complements GmailPlugin which uses Google OAuth.
 * Activates when the user has IMAP credentials configured in Settings.
 */
class PersonalEmailPlugin(private val context: Context) : EmmaPlugin {
    override val id = "personal_email"
    private val TAG = "PersonalEmailPlugin"

    private fun getConfig(): Triple<String, String, EmailService.EmailConfig>? {
        val securePrefs = com.beemovil.security.SecurePrefs.get(context)
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val email = securePrefs.getString("email_address", "") ?: ""
        val password = securePrefs.getString("email_password", "") ?: ""
        val imapHost = prefs.getString("email_imap_host", "") ?: ""
        val imapPort = prefs.getInt("email_imap_port", 993)
        val smtpHost = prefs.getString("email_smtp_host", "") ?: ""
        val smtpPort = prefs.getInt("email_smtp_port", 587)

        if (email.isBlank() || password.isBlank() || imapHost.isBlank()) return null
        return Triple(email, password, EmailService.EmailConfig(imapHost, imapPort, smtpHost, smtpPort))
    }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Lee y envía emails desde la cuenta personal (IMAP) del usuario. " +
                "Úsalo cuando el usuario pida 'léeme mis correos personales', 'revisa mi email personal', " +
                "'envía un email desde mi correo personal'. Si el usuario tiene Gmail conectado Y correo personal, " +
                "usa esta herramienta solo cuando explícitamente diga 'personal' o 'IMAP'.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("inbox").put("unread_count").put("send"))
                        put("description", "Acción: 'inbox' para ver bandeja, 'unread_count' para contar no leídos, 'send' para enviar.")
                    })
                    put("to", JSONObject().apply {
                        put("type", "string")
                        put("description", "Email del destinatario (solo para action='send').")
                    })
                    put("subject", JSONObject().apply {
                        put("type", "string")
                        put("description", "Asunto del correo (solo para action='send').")
                    })
                    put("body", JSONObject().apply {
                        put("type", "string")
                        put("description", "Cuerpo del correo (solo para action='send').")
                    })
                    put("attachment_paths", JSONObject().apply {
                        put("type", "string")
                        put("description", "Rutas de archivos a adjuntar, separadas por coma (para action='send'). Usa paths de otros plugins: export_pdf, generate_image, etc.")
                    })
                })
                put("required", JSONArray().put("action"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val (email, password, config) = getConfig()
            ?: return "❌ No tienes correo personal configurado. Ve a Settings → Email Personal para configurarlo."

        val action = args["action"] as? String ?: "inbox"
        val emailService = EmailService(context)

        return withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "inbox" -> {
                        val emails = emailService.fetchInbox(email, password, config, limit = 10)
                        if (emails.isEmpty()) {
                            "Tu bandeja personal ($email) está vacía."
                        } else {
                            formatForLlm(emails, email)
                        }
                    }
                    "unread_count" -> {
                        val emails = emailService.fetchInbox(email, password, config, limit = 50, unreadOnly = true)
                        "Tienes ${emails.size} correos sin leer en tu bandeja personal ($email)."
                    }
                    "send" -> {
                        val to = args["to"] as? String ?: return@withContext "Falta el destinatario."
                        val subject = args["subject"] as? String ?: "Mensaje de E.M.M.A."
                        val body = args["body"] as? String ?: ""
                        val attachPaths = (args["attachment_paths"] as? String)
                            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                        val ok = emailService.sendEmail(email, password, config, to, subject, body, attachmentPaths = attachPaths)
                        if (ok) {
                            val attLabel = if (attachPaths.isNotEmpty()) " con ${attachPaths.size} adjunto(s)" else ""
                            "✅ Email enviado desde $email a $to$attLabel"
                        } else "❌ Error enviando el email."
                    }
                    else -> "Acción '$action' no reconocida."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Personal email error: ${e.message}", e)
                "❌ Error con el correo personal: ${e.message}"
            }
        }
    }

    private fun formatForLlm(emails: List<EmailService.EmailMessage>, account: String): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("📧 Bandeja de $account (${emails.size} correos):")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        emails.forEachIndexed { i, e ->
            val readMark = if (e.isRead) "📖" else "🔵"
            sb.appendLine("$readMark ${i + 1}. De: ${e.from}")
            sb.appendLine("   Asunto: ${e.subject}")
            sb.appendLine("   Fecha: ${sdf.format(e.date)}")
            sb.appendLine("   Preview: ${e.body.take(100).replace("\n", " ")}")
            sb.appendLine()
        }
        return sb.toString()
    }
}
