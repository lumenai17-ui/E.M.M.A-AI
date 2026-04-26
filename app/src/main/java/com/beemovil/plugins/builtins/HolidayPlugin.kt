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
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * HolidayPlugin — Public holidays from Nager.Date API (100% free, no API key).
 *
 * Supports 100+ countries with national and regional holidays.
 *
 * API: https://date.nager.at/
 * Limits: Unlimited
 * Auth: None required
 */
class HolidayPlugin(private val context: Context) : EmmaPlugin {

    override val id = "get_holidays"
    private val TAG = "HolidayPlugin"

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Consulta los feriados y días festivos nacionales de cualquier país. " +
                    "Úsalo cuando el usuario pregunte: '¿Es feriado hoy?', '¿Cuándo es el próximo feriado?', " +
                    "'¿Qué feriados hay en diciembre?', '¿Mañana se trabaja?'. " +
                    "Códigos de país: MX (México), CO (Colombia), US, AR, CL, PE, EC, ES, BR, CR, etc.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("country_code", JSONObject().apply {
                        put("type", "string")
                        put("description", "Código ISO 3166-1 alpha-2 del país (2 letras). Ej: MX, CO, US, AR, CL, PE")
                    })
                    put("year", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Año a consultar. Default: año actual.")
                    })
                })
                put("required", JSONArray().apply {
                    put("country_code")
                })
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val countryCode = (args["country_code"] as? String)?.uppercase()?.trim()
            ?: return "ERROR_TOOL_FAILED: Falta el código de país."
        val year = (args["year"] as? Number)?.toInt() ?: LocalDate.now().year

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://date.nager.at/api/v3/publicholidays/$year/$countryCode"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "E.M.M.A. AI/7.2")
                    .build()

                val response = http.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext if (response.code == 404) {
                        "No se encontraron feriados para el país '$countryCode'. Verifica el código de país."
                    } else {
                        "ERROR_TOOL_FAILED: Nager.Date respondió con HTTP ${response.code}"
                    }
                }

                val holidays = JSONArray(response.body?.string() ?: return@withContext "ERROR_TOOL_FAILED: Respuesta vacía")

                if (holidays.length() == 0) {
                    return@withContext "No hay feriados registrados para $countryCode en $year."
                }

                val today = LocalDate.now()
                val countryName = getCountryName(countryCode)

                // Find next upcoming holiday
                var nextHoliday: JSONObject? = null
                for (i in 0 until holidays.length()) {
                    val h = holidays.getJSONObject(i)
                    val date = LocalDate.parse(h.optString("date"))
                    if (!date.isBefore(today)) {
                        nextHoliday = h
                        break
                    }
                }

                // Check if today is a holiday
                val todayHoliday = (0 until holidays.length())
                    .map { holidays.getJSONObject(it) }
                    .firstOrNull { LocalDate.parse(it.optString("date")).isEqual(today) }

                buildString {
                    appendLine("📅 Feriados de $countryName ($countryCode) — $year:")
                    appendLine()

                    // Today check
                    if (todayHoliday != null) {
                        appendLine("🎉 ¡HOY ES FERIADO! ${todayHoliday.optString("localName")} (${todayHoliday.optString("name")})")
                        appendLine()
                    }

                    // Next upcoming
                    if (nextHoliday != null && todayHoliday == null) {
                        val nextDate = nextHoliday.optString("date")
                        val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.parse(nextDate))
                        appendLine("⏭️ Próximo feriado (en $daysUntil días):")
                        appendLine("  📌 ${nextHoliday.optString("localName")} — $nextDate")
                        appendLine("  🌐 ${nextHoliday.optString("name")}")
                        appendLine()
                    }

                    // Full list (remaining)
                    appendLine("📋 Lista completa ($year):")
                    for (i in 0 until holidays.length()) {
                        val h = holidays.getJSONObject(i)
                        val date = h.optString("date", "")
                        val localName = h.optString("localName", "")
                        val name = h.optString("name", "")
                        val hDate = LocalDate.parse(date)
                        val marker = when {
                            hDate.isEqual(today) -> " ← HOY"
                            hDate.isBefore(today) -> " (pasado)"
                            else -> ""
                        }
                        appendLine("  • $date: $localName ($name)$marker")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Holiday lookup error: ${e.message}", e)
                "ERROR_TOOL_FAILED: Error consultando feriados: ${e.message}"
            }
        }
    }

    private fun getCountryName(code: String): String = when (code) {
        "MX" -> "México"
        "CO" -> "Colombia"
        "US" -> "Estados Unidos"
        "AR" -> "Argentina"
        "CL" -> "Chile"
        "PE" -> "Perú"
        "EC" -> "Ecuador"
        "BR" -> "Brasil"
        "CR" -> "Costa Rica"
        "ES" -> "España"
        "PA" -> "Panamá"
        "UY" -> "Uruguay"
        "PY" -> "Paraguay"
        "BO" -> "Bolivia"
        "VE" -> "Venezuela"
        "GT" -> "Guatemala"
        "HN" -> "Honduras"
        "SV" -> "El Salvador"
        "NI" -> "Nicaragua"
        "DO" -> "República Dominicana"
        "CU" -> "Cuba"
        "NZ" -> "Nueva Zelanda"
        "DE" -> "Alemania"
        "FR" -> "Francia"
        "IT" -> "Italia"
        "GB" -> "Reino Unido"
        "CA" -> "Canadá"
        "JP" -> "Japón"
        "CN" -> "China"
        "AU" -> "Australia"
        else -> code
    }
}
