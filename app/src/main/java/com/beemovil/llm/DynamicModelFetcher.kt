package com.beemovil.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * U-03 fix: Dynamic model fetcher that queries live APIs to get
 * the current list of available models from OpenRouter and Ollama.
 * 
 * Results are cached in SharedPrefs so we don't fetch every time
 * the Settings screen opens.
 */
object DynamicModelFetcher {

    private const val TAG = "DynamicModelFetcher"
    private const val PREF_NAME = "dynamic_models"
    private const val KEY_OPENROUTER_CACHE = "openrouter_models_json"
    private const val KEY_OLLAMA_CACHE = "ollama_models_json"
    private const val KEY_OPENROUTER_TIMESTAMP = "openrouter_fetch_ts"
    private const val KEY_OLLAMA_TIMESTAMP = "ollama_fetch_ts"
    private const val CACHE_TTL_MS = 4 * 60 * 60 * 1000L // 4 hours

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ═══════════════════════════════════════
    // OPENROUTER
    // ═══════════════════════════════════════

    /**
     * Fetch models from OpenRouter API. Returns list of ModelEntry.
     * Falls back to cached data if the network call fails.
     */
    suspend fun fetchOpenRouterModels(context: Context, apiKey: String): List<ModelRegistry.ModelEntry> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${body.take(200)}")
                }

                val json = JSONObject(body)
                val data = json.getJSONArray("data")
                val models = parseOpenRouterModels(data)

                // Cache the result
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_OPENROUTER_CACHE, data.toString())
                    .putLong(KEY_OPENROUTER_TIMESTAMP, System.currentTimeMillis())
                    .apply()

                Log.i(TAG, "OpenRouter: ${models.size} modelos cargados del servidor")
                models
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching OpenRouter models: ${e.message}")
                // Try cached data
                getCachedOpenRouterModels(context)
            }
        }
    }

    /**
     * Get cached OpenRouter models without network call.
     * Returns static defaults if no cache exists.
     */
    fun getCachedOpenRouterModels(context: Context): List<ModelRegistry.ModelEntry> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_OPENROUTER_CACHE, null) ?: return ModelRegistry.OPENROUTER
        return try {
            val data = JSONArray(cached)
            val models = parseOpenRouterModels(data)
            if (models.isEmpty()) ModelRegistry.OPENROUTER else models
        } catch (e: Exception) {
            ModelRegistry.OPENROUTER
        }
    }

    private fun parseOpenRouterModels(data: JSONArray): List<ModelRegistry.ModelEntry> {
        val models = mutableListOf<ModelRegistry.ModelEntry>()
        for (i in 0 until data.length()) {
            try {
                val m = data.getJSONObject(i)
                val id = m.getString("id")
                val name = m.optString("name", id.split("/").last())
                
                // Determine pricing
                val pricing = m.optJSONObject("pricing")
                val promptPrice = pricing?.optString("prompt", "0")?.toDoubleOrNull() ?: 0.0
                val isFree = promptPrice == 0.0 || id.contains(":free")
                
                // Determine capabilities from architecture/description
                val arch = m.optJSONObject("architecture")
                val modality = arch?.optString("modality", "") ?: ""
                val hasVision = modality.contains("image") || modality.contains("multimodal")
                
                // Category inference
                val category = when {
                    name.contains("vision", true) || hasVision -> ModelRegistry.Category.VISION
                    name.contains("code", true) || name.contains("coder", true) -> ModelRegistry.Category.CODE
                    name.contains("reason", true) || name.contains("think", true) -> ModelRegistry.Category.REASONING
                    else -> ModelRegistry.Category.CHAT
                }

                val contextLength = m.optInt("context_length", 0)
                val sizeLabel = when {
                    isFree -> "Free"
                    contextLength > 100000 -> "Pro ${contextLength / 1000}K"
                    else -> "Pro"
                }

                models.add(ModelRegistry.ModelEntry(
                    id = id,
                    name = name,
                    provider = "openrouter",
                    category = category,
                    free = isFree,
                    hasVision = hasVision,
                    hasTools = true, // Most OpenRouter models support tool calling
                    sizeLabel = sizeLabel,
                    description = m.optString("description", "").take(80)
                ))
            } catch (e: Exception) {
                // Skip malformed entries
            }
        }
        // Sort: free first, then by name
        return models.sortedWith(compareByDescending<ModelRegistry.ModelEntry> { it.free }.thenBy { it.name })
    }

    // ═══════════════════════════════════════
    // OLLAMA
    // ═══════════════════════════════════════

    /**
     * Fetch models from an Ollama instance.
     * Uses the /api/tags endpoint for local Ollama,
     * or /api/ps for running models.
     */
    suspend fun fetchOllamaModels(context: Context, baseUrl: String): List<ModelRegistry.ModelEntry> {
        return withContext(Dispatchers.IO) {
            try {
                // Clean up URL
                val cleanUrl = baseUrl.trimEnd('/').let {
                    if (it.endsWith("/api/chat") || it.endsWith("/api/generate")) {
                        it.substringBeforeLast("/api/")
                    } else it
                }
                
                val request = Request.Builder()
                    .url("$cleanUrl/api/tags")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")

                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}")
                }

                val json = JSONObject(body)
                val modelsArray = json.getJSONArray("models")
                val models = parseOllamaModels(modelsArray)

                // Cache
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_OLLAMA_CACHE, modelsArray.toString())
                    .putLong(KEY_OLLAMA_TIMESTAMP, System.currentTimeMillis())
                    .apply()

                Log.i(TAG, "Ollama: ${models.size} modelos cargados del servidor")
                models
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Ollama models: ${e.message}")
                getCachedOllamaModels(context)
            }
        }
    }

    fun getCachedOllamaModels(context: Context): List<ModelRegistry.ModelEntry> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_OLLAMA_CACHE, null) ?: return ModelRegistry.OLLAMA_CLOUD
        return try {
            val data = JSONArray(cached)
            val models = parseOllamaModels(data)
            if (models.isEmpty()) ModelRegistry.OLLAMA_CLOUD else models
        } catch (e: Exception) {
            ModelRegistry.OLLAMA_CLOUD
        }
    }

    private fun parseOllamaModels(data: JSONArray): List<ModelRegistry.ModelEntry> {
        val models = mutableListOf<ModelRegistry.ModelEntry>()
        for (i in 0 until data.length()) {
            try {
                val m = data.getJSONObject(i)
                val fullName = m.getString("name") // e.g., "llama3.3:70b"
                val modelName = m.optString("model", fullName)
                val sizeBytes = m.optLong("size", 0)
                val sizeGB = String.format("%.1f GB", sizeBytes / 1_073_741_824.0)

                val details = m.optJSONObject("details")
                val family = details?.optString("family", "") ?: ""
                val paramSize = details?.optString("parameter_size", "") ?: ""

                val displayName = fullName.split(":").first().replaceFirstChar { it.uppercase() } +
                        if (paramSize.isNotBlank()) " ($paramSize)" else ""

                val category = when {
                    family.contains("llava", true) || fullName.contains("vision") -> ModelRegistry.Category.VISION
                    family.contains("code", true) || fullName.contains("code") -> ModelRegistry.Category.CODE
                    else -> ModelRegistry.Category.CHAT
                }

                models.add(ModelRegistry.ModelEntry(
                    id = modelName,
                    name = displayName,
                    provider = "ollama",
                    category = category,
                    hasVision = category == ModelRegistry.Category.VISION,
                    hasTools = true,
                    sizeLabel = if (paramSize.isNotBlank()) paramSize else sizeGB,
                    description = "Family: $family"
                ))
            } catch (e: Exception) {
                // Skip malformed entries
            }
        }
        return models.sortedBy { it.name }
    }

    // ═══════════════════════════════════════
    // CACHE MANAGEMENT
    // ═══════════════════════════════════════

    fun isCacheStale(context: Context, provider: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = when (provider) {
            "openrouter" -> KEY_OPENROUTER_TIMESTAMP
            "ollama" -> KEY_OLLAMA_TIMESTAMP
            else -> return true
        }
        val lastFetch = prefs.getLong(key, 0L)
        return System.currentTimeMillis() - lastFetch > CACHE_TTL_MS
    }

    fun clearCache(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
