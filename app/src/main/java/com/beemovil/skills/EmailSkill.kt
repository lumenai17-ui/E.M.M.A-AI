package com.beemovil.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.beemovil.email.EmailService
import com.beemovil.google.GoogleAuthManager
import com.beemovil.security.SecurePrefs
import org.json.JSONObject

/**
 * EmailSkill v2 — Smart email sending:
 * 1. If Google Workspace connected → sends via SMTP+XOAUTH2 directly
 * 2. If IMAP/SMTP configured → sends via SMTP directly
 * 3. Fallback: opens Gmail/email app pre-filled (Intent)
 *
 * Actions:
 * - "send" → send directly (no app opening)
 * - "compose" → open email app pre-filled (legacy behavior)
 *
 * Required: 'to', 'subject', 'body'
 * Optional: 'cc', 'bcc', 'action' (default: "send")
 */
class EmailSkill(private val context: Context) : BeeSkill {
    override val name = "email"
    override val description = "Send an email. Can send DIRECTLY without opening Gmail if email is configured. " +
            "Params: 'to' (email), 'subject', 'body'. Optional: 'cc', 'bcc', 'action' ('send' for direct, 'compose' to open app)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "to":{"type":"string","description":"Recipient email address"},
            "subject":{"type":"string","description":"Email subject"},
            "body":{"type":"string","description":"Email body text"},
            "cc":{"type":"string","description":"CC email address"},
            "bcc":{"type":"string","description":"BCC email address"},
            "action":{"type":"string","description":"'send' to send directly, 'compose' to open email app. Default: send","enum":["send","compose"]}
        },"required":["to","subject","body"]}
    """.trimIndent())

    companion object {
        private const val TAG = "EmailSkill"
    }

    override fun execute(params: JSONObject): JSONObject {
        val to = params.optString("to", "")
        val subject = params.optString("subject", "")
        val body = params.optString("body", "")
        val cc = params.optString("cc", "")
        val bcc = params.optString("bcc", "")
        val action = params.optString("action", "send")

        if (to.isBlank()) return JSONObject().put("error", "No recipient email provided")

        // If compose mode, use Intent (legacy behavior)
        if (action == "compose") {
            return composeViaIntent(to, subject, body, cc, bcc)
        }

        // Try to send directly
        return try {
            sendDirect(to, subject, body, cc, bcc)
        } catch (e: Exception) {
            Log.w(TAG, "Direct send failed, falling back to Intent: ${e.message}")
            // Fallback to Intent
            composeViaIntent(to, subject, body, cc, bcc)
        }
    }

    private fun sendDirect(to: String, subject: String, body: String, cc: String, bcc: String): JSONObject {
        val securePrefs = SecurePrefs.get(context)
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)

        // Option 1: Google Workspace OAuth
        val googleAuth = GoogleAuthManager(context)
        if (googleAuth.isSignedIn() && googleAuth.hasGmailScope()) {
            val token = googleAuth.getAccessToken()
            val fromEmail = googleAuth.getEmail()
            if (token != null && fromEmail != null) {
                Log.i(TAG, "Sending via Google OAuth from $fromEmail")
                // Use EmailService with OAuth token
                val emailService = EmailService(context)
                emailService.sendWithOAuth(fromEmail, token, to, subject, body)
                return JSONObject()
                    .put("success", true)
                    .put("method", "google_oauth")
                    .put("message", "[MAIL] Email enviado directamente a: $to")
                    .put("from", fromEmail)
                    .put("subject", subject)
            }
        }

        // Option 2: Manual SMTP configuration
        val smtpEmail = securePrefs.getString("email_address", "") ?: ""
        val smtpPass = securePrefs.getString("email_password", "") ?: ""
        val smtpHost = prefs.getString("email_smtp_host", "") ?: ""
        val smtpPort = prefs.getString("email_smtp_port", "587") ?: "587"

        if (smtpEmail.isNotBlank() && smtpPass.isNotBlank() && smtpHost.isNotBlank()) {
            Log.i(TAG, "Sending via SMTP from $smtpEmail")
            val emailService = EmailService(context)
            emailService.sendEmail(to, subject, body)
            return JSONObject()
                .put("success", true)
                .put("method", "smtp")
                .put("message", "[MAIL] Email enviado directamente a: $to")
                .put("from", smtpEmail)
                .put("subject", subject)
        }

        // No direct send method available
        throw Exception("No email configuration found — no OAuth, no SMTP")
    }

    private fun composeViaIntent(to: String, subject: String, body: String, cc: String, bcc: String): JSONObject {
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
                .put("method", "intent")
                .put("message", "[MAIL] Email abierto para enviar a: $to (revisa y presiona enviar)")
                .put("subject", subject)
        } catch (e: Exception) {
            JSONObject().put("error", "Error al abrir email: ${e.message}")
        }
    }
}
