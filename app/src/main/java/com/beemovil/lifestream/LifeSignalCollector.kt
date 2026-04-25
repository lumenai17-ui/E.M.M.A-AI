package com.beemovil.lifestream

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * LifeSignalCollector — Periodic Life Signal Collection
 *
 * Collects environmental signals every 15-30 minutes via WorkManager:
 * - GPS location snapshot (FusedLocationProvider)
 * - Step counter (hardware sensor, cumulative)
 * - Battery level + charging state
 * - Network connectivity (WiFi/Cellular/None)
 *
 * All signals are saved to LifeStreamDB for Emma to query.
 * Runs as a periodic WorkManager job — survives app kill.
 */
class LifeSignalCollector(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "LifeSignalCollector"
        private const val WORK_NAME = "lifestream_signal_collector"
        private const val INTERVAL_MINUTES = 15L

        /**
         * Schedule the periodic collector. Call once on app start.
         * WorkManager handles deduplication via ExistingPeriodicWorkPolicy.KEEP.
         */
        fun schedule(context: Context) {
            if (!LifeStreamManager.isEnabled(context)) {
                Log.d(TAG, "LifeStream disabled — not scheduling collector")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false) // Collect even on low battery
                .build()

            val request = PeriodicWorkRequestBuilder<LifeSignalCollector>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES) // Start after 1 min
                .addTag("lifestream")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.i(TAG, "✅ Signal collector scheduled every ${INTERVAL_MINUTES}min")
        }

        /**
         * Cancel the periodic collector.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Signal collector cancelled")
        }

        /**
         * Run a one-shot collection immediately (for testing/debugging).
         */
        fun collectNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<LifeSignalCollector>()
                .addTag("lifestream_ondemand")
                .build()
            WorkManager.getInstance(context).enqueue(request)
            Log.i(TAG, "One-shot collection triggered")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!LifeStreamManager.isEnabled(applicationContext)) {
            Log.d(TAG, "LifeStream disabled — skipping collection")
            return@withContext Result.success()
        }

        Log.d(TAG, "Starting signal collection...")
        var collected = 0

        try {
            // 1. GPS Location
            if (collectLocation()) collected++

            // 2. Step Counter
            if (collectSteps()) collected++

            // 3. Battery + Connectivity (always available)
            if (collectBattery()) collected++
            if (collectConnectivity()) collected++

            // 4. Purge expired entries (piggyback on periodic job)
            val purged = LifeStreamManager.purgeExpired(applicationContext)

            Log.i(TAG, "✅ Collection complete: $collected signals, $purged purged")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Collection failed: ${e.message}", e)
            Result.retry()
        }
    }

    // ═══════════════════════════════════════
    // GPS LOCATION
    // ═══════════════════════════════════════

    private suspend fun collectLocation(): Boolean {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "GPS: no permission")
            return false
        }

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)

            // Try getCurrentLocation first (more reliable than lastLocation)
            val locationTask = fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, null
            )
            val location = Tasks.await(locationTask, 10, TimeUnit.SECONDS)

            if (location != null) {
                val address = reverseGeocode(location.latitude, location.longitude)

                val entry = LifeStreamManager.locationEntry(
                    lat = location.latitude,
                    lng = location.longitude,
                    address = address,
                    accuracy = location.accuracy
                )
                LifeStreamManager.logSync(applicationContext, entry)
                Log.d(TAG, "GPS: ${location.latitude}, ${location.longitude} → $address")
                true
            } else {
                // Fallback to last known location
                val lastLocation = Tasks.await(fusedClient.lastLocation, 5, TimeUnit.SECONDS)
                if (lastLocation != null) {
                    val address = reverseGeocode(lastLocation.latitude, lastLocation.longitude)
                    val entry = LifeStreamManager.locationEntry(
                        lat = lastLocation.latitude,
                        lng = lastLocation.longitude,
                        address = address,
                        accuracy = lastLocation.accuracy
                    )
                    LifeStreamManager.logSync(applicationContext, entry)
                    Log.d(TAG, "GPS (last): ${lastLocation.latitude}, ${lastLocation.longitude}")
                    true
                } else {
                    Log.d(TAG, "GPS: no location available")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GPS collection failed: ${e.message}")
            false
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String {
        return try {
            if (!Geocoder.isPresent()) return ""
            val geocoder = Geocoder(applicationContext, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                buildString {
                    addr.locality?.let { append(it) }
                    addr.adminArea?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    addr.countryName?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                }.ifBlank {
                    addr.getAddressLine(0) ?: ""
                }
            } else ""
        } catch (e: Exception) {
            Log.w(TAG, "Geocode failed: ${e.message}")
            ""
        }
    }

    // ═══════════════════════════════════════
    // STEP COUNTER
    // ═══════════════════════════════════════

    private fun collectSteps(): Boolean {
        return try {
            val sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                ?: return false

            val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                ?: run {
                    Log.d(TAG, "Steps: no step counter sensor")
                    return false
                }

            // Read current cumulative step count via a one-shot listener
            var steps = -1f
            val latch = java.util.concurrent.CountDownLatch(1)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
                        steps = event.values[0]
                        latch.countDown()
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            latch.await(3, TimeUnit.SECONDS)
            sensorManager.unregisterListener(listener)

            if (steps >= 0) {
                // Calculate daily steps
                val prefs = applicationContext.getSharedPreferences("lifestream_sensors", Context.MODE_PRIVATE)
                val lastReset = prefs.getLong("step_reset_date", 0L)
                val today = System.currentTimeMillis() / 86_400_000L // Day number

                val dailySteps: Int
                if (today != lastReset / 86_400_000L) {
                    // New day: save baseline
                    prefs.edit()
                        .putFloat("step_baseline", steps)
                        .putLong("step_reset_date", System.currentTimeMillis())
                        .apply()
                    dailySteps = 0
                } else {
                    val baseline = prefs.getFloat("step_baseline", steps)
                    dailySteps = (steps - baseline).toInt().coerceAtLeast(0)
                }

                val entry = LifeStreamManager.sensorEntry(
                    source = "steps",
                    title = "Pasos del día",
                    value = "$dailySteps pasos",
                    metadata = """{"cumulative":${steps.toInt()},"daily":$dailySteps}"""
                )
                LifeStreamManager.logSync(applicationContext, entry)
                Log.d(TAG, "Steps: $dailySteps today (cumulative: ${steps.toInt()})")
                true
            } else {
                Log.d(TAG, "Steps: sensor timeout")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Steps collection failed: ${e.message}")
            false
        }
    }

    // ═══════════════════════════════════════
    // BATTERY
    // ═══════════════════════════════════════

    private fun collectBattery(): Boolean {
        return try {
            val batteryIntent = applicationContext.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            if (batteryIntent != null) {
                val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val percent = (level * 100) / scale
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val chargeType = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "None"
                }

                val entry = LifeStreamManager.sensorEntry(
                    source = "battery",
                    title = "Batería",
                    value = "${percent}%${if (isCharging) " ⚡$chargeType" else ""}",
                    metadata = """{"percent":$percent,"charging":$isCharging,"chargeType":"$chargeType"}"""
                )
                LifeStreamManager.logSync(applicationContext, entry)
                Log.d(TAG, "Battery: $percent% ${if (isCharging) "(charging via $chargeType)" else ""}")
                true
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "Battery collection failed: ${e.message}")
            false
        }
    }

    // ═══════════════════════════════════════
    // CONNECTIVITY
    // ═══════════════════════════════════════

    private fun collectConnectivity(): Boolean {
        return try {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

            val network = cm.activeNetwork
            val caps = if (network != null) cm.getNetworkCapabilities(network) else null

            val connectionType = when {
                caps == null -> "Disconnected"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Other"
            }

            val downMbps = caps?.linkDownstreamBandwidthKbps?.let { it / 1000 } ?: 0

            val entry = LifeStreamManager.sensorEntry(
                source = "connectivity",
                title = "Conectividad",
                value = "$connectionType${if (downMbps > 0) " (~${downMbps}Mbps)" else ""}",
                metadata = """{"type":"$connectionType","downMbps":$downMbps}"""
            )
            LifeStreamManager.logSync(applicationContext, entry)
            Log.d(TAG, "Connectivity: $connectionType (~${downMbps}Mbps)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity collection failed: ${e.message}")
            false
        }
    }
}
