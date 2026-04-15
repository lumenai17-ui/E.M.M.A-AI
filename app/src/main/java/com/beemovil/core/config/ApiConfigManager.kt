package com.beemovil.core.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiConfigManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = createPrefs(context)
    
    companion object {
        private const val TAG = "ApiConfigManager"
        private const val PREFS_NAME = "emma_secure_prefs"
        
        @Volatile
        private var instance: ApiConfigManager? = null

        fun getInstance(context: Context): ApiConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ApiConfigManager(context.applicationContext).also { instance = it }
            }
        }
        
        private fun createPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences failed (likely reinstall): ${e.message}")
                // Try deleting the corrupted prefs file and retrying
                try {
                    val prefsFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/${PREFS_NAME}.xml")
                    if (prefsFile.exists()) {
                        prefsFile.delete()
                        Log.i(TAG, "Deleted corrupted prefs file, retrying...")
                    }
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Retry also failed, using plain fallback: ${e2.message}")
                    context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
                }
            }
        }
    }

    // "openrouter", "ollama", "local", "custom"
    var providerPreset: String
        get() = prefs.getString("provider_preset", "custom") ?: "custom"
        set(value) = prefs.edit().putString("provider_preset", value).apply()

    var huggingFaceToken: String
        get() = prefs.getString("hf_token", "") ?: ""
        set(value) = prefs.edit().putString("hf_token", value).apply()

    // Vacío por defecto
    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        set(value) = prefs.edit().putString("base_url", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var modelName: String
        get() = prefs.getString("model_name", "") ?: ""
        set(value) = prefs.edit().putString("model_name", value).apply()

    var deepgramKey: String
        get() = prefs.getString("deepgram_key", "") ?: ""
        set(value) = prefs.edit().putString("deepgram_key", value).apply()
}
