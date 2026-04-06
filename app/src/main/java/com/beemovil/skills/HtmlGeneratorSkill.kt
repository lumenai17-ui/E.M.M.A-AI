package com.beemovil.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

/**
 * HtmlGeneratorSkill — Create HTML pages (landing pages, reports, presentations).
 * The agent generates the full HTML/CSS/JS and saves it as a file the user can open or share.
 */
class HtmlGeneratorSkill(private val context: Context) : BeeSkill {
    override val name = "generate_html"
    override val description = """Generate an HTML page (landing page, report, presentation, etc).
        Provide:
        - 'filename': Output filename (without .html)
        - 'html': Complete HTML content (include <html>, <head>, <style>, <body>)
        - 'open': true to open in browser after generating (default: true)
        The agent should generate beautiful, modern HTML with inline CSS."""
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "filename":{"type":"string","description":"Output filename (without .html)"},
            "html":{"type":"string","description":"Complete HTML content"},
            "open":{"type":"boolean","description":"Open in browser after generating"}
        },"required":["filename","html"]}
    """.trimIndent())

    companion object {
        private const val TAG = "HtmlGenSkill"
    }

    override fun execute(params: JSONObject): JSONObject {
        val filename = params.optString("filename", "bee_page").replace(" ", "_")
        val html = params.optString("html", "")
        val openAfter = params.optBoolean("open", true)

        if (html.isBlank()) return JSONObject().put("error", "HTML content is required")

        return try {
            val dir = try {
                val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BeeMovil/HTML")
                d.mkdirs()
                if (d.canWrite()) d else File(context.getExternalFilesDir(null), "HTML").also { it.mkdirs() }
            } catch (_: Exception) {
                File(context.getExternalFilesDir(null), "HTML").also { it.mkdirs() }
            }
            val file = File(dir, "${filename}.html")
            file.writeText(html)

            Log.i(TAG, "HTML created: ${file.absolutePath} (${html.length} chars)")

            if (openAfter) {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "text/html")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback: open with browser URL
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("file://${file.absolutePath}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            }

            JSONObject()
                .put("success", true)
                .put("path", file.absolutePath)
                .put("filename", file.name)
                .put("size", file.length())
                .put("message", "🖥️ Página HTML generada: ${file.name}${if (openAfter) " · Abriendo en navegador" else "" }")
        } catch (e: Exception) {
            Log.e(TAG, "HTML error: ${e.message}")
            JSONObject().put("error", "HTML generation failed: ${e.message}")
        }
    }
}
