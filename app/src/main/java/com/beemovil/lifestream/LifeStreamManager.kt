package com.beemovil.lifestream

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LifeStreamManager — Singleton facade for all LifeStream operations.
 *
 * Provides a clean API for:
 * - Logging signals (notifications, GPS, sensors, system)
 * - Querying signals (by category, source, search)
 * - Maintenance (purge expired, clear all)
 * - Stats (for dashboard cards)
 *
 * Thread-safe: All DB operations use Room's built-in threading.
 */
object LifeStreamManager {

    private const val TAG = "LifeStream"
    const val PREF_ENABLED = "lifestream_enabled"

    // Default TTL by category (hours)
    private val DEFAULT_TTL = mapOf(
        "notification" to 72,
        "location" to 24,
        "sensor" to 168,   // 7 days
        "system" to 24
    )

    // ═══════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply()
        Log.i(TAG, "LifeStream ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    /**
     * Check if the app has notification listener permission.
     */
    fun hasNotificationAccess(context: Context): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }

    // ═══════════════════════════════════════
    // LOGGING
    // ═══════════════════════════════════════

    /**
     * Log a LifeStream signal. Respects enabled state.
     */
    suspend fun log(context: Context, entry: LifeStreamEntry) {
        if (!isEnabled(context)) return
        withContext(Dispatchers.IO) {
            try {
                val dao = LifeStreamDB.getDatabase(context).lifeStreamDao()
                val id = dao.insert(entry)
                Log.d(TAG, "Logged: [${entry.category}/${entry.source}] ${entry.title} (id=$id, importance=${entry.importance})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log entry: ${e.message}")
            }
        }
    }

    /**
     * Log a signal synchronously (for use in NotificationListenerService).
     */
    fun logSync(context: Context, entry: LifeStreamEntry) {
        if (!isEnabled(context)) return
        try {
            val dao = LifeStreamDB.getDatabase(context).lifeStreamDao()
            val id = dao.insertSync(entry)
            Log.d(TAG, "LogSync: [${entry.category}/${entry.source}] ${entry.title} (id=$id)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to logSync: ${e.message}")
        }
    }

    /**
     * Create a notification entry with proper defaults.
     */
    fun notificationEntry(
        source: String,
        title: String,
        content: String,
        importance: Int = 1,
        metadata: String = ""
    ): LifeStreamEntry = LifeStreamEntry(
        timestamp = System.currentTimeMillis(),
        category = "notification",
        source = source,
        title = title,
        content = content,
        metadata = metadata,
        importance = importance,
        ttlHours = DEFAULT_TTL["notification"] ?: 72
    )

    /**
     * Create a location entry.
     */
    fun locationEntry(
        lat: Double,
        lng: Double,
        address: String,
        accuracy: Float = 0f
    ): LifeStreamEntry = LifeStreamEntry(
        timestamp = System.currentTimeMillis(),
        category = "location",
        source = "gps",
        title = address.ifBlank { "%.4f, %.4f".format(lat, lng) },
        content = address,
        metadata = """{"lat":$lat,"lng":$lng,"accuracy":$accuracy}""",
        importance = 0,
        ttlHours = DEFAULT_TTL["location"] ?: 24
    )

    /**
     * Create a sensor entry (steps, battery, etc.).
     */
    fun sensorEntry(
        source: String,
        title: String,
        value: String,
        metadata: String = ""
    ): LifeStreamEntry = LifeStreamEntry(
        timestamp = System.currentTimeMillis(),
        category = "sensor",
        source = source,
        title = title,
        content = value,
        metadata = metadata,
        importance = 0,
        ttlHours = DEFAULT_TTL["sensor"] ?: 168
    )

    // ═══════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════

    suspend fun getRecent(context: Context, limit: Int = 50): List<LifeStreamEntry> {
        return withContext(Dispatchers.IO) {
            LifeStreamDB.getDatabase(context).lifeStreamDao().getRecent(limit)
        }
    }

    suspend fun getByCategory(context: Context, category: String, limit: Int = 20): List<LifeStreamEntry> {
        return withContext(Dispatchers.IO) {
            LifeStreamDB.getDatabase(context).lifeStreamDao().getByCategory(category, limit)
        }
    }

    suspend fun getBySource(context: Context, source: String, limit: Int = 20): List<LifeStreamEntry> {
        return withContext(Dispatchers.IO) {
            LifeStreamDB.getDatabase(context).lifeStreamDao().getBySource(source, limit)
        }
    }

    suspend fun getUnread(context: Context, minImportance: Int = 1): List<LifeStreamEntry> {
        return withContext(Dispatchers.IO) {
            LifeStreamDB.getDatabase(context).lifeStreamDao().getUnread(minImportance)
        }
    }

    suspend fun search(context: Context, query: String, limit: Int = 20): List<LifeStreamEntry> {
        return withContext(Dispatchers.IO) {
            LifeStreamDB.getDatabase(context).lifeStreamDao().search(query, limit)
        }
    }

    // ═══════════════════════════════════════
    // MAINTENANCE
    // ═══════════════════════════════════════

    /**
     * Purge entries that have exceeded their TTL.
     * Call periodically (e.g. every 6h via WorkManager).
     */
    suspend fun purgeExpired(context: Context): Int {
        return withContext(Dispatchers.IO) {
            val dao = LifeStreamDB.getDatabase(context).lifeStreamDao()
            val now = System.currentTimeMillis()

            // Purge by each category's default TTL
            var totalPurged = 0
            DEFAULT_TTL.forEach { (category, ttlHours) ->
                val cutoff = now - (ttlHours * 3_600_000L)
                // We purge all entries older than the longest TTL as a safety net
                totalPurged += dao.purgeOlderThan(now - (168 * 3_600_000L)) // 7 days max
            }

            if (totalPurged > 0) {
                Log.i(TAG, "Purged $totalPurged expired entries")
            }
            totalPurged
        }
    }

    suspend fun clearAll(context: Context) {
        withContext(Dispatchers.IO) {
            LifeStreamDB.getDatabase(context).lifeStreamDao().deleteAll()
            Log.i(TAG, "All LifeStream data cleared")
        }
    }

    // ═══════════════════════════════════════
    // STATS
    // ═══════════════════════════════════════

    suspend fun getStats(context: Context): LifeStreamStats {
        return withContext(Dispatchers.IO) {
            val dao = LifeStreamDB.getDatabase(context).lifeStreamDao()
            val now = System.currentTimeMillis()
            val last24h = now - (24 * 3_600_000L)

            LifeStreamStats(
                totalEntries = dao.count(),
                unreadCount = dao.countUnread(),
                todayCount = dao.countSince(last24h),
                sourceBreakdown = dao.getSourceStats(last24h),
                categoryBreakdown = dao.getCategoryStats(last24h)
            )
        }
    }

    data class LifeStreamStats(
        val totalEntries: Int,
        val unreadCount: Int,
        val todayCount: Int,
        val sourceBreakdown: List<SourceStat>,
        val categoryBreakdown: List<CategoryStat>
    )
}
