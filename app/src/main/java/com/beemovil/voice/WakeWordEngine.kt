package com.beemovil.voice

/**
 * WakeWordEngine — Voice Intelligence Phase V4
 *
 * Interface for pluggable wake word detection engines.
 * Implementations:
 * - NativeWakeWordEngine: Android SpeechRecognizer + keyword match (works NOW, no deps)
 * - Future: OpenWakeWordEngine (ONNX-based, always-on, no beep)
 * - Future: PorcupineWakeWordEngine (best quality, commercial)
 */
interface WakeWordEngine {

    /** Engine identifier */
    val id: String

    /** Display name */
    val displayName: String

    /** The wake phrase this engine listens for */
    val wakePhrase: String

    /**
     * Start listening for the wake word.
     * @param onWakeWordDetected Called when the wake word is detected
     * @param onError Called on error (engine will try to auto-restart)
     */
    fun start(
        onWakeWordDetected: () -> Unit,
        onError: ((String) -> Unit)? = null
    )

    /**
     * Stop listening.
     */
    fun stop()

    /**
     * Check if the engine is currently listening.
     */
    fun isListening(): Boolean

    /**
     * Release all resources.
     */
    fun destroy()
}
