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

/**
 * DeepgramTTS — Text-to-Speech using Deepgram Aura API.
 *
 * Replaces Android's built-in TTS with server-side voice synthesis.
 * Supports multiple voices and languages with natural-sounding output.
 *
 * Voices: aura-asteria-en (English female), aura-luna-en (English female),
 *         aura-stella-en (English female), aura-orion-en (English male),
 *         aura-arcas-en (English male)
 *
 * Note: Deepgram TTS currently supports English voices.
 * For Spanish, we can use the voice with Spanish text (it handles it reasonably well)
 * or fall back to Android TTS for Spanish content.
 */
class DeepgramTTS(private val context: Context) {

    companion object {
        private const val TAG = "DeepgramTTS"
        private const val TTS_URL = "https://api.deepgram.com/v1/speak"
        const val VOICE_ASTERIA = "aura-asteria-en"  // Female, warm
        const val VOICE_LUNA = "aura-luna-en"         // Female, soft
        const val VOICE_STELLA = "aura-stella-en"     // Female, confident
        const val VOICE_ORION = "aura-orion-en"       // Male, deep
        const val VOICE_ARCAS = "aura-arcas-en"       // Male, friendly
        const val DEFAULT_VOICE = VOICE_ASTERIA

        val AVAILABLE_VOICES = listOf(
            VoiceOption(VOICE_ASTERIA, "Asteria", "Femenina, calida"),
            VoiceOption(VOICE_LUNA, "Luna", "Femenina, suave"),
            VoiceOption(VOICE_STELLA, "Stella", "Femenina, segura"),
            VoiceOption(VOICE_ORION, "Orion", "Masculina, profunda"),
            VoiceOption(VOICE_ARCAS, "Arcas", "Masculina, amigable")
        )
    }

    data class VoiceOption(val id: String, val name: String, val description: String)

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isSpeaking = false
    var onSpeakingDone: (() -> Unit)? = null
    var onSpeakingStart: (() -> Unit)? = null

    fun speak(
        text: String,
        apiKey: String,
        voice: String = DEFAULT_VOICE,
        onDone: (() -> Unit)? = null,
        onStart: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (text.isBlank()) return
        if (apiKey.isBlank()) {
            onError?.invoke("Deepgram API key no configurada")
            return
        }

        // Stop any current playback
        stop()

        this.onSpeakingDone = onDone
        this.onSpeakingStart = onStart

        playbackJob = scope.launch {
            try {
                // Clean text for TTS
                val cleanText = text
                    .replace(Regex("[\\p{So}\\p{Cn}]"), "") // Remove emojis
                    .replace(Regex("[*_#`]"), "")             // Remove markdown
                    .replace(Regex("\\[.*?\\]\\(.*?\\)"), "") // Remove links
                    .take(1000) // Reasonable limit

                if (cleanText.isBlank()) return@launch

                val audioData = synthesize(apiKey, cleanText, voice)
                if (audioData != null && audioData.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onStart?.invoke()
                    }
                    playAudio(audioData)
                    withContext(Dispatchers.Main) {
                        onDone?.invoke()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError?.invoke("No se pudo generar audio")
                    }
                }
            } catch (e: CancellationException) {
                // Cancelled, expected
            } catch (e: Exception) {
                Log.e(TAG, "TTS error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError?.invoke("Error TTS: ${e.message}")
                }
            } finally {
                isSpeaking = false
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
        audioTrack = null
        isSpeaking = false
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    val speaking: Boolean get() = isSpeaking

    private fun synthesize(apiKey: String, text: String, voice: String): ByteArray? {
        val payload = JSONObject().apply {
            put("text", text)
        }

        val url = "$TTS_URL?model=$voice&encoding=linear16&sample_rate=24000"
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Token $apiKey")
            .post(body)
            .build()

        return try {
            val response = BeeHttpClient.default.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Deepgram TTS error ${response.code}: $errorBody")
                return null
            }
            response.body?.bytes()
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed: ${e.message}")
            null
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

        // Wait for playback to complete
        val durationMs = (audioData.size.toLong() * 1000) / (sampleRate * 2) // 16-bit mono
        delay(durationMs + 200) // Small buffer

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
        audioTrack = null
        isSpeaking = false
    }
}
