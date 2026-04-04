package com.beemovil.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

/**
 * ShareSkill — Share text, files, images via Android share dialog.
 * Can target specific apps (Instagram, WhatsApp, Twitter, etc).
 */
class ShareSkill(private val context: Context) : BeeSkill {
    override val name = "share"
    override val description = """Share text or files via Android share dialog (WhatsApp, Instagram, Telegram, email, etc).
        Provide 'text' for text sharing, or 'file_path' for file sharing.
        Optional: 'title', 'target_app' (e.g. 'instagram', 'whatsapp', 'twitter', 'telegram'), 'mime_type'"""
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "text":{"type":"string","description":"Text content to share"},
            "file_path":{"type":"string","description":"Path to file to share (PDF, image, HTML, etc)"},
            "title":{"type":"string","description":"Title/subject for the share"},
            "target_app":{"type":"string","description":"Target app: instagram, whatsapp, twitter, telegram, email"},
            "mime_type":{"type":"string","description":"MIME type of file (auto-detected if not provided)"}
        },"required":[]}
    """.trimIndent())

    companion object {
        private const val TAG = "ShareSkill"

        private val APP_PACKAGES = mapOf(
            "instagram" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "telegram" to "org.telegram.messenger",
            "facebook" to "com.facebook.katana",
            "linkedin" to "com.linkedin.android",
            "gmail" to "com.google.android.gm",
            "outlook" to "com.microsoft.office.outlook",
            "tiktok" to "com.zhiliaoapp.musically"
        )
    }

    override fun execute(params: JSONObject): JSONObject {
        val text = params.optString("text", "")
        val filePath = params.optString("file_path", "")
        val title = params.optString("title", "Compartir desde Bee-Movil")
        val targetApp = params.optString("target_app", "").lowercase()
        var mimeType = params.optString("mime_type", "")

        if (text.isBlank() && filePath.isBlank()) {
            return JSONObject().put("error", "Provide 'text' or 'file_path' to share")
        }

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                if (filePath.isNotBlank()) {
                    val file = File(filePath)
                    if (!file.exists()) {
                        return JSONObject().put("error", "File not found: $filePath")
                    }

                    // Auto-detect MIME type
                    if (mimeType.isBlank()) {
                        mimeType = when (file.extension.lowercase()) {
                            "pdf" -> "application/pdf"
                            "csv" -> "text/csv"
                            "html", "htm" -> "text/html"
                            "jpg", "jpeg" -> "image/jpeg"
                            "png" -> "image/png"
                            "gif" -> "image/gif"
                            "mp4" -> "video/mp4"
                            "txt" -> "text/plain"
                            else -> "application/octet-stream"
                        }
                    }

                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
                } else {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }

                putExtra(Intent.EXTRA_SUBJECT, title)

                // Target specific app
                if (targetApp.isNotBlank()) {
                    APP_PACKAGES[targetApp]?.let { pkg ->
                        setPackage(pkg)
                    }
                }
            }

            val chooserIntent = if (targetApp.isBlank()) {
                Intent.createChooser(intent, title).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                intent
            }

            context.startActivity(chooserIntent)

            val msg = buildString {
                append("Abriendo ")
                if (targetApp.isNotBlank()) append("$targetApp ") else append("opciones para compartir ")
                if (filePath.isNotBlank()) append("con archivo: ${File(filePath).name}")
                else append("con texto")
            }

            Log.i(TAG, msg)
            JSONObject().put("success", true).put("message", msg)
        } catch (e: Exception) {
            Log.e(TAG, "Share error: ${e.message}")
            val msg = if (targetApp.isNotBlank() && e.message?.contains("No Activity") == true) {
                "$targetApp no está instalado en este dispositivo"
            } else {
                "No se pudo compartir: ${e.message}"
            }
            JSONObject().put("error", msg)
        }
    }
}
