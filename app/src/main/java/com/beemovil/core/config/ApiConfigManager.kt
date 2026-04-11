package com.beemovil.core.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiConfigManager private constructor(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "emma_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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

    companion object {
        @Volatile
        private var instance: ApiConfigManager? = null

        fun getInstance(context: Context): ApiConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ApiConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
