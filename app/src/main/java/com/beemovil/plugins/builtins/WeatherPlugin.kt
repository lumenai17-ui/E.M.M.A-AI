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
 * WeatherPlugin — Real-time weather data from Open-Meteo (100% free, no API key).
 *
 * Provides current weather, forecast, and UV index for any location.
 * Uses E.M.M.A.'s GPS coordinates or user-specified city names.
 *
 * API: https://open-meteo.com/
 * Limits: 10,000 requests/day (very generous)
 * Auth: None required
 */
class WeatherPlugin(private val context: Context) : EmmaPlugin {

    override val id = "get_weather"
    private val TAG = "WeatherPlugin"

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Obtiene el clima actual y pronóstico para una ubicación usando coordenadas GPS. " +
                    "Úsalo cuando el usuario pregunte: '¿Qué clima hace?', '¿Va a llover?', '¿Qué temperatura hay en [ciudad]?', " +
                    "'¿Necesito paraguas?'. Devuelve temperatura, humedad, viento, condición climática y pronóstico de 3 días.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("latitude", JSONObject().apply {
                        put("type", "number")
                        put("description", "Latitud de la ubicación. Si el usuario no especifica ciudad, usa la ubicación actual del GPS.")
                    })
                    put("longitude", JSONObject().apply {
                        put("type", "number")
                        put("description", "Longitud de la ubicación.")
                    })
                    put("city_name", JSONObject().apply {
                        put("type", "string")
                        put("description", "Nombre de la ciudad (opcional, para display). Si el usuario dice una ciudad, primero busca sus coordenadas con geocoding.")
                    })
                })
                put("required", JSONArray().apply {
                    put("latitude")
                    put("longitude")
                })
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val lat = (args["latitude"] as? Number)?.toDouble() ?: return "ERROR_TOOL_FAILED: Falta la latitud."
        val lon = (args["longitude"] as? Number)?.toDouble() ?: return "ERROR_TOOL_FAILED: Falta la longitud."
        val cityName = args["city_name"] as? String ?: ""

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?" +
                        "latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,uv_index" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
                        "&timezone=auto&forecast_days=3"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "E.M.M.A. AI/7.2")
                    .build()

                val response = http.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext "ERROR_TOOL_FAILED: Open-Meteo respondió con HTTP ${response.code}"
                }

                val json = JSONObject(response.body?.string() ?: return@withContext "ERROR_TOOL_FAILED: Respuesta vacía")

                val current = json.getJSONObject("current")
                val daily = json.getJSONObject("daily")

                val temp = current.optDouble("temperature_2m", 0.0)
                val feelsLike = current.optDouble("apparent_temperature", 0.0)
                val humidity = current.optInt("relative_humidity_2m", 0)
                val windSpeed = current.optDouble("wind_speed_10m", 0.0)
                val uvIndex = current.optDouble("uv_index", 0.0)
                val weatherCode = current.optInt("weather_code", 0)
                val condition = weatherCodeToText(weatherCode)

                val location = if (cityName.isNotBlank()) cityName else "Lat: $lat, Lon: $lon"

                // Build forecast
                val forecastDays = daily.optJSONArray("time")
                val maxTemps = daily.optJSONArray("temperature_2m_max")
                val minTemps = daily.optJSONArray("temperature_2m_min")
                val precipProb = daily.optJSONArray("precipitation_probability_max")
                val dailyCodes = daily.optJSONArray("weather_code")

                val forecast = buildString {
                    if (forecastDays != null && maxTemps != null && minTemps != null) {
                        for (i in 0 until minOf(forecastDays.length(), 3)) {
                            val day = forecastDays.optString(i, "")
                            val max = maxTemps.optDouble(i, 0.0)
                            val min = minTemps.optDouble(i, 0.0)
                            val rain = precipProb?.optInt(i, 0) ?: 0
                            val code = dailyCodes?.optInt(i, 0) ?: 0
                            appendLine("  $day: ${weatherCodeToEmoji(code)} ${min.toInt()}°-${max.toInt()}°C, lluvia: ${rain}%")
                        }
                    }
                }

                buildString {
                    appendLine("🌍 Clima en $location:")
                    appendLine("${weatherCodeToEmoji(weatherCode)} $condition")
                    appendLine("🌡️ Temperatura: ${temp.toInt()}°C (sensación: ${feelsLike.toInt()}°C)")
                    appendLine("💧 Humedad: $humidity%")
                    appendLine("💨 Viento: ${windSpeed.toInt()} km/h")
                    appendLine("☀️ Índice UV: ${uvIndex.toInt()}")
                    if (forecast.isNotBlank()) {
                        appendLine()
                        appendLine("📅 Pronóstico 3 días:")
                        append(forecast)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Weather fetch error: ${e.message}", e)
                "ERROR_TOOL_FAILED: Error obteniendo clima: ${e.message}"
            }
        }
    }

    private fun weatherCodeToText(code: Int): String = when (code) {
        0 -> "Cielo despejado"
        1 -> "Mayormente despejado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        51, 53, 55 -> "Llovizna"
        56, 57 -> "Llovizna helada"
        61, 63, 65 -> "Lluvia"
        66, 67 -> "Lluvia helada"
        71, 73, 75 -> "Nieve"
        77 -> "Granizo"
        80, 81, 82 -> "Chubascos"
        85, 86 -> "Chubascos de nieve"
        95 -> "Tormenta eléctrica"
        96, 99 -> "Tormenta con granizo"
        else -> "Desconocido"
    }

    private fun weatherCodeToEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67 -> "🌧️"
        71, 73, 75, 77 -> "🌨️"
        80, 81, 82 -> "🌧️"
        85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
}
