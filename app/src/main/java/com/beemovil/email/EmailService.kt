package com.beemovil.email

import android.content.Context
import android.util.Log
import java.io.File
import java.util.*
import javax.mail.*
import javax.mail.internet.*
import javax.mail.search.FlagTerm

/**
 * EmailService — IMAP/SMTP email client with attachment support.
 * Works with Gmail (App Password), Outlook, and custom SMTP domains.
 */
class EmailService(private val context: Context) {

    companion object {
        private const val TAG = "EmailService"
        val PRESETS = mapOf(
            "Gmail" to EmailConfig("imap.gmail.com", 993, "smtp.gmail.com", 587),
            "Outlook" to EmailConfig("outlook.office365.com", 993, "smtp.office365.com", 587),
            "Dominio propio" to EmailConfig("mail.example.com", 993, "mail.example.com", 587)
        )
    }

    data class EmailConfig(
        val imapHost: String,
        val imapPort: Int,
        val smtpHost: String,
        val smtpPort: Int
    )

    data class EmailMessage(
        val uid: Long,
        val from: String,
        val fromEmail: String,
        val to: String,
        val subject: String,
        val body: String,
        val date: Date,
        val isRead: Boolean,
        val isStarred: Boolean,
        val hasAttachments: Boolean,
        val attachments: List<EmailAttachment> = emptyList()
    )

    data class EmailAttachment(
        val name: String,
        val size: Long,
        val mimeType: String,
        var localPath: String? = null
    )

    private var imapStore: Store? = null
    private val attachmentDir = File(context.filesDir, "email_attachments").also { it.mkdirs() }

