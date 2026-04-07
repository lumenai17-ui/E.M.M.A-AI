package com.beemovil.skills

import android.os.Environment
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * VideoGenSkill — Phase 23-B: AI Video Generation.
 *
 * Supports:
 *   1. fal.ai → Kling 2.1 (text-to-video + image-to-video)
 *   2. fal.ai → Veo via proxy (if available)
 *
 * Videos are generated asynchronously (30-90 sec) via queue polling,
 * then downloaded to BeeMovil/videos/.
 *
 * Usage: "genera un video de un atardecer en la playa"
 *        "anima esta imagen" (with attached image path)
 */
class VideoGenSkill(
    private val getFalKey: () -> String = { "" }
) : BeeSkill {
    override val name = "video_gen"
    override val description = """Generate a short AI video (5-10 seconds) from a text description or an image.
Parameters:
  - 'prompt' (required): Detailed english description of the video to generate.
  - 'duration' (optional): '5' or '10' seconds. Default: 5
  - 'aspect_ratio' (optional): '16:9' (landscape), '9:16' (portrait), '1:1' (square). Default: 16:9
  - 'model' (optional): 'kling-standard' (faster, cheaper), 'kling-pro' (better quality). Default: kling-standard
  - 'image_path' (optional): Path to a local image to animate (image-to-video mode).
IMPORTANT: Video generation takes 30-90 seconds and has a cost (~$0.25-1.00 per video). The video is saved locally."""

    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "prompt":{"type":"string","description":"Detailed english description of the video scene. Be specific about motion, camera angle, lighting."},
            "duration":{"type":"string","enum":["5","10"],"description":"Video duration in seconds"},
            "aspect_ratio":{"type":"string","enum":["16:9","9:16","1:1"],"description":"Video aspect ratio"},
            "model":{"type":"string","enum":["kling-standard","kling-pro"],"description":"Kling model quality tier"},
            "image_path":{"type":"string","description":"Path to local image for image-to-video animation"}
        },"required":["prompt"]}
    """.trimIndent())

    companion object {
        private const val TAG = "VideoGen"
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val MAX_POLL_ATTEMPTS = 60  // 60 * 3s = 3 min max
        private const val POLL_INTERVAL_MS = 3000L

        fun getVideosDir(): File {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "BeeMovil/videos"
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

        val duration = params.optString("duration", "5")
        val aspectRatio = params.optString("aspect_ratio", "16:9")
        val model = params.optString("model", "kling-standard")
        val imagePath = params.optString("image_path", "")

        val falKey = getFalKey()
        if (falKey.isBlank()) {
            return JSONObject()
                .put("error", "No fal.ai API key configured")
                .put("suggestion", "Configure your fal.ai API key in Settings → Media IA to generate videos")
        }

        return try {
            if (imagePath.isNotBlank() && File(imagePath).exists()) {
                generateImageToVideo(prompt, imagePath, duration, aspectRatio, model, falKey)
            } else {
                generateTextToVideo(prompt, duration, aspectRatio, model, falKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Video gen error: ${e.message}", e)
            JSONObject().put("error", "Failed to generate video: ${e.message}")
        }
    }

    // ═══════════════════════════════════════
    // TEXT-TO-VIDEO via Kling 2.1
    // ═══════════════════════════════════════
    private fun generateTextToVideo(
        prompt: String, duration: String, aspectRatio: String,
        model: String, apiKey: String
    ): JSONObject {
        val endpoint = when (model) {
            "kling-pro" -> "fal-ai/kling-video/v2.1/pro/text-to-video"
            else -> "fal-ai/kling-video/v2.1/standard/text-to-video"
        }

        val body = JSONObject().apply {
            put("prompt", prompt)
            put("duration", duration)
            put("aspect_ratio", aspectRatio)
        }

        Log.d(TAG, "Text-to-video → $endpoint | ${duration}s $aspectRatio")
        return submitAndPoll(endpoint, body, apiKey, prompt)
    }

    // ═══════════════════════════════════════
    // IMAGE-TO-VIDEO via Kling 2.1
    // ═══════════════════════════════════════
    private fun generateImageToVideo(
        prompt: String, imagePath: String, duration: String,
        aspectRatio: String, model: String, apiKey: String
    ): JSONObject {
        val endpoint = when (model) {
            "kling-pro" -> "fal-ai/kling-video/v2.1/pro/image-to-video"
            else -> "fal-ai/kling-video/v2.1/standard/image-to-video"
        }

        // Encode image to base64 data URI
        val imageFile = File(imagePath)
        val imageBytes = imageFile.readBytes()
        val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val ext = imagePath.substringAfterLast('.').lowercase()
        val mimeType = when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        val dataUri = "data:$mimeType;base64,$base64"

        val body = JSONObject().apply {
            put("prompt", prompt)
            put("image_url", dataUri)
            put("duration", duration)
            put("aspect_ratio", aspectRatio)
        }

        Log.d(TAG, "Image-to-video → $endpoint | ${duration}s from $imagePath")
        return submitAndPoll(endpoint, body, apiKey, prompt)
    }

    // ═══════════════════════════════════════
    // ASYNC QUEUE: Submit → Poll → Download
    // ═══════════════════════════════════════
    private fun submitAndPoll(endpoint: String, body: JSONObject, apiKey: String, prompt: String): JSONObject {
        // 1. Submit to queue
        val submitRequest = Request.Builder()
            .url("https://queue.fal.run/$endpoint")
            .addHeader("Authorization", "Key $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val submitResponse = client.newCall(submitRequest).execute()
        val submitBody = submitResponse.body?.string() ?: throw Exception("Empty response")

        if (!submitResponse.isSuccessful) {
            val errMsg = try {
                JSONObject(submitBody).optString("detail", submitBody.take(300))
            } catch (_: Exception) { submitBody.take(300) }
            return JSONObject().put("error", "fal.ai error ${submitResponse.code}: $errMsg")
        }

        val submitJson = JSONObject(submitBody)

        // Check if already completed (unlikely for video but handle it)
        if (submitJson.has("video")) {
            return parseVideoResult(submitJson, prompt)
        }

        val requestId = submitJson.optString("request_id", "")
        if (requestId.isBlank()) {
            return JSONObject().put("error", "No request_id from fal.ai")
        }

        Log.d(TAG, "Video submitted: $requestId — polling...")

        // 2. Poll for completion
        val pollClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        for (attempt in 1..MAX_POLL_ATTEMPTS) {
            Thread.sleep(POLL_INTERVAL_MS)

            try {
                val statusRequest = Request.Builder()
                    .url("https://queue.fal.run/$endpoint/requests/$requestId/status")
                    .addHeader("Authorization", "Key $apiKey")
                    .get()
                    .build()

                val statusResponse = pollClient.newCall(statusRequest).execute()
                val statusBody = statusResponse.body?.string() ?: continue
                val statusJson = JSONObject(statusBody)
                val status = statusJson.optString("status", "")

                Log.d(TAG, "Poll #$attempt: status=$status")

                when (status) {
                    "COMPLETED" -> {
                        // 3. Fetch result
                        val resultRequest = Request.Builder()
                            .url("https://queue.fal.run/$endpoint/requests/$requestId")
                            .addHeader("Authorization", "Key $apiKey")
                            .get()
                            .build()

                        val resultResponse = client.newCall(resultRequest).execute()
                        val resultBody = resultResponse.body?.string()
                            ?: throw Exception("Empty result")
                        val resultJson = JSONObject(resultBody)

                        val result = parseVideoResult(resultJson, prompt)

                        // 4. Download video
                        val videoUrl = result.optString("video_url", "")
                        if (videoUrl.isNotBlank()) {
                            val localPath = downloadVideo(videoUrl, prompt)
                            if (localPath != null) {
                                result.put("local_path", localPath)
                                result.put("file_path", localPath)
                            }
                        }
                        return result
                    }
                    "FAILED" -> {
                        return JSONObject().put("error", "Video generation failed on the server")
                    }
                    // IN_QUEUE, IN_PROGRESS → keep polling
                }
            } catch (e: Exception) {
                Log.w(TAG, "Poll #$attempt error: ${e.message}")
            }
        }

        return JSONObject()
            .put("error", "Video generation timed out after ${MAX_POLL_ATTEMPTS * POLL_INTERVAL_MS / 1000} seconds")
            .put("request_id", requestId)
            .put("suggestion", "The video might still be processing. Try again in a moment.")
    }

    private fun parseVideoResult(json: JSONObject, prompt: String): JSONObject {
        // fal.ai Kling returns: { "video": { "url": "..." } }
        val videoObj = json.optJSONObject("video")
        if (videoObj != null) {
            val videoUrl = videoObj.optString("url", "")
            if (videoUrl.isNotBlank()) {
                return JSONObject()
                    .put("success", true)
                    .put("video_url", videoUrl)
                    .put("prompt", prompt)
                    .put("message", "Video generado con Kling AI. Guardado localmente.")
            }
        }

        // Alternative format: { "data": [{ "url": "..." }] }
        val data = json.optJSONArray("data")
        if (data != null && data.length() > 0) {
            val videoUrl = data.getJSONObject(0).optString("url", "")
            if (videoUrl.isNotBlank()) {
                return JSONObject()
                    .put("success", true)
                    .put("video_url", videoUrl)
                    .put("prompt", prompt)
                    .put("message", "Video generado. Guardado localmente.")
            }
        }

        return JSONObject().put("error", "No video data in response: ${json.toString().take(200)}")
    }

    // ═══════════════════════════════════════
    // DOWNLOAD VIDEO
    // ═══════════════════════════════════════
    private fun downloadVideo(videoUrl: String, prompt: String): String? {
        return try {
            val dir = getVideosDir()
            val timestamp = System.currentTimeMillis()
            val safeName = prompt.take(25).replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
            val fileName = "vid_${timestamp}_${safeName}.mp4"
            val file = File(dir, fileName)

            Log.d(TAG, "Downloading video to ${file.absolutePath}")

            val conn = URL(videoUrl).openConnection()
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.getInputStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            Log.d(TAG, "Video saved: ${file.absolutePath} (${file.length() / 1024} KB)")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Video download failed: ${e.message}")
            null
        }
    }
}
