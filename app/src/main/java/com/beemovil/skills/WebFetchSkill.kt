package com.beemovil.skills

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebFetchSkill — Fetch and extract text content from any URL.
 * The agent can use this to read web pages, articles, documentation, etc.
 */
class WebFetchSkill : BeeSkill {
    override val name = "web_fetch"
    override val description = "Fetch content from a URL. Extracts readable text from web pages. Provide 'url'. Optional: 'max_chars' (default 4000) to limit response length."
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "url":{"type":"string","description":"URL to fetch content from"},
            "max_chars":{"type":"integer","description":"Max characters to return (default 4000)"}
        },"required":["url"]}
    """.trimIndent())

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun execute(params: JSONObject): JSONObject {
        val url = params.optString("url", "")
        val maxChars = params.optInt("max_chars", 4000)

        if (url.isBlank()) return JSONObject().put("error", "URL is required")

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 BeeMovil/3.5")
                .header("Accept", "text/html,application/xhtml+xml,text/plain,application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return JSONObject().put("error", "Empty response")
            val contentType = response.header("Content-Type", "text/html") ?: "text/html"

            val text = if (contentType.contains("json")) {
                body.take(maxChars)
            } else {
                // Simple HTML to text extraction
                htmlToText(body).take(maxChars)
            }

            val result = JSONObject()
            result.put("url", url)
            result.put("status", response.code)
            result.put("content_type", contentType)
            result.put("text", text)
            result.put("chars", text.length)
            result.put("title", extractTitle(body))
            result
        } catch (e: Exception) {
            Log.e("WebFetchSkill", "Fetch error: ${e.message}")
            JSONObject().put("error", "Fetch failed: ${e.message}")
        }
    }

    private fun htmlToText(html: String): String {
        var text = html
        // Remove script and style blocks
        text = text.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<nav[^>]*>[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<footer[^>]*>[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<header[^>]*>[\\s\\S]*?</header>", RegexOption.IGNORE_CASE), "")
        // Convert common elements
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("</p>|</div>|</li>|</h[1-6]>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
        // Remove all remaining tags
        text = text.replace(Regex("<[^>]+>"), "")
        // Decode HTML entities
        text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        // Clean whitespace
        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        return text.trim()
    }

    private fun extractTitle(html: String): String {
        val match = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }
}
