package com.beemovil.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver — Auto-start WakeWordService after device reboot.
 *
 * Only starts the service if the user had "Hello Emma" enabled before reboot.
 * Checks the SharedPreferences flag set by SettingsScreen toggle.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences("beemovil", Context.MODE_PRIVATE)
        val wakeWordEnabled = prefs.getBoolean("wake_word_enabled", false)

        if (wakeWordEnabled) {
            Log.i(TAG, "Boot completed — restarting WakeWordService")
            try {
                val serviceIntent = Intent(context, WakeWordService::class.java).apply {
                    action = WakeWordService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart WakeWordService: ${e.message}")
            }
        } else {
            Log.d(TAG, "Boot completed — WakeWord not enabled, skipping")
        }
    }
}
