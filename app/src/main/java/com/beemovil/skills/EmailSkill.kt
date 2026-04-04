package com.beemovil.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

/**
 * EmailSkill — opens the user's email app pre-filled with recipient, subject, body.
 * No SMTP needed — uses Android Intent system.
 */
class EmailSkill(private val context: Context) : BeeSkill {
    override val name = "email"
    override val description = "Send an email. Opens Gmail/Outlook pre-filled. Requires 'to' (email address), 'subject', 'body'. Optional: 'cc', 'bcc'"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "to":{"type":"string","description":"Recipient email address"},
            "subject":{"type":"string","description":"Email subject"},
            "body":{"type":"string","description":"Email body text"},
            "cc":{"type":"string","description":"CC email address"},
            "bcc":{"type":"string","description":"BCC email address"}
        },"required":["to","subject","body"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val to = params.optString("to", "")
        val subject = params.optString("subject", "")
        val body = params.optString("body", "")
        val cc = params.optString("cc", "")
        val bcc = params.optString("bcc", "")

        if (to.isBlank()) return JSONObject().put("error", "No recipient email provided")

        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                if (cc.isNotBlank()) putExtra(Intent.EXTRA_CC, arrayOf(cc))
                if (bcc.isNotBlank()) putExtra(Intent.EXTRA_BCC, arrayOf(bcc))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            JSONObject()
                .put("success", true)
                .put("message", "📧 Email listo para enviar a: $to")
                .put("subject", subject)
        } catch (e: Exception) {
            JSONObject().put("error", "Error: ${e.message}")
        }
    }
}
