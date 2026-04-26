package com.beemovil.plugins.builtins

import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import com.beemovil.plugins.SecurityGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * WebApiFetcherPlugin — generic HTTP GET/POST exposed to the LLM.
 *
 * C-04 hardening:
 *  - HTTPS only. Plain http is rejected (no MITM-friendly traffic from the LLM).
 *  - DNS is resolved BEFORE issuing the request and rejected if it points to
 *    private/loopback/link-local IPs. Defends against SSRF (LLM tricked into
 *    pointing at a router admin page or cloud metadata endpoint).
 *  - Body size capped: 64 KB outgoing, 256 KB incoming.
 *  - Each call passes through SecurityGate.YELLOW so the user must approve.
 *    Once C-03 is wired up, this is a real prompt to the user.
 */
class WebApiFetcherPlugin : EmmaPlugin {
    override val id: String = "fetch_external_api"
    private val TAG = "WebApiFetcherPlugin"

    companion object {
        private const val MAX_REQUEST_BODY_BYTES = 64 * 1024
        private const val MAX_RESPONSE_BYTES = 256 * 1024
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000
    }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Ejecutor de API/Fetch HTTPS. Úsalo cuando el usuario diga 'pégale a esta API', 'trae datos de mi servidor' o dicte una URL REST. Solo HTTPS y URLs públicas; las IP privadas/loopback se rechazan. La acción requiere confirmación del usuario.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("endpoint_url", JSONObject().apply {
                        put("type", "string")
                        put("description", "URL absoluta HTTPS, ej: https://api.example.com/v1/ping")
                    })
                    put("method", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().put("GET").put("POST"))
                        put("description", "GET o POST")
                    })
                    put("json_body", JSONObject().apply {
                        put("type", "string")
                        put("description", "(Solo POST) JSON crudo para el body. Máx 64 KB.")
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

        // 1) Static URL validation.
        val rejection = validateUrl(endpoint)
        if (rejection != null) {
            Log.w(TAG, "URL rechazada: $rejection")
            return "URL rechazada por la capa de seguridad: $rejection"
        }

        // 2) Body size cap.
        if (method == "POST" && jsonBody != null && jsonBody.toByteArray(Charsets.UTF_8).size > MAX_REQUEST_BODY_BYTES) {
            return "Body demasiado grande (>64 KB). Reduce el payload."
        }

        // 3) DNS check — reject if host resolves to a private/loopback/link-local IP.
        val parsedUrl = URL(endpoint)
        val dnsRejection = validateResolvedAddresses(parsedUrl.host)
        if (dnsRejection != null) {
            Log.w(TAG, "DNS rechazado: $dnsRejection")
            return "Host rechazado: $dnsRejection"
        }

        // 4) Ask the user before each external call (YELLOW).
        val gateOp = SecurityGate.yellow(
            id, "fetch_external_api",
            "E.M.M.A. quiere hacer $method a:\n${parsedUrl.host}\n\nRuta: ${parsedUrl.path.ifBlank { "/" }}"
        )
        if (!SecurityGate.evaluate(gateOp)) {
            return "Llamada cancelada por el usuario."
        }

        Log.i(TAG, "$method ${parsedUrl.host}${parsedUrl.path}")

        return withContext(Dispatchers.IO) {
            var connection: HttpsURLConnection? = null
            try {
                connection = (parsedUrl.openConnection() as HttpsURLConnection).apply {
                    requestMethod = method
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false  // SSRF guard: don't follow redirects to private IPs.
                }

                if (method == "POST" && !jsonBody.isNullOrEmpty()) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.doOutput = true
                    connection.outputStream.use { os ->
                        os.write(jsonBody.toByteArray(Charsets.UTF_8))
                    }
                }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseString = stream?.let { input ->
                    BufferedReader(InputStreamReader(input)).use { reader ->
                        // Read up to MAX_RESPONSE_BYTES then stop.
                        val sb = StringBuilder()
                        val buf = CharArray(8 * 1024)
                        var total = 0
                        while (true) {
                            val n = reader.read(buf)
                            if (n <= 0) break
                            total += n
                            if (total > MAX_RESPONSE_BYTES) {
                                sb.append(buf, 0, n.coerceAtMost(MAX_RESPONSE_BYTES - (total - n)))
                                sb.append("\n... [Truncado: respuesta excedió 256 KB]")
                                break
                            }
                            sb.append(buf, 0, n)
                        }
                        sb.toString()
                    }
                } ?: ""

                "API Fetch Completado (Status $status):\n\n$responseString"
            } catch (e: Exception) {
                Log.e(TAG, "Rotura API", e)
                "Catástrofe de Red Síncrona: ${e.javaClass.simpleName}: ${e.message}"
            } finally {
                connection?.disconnect()
            }
        }
    }

    // ── URL validation ──

    private fun validateUrl(rawUrl: String): String? {
        val url = try { URL(rawUrl) } catch (_: Throwable) { return "no se pudo parsear la URL" }
        val scheme = url.protocol.lowercase()
        if (scheme != "https") return "solo se permite HTTPS (recibido: $scheme)"
        val host = url.host?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return "URL sin host"
        if (host == "localhost" || host.endsWith(".localhost") || host == "ip6-localhost") {
            return "host bloqueado: localhost"
        }
        return null
    }

    private fun validateResolvedAddresses(host: String): String? {
        return try {
            val addresses = InetAddress.getAllByName(host)
            for (addr in addresses) {
                if (addr.isLoopbackAddress) return "$host resolvió a loopback ${addr.hostAddress}"
                if (addr.isAnyLocalAddress) return "$host resolvió a 0.0.0.0/::"
                if (addr.isLinkLocalAddress) return "$host resolvió a link-local ${addr.hostAddress}"
                if (addr.isSiteLocalAddress) return "$host resolvió a IP privada ${addr.hostAddress}"
                // Also block 169.254.169.254 specifically (cloud metadata).
                val text = addr.hostAddress
                if (text == "169.254.169.254") return "host de metadata de cloud bloqueado"
            }
            null
        } catch (e: Exception) {
            "no se pudo resolver $host: ${e.message}"
        }
    }
}
