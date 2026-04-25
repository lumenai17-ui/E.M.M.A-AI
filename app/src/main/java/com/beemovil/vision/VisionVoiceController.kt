package com.beemovil.vision

import android.content.Context
import android.util.Log
import com.beemovil.voice.DeepgramVoiceManager
import com.beemovil.voice.MicrophoneArbiter

/**
 * VisionVoiceController — Phase V3: La Voz
 *
 * State machine that coordinates TTS and STT for LiveVision.
 * Prevents TTS and STT from colliding. Mute = instant silence (TTS.stop()),
 * generation continues in background.
 *
 * States: IDLE -> LISTENING -> PROCESSING -> SPEAKING -> IDLE
 *         SPEAKING + mute -> IDLE (TTS.stop() instant)
 *
 * R3-1 FIX: startListening() returns Boolean to signal success/failure.
 * onError callback always resets state to IDLE for reliable UI sync.
 */
class VisionVoiceController(
    private val context: Context,
    private val voiceManager: DeepgramVoiceManager
) {
    companion object {
        private const val TAG = "VisionVoiceCtrl"
    }

    // -- State --
    enum class VoiceState { IDLE, LISTENING, PROCESSING, SPEAKING }

    var state: VoiceState = VoiceState.IDLE
        private set

    var isMuted: Boolean = false
        private set

    var isNarrationEnabled: Boolean = false
        private set

    /**
     * BUG-5 FIX: Toggle narration AND reset state machine.
     * When disabling narration while speaking, forces state back to IDLE
     * to prevent the deadlock where narrate() skips because state == SPEAKING.
     */
    fun setNarrationEnabled(enabled: Boolean) {
        isNarrationEnabled = enabled
        if (!enabled && state == VoiceState.SPEAKING) {
            voiceManager.stopSpeaking()
            setState(VoiceState.IDLE)
            Log.d(TAG, "Narration disabled -> TTS stopped, state reset to IDLE")
        }
    }

    // Currently selected personality
    var personality: NarratorPersonality = NARRATOR_PERSONALITIES.first()

    // -- Callbacks --
    var onStateChange: ((VoiceState) -> Unit)? = null
    var onSpeechResult: ((String) -> Unit)? = null
    var onMuteChange: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // -- TTS: Speak a result --
    /**
     * Narrate the vision result via TTS.
     * If muted, skips TTS but still transitions state.
     * If already speaking, queues nothing (skip).
     */
    fun narrate(text: String) {
        if (!isNarrationEnabled) return
        if (text.isBlank()) return
        if (state == VoiceState.LISTENING) return // Don't interrupt STT

        if (isMuted) {
            // Muted: don't speak, stay idle
            Log.d(TAG, "Narrate skipped (muted)")
            return
        }

        if (state == VoiceState.SPEAKING) {
            // Already speaking, skip this one
            Log.d(TAG, "Narrate skipped (already speaking)")
            return
        }

        setState(VoiceState.SPEAKING)
        voiceManager.speak(
            text = text,
            language = java.util.Locale.getDefault().language,
            onStart = {
                Log.d(TAG, "TTS started")
            },
            onDone = {
                Log.d(TAG, "TTS done")
                if (state == VoiceState.SPEAKING) {
                    setState(VoiceState.IDLE)
                }
            },
            onError = { error ->
                Log.w(TAG, "TTS error: $error")
                setState(VoiceState.IDLE)
                onError?.invoke("Voz: $error")
            }
        )
    }

    // -- Mute: Instant silence --
    /**
     * Toggle mute. If currently speaking, TTS.stop() is called IMMEDIATELY.
     * The LLM generation continues - only audio output is silenced.
     */
    fun toggleMute(): Boolean {
        isMuted = !isMuted

        if (isMuted && state == VoiceState.SPEAKING) {
            // INSTANT silence - the key feature
            voiceManager.stopSpeaking()
            setState(VoiceState.IDLE)
            Log.d(TAG, "Mute ON -> TTS stopped instantly")
        }

        onMuteChange?.invoke(isMuted)
        return isMuted
    }

    fun setMuted(muted: Boolean) {
        if (isMuted == muted) return
        isMuted = muted
        if (muted && state == VoiceState.SPEAKING) {
            voiceManager.stopSpeaking()
            setState(VoiceState.IDLE)
        }
        onMuteChange?.invoke(isMuted)
    }

    // -- STT: Listen for user question --
    /**
     * R3-1 FIX: Start listening for a voice question.
     * Returns true if STT was successfully initiated, false if it failed
     * (so UI can immediately reset visual state).
     */
    fun startListening(): Boolean {
        // Stop any TTS first
        if (state == VoiceState.SPEAKING) {
            voiceManager.stopSpeaking()
        }

        // R3-1: Check if voice manager can actually listen
        if (!voiceManager.hasApiKey && !voiceManager.hasNativeSTT()) {
            Log.w(TAG, "No STT available (no Deepgram key, no native STT)")
            onError?.invoke("Sin motor de voz disponible")
            setState(VoiceState.IDLE)
            return false
        }

        setState(VoiceState.LISTENING)
        voiceManager.startListening(
            language = java.util.Locale.getDefault().toLanguageTag(),
            onPartial = { partial ->
                if (com.beemovil.BuildConfig.DEBUG) {
                    Log.d(TAG, "Partial: $partial")
                }
            },
            onResult = { result ->
                if (com.beemovil.BuildConfig.DEBUG) {
                    Log.d(TAG, "Speech result: $result")
                }
                setState(VoiceState.PROCESSING)
                onSpeechResult?.invoke(result)
                // Caller should process and then call narrate() with the response
            },
            onError = { error ->
                Log.w(TAG, "STT error: $error")
                setState(VoiceState.IDLE)
                onError?.invoke("Mic: $error")
            },
            micOwner = MicrophoneArbiter.MicOwner.PUSH_TO_TALK,
            micTag = "LiveVision"
        )
        return true
    }

    /**
     * Stop listening.
     */
    fun stopListening() {
        voiceManager.stopListening()
        if (state == VoiceState.LISTENING) {
            setState(VoiceState.IDLE)
        }
    }

    // -- Lifecycle --
    fun stop() {
        voiceManager.stopSpeaking()
        voiceManager.stopListening()
        setState(VoiceState.IDLE)
    }

    private fun setState(newState: VoiceState) {
        if (state != newState) {
            state = newState
            onStateChange?.invoke(newState)
        }
    }
}
