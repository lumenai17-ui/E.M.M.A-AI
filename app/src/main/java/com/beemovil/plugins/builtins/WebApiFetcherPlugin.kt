package com.beemovil.plugins.builtins

import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class WebApiFetcherPlugin : EmmaPlugin {
    override val id: String = "fetch_external_api"
    private val TAG = "WebApiFetcherPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Ejecutor de API/Fetch de pura sangre. Herramienta sagrada Si el usuario dice 'Pégale a esta API', 'Trae datos de mi servidor', o dicta una URL de REST, usa esta herramienta armando headers y JSON BODY adecuados.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("endpoint_url", JSONObject().apply {
                        put("type", "string")
                        put("description", "URL absoluta tipo https://api.algo.com/v1/ping")
                    })
                    put("method", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("GET").put("POST"))
                        put("description", "Vía de transporte HTTP")
                    })
                    put("json_body", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Solo POST) Objeto JSON crudo que irá en el cuerpo para mandar.")
                    })
                })
                put("required", JSONArray().put("endpoint_url").put("method"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val endpoint = args["endpoint_url"] as? String ?: return "Error: La URL de la API es nula."
        val method = (args["method"] as? String ?: "GET").uppercase()
        val jsonBody = args["json_body"] as? String

        Log.i(TAG, "Lanzadera de red HTTP $method a $endpoint")

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                
                // Si es un posteador de datos
                if (method == "POST" && !jsonBody.isNullOrEmpty()) {
                    connection.setRequestProperty("Content-Type", "application/json; utf-8")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.doOutput = true
                    connection.outputStream.use { os ->
                        val input = jsonBody.toByteArray(Charsets.UTF_8)
                        os.write(input, 0, input.size)
                    }
                }

                val status = connection.responseCode
                Log.d(TAG, "API Response Code: $status")

                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseString = stream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader ->
                        reader.readText()
                    }
                } ?: "No Body Stream"

                val truncatedRes = if (responseString.length > 8000) {
                    responseString.substring(0, 8000) + "... [Truncado HTTP]"
                } else responseString

                "API Fetch Completado (Status $status):\n\n$truncatedRes"

            } catch (e: Exception) {
                Log.e(TAG, "Rotura API", e)
                "Catástrofe de Red Síncrona (Timeout/Server Down): ${e.message}"
            } finally {
                connection?.disconnect()
            }
        }
    }
}
