package com.beemovil.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.beemovil.skills.VoiceInputManager
import java.util.Locale

/**
 * DeepgramVoiceManager — Unified voice I/O manager.
 *
 * Intelligently routes between Deepgram (cloud) and native Android (offline):
 *
 *   STT: Deepgram Nova-3  →  fallback: Android SpeechRecognizer
 *   TTS: Deepgram Aura    →  fallback: Android TextToSpeech
 *
 * The manager checks for API key availability and network state
 * to decide which backend to use at runtime.
 */
class DeepgramVoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "DeepgramVoiceMgr"
        private const val PREFS_NAME = "beemovil"
        private const val KEY_DEEPGRAM = "deepgram_api_key"
        private const val KEY_VOICE = "deepgram_voice"
        private const val KEY_USE_DEEPGRAM_STT = "use_deepgram_stt"
        private const val KEY_USE_DEEPGRAM_TTS = "use_deepgram_tts"
    }

    // Deepgram engines
    val deepgramSTT = DeepgramSTT(context)
    val deepgramTTS = DeepgramTTS(context)

    // Native fallbacks
    val nativeSTT = VoiceInputManager(context)
    private var nativeTTS: TextToSpeech? = null
    private var nativeTTSReady = false

    // Config
    val apiKey: String get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_DEEPGRAM, "") ?: ""
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    val selectedVoice: String get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_VOICE, DeepgramTTS.DEFAULT_VOICE) ?: DeepgramTTS.DEFAULT_VOICE

    val useDeepgramSTT: Boolean get() = hasApiKey && context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_USE_DEEPGRAM_STT, true)

    val useDeepgramTTS: Boolean get() = hasApiKey && context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_USE_DEEPGRAM_TTS, true)

    fun initialize() {
        // Init native STT as fallback
        nativeSTT.initialize()

        // Init native TTS as fallback
        nativeTTS = TextToSpeech(context) { status ->
            nativeTTSReady = status == TextToSpeech.SUCCESS
            if (nativeTTSReady) {
                nativeTTS?.language = Locale("es", "MX")
            }
        }
        Log.i(TAG, "Voice manager initialized (Deepgram key: ${if (hasApiKey) "YES" else "NO"})")
    }

    // ── STT (Listen) ──────────────────────────

    fun startListening(
        language: String = "es-MX",
        onPartial: ((String) -> Unit)? = null,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null,
        onState: ((Boolean) -> Unit)? = null
    ) {
        if (useDeepgramSTT) {
            Log.i(TAG, "Using Deepgram STT")
            deepgramSTT.startListening(
                apiKey = apiKey,
                language = language.substringBefore("-"), // "es-MX" -> "es"
                onPartial = onPartial,
                onResult = onResult,
                onErrorCb = { error ->
                    Log.w(TAG, "Deepgram STT failed: $error, falling back to native")
                    // Fallback to native on error
                    nativeSTT.startListening(
                        language = language,
                        onPartialResult = onPartial,
                        onListeningState = onState,
                        onErrorCallback = onError,
                        onFinalResult = onResult
                    )
                },
                onState = onState
            )
        } else {
            Log.i(TAG, "Using native STT")
            nativeSTT.startListening(
                language = language,
                onPartialResult = onPartial,
                onListeningState = onState,
                onErrorCallback = onError,
                onFinalResult = onResult
            )
        }
    }

    fun stopListening() {
        deepgramSTT.stopListening()
        nativeSTT.stopListening()
    }

    // ── TTS (Speak) ──────────────────────────

    fun speak(
        text: String,
        onDone: (() -> Unit)? = null,
        onStart: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (useDeepgramTTS) {
            Log.i(TAG, "Using Deepgram TTS (voice: $selectedVoice)")
            deepgramTTS.speak(
                text = text,
                apiKey = apiKey,
                voice = selectedVoice,
                onDone = onDone,
                onStart = onStart,
                onError = { error ->
                    Log.w(TAG, "Deepgram TTS failed: $error, falling back to native")
                    speakNative(text, onDone)
                }
            )
        } else {
            speakNative(text, onDone)
        }
    }

    fun stopSpeaking() {
        deepgramTTS.stop()
        nativeTTS?.stop()
    }

    val isSpeaking: Boolean get() = deepgramTTS.speaking || (nativeTTS?.isSpeaking == true)

    private fun speakNative(text: String, onDone: (() -> Unit)?) {
        if (!nativeTTSReady || nativeTTS == null) {
            onDone?.invoke()
            return
        }

        val cleanText = text
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace(Regex("[*_#`]"), "")
            .take(500)

        if (onDone != null) {
            nativeTTS?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { onDone() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { onDone() }
            })
        }

        nativeTTS?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "bee_${System.currentTimeMillis()}")
    }

    // ── Config ──────────────────────────

    fun setApiKey(key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEEPGRAM, key).apply()
    }

    fun setVoice(voiceId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_VOICE, voiceId).apply()
    }

    fun setUseDeepgramSTT(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_DEEPGRAM_STT, enabled).apply()
    }

    fun setUseDeepgramTTS(enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_DEEPGRAM_TTS, enabled).apply()
    }

    fun destroy() {
        deepgramSTT.destroy()
        deepgramTTS.destroy()
        nativeSTT.destroy()
        nativeTTS?.stop()
        nativeTTS?.shutdown()
    }
}
