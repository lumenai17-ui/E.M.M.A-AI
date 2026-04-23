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

    // Deepgram engines
    private val deepgramSTT = try { DeepgramSTT(context) } catch (e: Exception) { null }
    private val deepgramTTS = try { DeepgramTTS(context) } catch (e: Exception) { null }
    private val elevenLabsTTS = try { ElevenLabsTTS(context) } catch (e: Exception) { null }

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
        
    val elevenLabsKey: String get() = SecurePrefs.get(context).getString(KEY_ELEVENLABS_API, "") ?: ""
    val elevenLabsVoice: String get() = SecurePrefs.get(context).getString(KEY_ELEVENLABS_VOICE, "") ?: ""

    fun initialize() {
        nativeTTS = TextToSpeech(context) { status ->
            nativeTTSReady = status == TextToSpeech.SUCCESS
            if (nativeTTSReady) {
                // Ahora respeta el idioma nativo del sistema operativo (Global fallback)
                nativeTTS?.language = Locale.getDefault()
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                nativeSTT = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
            }
        }
    }

    fun startListening(
        language: String = "es-MX",
        onPartial: ((String) -> Unit)? = null,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null,
        onState: ((Boolean) -> Unit)? = null
    ) {
        if (useDeepgramSTT) {
            deepgramSTT?.startListening(
                apiKey = apiKey,
                language = language.substringBefore("-"),
                onPartial = onPartial,
                onResult = onResult,
                onErrorCb = { error ->
                    Log.w(TAG, "Deepgram STT failed: $error")
                    onError?.invoke(error)
                },
                onState = onState
            )
        } else {
            startNativeListening(language, onResult, onError)
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

    fun stopListening() {
        deepgramSTT?.stopListening()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            nativeSTT?.stopListening()
        }
    }

    fun speak(
        text: String,
        language: String = "es",
        onDone: (() -> Unit)? = null,
        onStart: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val eKey = elevenLabsKey
        val eVoice = elevenLabsVoice

        if (eKey.isNotBlank() && eVoice.isNotBlank() && elevenLabsTTS != null) {
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
            speakNative(text, onDone)
        }
    }

    fun stopSpeaking() {
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
