package com.beemovil.vision

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * OfflineContextCache — Phase V6: El Bolsillo
 *
 * SQLite cache for web context, geocode, weather, and POI data.
 * Allows E.M.M.A. to function offline using previously fetched data.
 * Searches by GPS proximity (~500m radius).
 *
 * Storage: ~160 KB/day, 10 MB limit (~60 days of data).
 */
class OfflineContextCache private constructor(private val appContext: Context) : SQLiteOpenHelper(
    appContext, DB_NAME, null, DB_VERSION
) {

    companion object {
        private const val TAG = "OfflineContextCache"
        private const val DB_NAME = "emma_offline_cache.db"
        private const val DB_VERSION = 1
        private const val TABLE = "offline_context"
        private const val MAX_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB
        private const val PROXIMITY_DELTA = 0.005 // ~500m

        @Volatile
        private var INSTANCE: OfflineContextCache? = null

        fun getInstance(context: Context): OfflineContextCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineContextCache(context.applicationContext).also { INSTANCE = it }
            }
        }

        // V10: Public constructor for backward compatibility
        fun create(context: Context): OfflineContextCache = getInstance(context)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                lat_rounded REAL NOT NULL,
                lng_rounded REAL NOT NULL,
                address TEXT,
                context_type TEXT NOT NULL,
                content TEXT NOT NULL,
                source TEXT,
                mode TEXT DEFAULT 'general',
                created_at INTEGER NOT NULL,
                ttl_hours INTEGER DEFAULT 24
            )
        """)
        db.execSQL("CREATE INDEX idx_location ON $TABLE(lat_rounded, lng_rounded)")
        db.execSQL("CREATE INDEX idx_type ON $TABLE(context_type)")
        db.execSQL("CREATE INDEX idx_created ON $TABLE(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /**
     * V10: Safe database access — handles corruption by recreating.
     */
    private fun safeWrite(): SQLiteDatabase {
        return try {
            writableDatabase
        } catch (e: Exception) {
            Log.e(TAG, "Database corrupted, recreating: ${e.message}")
            appContext.deleteDatabase(DB_NAME)
            INSTANCE = null
            getInstance(appContext).writableDatabase
        }
    }

    /**
     * Save context data for a location.
     */
    fun save(
        lat: Double, lng: Double,
        type: String, // "web", "weather", "poi", "geocode"
        content: String,
        source: String = "duckduckgo",
        mode: String = "general",
        address: String? = null,
        ttlHours: Int = 24
    ) {
        if (content.isBlank()) return

        try {
            val values = ContentValues().apply {
                put("latitude", lat)
                put("longitude", lng)
                put("lat_rounded", Math.round(lat * 1000.0) / 1000.0)
                put("lng_rounded", Math.round(lng * 1000.0) / 1000.0)
                put("address", address)
                put("context_type", type)
                put("content", content)
                put("source", source)
                put("mode", mode)
                put("created_at", System.currentTimeMillis())
                put("ttl_hours", ttlHours)
            }
            safeWrite().insert(TABLE, null, values)
            Log.d(TAG, "Cached $type for (${"%.3f".format(lat)}, ${"%.3f".format(lng)})")
        } catch (e: Exception) {
            Log.w(TAG, "Cache save failed: ${e.message}")
        }
    }

    /**
     * Get cached context for a location within ~500m radius.
     * Returns combined text from matching entries, newest first.
     */
    fun get(lat: Double, lng: Double, mode: String? = null, type: String? = null): String {
        val latR = Math.round(lat * 1000.0) / 1000.0
        val lngR = Math.round(lng * 1000.0) / 1000.0
        val now = System.currentTimeMillis()

        val whereBuilder = StringBuilder("""
            lat_rounded BETWEEN ? AND ?
            AND lng_rounded BETWEEN ? AND ?
            AND created_at > (? - (ttl_hours * 3600000))
        """.trimIndent())

        val args = mutableListOf(
            (latR - PROXIMITY_DELTA).toString(),
            (latR + PROXIMITY_DELTA).toString(),
            (lngR - PROXIMITY_DELTA).toString(),
            (lngR + PROXIMITY_DELTA).toString(),
            now.toString()
        )

        if (mode != null) {
            whereBuilder.append(" AND (mode = ? OR mode = 'general')")
            args.add(mode)
        }
        if (type != null) {
            whereBuilder.append(" AND context_type = ?")
            args.add(type)
        }

        return try {
            val cursor = readableDatabase.query(
                TABLE, arrayOf("content"),
                whereBuilder.toString(), args.toTypedArray(),
                null, null, "created_at DESC", "5"
            )
            val results = mutableListOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    results.add(it.getString(0))
                }
            }
            results.joinToString("\n").take(600)
        } catch (e: Exception) {
            Log.w(TAG, "Cache query failed: ${e.message}")
            ""
        }
    }

    /**
     * Check if we have cached data for this location.
     */
    fun hasData(lat: Double, lng: Double): Boolean {
        return get(lat, lng).isNotBlank()
    }

    /**
     * Cleanup: remove expired entries and enforce size limit.
     * Call this on app startup.
     */
    fun cleanup() {
        try {
            val now = System.currentTimeMillis()
            // Delete expired entries
            val deleted = writableDatabase.delete(
                TABLE,
                "created_at < (? - (ttl_hours * 3600000))",
                arrayOf(now.toString())
            )
            if (deleted > 0) Log.i(TAG, "Cleaned $deleted expired entries")

            // Enforce size limit: delete oldest if over 10 MB
            val dbFile = readableDatabase.path?.let { java.io.File(it) }
            if (dbFile != null && dbFile.length() > MAX_SIZE_BYTES) {
                writableDatabase.execSQL("""
                    DELETE FROM $TABLE WHERE id IN (
                        SELECT id FROM $TABLE ORDER BY created_at ASC LIMIT 100
                    )
                """)
                Log.i(TAG, "Trimmed cache (was ${dbFile.length() / 1024}KB)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed: ${e.message}")
        }
    }

    /** Total entries in cache */
    fun entryCount(): Int {
        return try {
            val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
            cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } catch (_: Exception) { 0 }
    }

    /** R3-4: Get all entries of a specific type */
    fun getAllByType(type: String): List<CacheEntry> {
        val entries = mutableListOf<CacheEntry>()
        try {
            val cursor = readableDatabase.query(
                TABLE, null,
                "context_type = ?", arrayOf(type),
                null, null, "created_at DESC", "100"
            )
            cursor.use {
                while (it.moveToNext()) {
                    entries.add(CacheEntry(
                        id = it.getInt(it.getColumnIndexOrThrow("id")),
                        latitude = it.getDouble(it.getColumnIndexOrThrow("latitude")),
                        longitude = it.getDouble(it.getColumnIndexOrThrow("longitude")),
                        address = it.getString(it.getColumnIndexOrThrow("address")) ?: "",
                        type = type,
                        content = it.getString(it.getColumnIndexOrThrow("content")),
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                    ))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getAllByType failed: ${e.message}")
        }
        return entries
    }

    /** R3-7: Get database file size in bytes */
    fun dbSizeBytes(): Long {
        return try {
            readableDatabase.path?.let { java.io.File(it).length() } ?: 0
        } catch (_: Exception) { 0 }
    }

    /** R3-7: Purge all entries */
    fun purgeAll() {
        try {
            writableDatabase.delete(TABLE, null, null)
            Log.i(TAG, "Cache purged")
        } catch (e: Exception) {
            Log.w(TAG, "Purge failed: ${e.message}")
        }
    }

    /** R3-4: Delete entry by ID */
    fun deleteById(id: Int) {
        try {
            writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
        } catch (e: Exception) {
            Log.w(TAG, "Delete failed: ${e.message}")
        }
    }

    data class CacheEntry(
        val id: Int,
        val latitude: Double,
        val longitude: Double,
        val address: String,
        val type: String,
        val content: String,
        val createdAt: Long
    )
}
