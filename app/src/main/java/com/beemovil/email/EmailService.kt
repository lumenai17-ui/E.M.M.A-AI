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
        val htmlBody: String? = null,
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
        limit: Int = 50, unreadOnly: Boolean = false
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
                    // Fallback: return email with metadata only (no body)
                    safeParseEnvelope(msg, inbox)
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
     * Falls back to scanning recent messages if UID lookup fails.
     */
    fun fetchEmail(
        email: String, password: String, config: EmailConfig, uid: Long,
        subjectHint: String? = null
    ): EmailMessage? {
        val props = imapProperties(config)
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(config.imapHost, config.imapPort, email, password)

        val inbox = store.getFolder("INBOX")
        try {
            inbox.open(Folder.READ_ONLY)

            // Try UID first
            var msg: Message? = null
            if (uid > 0) {
                val uidFolder = inbox as? UIDFolder
                msg = uidFolder?.getMessageByUID(uid)
            }

            // Fallback: search by subject in recent messages
            if (msg == null && subjectHint != null) {
                val total = inbox.messageCount
                val start = maxOf(1, total - 50 + 1)
                val recent = inbox.getMessages(start, total)
                msg = recent.lastOrNull { m ->
                    try { m.subject?.contains(subjectHint, ignoreCase = true) == true } catch (_: Exception) { false }
                }
            }

            // Fallback: just get the message by index from the folder (last resort)
            if (msg == null && uid > 0 && uid.toInt() <= inbox.messageCount) {
                try { msg = inbox.getMessage(uid.toInt()) } catch (_: Exception) {}
            }

            return msg?.let { parseMessage(it, inbox, downloadAttachments = true) }
        } finally {
            try { inbox.close(false) } catch (_: Exception) {}
            try { store.close() } catch (_: Exception) {}
        }
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

    /**
     * Fetch sent emails. Tries multiple folder names for compatibility.
     */
    fun fetchSentFolder(
        email: String, password: String, config: EmailConfig, limit: Int = 25
    ): List<EmailMessage> {
        val props = imapProperties(config)
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        store.connect(config.imapHost, config.imapPort, email, password)

        val sentNames = listOf("[Gmail]/Sent Mail", "Sent", "INBOX.Sent", "Sent Items", "Sent Messages")
        var sentFolder: Folder? = null
        for (name in sentNames) {
            try {
                val f = store.getFolder(name)
                if (f.exists()) { sentFolder = f; break }
            } catch (_: Exception) {}
        }
        if (sentFolder == null) {
            store.close()
            return emptyList()
        }

        try {
            sentFolder.open(Folder.READ_ONLY)
            val total = sentFolder.messageCount
            val start = maxOf(1, total - limit + 1)
            val messages = sentFolder.getMessages(start, total)

            return messages.mapNotNull { msg ->
                try { parseMessage(msg, sentFolder) } catch (_: Exception) { safeParseEnvelope(msg, sentFolder) }
            }.sortedByDescending { it.date }.take(limit)
        } finally {
            try { sentFolder.close(false) } catch (_: Exception) {}
            try { store.close() } catch (_: Exception) {}
        }
    }

    // ═══════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════

    /**
     * Safe envelope parser — extracts only headers (from, to, subject, date)
     * without touching the body. Used as fallback when parseMessage crashes.
     */
    private fun safeParseEnvelope(msg: Message, folder: Folder): EmailMessage? {
        return try {
            val from = msg.from?.firstOrNull()
            val fromAddr = (from as? InternetAddress)
            val uid = try { (folder as? UIDFolder)?.getUID(msg) ?: 0L } catch (_: Exception) { 0L }
            EmailMessage(
                uid = uid,
                from = fromAddr?.personal ?: fromAddr?.address ?: "Desconocido",
                fromEmail = fromAddr?.address ?: "",
                to = try { msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { it.toString() } ?: "" } catch (_: Exception) { "" },
                subject = try { msg.subject ?: "(Sin asunto)" } catch (_: Exception) { "(Sin asunto)" },
                body = "",
                htmlBody = null,
                date = try { msg.sentDate ?: Date() } catch (_: Exception) { Date() },
                isRead = try { msg.flags.contains(Flags.Flag.SEEN) } catch (_: Exception) { true },
                isStarred = try { msg.flags.contains(Flags.Flag.FLAGGED) } catch (_: Exception) { false },
                hasAttachments = false,
                attachments = emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Even envelope parse failed: ${e.message}")
            null
        }
    }

    private fun parseMessage(msg: Message, folder: Folder, downloadAttachments: Boolean = false): EmailMessage {
        val from = msg.from?.firstOrNull()
        val fromAddr = (from as? InternetAddress)
        val uid = try { (folder as? UIDFolder)?.getUID(msg) ?: 0L } catch (_: Exception) { 0L }

        val attachments = mutableListOf<EmailAttachment>()
        // B3b: granular body fallback
        val rawBody = try {
            getTextContent(msg, attachments, downloadAttachments)
        } catch (e: Exception) {
            Log.e(TAG, "getTextContent failed, trying raw: ${e.message}")
            try {
                msg.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(10000) }
            } catch (_: Exception) {
                "(Error cargando contenido del correo)"
            }
        }

        // Separate HTML from plain text
        val isHtml = rawBody.trimStart().startsWith("<") || rawBody.contains("<html", ignoreCase = true) || rawBody.contains("<div", ignoreCase = true)
        val plainBody = if (isHtml) rawBody.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim() else rawBody
        val htmlBody = if (isHtml) rawBody else null

        return EmailMessage(
            uid = uid,
            from = fromAddr?.personal ?: fromAddr?.address ?: "Desconocido",
            fromEmail = fromAddr?.address ?: "",
            to = try { msg.getRecipients(Message.RecipientType.TO)?.joinToString(", ") { it.toString() } ?: "" } catch (_: Exception) { "" },
            subject = try { msg.subject ?: "(Sin asunto)" } catch (_: Exception) { "(Sin asunto)" },
            body = plainBody,
            htmlBody = htmlBody,
            date = try { msg.sentDate ?: Date() } catch (_: Exception) { Date() },
            isRead = try { msg.flags.contains(Flags.Flag.SEEN) } catch (_: Exception) { true },
            isStarred = try { msg.flags.contains(Flags.Flag.FLAGGED) } catch (_: Exception) { false },
            hasAttachments = attachments.isNotEmpty(),
            attachments = attachments
        )
    }

    private fun getTextContent(part: Part, attachments: MutableList<EmailAttachment>, download: Boolean): String {
        return try {
            when {
                part.isMimeType("text/plain") -> {
                    readPartContent(part)
                }
                part.isMimeType("text/html") -> {
                    readPartContent(part)
                }
                part.isMimeType("multipart/*") -> {
                    try {
                        val mp = part.content as Multipart
                        var htmlResult: String? = null
                        var plainResult: String? = null
                        val sb = StringBuilder()

                        for (i in 0 until mp.count) {
                            try {
                                val bodyPart = mp.getBodyPart(i)
                                val disposition = bodyPart.disposition
                                val fileName = try { bodyPart.fileName } catch (_: Exception) { null }

                                if (disposition != null && (disposition.equals(Part.ATTACHMENT, true) || (disposition.equals(Part.INLINE, true) && fileName != null))) {
                                    val att = EmailAttachment(
                                        name = fileName ?: "attachment_$i",
                                        size = try { bodyPart.size.toLong() } catch (_: Exception) { 0L },
                                        mimeType = bodyPart.contentType.split(";")[0].trim()
                                    )
                                    if (download) {
                                        try {
                                            val file = File(attachmentDir, att.name)
                                            bodyPart.inputStream.use { input ->
                                                file.outputStream().use { output -> input.copyTo(output) }
                                            }
                                            att.localPath = file.absolutePath
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Attachment download error: ${e.message}")
                                        }
                                    }
                                    attachments.add(att)
                                } else if (fileName != null && !bodyPart.isMimeType("text/*")) {
                                    // B3c: has filename but no disposition -> inline attachment
                                    val att = EmailAttachment(
                                        name = fileName,
                                        size = try { bodyPart.size.toLong() } catch (_: Exception) { 0L },
                                        mimeType = bodyPart.contentType.split(";")[0].trim()
                                    )
                                    if (download) {
                                        try {
                                            val file = File(attachmentDir, att.name)
                                            bodyPart.inputStream.use { input ->
                                                file.outputStream().use { output -> input.copyTo(output) }
                                            }
                                            att.localPath = file.absolutePath
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Inline attachment download error: ${e.message}")
                                        }
                                    }
                                    attachments.add(att)
                                } else {
                                    val content = getTextContent(bodyPart, attachments, download)
                                    if (bodyPart.isMimeType("text/html")) {
                                        htmlResult = content
                                    } else if (bodyPart.isMimeType("text/plain")) {
                                        plainResult = content
                                    } else {
                                        sb.append(content)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing body part $i: ${e.message}")
                            }
                        }

                        // Prefer HTML over plain text for multipart/alternative
                        htmlResult ?: plainResult ?: sb.toString()
                    } catch (e: Exception) {
                        Log.e(TAG, "Multipart parse error: ${e.message}")
                        try { part.inputStream.bufferedReader().use { it.readText().take(5000) } }
                        catch (_: Exception) { "" }
                    }
                }
                else -> ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTextContent top-level error: ${e.message}")
            ""
        }
    }

    /**
     * Safely read part content — handles String, InputStream, and QPDecoderStream.
     */
    private fun readPartContent(part: Part): String {
        return try {
            val content = part.content
            when (content) {
                is String -> content
                is java.io.InputStream -> content.bufferedReader(charset = getCharset(part)).use { it.readText() }
                else -> content.toString()
            }
        } catch (e: Exception) {
            try {
                // Fallback: read raw inputStream
                part.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                Log.e(TAG, "Failed to read email part: ${e.message}")
                ""
            }
        }
    }

    private fun getCharset(part: Part): java.nio.charset.Charset {
        return try {
            val ct = part.contentType ?: return Charsets.UTF_8
            val charset = ct.substringAfter("charset=", "").substringBefore(";").trim().removeSurrounding("\"")
            if (charset.isNotBlank()) java.nio.charset.Charset.forName(charset) else Charsets.UTF_8
        } catch (_: Exception) { Charsets.UTF_8 }
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
        put("mail.imaps.timeout", "15000")
        put("mail.imaps.connectiontimeout", "15000")
    }

    private fun smtpProperties(config: EmailConfig) = Properties().apply {
        val useSSL = config.smtpPort == 465
        
        if (useSSL) {
            // Port 465: SMTPS — SSL from the start
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", "465")
            put("mail.smtp.auth", "true")
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.socketFactory.port", "465")
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            put("mail.smtp.socketFactory.fallback", "false")
            put("mail.smtp.timeout", "15000")
            put("mail.smtp.connectiontimeout", "15000")
        } else {
            // Port 587 (or other): STARTTLS
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", config.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.timeout", "15000")
            put("mail.smtp.connectiontimeout", "15000")
        }
    }

    /**
     * Send email via Gmail SMTP using OAuth2 XOAUTH2 mechanism.
     * Uses access token instead of password.
     */
    fun sendWithOAuth(fromEmail: String, accessToken: String, to: String, subject: String, body: String) {
        val config = PRESETS["Gmail"]!!
        val props = Properties().apply {
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", config.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.auth.mechanisms", "XOAUTH2")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.ssl.trust", "*")
            put("mail.smtp.timeout", "15000")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(fromEmail, accessToken)
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(fromEmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            this.subject = subject
            setText(body, "utf-8")
            sentDate = Date()
        }

        Transport.send(message)
        Log.i(TAG, "Email sent via OAuth to $to from $fromEmail")
    }

    /**
     * Simplified sendEmail — auto-reads SMTP config from SecurePrefs.
     * Used by EmailSkill for direct sending without passing credentials.
     */
    fun sendEmail(to: String, subject: String, body: String) {
        val securePrefs = com.beemovil.security.SecurePrefs.get(context)
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)

        val email = securePrefs.getString("email_address", "") ?: ""
        val password = securePrefs.getString("email_password", "") ?: ""

        if (email.isBlank() || password.isBlank()) {
            throw Exception("Email not configured — set email and password in Settings")
        }

        // S-12: Leer directamente los campos guardados por Settings
        val config = EmailConfig(
            imapHost = prefs.getString("email_imap_host", "imap.gmail.com") ?: "imap.gmail.com",
            imapPort = prefs.getInt("email_imap_port", 993),
            smtpHost = prefs.getString("email_smtp_host", "smtp.gmail.com") ?: "smtp.gmail.com",
            smtpPort = prefs.getInt("email_smtp_port", 587)
        )
        val success = sendEmail(email, password, config, to, subject, body)
        if (!success) throw Exception("SMTP send failed")
    }
}
