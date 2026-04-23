package com.beemovil.plugins.builtins

import android.content.Context
import android.media.MediaPlayer
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
 * Text-to-Speech Plugin using Pollinations.ai (ElevenLabs v3 voices).
 *
 * Generates high-quality speech audio from text without requiring
 * a separate ElevenLabs or Deepgram API key.
 *
 * Audio is auto-played and saved to Downloads/EMMA/audio/.
 */
class PollinationsTTSPlugin(private val context: Context) : EmmaPlugin {

    override val id = "speak_with_ai_voice"
    private val TAG = "PollinationsTTS"

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Lee un texto en voz alta con una voz de IA de alta calidad. " +
                    "Úsalo cuando el usuario pida: 'lee esto en voz alta', 'dime con voz', " +
                    "'reproduce esto', 'léeme esto', 'habla esto'. " +
                    "El texto puede estar en cualquier idioma. Máximo 500 caracteres.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply {
                        put("type", "string")
                        put("description", "El texto a convertir en voz. Máximo 500 caracteres.")
                    })
                    put("voice", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().apply {
                            put("nova")     // Femenina, cálida
                            put("alloy")    // Neutra, profesional
                            put("echo")     // Masculina, profunda
                            put("shimmer")  // Femenina, suave
                            put("coral")    // Femenina, energética
                            put("sage")     // Masculina, tranquila
                        })
                        put("description", "Voz a usar. Default: nova (femenina cálida). " +
                                "Opciones: nova, alloy, echo, shimmer, coral, sage.")
                    })
                })
                put("required", JSONArray().put("text"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val text = (args["text"] as? String)?.take(500) 
            ?: return "❌ Falta el texto para leer en voz alta."
        val voice = args["voice"] as? String ?: "nova"

        if (!PollinationsClient.hasApiKey(context)) {
            return "❌ Se necesita una API key de Pollinations para la voz de IA. " +
                    "Ve a Settings → Pollinations y configura tu key gratuita desde enter.pollinations.ai"
        }

        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "TTS: '${text.take(50)}...' voice=$voice")

                val url = PollinationsClient.audioUrl(text, voice)
                val audioBytes = PollinationsClient.downloadMedia(context, url)
                    ?: return@withContext "❌ No se pudo generar el audio. Verifica tu API key de Pollinations."

                // Save to cache
                val timestamp = System.currentTimeMillis()
                val fileName = "emma_tts_${timestamp}.mp3"
                val cacheDir = File(context.cacheDir, "generated_audio").apply { mkdirs() }
                val localFile = File(cacheDir, fileName)

                FileOutputStream(localFile).use { fos ->
                    fos.write(audioBytes)
                }

                if (localFile.length() < 512) {
                    localFile.delete()
                    return@withContext "❌ El audio generado no es válido. Intenta con otro texto."
                }

                // Auto-play the audio
                try {
                    val mediaPlayer = MediaPlayer()
                    mediaPlayer.setDataSource(localFile.absolutePath)
                    mediaPlayer.prepare()
                    mediaPlayer.start()
                    mediaPlayer.setOnCompletionListener { it.release() }
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-play failed: ${e.message}")
                }

                // Copy to public Downloads/EMMA/audio/
                PublicFileWriter.copyToPublicDownloads(context, localFile, "audio/mpeg")

                Log.i(TAG, "TTS generated: ${localFile.length() / 1024}KB → $fileName")

                "🔊 Texto leído en voz alta con voz '$voice'"

            } catch (e: Exception) {
                Log.e(TAG, "TTS error: ${e.message}", e)
                "❌ Error generando voz: ${e.message}"
            }
        }
    }
}
