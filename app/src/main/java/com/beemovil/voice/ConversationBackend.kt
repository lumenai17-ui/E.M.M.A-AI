package com.beemovil.voice

/**
 * ConversationBackend — Voice Intelligence Phase V2
 *
 * Interface for pluggable conversation backends.
 * Each backend handles the full STT → LLM → TTS pipeline differently:
 * - OfflineBackend: Native Android STT + LLM via OpenRouter + Native TTS
 * - PipelineBackend: Deepgram streaming + any LLM + Nova/ElevenLabs/Deepgram TTS
 */
interface ConversationBackend {

    /** Unique identifier for this backend (e.g. "offline", "pipeline") */
    val id: String

    /** Display name for UI (e.g. "Offline", "Pipeline (Deepgram + LLM + EL)") */
    val displayName: String

    /** Whether this backend needs internet to function */
    val requiresInternet: Boolean

    /** List of SecurePrefs keys required for this backend to work */
    val requiredKeys: List<String>

    /** Check if all required keys are configured */
    fun isAvailable(): Boolean

    /**
     * Start a conversation session.
     * After this, the backend is ready to receive audio or text.
     */
    suspend fun startSession(config: ConversationConfig)

    /**
     * Process a completed user transcript through the LLM.
     * Called by ConversationEngine when STT produces a final result.
     *
     * @param transcript The user's spoken text
     * @return The LLM response text
     */
    suspend fun processTranscript(transcript: String): String

    /**
     * Stop the current session and clean up resources.
     */
    fun stopSession()

    /**
     * Stop any TTS playback immediately (for barge-in).
     */
    fun stopSpeaking()

    /**
     * Check if TTS is currently playing.
     */
    fun isSpeaking(): Boolean
}

/**
 * ProactiveBackend — V11 Phase 4
 *
 * Optional interface that ConversationBackends can implement to provide
 * proactive responses during periods of user silence.
 * The ConversationEngine calls evaluateProactivity() after detecting
 * sustained silence, allowing the backend to decide if it has something
 * worth saying (e.g., vision alerts, context changes, etc.).
 */
interface ProactiveBackend {
    /**
     * Evaluate whether the backend has something proactive to say.
     * Called by ConversationEngine during silence periods.
     *
     * @return A proactive response text, or null if nothing to say
     */
    suspend fun evaluateProactivity(): String?
}

/**
 * Configuration for a conversation session.
 */
data class ConversationConfig(
    /** Auto-listen again after TTS finishes */
    val autoListenAfterTTS: Boolean = true,

    /** Silence duration before ending a turn (ms) */
    val maxSilenceMs: Long = 3000,

    /** Maximum listening time per turn (ms) */
    val maxListenMs: Long = 30000,

    /** Language for STT */
    val language: String = "es-MX",

    /** Whether to speak responses via TTS */
    val speakResponses: Boolean = true,

    /** LLM provider (for pipeline backend) */
    val llmProvider: String = "openrouter",

    /** LLM model (for pipeline backend) */
    val llmModel: String = "openai/gpt-4o-mini",

    /** Active thread ID for message persistence */
    val threadId: String = "main",

    /** Active agent ID */
    val agentId: String = "emma",

    /** System prompt override (for agent-specific conversations) */
    val systemPrompt: String = ""
)

/**
 * Conversation state exposed to UI.
 */
enum class ConversationState {
    /** Engine not active */
    IDLE,
    /** Mic active, waiting for speech */
    LISTENING,
    /** User finished talking, processing STT / sending to LLM */
    PROCESSING,
    /** TTS playing Emma's response */
    SPEAKING,
    /** Error state */
    ERROR
}
