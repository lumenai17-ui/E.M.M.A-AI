package com.beemovil.voice

import android.content.Context
import android.util.Log
import com.beemovil.core.engine.EmmaEngine
import com.beemovil.database.ChatHistoryDB
import com.beemovil.database.ChatMessageEntity
import kotlinx.coroutines.*
import java.util.Locale

/**
 * ConversationEngine — Voice Intelligence Phase V2
 *
 * The orchestrator for continuous two-way voice conversations.
 * Manages the auto-listen loop: LISTEN → STT → LLM → TTS → LISTEN.
 *
 * Key responsibilities:
 * - Acquires/releases mic through MicrophoneArbiter
 * - Delegates STT/LLM/TTS to the selected ConversationBackend
 * - Persists messages to Room database
 * - Handles barge-in (interrupt TTS)
 * - Manages conversation state for UI
 *
 * Thread safety: All state changes go through setState() which posts to Main.
 */
class ConversationEngine(
    private val context: Context,
    private val voiceManager: DeepgramVoiceManager,
    private val engine: EmmaEngine,
    private val chatHistoryDB: ChatHistoryDB
) {

    companion object {
        private const val TAG = "ConversationEngine"
    }

    // --- State ---
    private var state: ConversationState = ConversationState.IDLE
    private var config: ConversationConfig = ConversationConfig()
    private var activeBackend: ConversationBackend? = null
    private var conversationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var turnCount = 0

    // --- Available backends ---
    val backends: List<ConversationBackend> by lazy {
        listOf(
            OfflineConversationBackend(context, voiceManager, engine),
            PipelineConversationBackend(context, voiceManager, engine)
        )
    }

    // --- Callbacks ---
    var onStateChange: ((ConversationState) -> Unit)? = null
    var onPartialTranscript: ((String) -> Unit)? = null
    var onFinalTranscript: ((String) -> Unit)? = null
    var onResponse: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onTurnComplete: ((userText: String, emmaText: String) -> Unit)? = null

    /**
     * Start a conversation session with the given backend and config.
     *
     * @param backend The backend to use for this session
     * @param config Session configuration
     * @return true if started successfully
     */
    fun start(backend: ConversationBackend, config: ConversationConfig): Boolean {
        if (state != ConversationState.IDLE) {
            Log.w(TAG, "start() called while state=$state — stopping first")
            stop()
        }

        // Acquire mic through arbiter
        if (!MicrophoneArbiter.requestMic(MicrophoneArbiter.MicOwner.CONVERSATION, "ConversationEngine")) {
            Log.e(TAG, "Failed to acquire mic — arbiter denied")
            onError?.invoke("No se pudo acceder al micrófono")
            return false
        }

        this.config = config
        this.activeBackend = backend
        this.turnCount = 0

        // Start backend session
        scope.launch {
            try {
                backend.startSession(config)
                Log.i(TAG, "Conversation started with backend: ${backend.displayName} (id=${backend.id}, thread=${config.threadId})")
                // Begin first listen cycle
                beginListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start session: ${e.message}")
                onError?.invoke("Error iniciando conversación: ${e.message}")
                MicrophoneArbiter.releaseMic(MicrophoneArbiter.MicOwner.CONVERSATION)
                setState(ConversationState.ERROR)
            }
        }

        return true
    }

    /**
     * Stop the conversation and release all resources.
     */
    fun stop() {
        Log.i(TAG, "Stopping conversation (turns=$turnCount)")
        conversationJob?.cancel()
        conversationJob = null

        activeBackend?.stopSession()
        activeBackend = null

        voiceManager.stopListening()
        voiceManager.stopSpeaking()

        MicrophoneArbiter.releaseMic(MicrophoneArbiter.MicOwner.CONVERSATION)
        setState(ConversationState.IDLE)
    }

    /**
     * Interrupt Emma's speech (barge-in).
     * Stops TTS immediately and goes back to listening.
     */
    fun interrupt() {
        if (state == ConversationState.SPEAKING) {
            Log.i(TAG, "BARGE-IN: Interrupting TTS")
            activeBackend?.stopSpeaking()
            voiceManager.stopSpeaking()

            if (config.autoListenAfterTTS) {
                beginListening()
            } else {
                setState(ConversationState.IDLE)
            }
        }
    }

    /**
     * Get current conversation state.
     */
    fun currentState(): ConversationState = state

    /**
     * Check if a conversation is active (not IDLE).
     */
    fun isActive(): Boolean = state != ConversationState.IDLE

    /**
     * Get the default/recommended backend based on available API keys.
     */
    fun getDefaultBackend(): ConversationBackend {
        // Prefer pipeline if keys are available, otherwise offline
        return backends.find { it.id == "pipeline" && it.isAvailable() }
            ?: backends.find { it.id == "offline" }
            ?: backends.first()
    }

    /**
     * Get available backends (ones that have required keys configured).
     */
    fun getAvailableBackends(): List<ConversationBackend> {
        return backends.filter { it.isAvailable() }
    }

    // --- Internal: The Conversation Loop ---

    private fun beginListening() {
        setState(ConversationState.LISTENING)

        val backend = activeBackend ?: run {
            Log.e(TAG, "No active backend — stopping")
            stop()
            return
        }

        val language = config.language

        voiceManager.startListening(
            language = language,
            onPartial = { partial ->
                onPartialTranscript?.invoke(partial)
            },
            onResult = { transcript ->
                if (transcript.isBlank()) {
                    // Empty result — restart listening or go idle
                    if (config.autoListenAfterTTS) {
                        beginListening()
                    } else {
                        setState(ConversationState.IDLE)
                    }
                    return@startListening
                }

                Log.i(TAG, "User said: ${transcript.take(80)}...")
                onFinalTranscript?.invoke(transcript)
                turnCount++

                // Process through LLM
                processUserTurn(transcript)
            },
            onError = { error ->
                Log.w(TAG, "STT error: $error")
                onError?.invoke(error)
                // On error, try to re-listen if auto mode
                if (config.autoListenAfterTTS && state != ConversationState.IDLE) {
                    scope.launch {
                        delay(500) // Brief pause before retry
                        if (state != ConversationState.IDLE) {
                            beginListening()
                        }
                    }
                } else {
                    setState(ConversationState.IDLE)
                }
            },
            // Use CONVERSATION priority — already acquired via arbiter
            micOwner = MicrophoneArbiter.MicOwner.CONVERSATION,
            micTag = "ConversationEngine-Turn$turnCount"
        )
    }

    private fun processUserTurn(transcript: String) {
        setState(ConversationState.PROCESSING)

        val backend = activeBackend ?: run {
            stop()
            return
        }

        conversationJob = scope.launch {
            try {
                // Persist user message
                persistMessage(transcript, "user")

                // Send to LLM through backend
                val response = withContext(Dispatchers.IO) {
                    backend.processTranscript(transcript)
                }

                if (response.isBlank()) {
                    Log.w(TAG, "Empty LLM response")
                    if (config.autoListenAfterTTS) {
                        beginListening()
                    } else {
                        setState(ConversationState.IDLE)
                    }
                    return@launch
                }

                // Persist assistant message
                persistMessage(response, "assistant")
                onResponse?.invoke(response)
                onTurnComplete?.invoke(transcript, response)

                // Speak the response
                if (config.speakResponses) {
                    speakResponse(response)
                } else {
                    // No TTS — go back to listening or idle
                    if (config.autoListenAfterTTS) {
                        beginListening()
                    } else {
                        setState(ConversationState.IDLE)
                    }
                }
            } catch (e: CancellationException) {
                // Expected on stop/interrupt
                Log.d(TAG, "Turn cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Turn error: ${e.message}")
                onError?.invoke("Error: ${e.message?.take(100)}")
                if (config.autoListenAfterTTS) {
                    delay(500)
                    beginListening()
                } else {
                    setState(ConversationState.IDLE)
                }
            }
        }
    }

    private fun speakResponse(text: String) {
        setState(ConversationState.SPEAKING)

        voiceManager.speak(
            text = text,
            language = Locale.getDefault().language,
            onStart = {
                Log.d(TAG, "TTS started")
            },
            onDone = {
                Log.d(TAG, "TTS done")
                if (state == ConversationState.SPEAKING) {
                    if (config.autoListenAfterTTS) {
                        // THE LOOP: TTS done → listen again
                        beginListening()
                    } else {
                        setState(ConversationState.IDLE)
                    }
                }
            },
            onError = { error ->
                Log.w(TAG, "TTS error: $error")
                // Even if TTS fails, continue the loop
                if (config.autoListenAfterTTS) {
                    beginListening()
                } else {
                    setState(ConversationState.IDLE)
                }
            }
        )
    }

    private suspend fun persistMessage(content: String, role: String) {
        try {
            withContext(Dispatchers.IO) {
                chatHistoryDB.chatHistoryDao().insertMessage(
                    ChatMessageEntity(
                        threadId = config.threadId,
                        senderId = if (role == "user") "user" else config.agentId,
                        timestamp = System.currentTimeMillis(),
                        role = role,
                        content = content
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist message: ${e.message}")
        }
    }

    private fun setState(newState: ConversationState) {
        if (state != newState) {
            Log.d(TAG, "State: $state → $newState")
            state = newState
            try {
                onStateChange?.invoke(newState)
            } catch (e: Exception) {
                Log.w(TAG, "setState callback failed (composable disposed?): ${e.message}")
            }
        }
    }

    /**
     * Clean up when no longer needed.
     */
    fun destroy() {
        stop()
        scope.cancel()
    }
}
