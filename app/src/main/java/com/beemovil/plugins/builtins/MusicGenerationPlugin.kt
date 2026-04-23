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
 * Music Generation Plugin for E.M.M.A.
 *
 * Uses Pollinations.ai to generate music and songs via:
 *   - ElevenLabs Music (studio-grade, text-to-music)
 *   - ACE-Step (open-source, supports lyrics)
 *
 * Generated audio is saved to Downloads/EMMA/music/ and returned as file attachment.
 */
class MusicGenerationPlugin(private val context: Context) : EmmaPlugin {

    override val id = "generate_ai_music"
    private val TAG = "MusicGenPlugin"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Genera música o canciones con inteligencia artificial a partir de una descripción. " +
                    "Úsalo cuando el usuario pida: 'genera música', 'crea una canción', 'haz un beat', " +
                    "'compón algo', 'haz música de fondo'. " +
                    "El prompt DEBE estar en inglés para mejores resultados.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("prompt", JSONObject().apply {
                        put("type", "string")
                        put("description", "Descripción detallada de la música a generar. DEBE estar en inglés. " +
                                "Incluye género, mood, instrumentos. Ej: 'upbeat electronic dance music with synth pads and energetic drums'")
                    })
                    put("duration", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Duración en segundos (default: 30). Rango: 5-180.")
                    })
                    put("model", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().apply {
                            put("elevenmusic")
                            put("acestep")
                        })
                        put("description", "Modelo: 'elevenmusic' (studio quality) o 'acestep' (open-source, soporta letras). Default: elevenmusic")
                    })
                })
                put("required", JSONArray().put("prompt"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val prompt = args["prompt"] as? String ?: return "❌ Falta la descripción de la música."
        val duration = (args["duration"] as? Number)?.toInt()?.coerceIn(5, 180) ?: 30
        val model = args["model"] as? String ?: "elevenmusic"

        if (!PollinationsClient.hasApiKey(context)) {
            return "❌ Se necesita una API key de Pollinations para generar música. " +
                    "Ve a Settings → Pollinations y configura tu key gratuita desde enter.pollinations.ai"
        }

        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Generating music: '$prompt' (${duration}s, model=$model)")

                val url = PollinationsClient.musicUrl(prompt, model, duration)
                val audioBytes = PollinationsClient.downloadMedia(context, url, useHeavyClient = true)
                    ?: return@withContext "❌ No se pudo generar la música. Intenta con otro prompt o verifica tu API key."

                // Save to cache
                val timestamp = System.currentTimeMillis()
                val fileName = "emma_music_${timestamp}.mp3"
                val cacheDir = File(context.cacheDir, "generated_music").apply { mkdirs() }
                val localFile = File(cacheDir, fileName)

                FileOutputStream(localFile).use { fos ->
                    fos.write(audioBytes)
                }

                // Verify minimum size (valid MP3 should be > 1KB)
                if (localFile.length() < 1024) {
                    localFile.delete()
                    return@withContext "❌ La música generada no es válida. Intenta con otro prompt."
                }

                // Copy to public Downloads/EMMA/ and use that path for the chat
                val publicPath = PublicFileWriter.copyToPublicDownloads(context, localFile, "audio/mpeg")

                Log.i(TAG, "Music generated: ${localFile.length() / 1024}KB → $fileName (public: $publicPath)")

                "TOOL_CALL::file_generated::$publicPath"

            } catch (e: Exception) {
                Log.e(TAG, "Music generation error: ${e.message}", e)
                "❌ Error generando música: ${e.message}"
            }
        }
    }
}
