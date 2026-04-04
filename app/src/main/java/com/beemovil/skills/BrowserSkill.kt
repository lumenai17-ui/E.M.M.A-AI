package com.beemovil.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

class BrowserSkill(private val context: Context) : BeeSkill {
    override val name = "browser"
    override val description = "Open a URL in the phone's web browser. Requires 'url'"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "url":{"type":"string","description":"URL to open in browser"}
        },"required":["url"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        var url = params.optString("url", "")
        if (url.isBlank()) return JSONObject().put("error", "No URL provided")

        // Add https if missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            JSONObject().put("success", true).put("url", url).put("message", "Abriendo: $url")
        } catch (e: Exception) {
            JSONObject().put("error", "No se pudo abrir: ${e.message}")
        }
    }
}
