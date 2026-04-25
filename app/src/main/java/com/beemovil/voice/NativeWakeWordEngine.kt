package com.beemovil.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * NativeWakeWordEngine — Voice Intelligence Phase V4
 *
 * Uses Android's built-in SpeechRecognizer in a continuous restart loop
 * to detect the wake phrase "Hello Emma".
 *
 * How it works:
 * 1. Starts SpeechRecognizer in partial results mode
 * 2. Checks every partial/final result for the wake phrase
 * 3. When detected → fires callback → pauses
 * 4. When not detected → auto-restarts after a brief pause
 *
 * Trade-offs:
 * + Zero dependencies (works on every Android device)
 * + Multilingual (uses device's speech recognition)
 * - Occasional beep between restarts (Google services)
 * - Slightly higher battery than dedicated wake word engine
 * - Requires MicrophoneArbiter WAKE_WORD priority
 *
 * The engine uses MicrophoneArbiter at WAKE_WORD priority (lowest),
 * so it automatically yields when PUSH_TO_TALK or CONVERSATION claims the mic.
 */
class NativeWakeWordEngine(
    private val context: Context
) : WakeWordEngine {

    companion object {
        private const val TAG = "NativeWakeWord"
        private const val RESTART_DELAY_MS = 500L // Delay between listen cycles
        private const val MAX_CONSECUTIVE_ERRORS = 5
    }

    override val id = "native"
    override val displayName = "Nativo (Android)"
    override val wakePhrase = "hello emma"

    // Variants the engine accepts (fuzzy matching for accented/noisy environments)
    private val wakeVariants = listOf(
        "hello emma",
        "hola emma",
        "hey emma",
        "hello em",
        "helloema",
        "holaema"
    )

    private var recognizer: SpeechRecognizer? = null
    private var isActive = false
    private var onWakeDetected: (() -> Unit)? = null
    private var onErr: ((String) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var consecutiveErrors = 0

    override fun start(
        onWakeWordDetected: () -> Unit,
        onError: ((String) -> Unit)?
    ) {
        if (isActive) {
            Log.w(TAG, "Already listening — ignoring start()")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer not available on this device")
            onError?.invoke("Reconocimiento de voz no disponible")
            return
        }

        this.onWakeDetected = onWakeWordDetected
        this.onErr = onError
        this.isActive = true
        this.consecutiveErrors = 0

        Log.i(TAG, "Wake word engine started — listening for '$wakePhrase'")
        startRecognitionCycle()
    }

    override fun stop() {
        Log.i(TAG, "Wake word engine stopped")
        isActive = false
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer: ${e.message}")
        }
        recognizer = null

        // Release mic from arbiter
        MicrophoneArbiter.releaseMic(MicrophoneArbiter.MicOwner.WAKE_WORD)
    }

    override fun isListening(): Boolean = isActive

    override fun destroy() {
        stop()
        onWakeDetected = null
        onErr = null
    }

    // --- Internal: Recognition Cycle ---

    private fun startRecognitionCycle() {
        if (!isActive) return

        // Acquire mic through arbiter (lowest priority)
        if (!MicrophoneArbiter.requestMic(MicrophoneArbiter.MicOwner.WAKE_WORD, "WakeWordEngine")) {
            Log.d(TAG, "Mic busy — will retry in ${RESTART_DELAY_MS * 4}ms")
            handler.postDelayed({ startRecognitionCycle() }, RESTART_DELAY_MS * 4)
            return
        }

        handler.post {
            try {
                // Create fresh recognizer each cycle (more reliable than reusing)
                recognizer?.destroy()
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer?.setRecognitionListener(wakeWordListener)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Short silence for faster turnover
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                }

                recognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recognition: ${e.message}")
                MicrophoneArbiter.releaseMic(MicrophoneArbiter.MicOwner.WAKE_WORD)
                scheduleRestart()
            }
        }
    }

    private fun scheduleRestart() {
        if (!isActive) return
        handler.postDelayed({ startRecognitionCycle() }, RESTART_DELAY_MS)
    }

    private fun checkForWakeWord(text: String): Boolean {
        val normalized = text.lowercase().trim()
        return wakeVariants.any { variant ->
            normalized.contains(variant)
        }
    }

    private val wakeWordListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Listening for wake word...")
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches.joinToString(" ")
                Log.d(TAG, "Partial: $text")
                if (checkForWakeWord(text)) {
                    Log.i(TAG, "🎯 WAKE WORD DETECTED (partial): '$text'")
                    handleWakeWordDetected()
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches.joinToString(" ")
                Log.d(TAG, "Final: $text")
                if (checkForWakeWord(text)) {
                    Log.i(TAG, "🎯 WAKE WORD DETECTED (final): '$text'")
                    handleWakeWordDetected()
                    return
                }
            }

            // No wake word — release mic and restart cycle
            MicrophoneArbiter.releaseMic(MicrophoneArbiter.MicOwner.WAKE_WORD)
            scheduleRestart()
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error ($error)"
            }

            // ERROR_NO_MATCH and ERROR_SPEECH_TIMEOUT are normal (nobody spoke)
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Log.d(TAG, "Normal cycle end: $errorMsg")
            } else {
                Log.w(TAG, "Recognition error: $errorMsg")
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    Log.e(TAG, "Too many consecutive errors — pausing for 5s")
                    onErr?.invoke("Wake word pausado: $errorMsg")
                    MicrophoneArbiter.releaseMic(MicrophoneArbiter.MicOwner.WAKE_WORD)
                    handler.postDelayed({
                        consecutiveErrors = 0
                        startRecognitionCycle()
                    }, 5000)
                    return
                }
            }

            MicrophoneArbiter.releaseMic(MicrophoneArbiter.MicOwner.WAKE_WORD)
            scheduleRestart()
        }
    }

    private fun handleWakeWordDetected() {
        // Stop listening temporarily
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
        } catch (_: Exception) {}

        // Release mic so ConversationEngine can take it
        MicrophoneArbiter.releaseMic(MicrophoneArbiter.MicOwner.WAKE_WORD)

        // Fire callback
        onWakeDetected?.invoke()

        // Note: The service will re-start us when conversation ends
    }
}
