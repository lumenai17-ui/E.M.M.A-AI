package com.beemovil.skills

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Voice input manager using Android's built-in SpeechRecognizer.
 * Zero dependencies — uses Google's on-device or cloud STT.
 *
 * Usage:
 *   voiceInput.startListening { text -> viewModel.sendMessage(text) }
 */
class VoiceInputManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInput"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var onResult: ((String) -> Unit)? = null
    private var onPartial: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var onStateChange: ((Boolean) -> Unit)? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun initialize() {
        if (!isAvailable) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                    isListening = true
                    onStateChange?.invoke(true)
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Volume level — could use for visual feedback
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended")
                    isListening = false
                    onStateChange?.invoke(false)
                }

                override fun onError(error: Int) {
                    isListening = false
                    onStateChange?.invoke(false)
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                        SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permiso de micrófono"
                        SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió, intenta de nuevo"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                        SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
                        else -> "Error desconocido ($error)"
                    }
                    Log.e(TAG, "Recognition error: $errorMsg")
                    onError?.invoke(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    onStateChange?.invoke(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        Log.i(TAG, "Result: $text")
                        onResult?.invoke(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onPartial?.invoke(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        Log.i(TAG, "SpeechRecognizer initialized")
    }

    fun startListening(
        language: String = "es-MX",
        onPartialResult: ((String) -> Unit)? = null,
        onListeningState: ((Boolean) -> Unit)? = null,
        onErrorCallback: ((String) -> Unit)? = null,
        onFinalResult: (String) -> Unit
    ) {
        if (!hasPermission) {
            onErrorCallback?.invoke("Sin permiso de micrófono")
            return
        }
        if (!isAvailable) {
            onErrorCallback?.invoke("Speech recognition no disponible")
            return
        }
        if (isListening) {
            stopListening()
            return
        }

        this.onResult = onFinalResult
        this.onPartial = onPartialResult
        this.onError = onErrorCallback
        this.onStateChange = onListeningState

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.i(TAG, "Started listening ($language)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start: ${e.message}")
            onErrorCallback?.invoke("No se pudo iniciar: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        isListening = false
        onStateChange?.invoke(false)
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
