package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * CurrencyPlugin — Real-time exchange rates from Frankfurter API (100% free, no API key).
 *
 * Converts between 30+ currencies with live rates from the European Central Bank.
 *
 * API: https://api.frankfurter.app/
 * Limits: Unlimited
 * Auth: None required
 */
class CurrencyPlugin(private val context: Context) : EmmaPlugin {

    override val id = "convert_currency"
    private val TAG = "CurrencyPlugin"

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Convierte monedas usando tasas de cambio reales del día (Banco Central Europeo). " +
                    "Úsalo cuando el usuario pregunte: '¿Cuánto es X USD en MXN?', '¿A cuánto está el dólar?', " +
                    "'Convierte 500 EUR a COP'. Monedas soportadas: USD, EUR, MXN, COP, BRL, ARS, GBP, JPY, CNY, CAD, CLP, PEN, y más.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("amount", JSONObject().apply {
                        put("type", "number")
                        put("description", "Cantidad a convertir. Ejemplo: 100")
                    })
                    put("from", JSONObject().apply {
                        put("type", "string")
                        put("description", "Código ISO de moneda origen (3 letras). Ejemplo: USD, EUR, MXN")
                    })
                    put("to", JSONObject().apply {
                        put("type", "string")
                        put("description", "Código(s) ISO de moneda destino, separados por coma. Ejemplo: 'MXN' o 'MXN,EUR,COP'")
                    })
                })
                put("required", JSONArray().apply {
                    put("amount")
                    put("from")
                    put("to")
                })
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val amount = (args["amount"] as? Number)?.toDouble() ?: return "ERROR_TOOL_FAILED: Falta el monto."
        val from = (args["from"] as? String)?.uppercase()?.trim() ?: return "ERROR_TOOL_FAILED: Falta la moneda origen."
        val to = (args["to"] as? String)?.uppercase()?.trim() ?: return "ERROR_TOOL_FAILED: Falta la moneda destino."

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.frankfurter.app/latest?amount=$amount&from=$from&to=$to"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "E.M.M.A. AI/7.2")
                    .build()

                val response = http.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext "ERROR_TOOL_FAILED: Frankfurter respondió con HTTP ${response.code}. " +
                            "Verifica que los códigos de moneda sean válidos (USD, EUR, MXN, COP, etc.)"
                }

                val json = JSONObject(response.body?.string() ?: return@withContext "ERROR_TOOL_FAILED: Respuesta vacía")

                val rates = json.optJSONObject("rates")
                val date = json.optString("date", "hoy")

                if (rates == null || rates.length() == 0) {
                    return@withContext "ERROR_TOOL_FAILED: No se encontraron tasas para $from → $to"
                }

                buildString {
                    appendLine("💱 Tipo de cambio ($date):")
                    appendLine()
                    val iterator = rates.keys()
                    while (iterator.hasNext()) {
                        val currency = iterator.next()
                        val converted = rates.optDouble(currency, 0.0)
                        val rate = if (amount != 0.0) converted / amount else 0.0
                        appendLine("  ${"%.2f".format(amount)} $from = ${"%.2f".format(converted)} $currency")
                        appendLine("  (1 $from = ${"%.4f".format(rate)} $currency)")
                        if (iterator.hasNext()) appendLine()
                    }
                    appendLine()
                    appendLine("📊 Fuente: Banco Central Europeo (ECB)")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Currency conversion error: ${e.message}", e)
                "ERROR_TOOL_FAILED: Error convirtiendo moneda: ${e.message}"
            }
        }
    }
}
