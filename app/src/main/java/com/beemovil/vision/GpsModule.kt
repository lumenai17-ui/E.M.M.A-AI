package com.beemovil.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * GpsModule — Provides real-time GPS data for Vision Pro.
 *
 * Features:
 * - Lat/lng/alt/speed/bearing
 * - Geocoded address (reverse lookup)
 * - Formatted string for AI prompt injection
 * - Cardinal direction from bearing
 */
class GpsModule(private val context: Context) {

    companion object {
        private const val TAG = "GpsModule"
        private const val MIN_TIME_MS = 2000L     // Update every 2s
        private const val MIN_DISTANCE_M = 1f      // Update every 1m
    }

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var geocoder: Geocoder? = null

    var currentData = GpsData()
        private set

    var onLocationUpdate: ((GpsData) -> Unit)? = null

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!hasPermission) {
            Log.w(TAG, "No GPS permission")
            return
        }

        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale("es")) else null

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val address = try {
                    geocoder?.getFromLocation(location.latitude, location.longitude, 1)
                        ?.firstOrNull()?.let { addr ->
                            buildString {
                                addr.thoroughfare?.let { append(it) }
                                addr.subLocality?.let { append(", $it") }
                                addr.locality?.let { append(", $it") }
                            }.ifBlank { "" }
                        } ?: ""
                } catch (e: Exception) {
                    ""
                }

                currentData = GpsData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    speed = location.speed,
                    bearing = location.bearing,
                    accuracy = location.accuracy,
                    address = address,
                    timestamp = location.time
                )

                onLocationUpdate?.invoke(currentData)
            }

            @Deprecated("Deprecated in API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            // Try GPS first, then network
            val provider = when {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ->
                    LocationManager.GPS_PROVIDER
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }

            if (provider != null) {
                locationManager?.requestLocationUpdates(
                    provider, MIN_TIME_MS, MIN_DISTANCE_M, locationListener!!
                )
                // Get last known immediately
                locationManager?.getLastKnownLocation(provider)?.let {
                    locationListener?.onLocationChanged(it)
                }
                Log.i(TAG, "GPS started ($provider)")
            } else {
                Log.w(TAG, "No GPS provider available")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "GPS security error: ${e.message}")
        }
    }

    fun stop() {
        locationListener?.let { locationManager?.removeUpdates(it) }
        locationListener = null
        locationManager = null
        Log.i(TAG, "GPS stopped")
    }
}
