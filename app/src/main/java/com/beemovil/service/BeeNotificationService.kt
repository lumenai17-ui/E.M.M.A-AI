package com.beemovil.service

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.beemovil.memory.NotificationEntry
import com.beemovil.memory.NotificationLogDB

/**
 * BeeNotificationService — Captures all device notifications.
 *
 * Uses Android's NotificationListenerService to intercept every notification.
 * Filters by user config (per-app ON/OFF) and logs to local SQLite.
 * Data never leaves the device.
 */
class BeeNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "BeeNotifService"
        private const val PREFS_NAME = "bee_notification_config"
        const val KEY_ENABLED = "notif_capture_enabled"
        const val KEY_EXCLUDED_APPS = "notif_excluded_apps"

        fun isServiceEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            return flat?.contains(context.packageName) == true
        }

        fun getExcludedApps(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(KEY_EXCLUDED_APPS, emptySet()) ?: emptySet()
        }

        fun setExcludedApps(context: Context, apps: Set<String>) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_EXCLUDED_APPS, apps)
                .apply()
        }

        fun isCaptureEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true)
        }

        fun setCaptureEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
        }
    }

    private var db: NotificationLogDB? = null
    private var prefs: SharedPreferences? = null

    override fun onCreate() {
        super.onCreate()
        db = NotificationLogDB(applicationContext)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.i(TAG, "Notification listener service started")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Skip if capture is globally disabled
        if (prefs?.getBoolean(KEY_ENABLED, true) != true) return

        val pkg = sbn.packageName ?: return

        // Never capture our own notifications
        if (pkg == applicationContext.packageName) return

        // Skip excluded apps
        val excluded = prefs?.getStringSet(KEY_EXCLUDED_APPS, emptySet()) ?: emptySet()
        if (pkg in excluded) return

        // Skip ongoing/group-summary notifications (reduce noise)
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Extract notification content
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Skip empty notifications
        if (title.isBlank() && text.isBlank()) return

        // Resolve app name
        val appName = try {
            val pm = applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) { pkg.substringAfterLast('.') }

        // Log to database
        val entry = NotificationEntry(
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        try {
            db?.logNotification(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log notification: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Optional: could track dismissed notifications
    }

    override fun onDestroy() {
        super.onDestroy()
        db?.close()
        Log.i(TAG, "Notification listener service stopped")
    }
}
