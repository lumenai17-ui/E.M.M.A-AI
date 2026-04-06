package com.beemovil.memory

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * NotificationLogDB — Stores captured notifications locally.
 * All data stays 100% on device. Auto-purges entries older than retention days.
 */
data class NotificationEntry(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false
)

data class AppNotifStats(
    val packageName: String,
    val appName: String,
    val count: Int,
    val lastTimestamp: Long
)

class NotificationLogDB(context: Context) : SQLiteOpenHelper(context, "bee_notifications.db", null, 1) {

    companion object {
        private const val TAG = "NotifLogDB"
        const val DEFAULT_RETENTION_DAYS = 30
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS notification_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                app_name TEXT DEFAULT '',
                title TEXT DEFAULT '',
                text TEXT DEFAULT '',
                timestamp INTEGER DEFAULT 0,
                read INTEGER DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_pkg ON notification_log(package_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notif_ts ON notification_log(timestamp DESC)")
        Log.i(TAG, "Notification log database created")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS notification_log")
        onCreate(db)
    }

    fun logNotification(entry: NotificationEntry): Long {
        // Auto-purge old entries first (every 100th insert)
        val count = getCount()
        if (count % 100 == 0) purgeOld(DEFAULT_RETENTION_DAYS)

        val values = ContentValues().apply {
            put("package_name", entry.packageName)
            put("app_name", entry.appName)
            put("title", entry.title)
            put("text", entry.text)
            put("timestamp", entry.timestamp)
            put("read", if (entry.read) 1 else 0)
        }
        return writableDatabase.insert("notification_log", null, values)
    }

    fun getRecent(limit: Int = 50): List<NotificationEntry> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM notification_log ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        return cursorToList(cursor)
    }

    fun getByApp(packageName: String, limit: Int = 50): List<NotificationEntry> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM notification_log WHERE package_name = ? ORDER BY timestamp DESC LIMIT ?",
            arrayOf(packageName, limit.toString())
        )
        return cursorToList(cursor)
    }

    fun getByDateRange(startMs: Long, endMs: Long, packageName: String? = null): List<NotificationEntry> {
        val query = if (packageName != null) {
            "SELECT * FROM notification_log WHERE timestamp BETWEEN ? AND ? AND package_name = ? ORDER BY timestamp DESC"
        } else {
            "SELECT * FROM notification_log WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC"
        }
        val args = if (packageName != null) {
            arrayOf(startMs.toString(), endMs.toString(), packageName)
        } else {
            arrayOf(startMs.toString(), endMs.toString())
        }
        return cursorToList(readableDatabase.rawQuery(query, args))
    }

    fun getTodayNotifications(): List<NotificationEntry> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        return getByDateRange(cal.timeInMillis, System.currentTimeMillis())
    }

    fun getAppStats(limit: Int = 10): List<AppNotifStats> {
        val cursor = readableDatabase.rawQuery(
            """SELECT package_name, app_name, COUNT(*) as cnt, MAX(timestamp) as last_ts 
               FROM notification_log 
               GROUP BY package_name 
               ORDER BY cnt DESC 
               LIMIT ?""",
            arrayOf(limit.toString())
        )
        val results = mutableListOf<AppNotifStats>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(AppNotifStats(
                    packageName = it.getString(0) ?: "",
                    appName = it.getString(1) ?: "",
                    count = it.getInt(2),
                    lastTimestamp = it.getLong(3)
                ))
            }
        }
        return results
    }

    fun getTodayCount(): Int {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM notification_log WHERE timestamp >= ?",
            arrayOf(cal.timeInMillis.toString())
        )
        cursor.use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM notification_log", null)
        cursor.use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getTrackedApps(): List<String> {
        val cursor = readableDatabase.rawQuery(
            "SELECT DISTINCT package_name FROM notification_log ORDER BY package_name", null
        )
        val apps = mutableListOf<String>()
        cursor.use { while (it.moveToNext()) apps.add(it.getString(0) ?: "") }
        return apps
    }

    fun purgeOld(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - (retentionDays * 86400_000L)
        val deleted = writableDatabase.delete("notification_log", "timestamp < ?", arrayOf(cutoff.toString()))
        if (deleted > 0) Log.i(TAG, "Purged $deleted old notifications")
    }

    fun clearAll() {
        writableDatabase.delete("notification_log", null, null)
    }

    private fun cursorToList(cursor: Cursor): List<NotificationEntry> {
        val results = mutableListOf<NotificationEntry>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(NotificationEntry(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    packageName = it.getString(it.getColumnIndexOrThrow("package_name")) ?: "",
                    appName = it.getString(it.getColumnIndexOrThrow("app_name")) ?: "",
                    title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                    text = it.getString(it.getColumnIndexOrThrow("text")) ?: "",
                    timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                    read = it.getInt(it.getColumnIndexOrThrow("read")) == 1
                ))
            }
        }
        return results
    }
}
