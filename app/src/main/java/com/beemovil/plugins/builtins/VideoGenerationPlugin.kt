package com.beemovil.plugins.builtins

import android.content.Context
import android.util.Log
import com.beemovil.files.PublicFileWriter
import com.beemovil.llm.ToolDefinition
import com.beemovil.media.PollinationsClient
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Video Generation Plugin for E.M.M.A.
 *
 * Uses Pollinations.ai to generate short AI videos via LTX-2.3 (free model).
 * Videos are saved to Downloads/EMMA/videos/ and returned as file attachment.
 */
class VideoGenerationPlugin(private val context: Context) : EmmaPlugin {

    override val id = "generate_ai_video"
    private val TAG = "VideoGenPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Genera un video corto con inteligencia artificial a partir de una descripción. " +
                    "Úsalo cuando el usuario pida: 'genera un video', 'crea un clip', 'haz un video de...', " +
                    "'anima esto'. Los videos son de 3-10 segundos. " +
                    "El prompt DEBE estar en inglés para mejores resultados.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("prompt", JSONObject().apply {
                        put("type", "string")
                        put("description", "Descripción detallada de la escena del video. DEBE estar en inglés. " +
                                "Ej: 'A golden sunset over the ocean with gentle waves, cinematic 4K'")
                    })
                    put("duration", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Duración en segundos (default: 5). Rango: 3-10.")
                    })
                    put("aspect_ratio", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().apply {
                            put("16:9")
                            put("9:16")
                        })
                        put("description", "Aspecto del video: '16:9' (horizontal/landscape) o '9:16' (vertical/portrait). Default: 16:9")
                    })
                })
                put("required", JSONArray().put("prompt"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val prompt = args["prompt"] as? String ?: return "❌ Falta la descripción del video."
        val duration = (args["duration"] as? Number)?.toInt()?.coerceIn(3, 10) ?: 5
        val aspectRatio = args["aspect_ratio"] as? String ?: "16:9"

        if (!PollinationsClient.hasApiKey(context)) {
            return "❌ Se necesita una API key de Pollinations para generar videos. " +
                    "Ve a Settings → Pollinations y configura tu key gratuita desde enter.pollinations.ai"
        }

        return withContext(Dispatchers.IO) {
            try {
                // Enhance prompt for video quality
                val enhancedPrompt = "$prompt, smooth motion, high quality, cinematic"
                Log.i(TAG, "Generating video: '$enhancedPrompt' (${duration}s, $aspectRatio)")

                val url = PollinationsClient.videoUrl(enhancedPrompt, "ltx-2", duration, aspectRatio)
                val videoBytes = PollinationsClient.downloadMedia(context, url, useHeavyClient = true)
                    ?: return@withContext "❌ No se pudo generar el video. Intenta con otro prompt o verifica tu API key."

                // Save to cache
                val timestamp = System.currentTimeMillis()
                val fileName = "emma_video_${timestamp}.mp4"
                val cacheDir = File(context.cacheDir, "generated_videos").apply { mkdirs() }
                val localFile = File(cacheDir, fileName)

                FileOutputStream(localFile).use { fos ->
                    fos.write(videoBytes)
                }

                // Verify minimum size (valid video should be > 10KB)
                if (localFile.length() < 10_240) {
                    localFile.delete()
                    return@withContext "❌ El video generado no es válido. Intenta con otro prompt."
                }

                // Copy to public Downloads/EMMA/videos/
                PublicFileWriter.copyToPublicDownloads(context, localFile, "video/mp4")

                val sizeMB = localFile.length() / (1024 * 1024f)
                Log.i(TAG, "Video generated: ${String.format("%.1f", sizeMB)}MB → $fileName")

                "TOOL_CALL::file_generated::${localFile.absolutePath}"

            } catch (e: Exception) {
                Log.e(TAG, "Video generation error: ${e.message}", e)
                "❌ Error generando video: ${e.message}"
            }
        }
    }
}