    /**
     * Test IMAP connection. Returns bot name or throws exception.
     */
    fun testConnection(email: String, password: String, config: EmailConfig): String {
        val props = imapProperties(config)
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(config.imapHost, config.imapPort, email, password)
        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)
        val count = inbox.messageCount
        inbox.close(false)
        store.close()
        return "OK · $count emails"
    }

    /**
     * Fetch recent emails from INBOX.
     */
    fun fetchInbox(
        email: String, password: String, config: EmailConfig,
        limit: Int = 30, unreadOnly: Boolean = false
    ): List<EmailMessage> {
        val props = imapProperties(config)
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(config.imapHost, config.imapPort, email, password)

        val inbox = store.getFolder("INBOX")
        try {
            inbox.open(Folder.READ_ONLY)

            val messages = if (unreadOnly) {
                inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
            } else {
                val total = inbox.messageCount
                val start = maxOf(1, total - limit + 1)
                inbox.getMessages(start, total)
            }

            val result = messages.mapNotNull { msg ->
                try {
                    parseMessage(msg, inbox)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    null
                }
            }.sortedByDescending { it.date }

            return result.take(limit)
        } finally {
            try { inbox.close(false) } catch (_: Exception) {}
            try { store.close() } catch (_: Exception) {}
        }
    }

    /**
     * Fetch a single email by UID with full body and attachments.
     */
    fun fetchEmail(
        email: String, password: String, config: EmailConfig, uid: Long
    ): EmailMessage? {
        val props = imapProperties(config)
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(config.imapHost, config.imapPort, email, password)

        val inbox = store.getFolder("INBOX")
        inbox.open(Folder.READ_ONLY)

        val uidFolder = inbox as? UIDFolder
        val msg = uidFolder?.getMessageByUID(uid)

        val result = msg?.let { parseMessage(it, inbox, downloadAttachments = true) }

        inbox.close(false)
        store.close()
        return result
    }

    /**
     * Send email with optional attachments.
     */
    fun sendEmail(
        email: String, password: String, config: EmailConfig,
        to: String, subject: String, body: String,
        isHtml: Boolean = false,
        attachmentPaths: List<String> = emptyList()
    ): Boolean {
        return try {
            val props = smtpProperties(config)
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(email, password)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(email))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                this.subject = subject
                sentDate = Date()
            }

            if (attachmentPaths.isEmpty()) {
                if (isHtml) {
                    message.setContent(body, "text/html; charset=utf-8")
                } else {
                    message.setText(body, "utf-8")
                }
            } else {
                val multipart = MimeMultipart()

                // Body part
                val textPart = MimeBodyPart()
                if (isHtml) textPart.setContent(body, "text/html; charset=utf-8")
                else textPart.setText(body, "utf-8")
                multipart.addBodyPart(textPart)

                // Attachments
                attachmentPaths.forEach { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val attachPart = MimeBodyPart()
                        attachPart.attachFile(file)
                        multipart.addBodyPart(attachPart)
                    }
                }

                message.setContent(multipart)
            }

            Transport.send(message)
            Log.i(TAG, "Email sent to $to")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send error: ${e.message}")
            false
        }
    }

    /**
     * Download attachment to local storage.
     */
    fun downloadAttachment(
        email: String, password: String, config: EmailConfig,
        uid: Long, attachmentName: String
    ): File? {
        try {
            val props = imapProperties(config)
            val session = Session.getInstance(props)
            val store = session.getStore("imaps")
            store.connect(config.imapHost, config.imapPort, email, password)

            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val uidFolder = inbox as? UIDFolder
            val msg = uidFolder?.getMessageByUID(uid) ?: return null

            val file = extractAttachment(msg, attachmentName)

            inbox.close(false)
            store.close()
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            return null
        }
    }

    /**
     * List all saved attachments.
     */
    fun getSavedAttachments(): List<File> {
        return attachmentDir.listFiles()?.toList() ?: emptyList()
    }

    // ═══════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════

    private fun parseMessage(msg: Message, folder: Folder, downloadAttachments: Boolean = false): EmailMessage {
        val from = msg.from?.firstOrNull()
        val fromAddr = (from as? InternetAddress)
        val uid = (folder as? UIDFolder)?.getUID(msg) ?: 0L

        val attachments = mutableListOf<EmailAttachment>()
        val body = getTextContent(msg, attachments, downloadAttachments)

        return EmailMessage(
            uid = uid,
            from = fromAddr?.personal ?: fromAddr?.address ?: "Desconocido",
            fromEmail = fromAddr?.address ?: "",
            to = msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { it.toString() } ?: "",
            subject = msg.subject ?: "(Sin asunto)",
            body = body,
            date = msg.sentDate ?: Date(),
            isRead = msg.flags.contains(Flags.Flag.SEEN),
            isStarred = msg.flags.contains(Flags.Flag.FLAGGED),
            hasAttachments = attachments.isNotEmpty(),
            attachments = attachments
        )
    }

    private fun getTextContent(part: Part, attachments: MutableList<EmailAttachment>, download: Boolean): String {
        return when {
            part.isMimeType("text/plain") -> part.content.toString()
            part.isMimeType("text/html") -> {
                // Strip HTML tags for preview, keep for detail
                part.content.toString()
            }
            part.isMimeType("multipart/*") -> {
                val mp = part.content as Multipart
                val sb = StringBuilder()
                for (i in 0 until mp.count) {
                    val bodyPart = mp.getBodyPart(i)
                    val disposition = bodyPart.disposition

                    if (disposition != null && (disposition.equals(Part.ATTACHMENT, true) || disposition.equals(Part.INLINE, true))) {
                        val att = EmailAttachment(
                            name = bodyPart.fileName ?: "attachment_$i",
                            size = bodyPart.size.toLong(),
                            mimeType = bodyPart.contentType.split(";")[0]
                        )
                        if (download) {
                            val file = File(attachmentDir, att.name)
                            bodyPart.inputStream.use { input ->
                                file.outputStream().use { output -> input.copyTo(output) }
                            }
                            att.localPath = file.absolutePath
                        }
                        attachments.add(att)
                    } else {
                        sb.append(getTextContent(bodyPart, attachments, download))
                    }
                }
                sb.toString()
            }
            else -> ""
        }
    }

    private fun extractAttachment(msg: Message, name: String): File? {
        if (!msg.isMimeType("multipart/*")) return null

        val mp = msg.content as Multipart
        for (i in 0 until mp.count) {
            val part = mp.getBodyPart(i)
            if (part.fileName == name || part.fileName?.contains(name) == true) {
                val file = File(attachmentDir, name)
                part.inputStream.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                return file
            }
        }
        return null
    }

    private fun imapProperties(config: EmailConfig) = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", config.imapHost)
        put("mail.imaps.port", config.imapPort.toString())
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.ssl.trust", "*")
        put("mail.imaps.timeout", "15000")
        put("mail.imaps.connectiontimeout", "15000")
    }

    private fun smtpProperties(config: EmailConfig) = Properties().apply {
        put("mail.smtp.host", config.smtpHost)
        put("mail.smtp.port", config.smtpPort.toString())
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.ssl.trust", "*")
        put("mail.smtp.timeout", "15000")
    }
}
