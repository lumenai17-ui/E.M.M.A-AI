package com.beemovil.plugins.builtins

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.beemovil.files.PublicFileWriter
import com.beemovil.llm.ToolDefinition
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Sprint 6: Image Generation Plugin for E.M.M.A.
 *
 * Uses Pollinations.ai (free, no API key needed) for text-to-image generation.
 * Falls back gracefully if the service is unavailable.
 *
 * Generated images are:
 *   1. Saved to internal cache
 *   2. Copied to Downloads/EMMA/ via PublicFileWriter for visibility
 *   3. Returned as a TOOL_CALL::file_generated signal for chat attachment
 */
class ImageGenerationPlugin(private val context: Context) : EmmaPlugin {

    override val id = "generate_ai_image"
    private val TAG = "ImageGenPlugin"

    companion object {
        private const val POLLINATIONS_URL = "https://image.pollinations.ai/prompt/"
        private const val DEFAULT_WIDTH = 1024
        private const val DEFAULT_HEIGHT = 1024
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // Image gen can be slow
        .build()

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Genera una imagen con inteligencia artificial a partir de una descripción de texto (text-to-image). " +
                    "Úsalo cuando el usuario pida: 'genera una imagen de...', 'dibuja...', 'crea una ilustración de...', " +
                    "'hazme un logo', 'genera un arte de...'. " +
                    "El prompt DEBE estar en inglés para mejores resultados.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("prompt", JSONObject().apply {
                        put("type", "string")
                        put("description", "Descripción detallada de la imagen a generar. DEBE estar en inglés. " +
                                "Incluye estilo artístico, colores, composición. Ej: 'A futuristic city at sunset, cyberpunk style, neon lights, 4k quality'")
                    })
                    put("style", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().apply {
                            put("photorealistic")
                            put("digital_art")
                            put("anime")
                            put("oil_painting")
                            put("watercolor")
                            put("3d_render")
                            put("pixel_art")
                            put("sketch")
                            put("logo")
                            put("flat_design")
                        })
                        put("description", "Estilo visual de la imagen.")
                    })
                    put("width", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Ancho en píxeles (default: 1024). Rango: 256-1536.")
                    })
                    put("height", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Alto en píxeles (default: 1024). Rango: 256-1536.")
                    })
                })
                put("required", JSONArray().put("prompt"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val prompt = args["prompt"] as? String ?: return "❌ Falta el prompt para generar la imagen."
        val style = args["style"] as? String ?: "digital_art"
        val width = (args["width"] as? Number)?.toInt()?.coerceIn(256, 1536) ?: DEFAULT_WIDTH
        val height = (args["height"] as? Number)?.toInt()?.coerceIn(256, 1536) ?: DEFAULT_HEIGHT

        return withContext(Dispatchers.IO) {
            try {
                // Build enhanced prompt with style
                val enhancedPrompt = buildEnhancedPrompt(prompt, style)
                Log.i(TAG, "Generating image: $enhancedPrompt (${width}x${height})")

                // Download image from Pollinations
                val imageBytes = downloadImage(enhancedPrompt, width, height)
                    ?: return@withContext "❌ No se pudo generar la imagen. El servicio no está disponible."

                // Save to internal cache
                val timestamp = System.currentTimeMillis()
                val fileName = "emma_generated_${timestamp}.png"
                val cacheDir = File(context.cacheDir, "generated_images").apply { mkdirs() }
                val localFile = File(cacheDir, fileName)

                FileOutputStream(localFile).use { fos ->
                    fos.write(imageBytes)
                }

                // Verify it's a valid image
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(localFile.absolutePath, options)
                if (options.outWidth <= 0) {
                    localFile.delete()
                    return@withContext "❌ La imagen generada no es válida. Intenta con otro prompt."
                }

                // Copy to public Downloads/EMMA/
                val publicUri = PublicFileWriter.copyToPublicDownloads(
                    context, localFile, "image/png"
                )

                Log.i(TAG, "Image generated: ${options.outWidth}x${options.outHeight} → $fileName")

                // Return signal for chat attachment
                "TOOL_CALL::file_generated::${localFile.absolutePath}"

            } catch (e: Exception) {
                Log.e(TAG, "Image generation error: ${e.message}", e)
                "❌ Error generando imagen: ${e.message}"
            }
        }
    }

    private fun downloadImage(prompt: String, width: Int, height: Int): ByteArray? {
        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        val url = "${POLLINATIONS_URL}${encodedPrompt}?width=$width&height=$height&nologo=true&seed=${System.currentTimeMillis() % 100000}"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            response.body?.bytes()
        } else {
            Log.e(TAG, "Pollinations error: ${response.code}")
            response.close()
            null
        }
    }

    private fun buildEnhancedPrompt(basePrompt: String, style: String): String {
        val styleModifier = when (style) {
            "photorealistic" -> "photorealistic, ultra detailed, 8k resolution, studio lighting"
            "digital_art" -> "digital art, vibrant colors, highly detailed, artstation quality"
            "anime" -> "anime style, studio ghibli inspired, cel shading, vibrant"
            "oil_painting" -> "oil painting style, classical art, rich textures, museum quality"
            "watercolor" -> "watercolor painting, soft edges, fluid colors, artistic"
            "3d_render" -> "3D render, octane render, volumetric lighting, high detail"
            "pixel_art" -> "pixel art, retro gaming style, 16-bit aesthetic"
            "sketch" -> "pencil sketch, hand drawn, fine lines, artistic"
            "logo" -> "logo design, clean vector style, minimalist, professional, white background"
            "flat_design" -> "flat design, minimal, clean lines, modern illustration"
            else -> "digital art, high quality"
        }
        return "$basePrompt, $styleModifier"
    }
}
