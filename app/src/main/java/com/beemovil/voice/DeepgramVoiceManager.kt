package com.beemovil.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.beemovil.security.SecurePrefs
import java.util.Locale

class DeepgramVoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "DeepgramVoiceMgr"
        private const val PREFS_NAME = "beemovil"
        private const val KEY_DEEPGRAM = "deepgram_api_key"
        private const val KEY_VOICE = "deepgram_voice"
        private const val KEY_USE_DEEPGRAM_STT = "use_deepgram_stt"
        private const val KEY_USE_DEEPGRAM_TTS = "use_deepgram_tts"
        const val KEY_ELEVENLABS_API = "elevenlabs_api_key"
        const val KEY_ELEVENLABS_VOICE = "elevenlabs_voice_id"
    }

    // TTS/STT engines
    private val deepgramSTT = try { DeepgramSTT(context) } catch (e: Exception) { null }
    private val deepgramTTS = try { DeepgramTTS(context) } catch (e: Exception) { null }
    private val elevenLabsTTS = try { ElevenLabsTTS(context) } catch (e: Exception) { null }
    private val pollinationsTTS = try { PollinationsTTS(context) } catch (e: Exception) { null }

    private var nativeTTS: TextToSpeech? = null
    private var nativeTTSReady = false
    private var nativeSTT: android.speech.SpeechRecognizer? = null

    val apiKey: String get() = SecurePrefs.get(context).getString(KEY_DEEPGRAM, "") ?: ""
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    val selectedVoice: String get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_VOICE, "aura-asteria-en") ?: "aura-asteria-en"

    val useDeepgramSTT: Boolean get() = hasApiKey && context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_USE_DEEPGRAM_STT, true)

    val useDeepgramTTS: Boolean get() = hasApiKey && context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_USE_DEEPGRAM_TTS, true)
    
    // Pollinations Nova voice preference (default: nova)
    val novaVoice: String get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString("pollinations_tts_voice", PollinationsTTS.DEFAULT_VOICE) ?: PollinationsTTS.DEFAULT_VOICE
    
    // Whether to use Pollinations Nova as primary TTS (default: true)
    val usePollinationsTTS: Boolean get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean("use_pollinations_tts", false)
        
    val elevenLabsKey: String get() = SecurePrefs.get(context).getString(KEY_ELEVENLABS_API, "") ?: ""
    val elevenLabsVoice: String get() = SecurePrefs.get(context).getString(KEY_ELEVENLABS_VOICE, "") ?: ""

    fun initialize() {
        nativeTTS = TextToSpeech(context) { status ->
            nativeTTSReady = status == TextToSpeech.SUCCESS
            if (nativeTTSReady) {
                // Respeta el idioma nativo del sistema operativo
                nativeTTS?.language = Locale.getDefault()
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                nativeSTT = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
                nativeSTTReady = true
            }
        }
    }

    // R3-1: Expose native STT availability for VisionVoiceController check
    private var nativeSTTReady = false
    fun hasNativeSTT(): Boolean = nativeSTTReady || android.speech.SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Start listening with MicrophoneArbiter gate.
     * @param micOwner Who is requesting the mic (defaults to PUSH_TO_TALK for backward compat)
     * @param micTag Human-readable tag for debug logging
     */
    fun startListening(
        language: String = "es-MX",
        onPartial: ((String) -> Unit)? = null,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null,
        onState: ((Boolean) -> Unit)? = null,
        micOwner: MicrophoneArbiter.MicOwner = MicrophoneArbiter.MicOwner.PUSH_TO_TALK,
        micTag: String = "DeepgramVoiceManager"
    ) {
        // V1: Gate through MicrophoneArbiter
        if (!MicrophoneArbiter.requestMic(micOwner, micTag)) {
            Log.w(TAG, "Mic denied by arbiter for $micOwner ($micTag)")
            onError?.invoke("Micrófono en uso por otra función")
            return
        }
        activeMicOwner = micOwner

        // Wrap onResult and onError to release mic when done
        val wrappedResult: (String) -> Unit = { text ->
            MicrophoneArbiter.releaseMic(micOwner)
            activeMicOwner = null
            onResult(text)
        }
        val wrappedError: (String) -> Unit = { error ->
            MicrophoneArbiter.releaseMic(micOwner)
            activeMicOwner = null
            onError?.invoke(error)
        }

        // R3-1c FIX: If Deepgram STT is enabled but null (init failed), fall back to native
        if (useDeepgramSTT && deepgramSTT != null) {
            deepgramSTT.startListening(
                apiKey = apiKey,
                language = language.substringBefore("-"),
                onPartial = onPartial,
                onResult = wrappedResult,
                onErrorCb = { error ->
                    Log.w(TAG, "Deepgram STT failed: $error, falling back to native")
                    // R3-1: Auto-fallback to native on Deepgram error
                    startNativeListening(language, wrappedResult, wrappedError)
                },
                onState = onState
            )
        } else {
            if (useDeepgramSTT && deepgramSTT == null) {
                Log.w(TAG, "Deepgram STT configured but unavailable, using native fallback")
            }
            startNativeListening(language, wrappedResult, wrappedError)
        }
    }

    private fun startNativeListening(
        language: String,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)?
    ) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (nativeSTT == null) {
                onError?.invoke("No STT fallback connected. Deepgram not enabled.")
                return@post
            }
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            nativeSTT?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    onError?.invoke("Native STT Error: $error")
                    // Recreate STT instance after error so next attempt doesn't fail silently
                    nativeSTT?.destroy()
                    nativeSTT = if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                        android.speech.SpeechRecognizer.createSpeechRecognizer(context)
                    } else null
                }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            nativeSTT?.startListening(intent)
        }
    }

    // V1: Track which MicOwner is active for release on stop
    private var activeMicOwner: MicrophoneArbiter.MicOwner? = null

    fun stopListening() {
        deepgramSTT?.stopListening()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            nativeSTT?.stopListening()
        }
        // V1: Release mic through arbiter
        activeMicOwner?.let { owner ->
            MicrophoneArbiter.releaseMic(owner)
            activeMicOwner = null
        }
    }

    /**
     * TTS Priority Chain:
     * 1. Pollinations Nova (FREE, premium, multilingual) — if internet
     * 2. ElevenLabs (PAID, premium, multilingual)        — if key configured
     * 3. Deepgram Aura (PAID, EN only)                   — if key configured + English
     * 4. Android Native (FREE, offline)                  — always available
     */
    fun speak(
        text: String,
        language: String = "es",
        onDone: (() -> Unit)? = null,
        onStart: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        // Priority 1: Pollinations Nova (free, premium)
        if (usePollinationsTTS && pollinationsTTS != null) {
            Log.d(TAG, "TTS: Trying Pollinations Nova (voice=$novaVoice)")
            pollinationsTTS.speak(
                text = text,
                voice = novaVoice,
                onDone = onDone,
                onStart = onStart,
                onError = { error ->
                    Log.w(TAG, "Pollinations TTS failed: $error, falling back")
                    speakElevenLabsOrBelow(text, language, onDone, onStart, onError)
                }
            )
            return
        }

        speakElevenLabsOrBelow(text, language, onDone, onStart, onError)
    }

    private fun speakElevenLabsOrBelow(
        text: String,
        language: String,
        onDone: (() -> Unit)?,
        onStart: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        // Priority 2: ElevenLabs (paid, premium)
        val eKey = elevenLabsKey
        val eVoice = elevenLabsVoice

        if (eKey.isNotBlank() && eVoice.isNotBlank() && elevenLabsTTS != null) {
            Log.d(TAG, "TTS: Trying ElevenLabs")
            elevenLabsTTS.speak(
                text = text,
                apiKey = eKey,
                voiceId = eVoice,
                onDone = onDone,
                onStart = onStart,
                onError = { error ->
                    Log.w(TAG, "ElevenLabs TTS failed: $error, falling back to Deepgram or Native")
                    speakDeepgramOrNative(text, language, onDone, onStart, onError)
                }
            )
        } else {
            speakDeepgramOrNative(text, language, onDone, onStart, onError)
        }
    }

    private fun speakDeepgramOrNative(
        text: String,
        language: String,
        onDone: (() -> Unit)?,
        onStart: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        val isEnglish = language.lowercase().startsWith("en")

        // Priority 3: Deepgram Aura (paid, EN only)
        if (useDeepgramTTS && isEnglish) {
            deepgramTTS?.speak(
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
            // Priority 4: Android Native (free, offline)
            speakNative(text, onDone)
        }
    }

    fun stopSpeaking() {
        pollinationsTTS?.stop()
        elevenLabsTTS?.stop()
        deepgramTTS?.stop()
        nativeTTS?.stop()
    }

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

    fun destroy() {
        elevenLabsTTS?.destroy()
        deepgramSTT?.destroy()
        deepgramTTS?.destroy()
        nativeTTS?.stop()
        nativeTTS?.shutdown()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            nativeSTT?.destroy()
        }
    }
}
