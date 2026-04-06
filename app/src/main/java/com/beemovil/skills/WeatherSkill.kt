package com.beemovil.skills

import com.beemovil.network.BeeHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WeatherSkill — get current weather using Open-Meteo (free, no API key).
 * Uses GPS coordinates from the phone.
 */
class WeatherSkill : BeeSkill {
    override val name = "weather"
    override val description = "Get current weather. Provide 'latitude' and 'longitude', or 'city' name. Uses free Open-Meteo API."
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "latitude":{"type":"number","description":"GPS latitude"},
            "longitude":{"type":"number","description":"GPS longitude"},
            "city":{"type":"string","description":"City name (will geocode)"}
        }}
    """.trimIndent())

    private val client = BeeHttpClient.default

    override fun execute(params: JSONObject): JSONObject {
        var lat = params.optDouble("latitude", Double.NaN)
        var lon = params.optDouble("longitude", Double.NaN)
        val city = params.optString("city", "")

        // If city provided, geocode it
        if ((lat.isNaN() || lon.isNaN()) && city.isNotBlank()) {
            val geo = geocode(city) ?: return JSONObject().put("error", "No se encontró: $city")
            lat = geo.first
            lon = geo.second
        }

        if (lat.isNaN() || lon.isNaN()) {
            return JSONObject().put("error", "Necesito coordenadas GPS o nombre de ciudad. Usa el skill 'connectivity' con action 'location' primero para obtener tu GPS.")
        }

        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return JSONObject().put("error", "Empty response")
            val json = JSONObject(body)

            val current = json.optJSONObject("current") ?: return JSONObject().put("error", "No weather data")
            val temp = current.optDouble("temperature_2m", 0.0)
            val feelsLike = current.optDouble("apparent_temperature", 0.0)
            val humidity = current.optInt("relative_humidity_2m", 0)
            val windSpeed = current.optDouble("wind_speed_10m", 0.0)
            val weatherCode = current.optInt("weather_code", 0)

            val condition = weatherCodeToText(weatherCode)
            val emoji = weatherCodeToEmoji(weatherCode)

            val result = JSONObject()
                .put("temperature", temp)
                .put("feels_like", feelsLike)
                .put("humidity", humidity)
                .put("wind_speed", windSpeed)
                .put("condition", condition)
                .put("emoji", emoji)
                .put("message", "$emoji $condition, ${temp}°C (se siente ${feelsLike}°C)")

            // Add 3-day forecast
            val daily = json.optJSONObject("daily")
            if (daily != null) {
                val dates = daily.optJSONArray("time")
                val maxTemps = daily.optJSONArray("temperature_2m_max")
                val minTemps = daily.optJSONArray("temperature_2m_min")
                val codes = daily.optJSONArray("weather_code")

                val forecast = org.json.JSONArray()
                for (i in 0 until minOf(3, dates?.length() ?: 0)) {
                    forecast.put(JSONObject().apply {
                        put("date", dates?.getString(i) ?: "")
                        put("max", maxTemps?.optDouble(i, 0.0) ?: 0.0)
                        put("min", minTemps?.optDouble(i, 0.0) ?: 0.0)
                        put("condition", weatherCodeToText(codes?.optInt(i, 0) ?: 0))
                        put("emoji", weatherCodeToEmoji(codes?.optInt(i, 0) ?: 0))
                    })
                }
                result.put("forecast", forecast)
            }

            result
        } catch (e: Exception) {
            JSONObject().put("error", "Weather error: ${e.message}")
        }
    }

    private fun geocode(city: String): Pair<Double, Double>? {
        return try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${city.replace(" ", "+")}&count=1"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            Pair(first.getDouble("latitude"), first.getDouble("longitude"))
        } catch (_: Exception) { null }
    }

    private fun weatherCodeToText(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Principalmente despejado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55 -> "Llovizna"
        61, 63, 65 -> "Lluvia"
        66, 67 -> "Lluvia helada"
        71, 73, 75 -> "Nieve"
        77 -> "Granizo"
        80, 81, 82 -> "Chubascos"
        85, 86 -> "Nieve intensa"
        95 -> "Tormenta"
        96, 99 -> "Tormenta con granizo"
        else -> "Desconocido"
    }

    private fun weatherCodeToEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 61, 63, 65 -> "🌧️"
        66, 67 -> "🌨️"
        71, 73, 75, 77 -> "❄️"
        80, 81, 82 -> "🌦️"
        85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
}
