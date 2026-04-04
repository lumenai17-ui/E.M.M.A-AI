package com.beemovil.skills

import android.content.Context
import android.content.Intent
import org.json.JSONObject

class ShareSkill(private val context: Context) : BeeSkill {
    override val name = "share"
    override val description = "Share text or content via Android share dialog (WhatsApp, Telegram, email, etc). Requires 'text'. Optional: 'title'"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "text":{"type":"string","description":"Text content to share"},
            "title":{"type":"string","description":"Title for the share dialog"}
        },"required":["text"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val text = params.optString("text", "")
        val title = params.optString("title", "Compartir desde Bee-Movil")

        if (text.isBlank()) return JSONObject().put("error", "No text to share")

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            JSONObject().put("success", true).put("message", "Abriendo opciones para compartir")
        } catch (e: Exception) {
            JSONObject().put("error", "No se pudo compartir: ${e.message}")
        }
    }
}
