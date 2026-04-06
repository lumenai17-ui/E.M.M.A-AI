package com.beemovil.skills

import android.util.Log
import com.beemovil.network.BeeHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebSearchSkill — search the web using DuckDuckGo HTML (free, no API key needed).
 *
 * Strategy: Use DuckDuckGo HTML endpoint which returns actual search results,
 * not just the Instant Answer API which only returns Wikipedia snippets.
 */
class WebSearchSkill : BeeSkill {
    override val name = "web_search"
    override val description = "Search the web for current information. Provide 'query' to search. Returns titles, snippets, and URLs from DuckDuckGo."
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "query":{"type":"string","description":"Search query"}
        },"required":["query"]}
    """.trimIndent())

    private val client = BeeHttpClient.default

    override fun execute(params: JSONObject): JSONObject {
        val query = params.optString("query", "")
        if (query.isBlank()) return JSONObject().put("error", "No search query")

        return try {
            // Try DuckDuckGo HTML search first (returns real results)
            val htmlResults = searchDuckDuckGoHtml(query)
            if (htmlResults.length() > 0) {
                return JSONObject().apply {
                    put("query", query)
                    put("results", htmlResults)
                    put("result_count", htmlResults.length())
                }
            }

            // Fallback: DuckDuckGo Instant Answer API
            val instantResult = searchDuckDuckGoInstant(query)
            if (instantResult.has("answer")) {
                return instantResult
            }

            // Last resort: return search URL
            JSONObject().apply {
                put("query", query)
                put("message", "No encontré resultados directos para \"$query\".")
                put("search_url", "https://duckduckgo.com/?q=${query.replace(" ", "+")}")
                put("suggestion", "Puedo abrir el navegador con web_fetch para buscar más información.")
            }
        } catch (e: Exception) {
            Log.e("WebSearchSkill", "Search error: ${e.message}", e)
            JSONObject().put("error", "Error de búsqueda: ${e.message}")
        }
    }

    /**
     * Search DuckDuckGo HTML endpoint — returns actual web results.
     */
    private fun searchDuckDuckGoHtml(query: String): JSONArray {
        val results = JSONArray()
        try {
            val url = "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .header("Accept", "text/html")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return results

            // Parse HTML results — extract titles, snippets, URLs
            // DuckDuckGo HTML format: <a class="result__a" href="...">Title</a>
            //                         <a class="result__snippet" ...>Snippet</a>

            // Extract result blocks
            val resultPattern = Regex(
                """<a[^>]*class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val snippetPattern = Regex(
                """<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
                RegexOption.DOT_MATCHES_ALL
            )

            val titles = resultPattern.findAll(body).toList()
            val snippets = snippetPattern.findAll(body).toList()

            for (i in 0 until minOf(8, titles.size)) {
                val title = titles[i]
                val rawUrl = title.groupValues[1]
                val titleText = title.groupValues[2].replace(Regex("<[^>]+>"), "").trim()

                // DuckDuckGo wraps URLs — extract the real one
                val actualUrl = if (rawUrl.contains("uddg=")) {
                    try {
                        java.net.URLDecoder.decode(
                            rawUrl.substringAfter("uddg=").substringBefore("&"), "UTF-8"
                        )
                    } catch (_: Exception) { rawUrl }
                } else rawUrl

                val snippet = if (i < snippets.size) {
                    snippets[i].groupValues[1].replace(Regex("<[^>]+>"), "").trim()
                } else ""

                if (titleText.isNotBlank()) {
                    results.put(JSONObject().apply {
                        put("title", titleText)
                        put("url", actualUrl)
                        put("snippet", snippet)
                    })
                }
            }
        } catch (e: Exception) {
            Log.e("WebSearchSkill", "HTML search error: ${e.message}")
        }
        return results
    }

    /**
     * Fallback: DuckDuckGo Instant Answer API (only works for Wikipedia-type queries).
     */
    private fun searchDuckDuckGoInstant(query: String): JSONObject {
        val result = JSONObject()
        result.put("query", query)

        try {
            val url = "https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BeeMovil/4.2.5")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return result
            val json = JSONObject(body)

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
                if (related.length() > 0) result.put("related", related)
            }
        } catch (e: Exception) {
            Log.e("WebSearchSkill", "Instant search error: ${e.message}")
        }

        return result
    }
}
