package com.beemovil.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecurePrefs — Encrypted storage for sensitive credentials.
 *
 * Uses Android Keystore-backed AES-256 encryption via EncryptedSharedPreferences.
 * On first access, automatically migrates plain-text keys from the old SharedPreferences.
 *
 * Usage:
 *   val secure = SecurePrefs.get(context)
 *   val apiKey = secure.getString("openrouter_api_key", "") ?: ""
 *   secure.edit().putString("openrouter_api_key", newKey).apply()
 */
object SecurePrefs {

    private const val TAG = "SecurePrefs"
    private const val SECURE_PREFS_NAME = "beemovil_secure"
    private const val PLAIN_PREFS_NAME = "beemovil"
    private const val MIGRATION_DONE_KEY = "__migration_done_v1"

    /** Keys that contain sensitive data and must be encrypted. */
    val SENSITIVE_KEYS = setOf(
        "openrouter_api_key",
        "ollama_api_key",
        "telegram_bot_token",
        "github_token",
        "email_password",
        "email_address",
        "telegram_owner_username"
    )

    @Volatile
    private var instance: SharedPreferences? = null

    /**
     * Get the encrypted SharedPreferences instance.
     * Thread-safe, lazily initialized, auto-migrates on first call.
     */
    fun get(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: createSecurePrefs(context).also {
                instance = it
                migrateIfNeeded(context, it)
            }
        }
    }

    private fun createSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback for devices with broken Keystore (very rare, old devices)
            Log.e(TAG, "EncryptedSharedPreferences failed, using fallback: ${e.message}")
            context.getSharedPreferences("${SECURE_PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * One-time migration: copies sensitive keys from plain SharedPreferences
     * to encrypted storage, then removes them from plain storage.
     */
    private fun migrateIfNeeded(context: Context, secure: SharedPreferences) {
        if (secure.getBoolean(MIGRATION_DONE_KEY, false)) return

        val plain = context.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
        val secureEditor = secure.edit()
        val plainEditor = plain.edit()
        var migrated = 0

        SENSITIVE_KEYS.forEach { key ->
            val value = plain.getString(key, null)
            if (!value.isNullOrBlank()) {
                // Only migrate if not already in secure (don't overwrite)
                if (secure.getString(key, null).isNullOrBlank()) {
                    secureEditor.putString(key, value)
                    migrated++
                }
                // Remove from plain storage regardless
                plainEditor.remove(key)
            }
        }

        secureEditor.putBoolean(MIGRATION_DONE_KEY, true)
        secureEditor.apply()
        plainEditor.apply()

        if (migrated > 0) {
            Log.i(TAG, "Migrated $migrated sensitive keys to encrypted storage")
        } else {
            Log.i(TAG, "Migration check complete — no keys to migrate")
        }
    }
}
