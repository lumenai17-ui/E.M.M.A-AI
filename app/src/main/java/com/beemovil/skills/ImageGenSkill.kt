package com.beemovil.skills

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ImageGenSkill — generate images using OpenAI DALL-E API via OpenRouter.
 * The generated image URL is returned for the user to view.
 */
class ImageGenSkill(
    private val getApiKey: () -> String,
    private val getProvider: () -> String
) : BeeSkill {
    override val name = "image_gen"
    override val description = "Generate an image from a text description using AI. Requires 'prompt' with detailed image description. Optional: 'size' (256x256, 512x512, 1024x1024)"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "prompt":{"type":"string","description":"Detailed description of the image to generate"},
            "size":{"type":"string","enum":["256x256","512x512","1024x1024"],"description":"Image size"}
        },"required":["prompt"]}
    """.trimIndent())

    companion object {
        private const val TAG = "ImageGen"
        private val JSON_MEDIA = "application/json".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun execute(params: JSONObject): JSONObject {
        val prompt = params.optString("prompt", "")
        if (prompt.isBlank()) return JSONObject().put("error", "No prompt provided")

        val size = params.optString("size", "512x512")
        val apiKey = getApiKey()
        val provider = getProvider()

        if (apiKey.isBlank()) return JSONObject().put("error", "No API key configured")

        return try {
            when (provider) {
                "openrouter" -> generateViaOpenRouter(prompt, size, apiKey)
                else -> {
                    // For providers without image gen, return a helpful message
                    JSONObject()
                        .put("error", "Image generation not supported on $provider")
                        .put("suggestion", "Switch to OpenRouter to use DALL-E image generation")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image gen error: ${e.message}")
            JSONObject().put("error", "Failed to generate image: ${e.message}")
        }
    }

    private fun generateViaOpenRouter(prompt: String, size: String, apiKey: String): JSONObject {
        val body = JSONObject().apply {
            put("model", "openai/dall-e-3")
            put("prompt", prompt)
            put("n", 1)
            put("size", if (size == "256x256") "1024x1024" else size) // DALL-E 3 min is 1024
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/images/generations")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        Log.d(TAG, "Response: ${response.code}")

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
                .put("message", "🎨 Imagen generada! URL: $imageUrl")
        }

        return JSONObject().put("error", "No image data in response")
    }
}
