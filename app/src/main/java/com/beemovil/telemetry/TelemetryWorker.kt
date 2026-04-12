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
            
            // --- CEREBRO SILENCIOSO (GHOST MEMORY SCAN) ---
            var ghostMemoryStr = ""
            try {
                // 1. Escanear Archivos Descargados o Agregados
                if (androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val projection = arrayOf(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME, android.provider.MediaStore.Files.FileColumns.DATE_ADDED)
                    val sortOrder = "${android.provider.MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                    val queryUri = android.provider.MediaStore.Files.getContentUri("external")
                    
                    val cursor = applicationContext.contentResolver.query(
                        queryUri, projection, null, null, sortOrder
                    )
                    
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME)
                            val dateIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATE_ADDED)
                            val addedTime = c.getLong(dateIdx) * 1000L
                            
                            // Si fue agregado en la última hora (o el último archivo absoluto)
                            ghostMemoryStr += "Un nuevo archivo detectado recientemente: '${c.getString(nameIdx)}'."
                        }
                    }
                }
                
                // 2. Grabar Baliza en Preferencias
                if (ghostMemoryStr.isNotBlank()) {
                    val prefs = applicationContext.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
                    prefs.edit().putString("GHOST_MEMORY", ghostMemoryStr).apply()
                }
            } catch (e: Exception) {
                // Falla silenciosa
            }
            // ----------------------------------------------
            
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
