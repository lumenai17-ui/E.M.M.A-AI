package com.beemovil.skills

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import org.json.JSONObject
import java.util.Locale

/**
 * ConnectivitySkill — get WiFi, network, and location info.
 */
class ConnectivitySkill(private val context: Context) : BeeSkill {
    override val name = "connectivity"
    override val description = "Get network/WiFi/location info. Actions: 'wifi' (WiFi details), 'network' (connection status), 'location' (last known GPS position)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["wifi","network","location"]}
        },"required":["action"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "network")

        return when (action) {
            "wifi" -> getWifiInfo()
            "network" -> getNetworkInfo()
            "location" -> getLocationInfo()
            else -> JSONObject().put("error", "Action not supported")
        }
    }

    private fun getWifiInfo(): JSONObject {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            JSONObject()
                .put("ssid", info.ssid?.replace("\"", "") ?: "Unknown")
                .put("rssi", info.rssi)
                .put("signal_strength", WifiManager.calculateSignalLevel(info.rssi, 5))
                .put("link_speed", "${info.linkSpeed} Mbps")
                .put("ip", intToIp(info.ipAddress))
                .put("enabled", wifiManager.isWifiEnabled)
        } catch (e: Exception) {
            JSONObject().put("error", "Cannot read WiFi: ${e.message}")
        }
    }

    private fun getNetworkInfo(): JSONObject {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return try {
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            JSONObject()
                .put("connected", network != null)
                .put("type", when {
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                    else -> "None"
                })
                .put("has_internet", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                .put("metered", !caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)!!)
        } catch (e: Exception) {
            JSONObject().put("connected", false).put("error", e.message)
        }
    }

    private fun getLocationInfo(): JSONObject {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)

            if (!isGpsEnabled) {
                return JSONObject()
                    .put("gps_enabled", false)
                    .put("message", "GPS desactivado. Actívalo en configuración del teléfono.")
            }

            // Try last known location (doesn't need active GPS fix)
            @Suppress("MissingPermission")
            val loc = try {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: SecurityException) { null }

            if (loc != null) {
                val result = JSONObject()
                    .put("latitude", loc.latitude)
                    .put("longitude", loc.longitude)
                    .put("accuracy", loc.accuracy)
                    .put("altitude", loc.altitude)
                    .put("gps_enabled", true)

                // Try reverse geocoding
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        result.put("address", addr.getAddressLine(0) ?: "")
                        result.put("city", addr.locality ?: "")
                        result.put("country", addr.countryName ?: "")
                    }
                } catch (_: Exception) {}

                result
            } else {
                JSONObject()
                    .put("gps_enabled", true)
                    .put("message", "No se pudo obtener ubicación. Necesita permiso ACCESS_FINE_LOCATION.")
            }
        } catch (e: Exception) {
            JSONObject().put("error", "Location error: ${e.message}")
        }
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
