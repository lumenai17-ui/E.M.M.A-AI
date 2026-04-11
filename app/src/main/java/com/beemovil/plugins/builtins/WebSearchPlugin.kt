package com.beemovil.plugins.builtins

import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class WebSearchPlugin : EmmaPlugin {
    override val id: String = "web_search"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Busca información en vivo en internet. Usa esta herramienta cuando necesites información actualizada, noticias, clima o datos que no sabes.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Término o pregunta a buscar en internet.")
                    })
                })
                put("required", org.json.JSONArray().put("query"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        return withContext(Dispatchers.IO) {
            val query = args["query"]?.toString() ?: return@withContext "Error: No query provided"
            try {
                // A very simplified DuckDuckGo HTML fetcher for the demonstration of the Plugin system
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = URL("https://html.duckduckgo.com/html/?q=$encodedQuery")
                val connection = url.openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Extract result snippets roughly
                val snippets = mutableListOf<String>()
                val regex = Regex("<a class=\"result__snippet[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                val matches = regex.findAll(response)
                
                for (match in matches.take(3)) {
                    val text = match.groupValues[1].replace(Regex("<[^>]*>"), "").replace("\n", " ").trim()
                    if (text.isNotEmpty()) {
                        snippets.add(text)
                    }
                }
                
                if (snippets.isEmpty()) {
                    return@withContext "No se encontraron resultados concisos para: $query"
                }
                
                return@withContext "Resultados web para '$query':\n" + snippets.joinToString("\n- ", prefix = "- ")
            } catch (e: Exception) {
                return@withContext "Error de red al buscar en web: ${e.message}"
            }
        }
    }
}
