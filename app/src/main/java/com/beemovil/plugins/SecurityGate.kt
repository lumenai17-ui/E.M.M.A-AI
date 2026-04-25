package com.beemovil.plugins

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
 * In Phase S1 (read-only plugins), everything is GREEN.
 * Phase S2 will add YELLOW/RED handling with UI callbacks.
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
     * Implemented by ChatViewModel or LiveVisionScreen.
     */
    interface ConfirmationHandler {
        /**
         * Called when a YELLOW or RED operation needs user approval.
         * Returns true if user confirmed, false if cancelled.
         */
        suspend fun requestConfirmation(operation: SecureOperation): Boolean
    }

    // The active handler — set by the UI layer
    @Volatile
    var confirmationHandler: ConfirmationHandler? = null

    /**
     * Evaluate whether an operation should proceed.
     * GREEN → always true
     * YELLOW/RED → asks the confirmation handler
     * If no handler is set, YELLOW auto-approves but RED blocks.
     */
    suspend fun evaluate(operation: SecureOperation): Boolean {
        return when (operation.level) {
            Level.GREEN -> true
            Level.YELLOW -> {
                confirmationHandler?.requestConfirmation(operation) ?: true // Auto-approve if no UI
            }
            Level.RED -> {
                confirmationHandler?.requestConfirmation(operation) ?: false // Block if no UI
            }
        }
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
