package com.beemovil.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlinx.coroutines.tasks.await

class EnvironmentScanner(private val context: Context) {

    fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }
    }

    fun getNetworkStatus(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "Sin Conexión"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Red Inestable"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi Activo"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Heuristica basica
                "Datos Móviles (LTE/5G)"
            }
            else -> "Señal Desconocida"
        }
    }

    suspend fun getCurrentLocation(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }
        try {
            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            val locationTask = fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
            val location = locationTask.await()
            if (location != null) {
                return@withContext Pair(location.latitude, location.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun getSemanticLocation(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val neighborhood = address.subLocality ?: address.locality ?: address.featureName ?: "Zona Desconocida"
                val city = address.adminArea ?: address.countryName ?: ""
                return@withContext "📍 $neighborhood, $city"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext "🛰️ Triangulando coordenadas..."
    }
    suspend fun fetchWeather(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(response)
                val current = json.getJSONObject("current_weather")
                val temp = current.getDouble("temperature").toInt()
                val code = current.getInt("weathercode")
                
                val (desc, emoji) = when(code) {
                    0, 1 -> "Soleado" to "☀️"
                    2 -> "Parcialmente Nublado" to "⛅"
                    3 -> "Nublado Mayormente" to "☁️"
                    45, 48 -> "Niebla" to "🌫️"
                    51, 53, 55, 61, 63, 65 -> "Lluvia" to "🌧️"
                    71, 73, 75 -> "Nieve" to "❄️"
                    95, 96, 99 -> "Tormenta" to "⛈️"
                    else -> "Despejado" to "☀️"
                }
                return@withContext "$emoji $desc ($temp°C)"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext "🌤️ Clima No Disponible (--°C)"
    }
}
