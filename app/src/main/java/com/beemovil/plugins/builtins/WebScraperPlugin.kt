package com.beemovil.plugins.builtins

import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

class WebScraperPlugin : EmmaPlugin {
    override val id: String = "scrape_website_text"
    private val TAG = "WebScraperPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Un lector de artículos web en crudo. Usa ESTA HERRAMIENTA si necesitas extraer el texto sustancial de un enlace web (Wikipedia, noticias, blogs, etc.) aportado por el usuario. Eliminará todos los estilos y scripts, devolviendo solo el texto legible.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply {
                        put("type", "string")
                        put("description", "La URL literal que se desea leer en fondo (Ej: https://es.wikipedia.org/...).")
                    })
                })
                put("required", JSONArray().put("url"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val url = args["url"] as? String ?: return "Error: Parameter 'url' missing."
        
        Log.i(TAG, "Ingesting text from URL: $url")
        
        return withContext(Dispatchers.IO) {
            try {
                // Fetch de la web, simulando un User-Agent normal para saltar bloqueos básicos
                val document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                    .timeout(5000)
                    .get()
                
                val rawText = document.body().text()
                val finalText = if (rawText.length > 15000) {
                    rawText.substring(0, 15000) + "... [Texto truncado por seguridad]"
                } else rawText
                
                Log.d(TAG, "Extracción HTML exitosa. Longitud: ${finalText.length}")
                "Contenido extraído exitosamente de $url:\n\n$finalText"
                
            } catch (e: Exception) {
                Log.e(TAG, "Fallo al raspar la web $url", e)
                "Error al extraer la URL: ${e.message}"
            }
        }
    }
}
