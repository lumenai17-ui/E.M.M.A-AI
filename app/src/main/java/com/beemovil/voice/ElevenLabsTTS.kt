package com.beemovil.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.beemovil.network.BeeHttpClient
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ElevenLabsTTS(private val context: Context) {

    companion object {
        private const val TAG = "ElevenLabsTTS"
        private const val BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech/"
    }

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isSpeaking = false

    fun speak(
        text: String,
        apiKey: String,
        voiceId: String,
        onDone: (() -> Unit)? = null,
        onStart: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (text.isBlank()) return
        if (apiKey.isBlank() || voiceId.isBlank()) {
            onError?.invoke("Credenciales de ElevenLabs incompletas")
            return
        }

        stop()

        playbackJob = scope.launch {
            try {
                val cleanText = text
                    .replace(Regex("[*#`]"), "")
                    .replace(Regex("\\[.*?\\]\\(.*?\\)"), "")
                    .take(2000)

                if (cleanText.isBlank()) return@launch

                val audioData = synthesize(apiKey, voiceId, cleanText)
                if (audioData != null && audioData.isNotEmpty()) {
                    withContext(Dispatchers.Main) { onStart?.invoke() }
                    playAudio(audioData)
                    withContext(Dispatchers.Main) { onDone?.invoke() }
                } else {
                    withContext(Dispatchers.Main) { onError?.invoke("Null o Empty de ElevenLabs") }
                }
            } catch (e: CancellationException) {
                // Expected
            } catch (e: Exception) {
                Log.e(TAG, "Error TTS ElevenLabs: ${e.message}")
                withContext(Dispatchers.Main) { onError?.invoke("Error local ElevenLabs: ${e.message}") }
            } finally {
                isSpeaking = false
            }
        }
    }

    private fun synthesize(apiKey: String, voiceId: String, text: String): ByteArray? {
        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
            put("output_format", "pcm_24000")
            // Opcional: configuracion de voz
            put("voice_settings", JSONObject().apply {
                put("similarity_boost", 0.7)
                put("stability", 0.5)
                put("style", 0.0)
                put("use_speaker_boost", true)
            })
        }

        val url = "\$BASE_URL\$voiceId"
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .post(body)
            .build()

        return try {
            val response = BeeHttpClient.default.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                Log.e(TAG, "Error HTTP ${response.code} en ElevenLabs: $err")
                throw Exception("HTTP ${response.code}: $err")
            }
            response.body?.bytes()
        } catch (e: Exception) {
            Log.e(TAG, "Http Exception ElevenLabs: ${e.message}")
            throw e
        }
    }

    private suspend fun playAudio(audioData: ByteArray) {
        val sampleRate = 24000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize.coerceAtLeast(audioData.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        isSpeaking = true
        audioTrack?.write(audioData, 0, audioData.size)
        audioTrack?.play()

        val durationMs = (audioData.size.toLong() * 1000) / (sampleRate * 2)
        delay(durationMs + 200)

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        isSpeaking = false
    }

    fun stop() {
        playbackJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        isSpeaking = false
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
