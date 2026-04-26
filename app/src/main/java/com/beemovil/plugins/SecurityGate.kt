package com.beemovil.plugins

import java.util.concurrent.CopyOnWriteArrayList

/**
 * SecurityGate — Project Autonomía Phase S1
 *
 * The Semáforo system: every self-control operation passes through here
 * before execution. Defines three security levels:
 *
 *  🟢 GREEN  → Execute immediately, no confirmation needed (read ops)
 *  🟡 YELLOW → Inline confirmation in chat (modify ops)
 *  🔴 RED    → Modal dialog with details (destructive ops)
 *
 * C-03 fix: previously, the single `confirmationHandler` was never assigned
 * anywhere, so YELLOW operations auto-approved silently and RED operations
 * always blocked. Now:
 *  - The fallback for YELLOW is `false` (deny by default when no UI is attached).
 *  - Multiple handlers can register so that whichever screen is in the
 *    foreground can answer. The first handler to return wins.
 */
object SecurityGate {

    enum class Level {
        GREEN,   // Auto-execute
        YELLOW,  // Inline confirm button
        RED      // Modal dialog confirmation
    }

    /**
     * Describes a secured operation before execution.
     * The UI layer uses this to show appropriate confirmation.
     */
    data class SecureOperation(
        val pluginId: String,
        val operation: String,
        val level: Level,
        val description: String,  // Human-readable for confirmation UI
        val params: Map<String, Any> = emptyMap()
    )

    /**
     * Callback interface for the UI to handle confirmation requests.
     * Implemented by ChatViewModel (or any screen that wants to answer).
     */
    interface ConfirmationHandler {
        /**
         * Called when a YELLOW or RED operation needs user approval.
         * Returns true if user confirmed, false if cancelled / timed out
         * / this handler can't currently answer.
         */
        suspend fun requestConfirmation(operation: SecureOperation): Boolean
    }

    // CopyOnWriteArrayList: safe for many reads, occasional writes (register/unregister).
    private val handlers = CopyOnWriteArrayList<ConfirmationHandler>()

    /** Register a handler. Idempotent: registering the same instance twice is a no-op. */
    fun registerHandler(handler: ConfirmationHandler) {
        if (!handlers.contains(handler)) handlers.add(handler)
    }

    /** Unregister a handler. Call this from onDestroy / DisposableEffect cleanup. */
    fun unregisterHandler(handler: ConfirmationHandler) {
        handlers.remove(handler)
    }

    /**
     * Backwards-compatible setter that mirrors the old API. Prefer
     * registerHandler / unregisterHandler from new code.
     */
    var confirmationHandler: ConfirmationHandler?
        get() = handlers.firstOrNull()
        set(value) {
            handlers.clear()
            if (value != null) handlers.add(value)
        }

    /**
     * Evaluate whether an operation should proceed.
     * GREEN → always true (no UI needed).
     * YELLOW/RED → ask each registered handler in order; the first one that
     * returns true approves. If none return true (or no handler is registered),
     * the operation is denied.
     */
    suspend fun evaluate(operation: SecureOperation): Boolean {
        if (operation.level == Level.GREEN) return true
        // Snapshot to avoid holding any lock while suspending.
        val snapshot = handlers.toList()
        if (snapshot.isEmpty()) return false  // C-03: deny by default when no UI.
        for (handler in snapshot) {
            if (handler.requestConfirmation(operation)) return true
        }
        return false
    }

    /**
     * Convenience: create a GREEN operation (no confirmation needed).
     */
    fun green(pluginId: String, operation: String, description: String = ""): SecureOperation {
        return SecureOperation(pluginId, operation, Level.GREEN, description)
    }

    /**
     * Convenience: create a YELLOW operation (inline confirm).
     */
    fun yellow(pluginId: String, operation: String, description: String, params: Map<String, Any> = emptyMap()): SecureOperation {
        return SecureOperation(pluginId, operation, Level.YELLOW, description, params)
    }

    /**
     * Convenience: create a RED operation (modal dialog).
     */
    fun red(pluginId: String, operation: String, description: String, params: Map<String, Any> = emptyMap()): SecureOperation {
        return SecureOperation(pluginId, operation, Level.RED, description, params)
    }
}
