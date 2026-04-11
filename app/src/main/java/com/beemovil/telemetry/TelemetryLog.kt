package com.beemovil.telemetry

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_logs")
data class TelemetryLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long, // Epoch ms
    val batteryPercent: Int,
    val batteryTemperatureC: Float,
    val wifiSsid: String,
    val operatorName: String,
    val volumeLevel: Int,
    val ambientLux: Float,
    val totalSteps: Float,
    // Coordenadas Crudas
    val locationLat: Double?,
    val locationLon: Double?
)
