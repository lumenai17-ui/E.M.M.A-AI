package com.beemovil.media

import android.content.Context
import android.util.Log
import com.beemovil.security.SecurePrefs
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Centralized HTTP client for all Pollinations.ai services.
 * Manages API key resolution: user key → embedded default → legacy (no key).
 *
 * Services available:
 *  - Image generation (GET /image/{prompt})
 *  - Audio/TTS (GET /audio/{text})
 *  - Music generation (GET /audio/{prompt}?model=elevenmusic)
 *  - Video generation (GET /image/{prompt}?model=ltx-2)
 *
 * Docs: https://enter.pollinations.ai/api/docs/llm.txt
 */
object PollinationsClient {

    private const val TAG = "PollinationsClient"
    const val BASE_URL = "https://gen.pollinations.ai"
    const val LEGACY_IMAGE_URL = "https://image.pollinations.ai/prompt/"

    // Default shared key — users should replace with their own for better rate limits
    // This key is free tier and shared across all E.M.M.A. users
    private const val DEFAULT_KEY = "sk_4vnLOAllOzioqxUpx0lwf6OgwTja6nyx"

    // Image/TTS client: moderate timeout
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Music/Video client: longer timeout (generation can take 2+ minutes)
    private val heavyClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Get the active API key: user's own key > default embedded key > empty
     */
    fun getApiKey(context: Context): String {
        val securePrefs = SecurePrefs.get(context)
        val userKey = securePrefs.getString("pollinations_api_key", null)
        if (!userKey.isNullOrBlank()) return userKey
        return DEFAULT_KEY
    }

    /**
     * Check if we have any API key available (user or default)
     */
    fun hasApiKey(context: Context): Boolean = getApiKey(context).isNotBlank()

    /**
     * Check if user is using their own key (not the shared default)
     */
    fun isUsingOwnKey(context: Context): Boolean {
        val securePrefs = SecurePrefs.get(context)
        val userKey = securePrefs.getString("pollinations_api_key", null)
        return !userKey.isNullOrBlank() && userKey != DEFAULT_KEY
    }

    /**
     * Custom exception for 402 Payment Required — user needs pollen credits.
     */
    class InsufficientPollenException(message: String) : Exception(message)

    /**
     * Download binary content (image, audio, video) from a Pollinations URL.
     * Auth via both Header AND query param for maximum compatibility.
     *
     * @throws InsufficientPollenException when the API returns 402 (no pollen credits)
     */
    fun downloadMedia(context: Context, url: String, useHeavyClient: Boolean = false): ByteArray? {
        val apiKey = getApiKey(context)
        
        // Append key as query param too (some endpoints prefer this)
        val finalUrl = if (apiKey.isNotBlank()) {
            val separator = if (url.contains("?")) "&" else "?"
            "$url${separator}key=$apiKey"
        } else {
            url
        }

        val requestBuilder = Request.Builder().url(finalUrl).get()
        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val client = if (useHeavyClient) heavyClient else httpClient

        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                val code = response.code
                val errorBody = response.body?.string()?.take(200) ?: "No body"
                Log.e(TAG, "Pollinations error $code: $errorBody (url: ${url.take(100)})")
                response.close()
                
                if (code == 402) {
                    throw InsufficientPollenException(
                        "Tu cuenta de Pollinations no tiene créditos suficientes. " +
                        "Ve a enter.pollinations.ai para recargar o conectar tu cuenta."
                    )
                }
                null
            }
        } catch (e: InsufficientPollenException) {
            throw e // Re-throw so plugins can catch it
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}", e)
            null
        }
    }

    /**
     * Build image generation URL
     */
    fun imageUrl(prompt: String, width: Int = 1024, height: Int = 1024, model: String = "flux"): String {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        return "$BASE_URL/image/$encoded?width=$width&height=$height&model=$model&nologo=true&seed=${System.currentTimeMillis() % 100000}"
    }

    /**
     * Build legacy image URL (no API key needed, for fallback)
     */
    fun legacyImageUrl(prompt: String, width: Int = 1024, height: Int = 1024): String {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        return "${LEGACY_IMAGE_URL}$encoded?width=$width&height=$height&nologo=true&seed=${System.currentTimeMillis() % 100000}"
    }

    /**
     * Build audio/TTS URL (model: elevenlabs default)
     */
    fun audioUrl(text: String, voice: String = "nova"): String {
        val encoded = java.net.URLEncoder.encode(text, "UTF-8")
        return "$BASE_URL/audio/$encoded?voice=$voice&model=elevenlabs"
    }

    /**
     * Build music generation URL
     */
    fun musicUrl(prompt: String, model: String = "elevenmusic", durationSecs: Int = 30): String {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        return "$BASE_URL/audio/$encoded?model=$model&duration=$durationSecs"
    }

    /**
     * Build video generation URL
     */
    fun videoUrl(prompt: String, model: String = "ltx-2", durationSecs: Int = 5, aspectRatio: String = "16:9"): String {
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8")
        return "$BASE_URL/image/$encoded?model=$model&duration=$durationSecs&aspectRatio=$aspectRatio&nologo=true"
    }
}
