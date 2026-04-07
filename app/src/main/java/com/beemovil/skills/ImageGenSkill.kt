package com.beemovil.skills

import android.os.Environment
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * ImageGenSkill — Phase 23-A: Multi-provider image generation.
 *
 * Supports:
 *   1. fal.ai  → Flux Schnell / Flux Pro (fast, starter credits)
 *   2. Together AI → Flux.1 Schnell (starter credits)
 *   3. OpenRouter → DALL-E 3 (paid)
 *
 * Images are auto-downloaded to BeeMovil/images/ for inline display.
 * Provider fallback chain: fal.ai → Together → OpenRouter
 */
class ImageGenSkill(
    private val getApiKey: () -> String,
    private val getProvider: () -> String,
    private val getFalKey: () -> String = { "" },
    private val getTogetherKey: () -> String = { "" }
) : BeeSkill {
    override val name = "image_gen"
    override val description = """Generate an image from a text description using AI.
Parameters:
  - 'prompt' (required): Detailed english description of the image to generate. Be specific and descriptive.
  - 'size' (optional): 'square' (1024x1024), 'landscape' (1280x720), 'portrait' (720x1280). Default: square
  - 'provider' (optional): 'fal' (Flux Schnell, fastest), 'together' (Flux.1), 'openrouter' (DALL-E 3). Default: auto (uses first available)
  - 'model' (optional): 'flux-schnell' (fast), 'flux-pro' (quality), 'dall-e-3'. Default: flux-schnell
The generated image is saved locally and its path is returned for inline display."""

    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "prompt":{"type":"string","description":"Detailed english description of the image to generate. Be vivid and specific."},
            "size":{"type":"string","enum":["square","landscape","portrait"],"description":"Image aspect ratio"},
            "provider":{"type":"string","enum":["fal","together","openrouter","auto"],"description":"Image provider to use"},
            "model":{"type":"string","enum":["flux-schnell","flux-pro","dall-e-3"],"description":"Specific model"}
        },"required":["prompt"]}
    """.trimIndent())

    companion object {
        private const val TAG = "ImageGen"
        private val JSON_MEDIA = "application/json".toMediaType()

        // Ensure images directory exists
        fun getImagesDir(): File {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "BeeMovil/images"
            )
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun execute(params: JSONObject): JSONObject {
        val prompt = params.optString("prompt", "")
        if (prompt.isBlank()) return JSONObject().put("error", "No prompt provided")

        val size = params.optString("size", "square")
        val requestedProvider = params.optString("provider", "auto")
        val requestedModel = params.optString("model", "flux-schnell")

        // Determine which providers have keys
        val falKey = getFalKey()
        val togetherKey = getTogetherKey()
        val llmApiKey = getApiKey()
        val llmProvider = getProvider()

        // Build provider chain based on preference
        val providerChain = when (requestedProvider) {
            "fal" -> listOf("fal")
            "together" -> listOf("together")
            "openrouter" -> listOf("openrouter")
            else -> listOf("fal", "together", "openrouter") // auto: try all
        }

        // Try each provider in order
        for (provider in providerChain) {
            try {
                val result = when (provider) {
                    "fal" -> {
                        if (falKey.isBlank()) continue
                        generateViaFal(prompt, size, requestedModel, falKey)
                    }
                    "together" -> {
                        if (togetherKey.isBlank()) continue
                        generateViaTogether(prompt, size, togetherKey)
                    }
                    "openrouter" -> {
                        if (llmApiKey.isBlank() || llmProvider != "openrouter") continue
                        generateViaOpenRouter(prompt, size, llmApiKey)
                    }
                    else -> continue
                }

                // If successful, download and save locally
                if (result.optBoolean("success", false)) {
                    val imageUrl = result.optString("image_url", "")
                    if (imageUrl.isNotBlank()) {
                        val localPath = downloadImage(imageUrl, prompt)
                        if (localPath != null) {
                            result.put("local_path", localPath)
                            result.put("file_path", localPath) // For FileAttachmentCard
                        }
                    }
                    result.put("provider_used", provider)
                    return result
                }

                // If error but not a key issue, return it
                if (result.has("error") && !result.optString("error").contains("key", ignoreCase = true)) {
                    return result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Provider $provider failed: ${e.message}")
                continue
            }
        }

        return JSONObject()
            .put("error", "No image generation provider available")
            .put("suggestion", "Configure a fal.ai or Together AI API key in Settings → Media Generation")
    }

    // ═══════════════════════════════════════
    // FAL.AI — Flux Schnell / Flux Pro
    // ═══════════════════════════════════════
    private fun generateViaFal(prompt: String, size: String, model: String, apiKey: String): JSONObject {
        val imageSize = when (size) {
            "landscape" -> "landscape_16_9"
            "portrait" -> "portrait_16_9"
            else -> "square"
        }

        val modelEndpoint = when (model) {
            "flux-pro" -> "fal-ai/flux-pro/v1.1"
            else -> "fal-ai/flux/schnell"
        }

        val body = JSONObject().apply {
            put("prompt", prompt)
            put("image_size", imageSize)
            put("num_inference_steps", if (model == "flux-pro") 25 else 4)
            put("num_images", 1)
        }

        Log.d(TAG, "fal.ai → $modelEndpoint | size=$imageSize")

        // Submit to queue
        val submitRequest = Request.Builder()
            .url("https://queue.fal.run/$modelEndpoint")
            .addHeader("Authorization", "Key $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val submitResponse = client.newCall(submitRequest).execute()
        val submitBody = submitResponse.body?.string() ?: throw Exception("Empty response")

        if (!submitResponse.isSuccessful) {
            val errMsg = try {
                JSONObject(submitBody).optString("detail", submitBody.take(200))
            } catch (_: Exception) { submitBody.take(200) }
            return JSONObject().put("error", "fal.ai error ${submitResponse.code}: $errMsg")
        }

        val submitJson = JSONObject(submitBody)

        // Check if result is already available (sync response)
        if (submitJson.has("images")) {
            return parseFalResult(submitJson, prompt, "flux-schnell")
        }

        // Async: poll for result
        val requestId = submitJson.optString("request_id", "")
        if (requestId.isBlank()) {
            return JSONObject().put("error", "No request_id from fal.ai")
        }

        // Poll up to 60 seconds
        val pollClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        for (attempt in 1..30) {
            Thread.sleep(2000)

            val statusRequest = Request.Builder()
                .url("https://queue.fal.run/$modelEndpoint/requests/$requestId/status")
                .addHeader("Authorization", "Key $apiKey")
                .get()
                .build()

            try {
                val statusResponse = pollClient.newCall(statusRequest).execute()
                val statusBody = statusResponse.body?.string() ?: continue
                val statusJson = JSONObject(statusBody)
                val status = statusJson.optString("status", "")

                if (status == "COMPLETED") {
                    // Fetch result
                    val resultRequest = Request.Builder()
                        .url("https://queue.fal.run/$modelEndpoint/requests/$requestId")
                        .addHeader("Authorization", "Key $apiKey")
                        .get()
                        .build()

                    val resultResponse = client.newCall(resultRequest).execute()
                    val resultBody = resultResponse.body?.string() ?: throw Exception("Empty result")
                    return parseFalResult(JSONObject(resultBody), prompt, model)
                }

                if (status == "FAILED") {
                    return JSONObject().put("error", "fal.ai generation failed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Poll attempt $attempt failed: ${e.message}")
            }
        }

        return JSONObject().put("error", "fal.ai generation timed out")
    }

    private fun parseFalResult(json: JSONObject, prompt: String, model: String): JSONObject {
        val images = json.optJSONArray("images")
        if (images != null && images.length() > 0) {
            val imageUrl = images.getJSONObject(0).optString("url", "")
            if (imageUrl.isNotBlank()) {
                return JSONObject()
                    .put("success", true)
                    .put("image_url", imageUrl)
                    .put("prompt", prompt)
                    .put("model", model)
                    .put("message", "Imagen generada con Flux ($model). Guardada localmente.")
            }
        }
        return JSONObject().put("error", "No image data in fal.ai response")
    }

    // ═══════════════════════════════════════
    // TOGETHER AI — Flux.1 Schnell
    // ═══════════════════════════════════════
    private fun generateViaTogether(prompt: String, size: String, apiKey: String): JSONObject {
        val (w, h) = when (size) {
            "landscape" -> Pair(1280, 720)
            "portrait" -> Pair(720, 1280)
            else -> Pair(1024, 1024)
        }

        val body = JSONObject().apply {
            put("model", "black-forest-labs/FLUX.1-schnell-Free")
            put("prompt", prompt)
            put("width", w)
            put("height", h)
            put("steps", 4)
            put("n", 1)
        }

        Log.d(TAG, "Together AI → FLUX.1-schnell | ${w}x${h}")

        val request = Request.Builder()
            .url("https://api.together.xyz/v1/images/generations")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            val errMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: responseBody.take(200)
            } catch (_: Exception) { responseBody.take(200) }
            return JSONObject().put("error", "Together AI error ${response.code}: $errMsg")
        }

        val json = JSONObject(responseBody)
        val data = json.optJSONArray("data")
        if (data != null && data.length() > 0) {
            val item = data.getJSONObject(0)
            // Together returns either url or b64_json
            val imageUrl = item.optString("url", "")
            val b64 = item.optString("b64_json", "")

            if (imageUrl.isNotBlank()) {
                return JSONObject()
                    .put("success", true)
                    .put("image_url", imageUrl)
                    .put("prompt", prompt)
                    .put("model", "flux.1-schnell")
                    .put("message", "Imagen generada con Together AI (Flux.1 Schnell). Guardada localmente.")
            }

            if (b64.isNotBlank()) {
                // Base64 → save directly
                val localPath = saveBase64Image(b64, prompt)
                return JSONObject()
                    .put("success", true)
                    .put("image_url", "") // no URL, direct save
                    .put("local_path", localPath)
                    .put("file_path", localPath)
                    .put("prompt", prompt)
                    .put("model", "flux.1-schnell")
                    .put("message", "Imagen generada con Together AI (Flux.1 Schnell). Guardada localmente.")
            }
        }

        return JSONObject().put("error", "No image data in Together AI response")
    }

    // ═══════════════════════════════════════
    // OPENROUTER — DALL-E 3
    // ═══════════════════════════════════════
    private fun generateViaOpenRouter(prompt: String, size: String, apiKey: String): JSONObject {
        val dalleSize = when (size) {
            "landscape" -> "1792x1024"
            "portrait" -> "1024x1792"
            else -> "1024x1024"
        }

        val body = JSONObject().apply {
            put("model", "openai/dall-e-3")
            put("prompt", prompt)
            put("n", 1)
            put("size", dalleSize)
        }

        Log.d(TAG, "OpenRouter → DALL-E 3 | $dalleSize")

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/images/generations")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            val error = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: responseBody.take(200)
            } catch (_: Exception) { responseBody.take(200) }
            return JSONObject()
                .put("error", "API error ${response.code}: $error")
                .put("note", "Image generation requires a paid OpenRouter account with DALL-E access")
        }

        val json = JSONObject(responseBody)
        val data = json.optJSONArray("data")
        if (data != null && data.length() > 0) {
            val imageUrl = data.getJSONObject(0).optString("url", "")
            return JSONObject()
                .put("success", true)
                .put("image_url", imageUrl)
                .put("prompt", prompt)
                .put("model", "dall-e-3")
                .put("message", "Imagen generada con DALL-E 3. Guardada localmente.")
        }

        return JSONObject().put("error", "No image data in OpenRouter response")
    }

    // ═══════════════════════════════════════
    // DOWNLOAD & SAVE
    // ═══════════════════════════════════════
    private fun downloadImage(imageUrl: String, prompt: String): String? {
        return try {
            val dir = getImagesDir()
            val timestamp = System.currentTimeMillis()
            val safeName = prompt.take(30).replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
            val fileName = "img_${timestamp}_${safeName}.png"
            val file = File(dir, fileName)

            Log.d(TAG, "Downloading image to ${file.absolutePath}")

            val conn = URL(imageUrl).openConnection()
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.getInputStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Image saved: ${file.absolutePath} (${file.length() / 1024} KB)")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            null
        }
    }

    private fun saveBase64Image(b64: String, prompt: String): String? {
        return try {
            val dir = getImagesDir()
            val timestamp = System.currentTimeMillis()
            val safeName = prompt.take(30).replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
            val fileName = "img_${timestamp}_${safeName}.png"
            val file = File(dir, fileName)

            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            FileOutputStream(file).use { it.write(bytes) }

            Log.d(TAG, "Base64 image saved: ${file.absolutePath} (${file.length() / 1024} KB)")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Base64 save failed: ${e.message}")
            null
        }
    }
}
