package com.beemovil.telemetry

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TelemetryDao {
    @Insert
    suspend fun insertLog(log: TelemetryLog)

    @Query("SELECT * FROM telemetry_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<TelemetryLog>

    @Query("SELECT * FROM telemetry_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getLogsBetween(startTime: Long, endTime: Long): List<TelemetryLog>

    @Query("DELETE FROM telemetry_logs WHERE timestamp < :olderThanTimestamp")
    suspend fun purgeOldLogs(olderThanTimestamp: Long)
}
