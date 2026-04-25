package com.beemovil.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.beemovil.media.PollinationsClient
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

/**
 * PollinationsTTS — Text-to-Speech using Pollinations.ai (ElevenLabs proxy).
 *
 * Free, premium-quality TTS that works via Pollinations' built-in API key.
 * Supports 6 voices (OpenAI-compatible names via ElevenLabs backend).
 *
 * Pipeline: Text → Pollinations API → MP3 bytes → MediaPlayer → Speaker
 *
 * Voices:
 *   - nova:    Femenina, cálida (DEFAULT)
 *   - alloy:   Neutra, profesional
 *   - echo:    Masculina, profunda
 *   - shimmer: Femenina, suave
 *   - coral:   Femenina, energética
 *   - sage:    Masculina, tranquila
 *
 * Cost: FREE (uses Pollinations shared API key)
 * Latency: ~1-3 seconds (HTTP round-trip + audio download)
 * Languages: Multilingual (ElevenLabs v2 backend)
 */
class PollinationsTTS(private val context: Context) {

    companion object {
        private const val TAG = "PollinationsTTS"

        const val VOICE_NOVA = "nova"
        const val VOICE_ALLOY = "alloy"
        const val VOICE_ECHO = "echo"
        const val VOICE_SHIMMER = "shimmer"
        const val VOICE_CORAL = "coral"
        const val VOICE_SAGE = "sage"
        const val DEFAULT_VOICE = VOICE_NOVA

        val AVAILABLE_VOICES = listOf(
            VoiceOption(VOICE_NOVA, "Nova", "Femenina, cálida"),
            VoiceOption(VOICE_ALLOY, "Alloy", "Neutra, profesional"),
            VoiceOption(VOICE_ECHO, "Echo", "Masculina, profunda"),
            VoiceOption(VOICE_SHIMMER, "Shimmer", "Femenina, suave"),
            VoiceOption(VOICE_CORAL, "Coral", "Femenina, energética"),
            VoiceOption(VOICE_SAGE, "Sage", "Masculina, tranquila")
        )
    }

    data class VoiceOption(val id: String, val name: String, val description: String)

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var isSpeaking = false

    /**
     * Speak text using Pollinations TTS.
     * Downloads MP3 audio and plays it through MediaPlayer.
     *
     * @param text The text to speak (max 500 chars, auto-truncated)
     * @param voice Voice ID (nova, alloy, echo, shimmer, coral, sage)
     * @param onDone Called when speech finishes (on Main thread)
     * @param onStart Called when speech starts playing (on Main thread)
     * @param onError Called on error with message (on Main thread) — caller should fallback
     */
    fun speak(
        text: String,
        voice: String = DEFAULT_VOICE,
        onDone: (() -> Unit)? = null,
        onStart: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        // Check internet connectivity
        if (!isOnline()) {
            onError?.invoke("Sin conexión a internet para TTS premium")
            return
        }

        stop()

        playbackJob = scope.launch {
            try {
                val cleanText = text
                    .replace(Regex("[\\p{So}\\p{Cn}]"), "") // Remove emojis
                    .replace(Regex("[*#`_]"), "")           // Remove markdown
                    .replace(Regex("\\[.*?]\\(.*?\\)"), "") // Remove links
                    .trim()
                    .take(500)

                if (cleanText.isBlank()) {
                    withContext(Dispatchers.Main) { onDone?.invoke() }
                    return@launch
                }

                Log.d(TAG, "TTS: '${cleanText.take(50)}...' voice=$voice")

                // Build URL and download audio
                val url = PollinationsClient.audioUrl(cleanText, voice)
                val audioBytes = PollinationsClient.downloadMedia(context, url)

                if (audioBytes == null || audioBytes.size < 512) {
                    Log.w(TAG, "Audio download failed or too small (${audioBytes?.size ?: 0} bytes)")
                    withContext(Dispatchers.Main) { onError?.invoke("Error descargando audio TTS") }
                    return@launch
                }

                Log.d(TAG, "Audio downloaded: ${audioBytes.size / 1024}KB")

                // Save to temp file (MediaPlayer needs a file or URL)
                val tempFile = File(context.cacheDir, "pollinations_tts_${System.currentTimeMillis()}.mp3")
                FileOutputStream(tempFile).use { it.write(audioBytes) }

                // Play on Main thread
                withContext(Dispatchers.Main) {
                    try {
                        isSpeaking = true
                        val player = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            setDataSource(tempFile.absolutePath)
                            prepare()

                            setOnCompletionListener {
                                isSpeaking = false
                                it.release()
                                mediaPlayer = null
                                tempFile.delete()
                                onDone?.invoke()
                            }

                            setOnErrorListener { mp, what, extra ->
                                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                isSpeaking = false
                                mp.release()
                                mediaPlayer = null
                                tempFile.delete()
                                onError?.invoke("Error reproduciendo audio")
                                true
                            }
                        }

                        mediaPlayer = player
                        onStart?.invoke()
                        player.start()
                        Log.i(TAG, "Playing TTS audio (${player.duration}ms)")

                    } catch (e: Exception) {
                        Log.e(TAG, "MediaPlayer setup failed: ${e.message}")
                        isSpeaking = false
                        tempFile.delete()
                        onError?.invoke("Error reproduciendo: ${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                // Expected on stop()
            } catch (e: Exception) {
                Log.e(TAG, "TTS error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Error TTS Pollinations: ${e.message}")
                }
            }
        }
    }

    /** Stop any current playback */
    fun stop() {
        playbackJob?.cancel()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        isSpeaking = false
    }

    /** Check if currently speaking */
    fun isSpeaking(): Boolean = isSpeaking

    /** Check internet connectivity */
    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Release resources */
    fun destroy() {
        stop()
        scope.cancel()
    }
}
