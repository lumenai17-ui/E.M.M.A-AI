package com.beemovil.skills

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSearchSkill — search the web using DuckDuckGo Instant Answer API (free, no key).
 */
class WebSearchSkill : BeeSkill {
    override val name = "web_search"
    override val description = "Search the web. Provide 'query' to search. Returns instant answers and related topics from DuckDuckGo."
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "query":{"type":"string","description":"Search query"}
        },"required":["query"]}
    """.trimIndent())

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun execute(params: JSONObject): JSONObject {
        val query = params.optString("query", "")
        if (query.isBlank()) return JSONObject().put("error", "No search query")

        return try {
            val url = "https://api.duckduckgo.com/?q=${query.replace(" ", "+")}&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BeeMovil/1.0")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return JSONObject().put("error", "Empty response")
            val json = JSONObject(body)

            val result = JSONObject()
            result.put("query", query)

            // Instant answer
            val abstract_ = json.optString("Abstract", "")
            val abstractSource = json.optString("AbstractSource", "")
            val abstractUrl = json.optString("AbstractURL", "")
            val answer = json.optString("Answer", "")

            if (abstract_.isNotBlank()) {
                result.put("answer", abstract_)
                result.put("source", abstractSource)
                result.put("url", abstractUrl)
            } else if (answer.isNotBlank()) {
                result.put("answer", answer)
            }

            // Related topics
            val topics = json.optJSONArray("RelatedTopics")
            if (topics != null && topics.length() > 0) {
                val related = JSONArray()
                for (i in 0 until minOf(5, topics.length())) {
                    val topic = topics.optJSONObject(i) ?: continue
                    val text = topic.optString("Text", "")
                    val firstUrl = topic.optString("FirstURL", "")
                    if (text.isNotBlank()) {
                        related.put(JSONObject().apply {
                            put("text", text)
                            put("url", firstUrl)
                        })
                    }
                }
                result.put("related", related)
            }

            if (!result.has("answer") && !result.has("related")) {
                result.put("message", "No encontré resultados directos. Intenta buscar en el navegador.")
                result.put("search_url", "https://duckduckgo.com/?q=${query.replace(" ", "+")}")
            }

            result
        } catch (e: Exception) {
            JSONObject().put("error", "Search error: ${e.message}")
        }
    }
}
