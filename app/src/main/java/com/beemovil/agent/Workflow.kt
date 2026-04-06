package com.beemovil.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Workflow — A sequence of steps that execute agents/skills in order.
 * Each step feeds its output to the next step's input.
 *
 * Think of it as a "digital assembly line" — n8n/Zapier style but on your phone.
 *
 * Example workflow: "Research → Write → PDF → Email"
 * Step 1: web_search("latest AI trends") → research text
 * Step 2: Main Agent("write article from this research: {prev}") -> article text
 * Step 3: generate_pdf(content={prev}) → PDF file path
 * Step 4: email(attach={prev}) → sent confirmation
 */

data class Workflow(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val steps: List<WorkflowStep>,
    val isTemplate: Boolean = false
)

data class WorkflowStep(
    val id: String,
    val label: String,
    val icon: String,
    val type: StepType,
    /** For AGENT type: the agent ID to use */
    val agentId: String = "main",
    /** For SKILL type: the skill name to execute */
    val skillName: String = "",
    /** The prompt/instruction for this step. Use {input} to reference previous step output */
    val prompt: String = "",
    /** For SKILL type: fixed params to merge with dynamic input */
    val fixedParams: Map<String, String> = emptyMap()
)

enum class StepType {
    AGENT,   // Run a full agent with prompt (chat call)
    SKILL    // Execute a single skill directly
}

/**
 * Runtime state for a running workflow.
 */
data class WorkflowRun(
    val workflow: Workflow,
    val stepStates: MutableList<StepState>,
    var currentStepIndex: Int = -1,
    var isComplete: Boolean = false,
    var isFailed: Boolean = false,
    var finalOutput: String = ""
)

data class StepState(
    val step: WorkflowStep,
    var status: StepStatus = StepStatus.PENDING,
    var output: String = "",
    var error: String = "",
    var elapsedMs: Long = 0
)

enum class StepStatus {
    PENDING,    // Waiting
    RUNNING,    // Executing
    COMPLETED,  // Done
    FAILED,     // Error
    SKIPPED     // Skipped
}
