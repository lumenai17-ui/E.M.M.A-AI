package com.beemovil.google

import android.util.Base64
import android.util.Log
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * GoogleGmailService — Read inbox and send emails via Gmail API.
 *
 * All methods are SYNCHRONOUS — call from Dispatchers.IO.
 * Access token provided by GoogleAuthManager.
 */
class GoogleGmailService(private val accessToken: String) {

    companion object {
        private const val TAG = "GoogleGmail"
        const val SCOPE = GmailScopes.GMAIL_MODIFY
        const val SCOPE_READONLY = GmailScopes.GMAIL_READONLY
        const val SCOPE_SEND = GmailScopes.GMAIL_SEND
    }

    data class EmailMessage(
        val id: String,
        val threadId: String,
        val from: String,
        val to: String,
        val subject: String,
        val snippet: String,
        val date: Long,
        val isUnread: Boolean,
        val labelIds: List<String>
    )

    private val gmailService: Gmail by lazy {
        val credentials = GoogleCredentials.create(AccessToken(accessToken, Date(System.currentTimeMillis() + 3600_000)))
        Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        )
            .setApplicationName("BeeMovil")
            .build()
    }

    // ═══════════════════════════════════════
    // LIST MESSAGES (INBOX)
    // ═══════════════════════════════════════

    /**
     * List messages from inbox.
     * @param maxResults Number of messages to retrieve
     * @param query Gmail search query (e.g., "is:unread", "from:boss@company.com")
     */
    fun listInbox(maxResults: Int = 20, query: String = "in:inbox"): List<EmailMessage> {
        return try {
            val response = gmailService.users().messages().list("me")
                .setQ(query)
                .setMaxResults(maxResults.toLong())
                .execute()

            val messageIds = response.messages ?: return emptyList()
            
            // Fetch details for each message (batched would be better for perf)
            messageIds.take(maxResults).mapNotNull { msg ->
                try {
                    val full = gmailService.users().messages().get("me", msg.id)
                        .setFormat("metadata")
                        .setMetadataHeaders(listOf("From", "To", "Subject", "Date"))
                        .execute()
                    parseMessage(full)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "List inbox error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get unread count
     */
    fun getUnreadCount(): Int {
        return try {
            val response = gmailService.users().messages().list("me")
                .setQ("is:unread in:inbox")
                .setMaxResults(1)
                .execute()
            response.resultSizeEstimate?.toInt() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Unread count error: ${e.message}", e)
            0
        }
    }

    // ═══════════════════════════════════════
    // SEND EMAIL
    // ═══════════════════════════════════════

    /**
     * Send an email.
     * @return Message ID or null on error
     */
    fun sendEmail(to: String, subject: String, body: String, fromEmail: String = "me"): String? {
        return try {
            val props = Properties()
            val session = Session.getDefaultInstance(props, null)

            val mimeMessage = MimeMessage(session).apply {
                setFrom(InternetAddress(fromEmail))
                addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(to))
                setSubject(subject)
                setText(body, "UTF-8")
            }

            val buffer = ByteArrayOutputStream()
            mimeMessage.writeTo(buffer)
            val rawMessage = Base64.encodeToString(buffer.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

            val message = Message().setRaw(rawMessage)
            val sent = gmailService.users().messages().send("me", message).execute()

            Log.i(TAG, "Email sent: ${sent.id} → $to")
            sent.id
        } catch (e: Exception) {
            Log.e(TAG, "Send email error: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════
    // MARK AS READ
    // ═══════════════════════════════════════

    fun markAsRead(messageId: String): Boolean {
        return try {
            val modify = com.google.api.services.gmail.model.ModifyMessageRequest()
                .setRemoveLabelIds(listOf("UNREAD"))
            gmailService.users().messages().modify("me", messageId, modify).execute()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Mark read error: ${e.message}", e)
            false
        }
    }

    // ═══════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════

    fun searchEmails(query: String, maxResults: Int = 10): List<EmailMessage> {
        return listInbox(maxResults, query)
    }

    // ═══════════════════════════════════════
    // FORMAT FOR LLM
    // ═══════════════════════════════════════

    fun formatInboxForLlm(emails: List<EmailMessage>): String {
        if (emails.isEmpty()) return "Tu bandeja está vacía. 📭"
        val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
        return buildString {
            appendLine("📧 Tus últimos ${emails.size} correos:")
            emails.forEachIndexed { i, e ->
                val unread = if (e.isUnread) "🔴" else "⚪"
                appendLine("$unread ${i + 1}. **${e.subject}** — de: ${e.from}")
                appendLine("   ${sdf.format(Date(e.date))} | ${e.snippet.take(60)}...")
            }
        }
    }

    private fun parseMessage(msg: Message): EmailMessage {
        val headers = msg.payload?.headers ?: emptyList()
        fun header(name: String) = headers.find { it.name.equals(name, true) }?.value ?: ""

        return EmailMessage(
            id = msg.id ?: "",
            threadId = msg.threadId ?: "",
            from = header("From"),
            to = header("To"),
            subject = header("Subject").ifBlank { "(Sin asunto)" },
            snippet = msg.snippet ?: "",
            date = msg.internalDate ?: 0L,
            isUnread = msg.labelIds?.contains("UNREAD") == true,
            labelIds = msg.labelIds ?: emptyList()
        )
    }

    // ═══════════════════════════════════════
    // FULL MESSAGE (body + attachments)
    // ═══════════════════════════════════════

    data class FullEmailMessage(
        val id: String,
        val body: String,
        val htmlBody: String?,
        val attachments: List<GmailAttachmentInfo>
    )

    data class GmailAttachmentInfo(
        val attachmentId: String,
        val name: String,
        val mimeType: String,
        val size: Long
    )

    fun getFullMessage(messageId: String): FullEmailMessage? {
        return try {
            val msg = gmailService.users().messages().get("me", messageId)
                .setFormat("full")
                .execute()

            var plainBody = ""
            var htmlBody: String? = null
            val attachments = mutableListOf<GmailAttachmentInfo>()

            fun decodePart(part: com.google.api.services.gmail.model.MessagePart) {
                val mime = part.mimeType ?: ""
                val bodyData = part.body?.data
                val attId = part.body?.attachmentId

                if (attId != null && part.filename?.isNotBlank() == true) {
                    attachments.add(GmailAttachmentInfo(
                        attachmentId = attId,
                        name = part.filename ?: "attachment",
                        mimeType = mime,
                        size = part.body?.size?.toLong() ?: 0L
                    ))
                } else if (bodyData != null) {
                    val decoded = String(Base64.decode(bodyData, Base64.URL_SAFE))
                    if (mime.contains("text/html")) htmlBody = decoded
                    else if (mime.contains("text/plain")) plainBody = decoded
                }

                part.parts?.forEach { decodePart(it) }
            }

            msg.payload?.let { decodePart(it) }

            FullEmailMessage(
                id = msg.id ?: messageId,
                body = plainBody.ifBlank { htmlBody?.replace(Regex("<[^>]*>"), " ")?.replace(Regex("\\s+"), " ")?.trim() ?: "" },
                htmlBody = htmlBody,
                attachments = attachments
            )
        } catch (e: Exception) {
            Log.e(TAG, "getFullMessage error: ${e.message}", e)
            null
        }
    }

    fun downloadGmailAttachment(messageId: String, attachmentId: String): ByteArray? {
        return try {
            val att = gmailService.users().messages().attachments()
                .get("me", messageId, attachmentId).execute()
            Base64.decode(att.data, Base64.URL_SAFE)
        } catch (e: Exception) {
            Log.e(TAG, "Download attachment error: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════
    // SENT FOLDER
    // ═══════════════════════════════════════

    fun listSent(maxResults: Int = 20): List<EmailMessage> {
        return listInbox(maxResults, "in:sent")
    }
}
