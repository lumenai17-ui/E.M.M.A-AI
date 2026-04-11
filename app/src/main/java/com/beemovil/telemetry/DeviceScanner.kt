package com.beemovil.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

import android.location.Location
import android.location.LocationManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class DeviceState(
    val batteryPercent: Int,
    val batteryTemp: Float, // En grados Celsius puros
    val wifiSSID: String,
    val telephonyOperator: String,
    val volumeLevel: Int,
    val ambientLux: Float,
    val totalSteps: Float,
    val latitude: Double?,
    val longitude: Double?
)

class DeviceScanner(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    suspend fun getCurrentDeviceState(): DeviceState {
        val location = getLastLocation()
        return DeviceState(
            batteryPercent = getBatteryPercentage(),
            batteryTemp = getBatteryTemperature(),
            wifiSSID = getWifiSSID(),
            telephonyOperator = getTelephonyOperator(),
            volumeLevel = getVolumeLevel(),
            ambientLux = getSensorValue(Sensor.TYPE_LIGHT, 500L) ?: -1f,
            totalSteps = getSensorValue(Sensor.TYPE_STEP_COUNTER, 500L) ?: -1f,
            latitude = location?.latitude,
            longitude = location?.longitude
        )
    }

    private fun getLastLocation(): Location? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        
        return try {
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val l = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
            bestLocation
        } catch (e: Exception) {
            null
        }
    }

    private fun getBatteryPercentage(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level == -1 || scale == -1) 0 else (level * 100 / scale)
    }

    private fun getBatteryTemperature(): Float {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val tempTenths: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (tempTenths == -1) 0f else (tempTenths / 10.0f)
    }

    private fun getTelephonyOperator(): String {
        return try {
            val operatorName = telephonyManager.networkOperatorName
            if (operatorName.isNullOrBlank()) "No Network" else operatorName
        } catch (e: Exception) {
            "Permission/Error"
        }
    }

    private fun getWifiSSID(): String {
        return try {
            val info = wifiManager.connectionInfo
            val ssid = info.ssid
            if (ssid == WifiManager.UNKNOWN_SSID || ssid.isNullOrBlank()) "Disconnected" else ssid.replace("\"", "")
        } catch (e: Exception) {
            "Permission/Error"
        }
    }

    private fun getVolumeLevel(): Int {
        return try {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            0
        }
    }

    // Usamos Coroutines suspender para esperar el primer valor válido del SensorManager con timeout.
    private suspend fun getSensorValue(sensorType: Int, timeoutMs: Long): Float? = suspendCancellableCoroutine { cont ->
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        var listener: SensorEventListener? = null
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.values.isNotEmpty()) {
                    sensorManager.unregisterListener(this)
                    if (cont.isActive) {
                        cont.resume(event.values[0])
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        
        // Timeout handling can be managed externally or by attaching an invokeOnCancellation
        cont.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
        }
    }
}
