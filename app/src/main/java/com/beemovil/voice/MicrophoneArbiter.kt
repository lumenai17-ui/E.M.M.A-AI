package com.beemovil.voice

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * MicrophoneArbiter — Voice Intelligence Phase V1
 *
 * Singleton that manages exclusive microphone access across the entire app.
 * Only ONE component can hold the mic at a time. Priority-based preemption
 * ensures higher-priority consumers (CONVERSATION) can take the mic from
 * lower-priority ones (WAKE_WORD).
 *
 * Thread-safe via @Synchronized on all public methods.
 *
 * Usage:
 *   if (MicrophoneArbiter.requestMic(MicOwner.PUSH_TO_TALK, tag)) {
 *       // mic is yours — do STT
 *   }
 *   // when done:
 *   MicrophoneArbiter.releaseMic(MicOwner.PUSH_TO_TALK)
 */
object MicrophoneArbiter {

    private const val TAG = "MicArbiter"
    private const val ZOMBIE_TIMEOUT_MS = 60_000L // 60s auto-release

    /**
     * Who can own the mic, ordered by priority (higher = more priority).
     */
    enum class MicOwner(val priority: Int) {
        NONE(0),
        WAKE_WORD(1),      // Porcupine/openWakeWord background listening
        PUSH_TO_TALK(2),   // Single-turn STT from Chat or Vision
        CONVERSATION(3)    // ConversationEngine auto-loop
    }

    /**
     * Callbacks for mic lifecycle events.
     * Set these to react to mic state changes globally.
     */
    var onMicAcquired: ((MicOwner, String) -> Unit)? = null
    var onMicReleased: ((MicOwner) -> Unit)? = null
    var onMicDenied: ((MicOwner, String) -> Unit)? = null
    var onWakeWordPaused: (() -> Unit)? = null
    var onWakeWordResumed: (() -> Unit)? = null

    // Current state
    private val currentOwner = AtomicReference(MicOwner.NONE)
    private var currentTag: String = ""
    private var acquireTimestamp: Long = 0L
    private var wakeWordEnabled: Boolean = false
    private var zombieJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Request exclusive mic access.
     *
     * @param owner Who is requesting
     * @param tag Human-readable tag for logging (e.g., "ChatScreen", "VisionMic")
     * @return true if granted, false if denied
     */
    @Synchronized
    fun requestMic(owner: MicOwner, tag: String = ""): Boolean {
        val current = currentOwner.get()

        // Already held by the same owner — allow (idempotent)
        if (current == owner) {
            Log.d(TAG, "requestMic($owner, $tag) — already held, allowing")
            return true
        }

        // Mic is free — grant immediately
        if (current == MicOwner.NONE) {
            grantMic(owner, tag)
            return true
        }

        // Priority comparison
        if (owner.priority > current.priority) {
            // Preempt: pause current holder, grant to new
            Log.i(TAG, "requestMic($owner) PREEMPTING $current ($currentTag)")

            if (current == MicOwner.WAKE_WORD) {
                onWakeWordPaused?.invoke()
            }

            // Release current without auto-resuming wake word
            forceRelease(current)

            // Grant to new owner
            grantMic(owner, tag)
            return true
        }

        // Lower or equal priority — denied
        Log.w(TAG, "requestMic($owner, $tag) DENIED — mic held by $current ($currentTag)")
        onMicDenied?.invoke(owner, "Mic en uso por $current")
        return false
    }

    /**
     * Release the mic. If wake word is enabled, auto-resume it.
     *
     * @param owner Who is releasing — must match current holder
     */
    @Synchronized
    fun releaseMic(owner: MicOwner) {
        val current = currentOwner.get()
        if (current != owner && current != MicOwner.NONE) {
            Log.w(TAG, "releaseMic($owner) — mismatch, current is $current. Ignoring.")
            return
        }

        Log.i(TAG, "releaseMic($owner) — held for ${System.currentTimeMillis() - acquireTimestamp}ms")
        currentOwner.set(MicOwner.NONE)
        currentTag = ""
        zombieJob?.cancel()
        zombieJob = null
        onMicReleased?.invoke(owner)

        // Auto-resume wake word if enabled and the releaser wasn't the wake word itself
        if (wakeWordEnabled && owner != MicOwner.WAKE_WORD) {
            Log.i(TAG, "Auto-resuming wake word listener")
            onWakeWordResumed?.invoke()
        }
    }

    /**
     * Check if the mic is currently available.
     */
    fun isFree(): Boolean = currentOwner.get() == MicOwner.NONE

    /**
     * Check who currently holds the mic.
     */
    fun currentHolder(): MicOwner = currentOwner.get()

    /**
     * Check if a specific owner currently holds the mic.
     */
    fun isHeldBy(owner: MicOwner): Boolean = currentOwner.get() == owner

    /**
     * Enable/disable wake word auto-resume.
     * When enabled, releasing the mic from any non-wake-word source
     * will trigger onWakeWordResumed callback.
     */
    @Synchronized
    fun setWakeWordEnabled(enabled: Boolean) {
        wakeWordEnabled = enabled
        Log.i(TAG, "Wake word auto-resume: $enabled")

        if (enabled && isFree()) {
            onWakeWordResumed?.invoke()
        } else if (!enabled && currentOwner.get() == MicOwner.WAKE_WORD) {
            // Wake word was active but just got disabled — release
            forceRelease(MicOwner.WAKE_WORD)
        }
    }

    fun isWakeWordEnabled(): Boolean = wakeWordEnabled

    /**
     * Force-release all mic claims. Used on app destroy or error recovery.
     */
    @Synchronized
    fun forceReleaseAll() {
        val was = currentOwner.get()
        if (was != MicOwner.NONE) {
            Log.w(TAG, "forceReleaseAll() — releasing $was ($currentTag)")
        }
        currentOwner.set(MicOwner.NONE)
        currentTag = ""
        zombieJob?.cancel()
        zombieJob = null
    }

    /**
     * Get debug state string for diagnostics.
     */
    fun debugState(): String {
        val current = currentOwner.get()
        val elapsed = if (current != MicOwner.NONE) {
            "${(System.currentTimeMillis() - acquireTimestamp) / 1000}s"
        } else "—"
        return "MicArbiter[owner=$current, tag=$currentTag, held=$elapsed, wakeWord=$wakeWordEnabled]"
    }

    // --- Internal ---

    private fun grantMic(owner: MicOwner, tag: String) {
        currentOwner.set(owner)
        currentTag = tag
        acquireTimestamp = System.currentTimeMillis()
        Log.i(TAG, "MIC GRANTED → $owner ($tag)")
        onMicAcquired?.invoke(owner, tag)

        // Start zombie timeout
        zombieJob?.cancel()
        zombieJob = scope.launch {
            delay(ZOMBIE_TIMEOUT_MS)
            val stillHeld = currentOwner.get()
            if (stillHeld == owner) {
                Log.w(TAG, "ZOMBIE DETECTED: $owner ($tag) held mic for ${ZOMBIE_TIMEOUT_MS / 1000}s — auto-releasing")
                releaseMic(owner)
            }
        }
    }

    private fun forceRelease(owner: MicOwner) {
        currentOwner.set(MicOwner.NONE)
        currentTag = ""
        zombieJob?.cancel()
        zombieJob = null
        onMicReleased?.invoke(owner)
    }
}
