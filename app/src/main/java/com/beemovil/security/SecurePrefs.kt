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
 * C-06 fix: previously, if EncryptedSharedPreferences failed to open, the code
 * silently
 *   1) deleted the existing encrypted prefs file (losing the user's keys), and
 *   2) fell back to a plain SharedPreferences file `${SECURE_PREFS_NAME}_fallback`.
 * The user was never told. Now:
 *   - The file is NOT deleted automatically.
 *   - If everything fails, we expose `isUsingInsecureFallback()` so the UI can warn.
 *   - A SharedPrefs flag is set so any caller can check "are my keys actually encrypted?".
 */
object SecurePrefs {

    private const val TAG = "SecurePrefs"
    private const val SECURE_PREFS_NAME = "beemovil_secure"
    private const val PLAIN_PREFS_NAME = "beemovil"
    private const val MIGRATION_DONE_KEY = "__migration_done_v1"
    private const val INSECURE_FALLBACK_FLAG = "__insecure_fallback_active"

    /** Keys that contain sensitive data and must be encrypted. */
    val SENSITIVE_KEYS = setOf(
        "openrouter_api_key",
        "ollama_api_key",
        "telegram_bot_token",
        "github_token",
        "email_password",
        "email_address",
        "telegram_owner_username",
        "deepgram_api_key",
        "google_ai_key",
        "elevenlabs_api_key",
        "elevenlabs_voice_id"
    )

    @Volatile
    private var instance: SharedPreferences? = null

    @Volatile
    private var insecureFallback: Boolean = false

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

    /**
     * True if the current backing prefs are NOT encrypted (because the Keystore
     * could not be initialized). The UI should display a prominent warning if true.
     */
    fun isUsingInsecureFallback(): Boolean = insecureFallback

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
            ).also {
                // Successful encrypted open: clear any insecure-fallback flag from a previous run.
                insecureFallback = false
                it.edit().remove(INSECURE_FALLBACK_FLAG).apply()
            }
        } catch (e: Exception) {
            // C-06: do NOT delete the existing encrypted prefs file. The keys may still
            // be recoverable on a future run (e.g., after the device finishes setup).
            Log.e(TAG, "EncryptedSharedPreferences failed: ${e.javaClass.simpleName}: ${e.message}", e)
            try {
                // Try once more without touching anything on disk.
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
            } catch (e2: Exception) {
                Log.e(TAG, "Encrypted retry failed: ${e2.javaClass.simpleName}: ${e2.message}", e2)
                Log.e(TAG, "⚠️ FALLING BACK TO PLAIN SHARED-PREFERENCES — credentials will NOT be encrypted at rest.")
                insecureFallback = true
                val fallback = context.getSharedPreferences(
                    "${SECURE_PREFS_NAME}_fallback",
                    Context.MODE_PRIVATE
                )
                fallback.edit().putBoolean(INSECURE_FALLBACK_FLAG, true).apply()
                fallback
            }
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
