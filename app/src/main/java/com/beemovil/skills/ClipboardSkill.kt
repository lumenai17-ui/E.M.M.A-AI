package com.beemovil.skills

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONObject

class ClipboardSkill(private val context: Context) : BeeSkill {
    override val name = "clipboard"
    override val description = "Copy text to clipboard or read current clipboard content. Actions: 'copy' (requires 'text'), 'read' (no params)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["copy","read"],"description":"copy or read"},
            "text":{"type":"string","description":"Text to copy (only for copy action)"}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "read")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        return when (action) {
            "copy" -> {
                val text = params.optString("text", "")
                if (text.isBlank()) return JSONObject().put("error", "No text to copy")
                clipboard.setPrimaryClip(ClipData.newPlainText("BeeMovil", text))
                JSONObject().put("success", true).put("message", "Texto copiado al clipboard")
            }
            "read" -> {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: ""
                    JSONObject().put("text", text).put("hasContent", text.isNotEmpty())
                } else {
                    JSONObject().put("text", "").put("hasContent", false)
                }
            }
            else -> JSONObject().put("error", "Action '$action' not supported. Use 'copy' or 'read'")
        }
    }
}
