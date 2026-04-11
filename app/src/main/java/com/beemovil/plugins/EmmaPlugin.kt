package com.beemovil.plugins

import com.beemovil.llm.ToolDefinition

/**
 * Base interface for all E.M.M.A. Ai Skills (Tools).
 * A plugin defines its metadata (schema) and execution logic.
 */
interface EmmaPlugin {
    /**
     * Unique identifier for this plugin instance (e.g., "web_search")
     */
    val id: String

    /**
     * Returns the ToolDefinition required by OpenRouter/Ollama/OpenAi to understand this tool.
     */
    fun getToolDefinition(): ToolDefinition

    /**
     * Executes the plugin with the arguments provided by the LLM.
     * @param args A map of arguments extracted from the LLM's JSON tool call.
     * @return A String representing the result of the execution (to be fed back to the LLM).
     */
    suspend fun execute(args: Map<String, Any>): String

    /**
     * Optional cleanup logic.
     */
    fun cleanup() {}
}
