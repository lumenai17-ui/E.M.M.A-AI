package com.beemovil.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.beemovil.network.BeeHttpClient
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * DeepgramSTT — Speech-to-Text using Deepgram Nova-3 API.
 *
 * Replaces Android's SpeechRecognizer with server-side transcription.
 * Records raw audio from mic, sends to Deepgram REST API, returns transcript.
 *
 * Benefits over Google STT:
 * - Better accuracy for Spanish
 * - No dependency on Google Play Services
 * - Works on all Android devices
 * - Supports multi-language detection
 */
class DeepgramSTT(private val context: Context) {

    companion object {
        private const val TAG = "DeepgramSTT"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val STT_URL = "https://api.deepgram.com/v1/listen"
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onListeningState: ((Boolean) -> Unit)? = null

    fun startListening(
        apiKey: String,
        language: String = "es",
        onPartial: ((String) -> Unit)? = null,
        onResult: (String) -> Unit,
        onErrorCb: ((String) -> Unit)? = null,
        onState: ((Boolean) -> Unit)? = null
    ) {
        if (isRecording) {
            stopListening()
            return
        }

        this.onPartialResult = onPartial
        this.onFinalResult = onResult
        this.onError = onErrorCb
        this.onListeningState = onState

        if (apiKey.isBlank()) {
            onErrorCb?.invoke("Deepgram API key no configurada")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufferSize <= 0) {
            onErrorCb?.invoke("Error de audio: buffer invalido")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL, ENCODING, bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onErrorCb?.invoke("No se pudo inicializar el microfono")
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            onState?.invoke(true)
            Log.i(TAG, "Recording started ($language)")

            // Record in background
            recordingJob = scope.launch {
                val audioData = ByteArrayOutputStream()
                val buffer = ByteArray(bufferSize)
                var silenceFrames = 0
                val maxSilenceFrames = (SAMPLE_RATE * 2) / bufferSize  // ~2 seconds of silence
                var hasDetectedSpeech = false

                // Adaptive noise floor: calibrate during first 0.5s
                var noiseFloor = 500.0
                var calibrationFrames = 0
                val calibrationLimit = (SAMPLE_RATE / 2) / bufferSize  // ~0.5 seconds
                var calibrationSum = 0.0

                while (isRecording && isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        audioData.write(buffer, 0, bytesRead)

                        // Detect silence (adaptive threshold)
                        val rms = calculateRMS(buffer, bytesRead)

                        // Calibrate noise floor from initial samples
                        if (calibrationFrames < calibrationLimit) {
                            calibrationSum += rms
                            calibrationFrames++
                            if (calibrationFrames == calibrationLimit) {
                                noiseFloor = (calibrationSum / calibrationFrames) * 1.8
                                if (noiseFloor < 300) noiseFloor = 300.0  // Minimum threshold
                                Log.d(TAG, "Noise floor calibrated: $noiseFloor")
                            }
                            continue
                        }

                        if (rms < noiseFloor) {
                            silenceFrames++
                        } else {
                            silenceFrames = 0
                            hasDetectedSpeech = true
                        }

                        // Auto-stop after sustained silence ONLY if speech was detected
                        if (hasDetectedSpeech && silenceFrames > maxSilenceFrames && audioData.size() > bufferSize * 4) {
                            Log.i(TAG, "Silence after speech detected, stopping")
                            break
                        }

                        // Safety limit: 15 seconds max recording
                        if (audioData.size() > SAMPLE_RATE * 2 * 15) {
                            Log.i(TAG, "Max duration reached (15s)")
                            break
                        }

                        // If no speech detected after 8 seconds, stop (user isn't speaking)
                        if (!hasDetectedSpeech && audioData.size() > SAMPLE_RATE * 2 * 8) {
                            Log.i(TAG, "No speech detected after 8s, stopping")
                            break
                        }
                    }
                }

                // Stop recording
                stopRecordingHardware()

                // Transcribe
                if (audioData.size() > bufferSize * 2) {
                    withContext(Dispatchers.Main) {
                        onPartial?.invoke("Transcribiendo...")
                    }
                    val transcript = transcribe(apiKey, audioData.toByteArray(), language)
                    withContext(Dispatchers.Main) {
                        if (transcript.isNotBlank()) {
                            onResult(transcript)
                        } else {
                            onErrorCb?.invoke("No se detecto voz")
                        }
                        onState?.invoke(false)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onErrorCb?.invoke("Grabacion muy corta")
                        onState?.invoke(false)
                    }
                }
            }
        } catch (e: SecurityException) {
            onErrorCb?.invoke("Sin permiso de microfono")
        } catch (e: Exception) {
            Log.e(TAG, "Start error: ${e.message}")
            onErrorCb?.invoke("Error: ${e.message}")
        }
    }

    fun stopListening() {
        isRecording = false
        // The recording loop will exit and trigger transcription
    }

    fun destroy() {
        isRecording = false
        recordingJob?.cancel()
        stopRecordingHardware()
        scope.cancel()
    }

    private fun stopRecordingHardware() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) { android.util.Log.w("EmmaSwallowed", "ignored exception: ${e.message}") }
        audioRecord = null
        isRecording = false
    }

    private fun transcribe(apiKey: String, audioData: ByteArray, language: String): String {
        val langCode = when (language.lowercase()) {
            "es", "es-mx", "es-es" -> "es"
            "en", "en-us", "en-gb" -> "en"
            "pt", "pt-br" -> "pt-BR"
            "fr" -> "fr"
            "de" -> "de"
            "it" -> "it"
            else -> "es"
        }

        val url = "$STT_URL?model=nova-3&language=$langCode&smart_format=true&punctuate=true"

        val body = audioData.toRequestBody("audio/raw".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Token $apiKey")
            .addHeader("Content-Type", "audio/raw;encoding=linear16;sample_rate=$SAMPLE_RATE;channels=1")
            .post(body)
            .build()

        return try {
            val response = BeeHttpClient.default.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Deepgram STT error ${response.code}: $errorBody")
                return ""
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            val results = json.optJSONObject("results")
            val channels = results?.optJSONArray("channels")
            val channel = channels?.optJSONObject(0)
            val alternatives = channel?.optJSONArray("alternatives")
            val best = alternatives?.optJSONObject(0)
            best?.optString("transcript", "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed: ${e.message}")
            ""
        }
    }

    private fun calculateRMS(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                sum += sample * sample
            }
        }
        return Math.sqrt(sum / (length / 2))
    }
}
