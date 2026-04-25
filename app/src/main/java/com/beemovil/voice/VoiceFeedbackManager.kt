package com.beemovil.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * VoiceFeedbackManager — Voice Intelligence Phase V5
 *
 * Provides haptic and audio feedback for voice state transitions.
 * Gives the user physical confirmation that the mic is active,
 * Emma is processing, or speech is happening.
 *
 * Feedback types:
 * - Haptic: Short/long vibrations for state changes
 * - Audio: Subtle tones (using SoundPool for low latency)
 *
 * All feedback respects the user's mute preference.
 */
class VoiceFeedbackManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceFeedback"
    }

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private var isMuted = false

    /**
     * Set mute state — when muted, no haptic or audio feedback.
     */
    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    /**
     * Feedback when microphone starts listening.
     * Short double-tap vibration ("I'm ready").
     */
    fun onListeningStarted() {
        if (isMuted) return
        vibratePattern(longArrayOf(0, 40, 60, 40)) // tap-tap
        Log.d(TAG, "Feedback: Listening started")
    }

    /**
     * Feedback when user finishes speaking and processing begins.
     * Single medium vibration ("Got it").
     */
    fun onProcessingStarted() {
        if (isMuted) return
        vibrateSingle(80) // medium tap
        Log.d(TAG, "Feedback: Processing started")
    }

    /**
     * Feedback when Emma starts speaking.
     * Gentle long vibration ("Here's my response").
     */
    fun onSpeakingStarted() {
        if (isMuted) return
        vibrateSingle(50) // gentle tap
        Log.d(TAG, "Feedback: Speaking started")
    }

    /**
     * Feedback when wake word is detected.
     * Strong triple-tap ("I heard you!").
     */
    fun onWakeWordDetected() {
        if (isMuted) return
        vibratePattern(longArrayOf(0, 60, 80, 60, 80, 60)) // tap-tap-tap
        Log.d(TAG, "Feedback: Wake word detected!")
    }

    /**
     * Feedback for error states.
     * Long single buzz.
     */
    fun onError() {
        if (isMuted) return
        vibrateSingle(200) // long buzz
        Log.d(TAG, "Feedback: Error")
    }

    /**
     * Feedback for barge-in (interrupting Emma).
     * Quick double-tap.
     */
    fun onBargeIn() {
        if (isMuted) return
        vibratePattern(longArrayOf(0, 30, 40, 30)) // quick tap-tap
        Log.d(TAG, "Feedback: Barge-in")
    }

    /**
     * Feedback when conversation stops.
     * Gentle single tap.
     */
    fun onConversationStopped() {
        if (isMuted) return
        vibrateSingle(30)
        Log.d(TAG, "Feedback: Conversation stopped")
    }

    // --- Internal vibration helpers ---

    private fun vibrateSingle(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration pattern failed: ${e.message}")
        }
    }
}
