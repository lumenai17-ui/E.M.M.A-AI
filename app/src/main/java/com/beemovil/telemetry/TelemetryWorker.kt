package com.beemovil.telemetry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class TelemetryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = TelemetryDatabase.getDatabase(applicationContext)
            val scanner = DeviceScanner(applicationContext)
            
            // Reclamamos los datos brutos asíncronamente
            val state = scanner.getCurrentDeviceState()
            
            // Llenamos el esquema DTO de la base de tiempo
            val log = TelemetryLog(
                timestamp = System.currentTimeMillis(),
                batteryPercent = state.batteryPercent,
                batteryTemperatureC = state.batteryTemp,
                wifiSsid = state.wifiSSID,
                operatorName = state.telephonyOperator,
                volumeLevel = state.volumeLevel,
                ambientLux = state.ambientLux,
                totalSteps = state.totalSteps,
                locationLat = state.latitude,
                locationLon = state.longitude
            )
            
            // 7 Días en millis:
            val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L
            val purgeThreshold = System.currentTimeMillis() - sevenDaysMillis
            
            // Atomicidad simple de guardado
            db.telemetryDao().apply {
                insertLog(log)
                purgeOldLogs(purgeThreshold)
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
