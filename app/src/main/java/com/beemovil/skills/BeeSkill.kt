package com.beemovil.skills

import org.json.JSONObject

/**
 * Interface that all Bee-Movil skills must implement.
 * Each skill is a native Android capability the agent can use.
 */
interface BeeSkill {
    /** Unique skill identifier used in tool calls */
    val name: String

    /** Human-readable description for the LLM */
    val description: String

    /** JSON Schema for the skill's parameters (OpenAI function calling format) */
    val parametersSchema: JSONObject

    /** Execute the skill with given parameters, return result as JSON */
    fun execute(params: JSONObject): JSONObject
}
