package com.beemovil.lifestream

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * LifeStreamEntry — LifeStream Intelligence Layer
 *
 * A single signal captured from the user's digital life.
 * Sources: notifications, GPS, sensors, system events.
 * 
 * Privacy: Banking, OTP, and authenticator notifications are NEVER captured.
 * Retention: Auto-purge based on ttlHours (default 72h).
 */
@Entity(
    tableName = "lifestream",
    indices = [
        Index("category"),
        Index("source"),
        Index("timestamp"),
        Index("isRead")
    ]
)
data class LifeStreamEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,           // System.currentTimeMillis()
    val category: String,          // "notification", "location", "sensor", "system"
    val source: String,            // "whatsapp", "gmail", "gps", "steps", etc.
    val title: String,             // Short summary ("María García")
    val content: String,           // Preview text ("¿Nos vemos a las 3?")
    val metadata: String = "",     // JSON extras: {"package":"com.whatsapp","lat":8.43}
    val importance: Int = 0,       // 0=low, 1=medium, 2=high, 3=critical
    val isRead: Boolean = false,   // Has Emma "processed" this signal?
    val ttlHours: Int = 72         // Auto-purge after N hours
)
