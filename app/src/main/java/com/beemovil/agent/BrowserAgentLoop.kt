package com.beemovil.agent

import android.content.Context
import android.util.Log
import com.beemovil.llm.*
import com.beemovil.memory.BrowserActivityLog
import com.beemovil.skills.BrowserSkill
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * BrowserAgentLoop — Multi-step autonomous browser agent engine.
 *
 * Executes a user's web task step-by-step using the BrowserSkill.
 * - Injects page context before each LLM decision
 * - Detects blockers (CAPTCHA, login) and pauses for human help
 * - Detects loops (3x same action) and auto-pauses
 * - Limits to MAX_STEPS per task
 * - Reports progress via callbacks
 * - Supports pause/resume/cancel
 * - Logs all actions to BrowserActivityLog
 */
class BrowserAgentLoop(
    private val context: Context,
    private val browserSkill: BrowserSkill,
    private val activityLog: BrowserActivityLog
) {
    companion object {
        private const val TAG = "BrowserAgentLoop"
        private const val MAX_STEPS = 20
        private const val PAGE_LOAD_TIMEOUT_MS = 30_000L
    }

    // ── Current task state ──
    var currentTask: BrowserTask? = null
        private set

    var status: TaskStatus = TaskStatus.IDLE
        private set

    // ── Callbacks ──
    var onStepComplete: ((BrowserStep, String) -> Unit)? = null
    var onStatusChange: ((TaskStatus, String?) -> Unit)? = null
    var onAgentMessage: ((String) -> Unit)? = null

    // Action history for loop detection
    private val actionHistory = mutableListOf<Pair<String, String>>() // (action, target)

    /**
     * Start a new browser task. MUST be called from a background thread.
     * This function blocks until the task completes, pauses, or is cancelled.
     */
    fun startTask(
        goal: String,
        llmProvider: LlmProvider,
        modelName: String = ""
    ): BrowserTaskResult {
        val sessionId = UUID.randomUUID().toString().take(8)
        val task = BrowserTask(
            sessionId = sessionId,
            goal = goal,
            completedSteps = mutableListOf(),
            status = TaskStatus.RUNNING
        )
        currentTask = task
        status = TaskStatus.RUNNING
        actionHistory.clear()
        onStatusChange?.invoke(TaskStatus.RUNNING, "Iniciando tarea: ${goal.take(50)}")

        activityLog.logAction(sessionId, "", "task_start", goal, "", goal, modelName)

        return executeLoop(task, llmProvider, modelName)
    }

    /**
     * Resume a paused task (after human intervention).
     */
    fun resumeTask(llmProvider: LlmProvider, modelName: String = ""): BrowserTaskResult {
        val task = currentTask ?: return BrowserTaskResult("No hay tarea activa", false)
        if (status != TaskStatus.PAUSED_NEED_HELP && status != TaskStatus.PAUSED_LOOP) {
            return BrowserTaskResult("Tarea no esta pausada", false)
        }

        status = TaskStatus.RUNNING
        onStatusChange?.invoke(TaskStatus.RUNNING, "Reanudando tarea...")
        actionHistory.clear() // Reset loop detection after human help

        activityLog.logAction(task.sessionId, "", "task_resume", "", "", task.goal, modelName)

        return executeLoop(task, llmProvider, modelName)
    }

    /**
     * Cancel the current task.
     */
    fun cancelTask() {
        status = TaskStatus.CANCELLED
        onStatusChange?.invoke(TaskStatus.CANCELLED, "Tarea cancelada")
        currentTask?.let { task ->
            activityLog.logAction(task.sessionId, "", "task_cancel", "", "", task.goal)
        }
    }

    /**
     * Main execution loop. Each iteration:
     * 1. Read current page state
     * 2. Build context for LLM
     * 3. LLM decides next action
     * 4. Execute action
     * 5. Check for blockers/loops
     * 6. Repeat until done, paused, or max steps
     */
    private fun executeLoop(
        task: BrowserTask,
        llmProvider: LlmProvider,
        modelName: String
    ): BrowserTaskResult {

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage(role = "system", content = PageContextProvider.getBrowserAgentPrompt()))

        // Add completed steps as context (for resume)
        if (task.completedSteps.isNotEmpty()) {
            val historyText = buildString {
                appendLine("Pasos completados anteriormente:")
                task.completedSteps.forEach { step ->
                    appendLine("${step.stepNumber}. ${step.action}(${step.target}) → ${step.result.take(80)}")
                }
                appendLine("\nContinua desde donde te quedaste.")
            }
            messages.add(ChatMessage(role = "system", content = historyText))
        }

        // Initial user message with goal
        messages.add(ChatMessage(role = "user", content = task.goal))

        // Memory context from past visits
        val currentInfo = browserSkill.execute(JSONObject().put("action", "current"))
        val currentUrl = currentInfo.optString("url", "")
        val memoryContext = activityLog.getMemoryContext(currentUrl)

        var stepCount = task.completedSteps.size

        while (status == TaskStatus.RUNNING && stepCount < MAX_STEPS) {
            try {
                // 1. Read page state
                val pageState = readPageState()

                // 2. Check for blockers
                val blocker = PageContextProvider.detectBlocker(
                    pageState.optString("text", ""),
                    parseElements(pageState)
                )
                if (blocker != null) {
                    status = TaskStatus.PAUSED_NEED_HELP
                    onStatusChange?.invoke(TaskStatus.PAUSED_NEED_HELP, blocker)
                    onAgentMessage?.invoke(blocker)
                    activityLog.logAction(task.sessionId, currentUrl, "blocker_detected", blocker, "paused", task.goal, modelName)
                    return BrowserTaskResult(blocker, false, isPaused = true)
                }

                // 3. Build context for LLM
                val contextMsg = PageContextProvider.generateContext(
                    url = pageState.optString("url", ""),
                    title = pageState.optString("title", ""),
                    pageText = pageState.optString("text", ""),
                    elements = parseElements(pageState),
                    memoryContext = if (stepCount == 0) memoryContext else ""
                )
                messages.add(ChatMessage(role = "system", content = contextMsg))

                // 4. Ask LLM for next action
                val tools = listOf(
                    ToolDefinition(
                        name = browserSkill.name,
                        description = browserSkill.description,
                        parameters = browserSkill.parametersSchema
                    )
                )

                onStatusChange?.invoke(TaskStatus.RUNNING, "Paso ${stepCount + 1}: Pensando...")
                val response = llmProvider.complete(messages, tools)

                // 5. Process response
                if (response.text?.contains("[TASK_DONE]") == true) {
                    val resultText = response.text.substringAfter("[TASK_DONE]").trim()
                    status = TaskStatus.COMPLETED
                    onStatusChange?.invoke(TaskStatus.COMPLETED, "Tarea completada")
                    onAgentMessage?.invoke(resultText.ifBlank { "Tarea completada." })
                    activityLog.logAction(task.sessionId, "", "task_done", "", resultText.take(200), task.goal, modelName)
                    return BrowserTaskResult(resultText, true)
                }

                if (response.text?.contains("[NEED_HELP]") == true) {
                    val helpText = response.text.substringAfter("[NEED_HELP]").trim()
                    status = TaskStatus.PAUSED_NEED_HELP
                    onStatusChange?.invoke(TaskStatus.PAUSED_NEED_HELP, helpText)
                    onAgentMessage?.invoke(helpText)
                    activityLog.logAction(task.sessionId, "", "need_help", "", helpText.take(200), task.goal, modelName)
                    return BrowserTaskResult(helpText, false, isPaused = true)
                }

                if (response.hasToolCalls) {
                    for (toolCall in response.toolCalls) {
                        val action = toolCall.params.optString("action", "unknown")
                        val target = toolCall.params.optString("selector",
                            toolCall.params.optString("url", ""))

                        onStatusChange?.invoke(TaskStatus.RUNNING, "Paso ${stepCount + 1}: $action")

                        // 6. Check for loop
                        actionHistory.add(Pair(action, target))
                        if (PageContextProvider.detectLoop(actionHistory)) {
                            status = TaskStatus.PAUSED_LOOP
                            val loopMsg = "Loop detectado: repitiendo '$action' en '$target'. Respondeme que debo hacer diferente."
                            onStatusChange?.invoke(TaskStatus.PAUSED_LOOP, loopMsg)
                            onAgentMessage?.invoke(loopMsg)
                            activityLog.logAction(task.sessionId, "", "loop_detected", action, target, task.goal, modelName)
                            return BrowserTaskResult(loopMsg, false, isPaused = true)
                        }

                        // 7. Execute the action
                        val result = browserSkill.execute(toolCall.params)
                        val resultStr = result.toString().take(300)

                        val step = BrowserStep(
                            stepNumber = stepCount + 1,
                            action = action,
                            target = target,
                            result = resultStr,
                            timestamp = System.currentTimeMillis()
                        )
                        task.completedSteps.add(step)
                        stepCount++

                        onStepComplete?.invoke(step, resultStr)
                        onAgentMessage?.invoke("$action: ${result.optString("message", resultStr.take(80))}")

                        activityLog.logAction(
                            task.sessionId,
                            pageState.optString("url", ""),
                            action, target, resultStr, task.goal, modelName
                        )

                        // Add tool result to messages
                        messages.add(ChatMessage(role = "assistant", content = "", toolCalls = listOf(toolCall)))
                        messages.add(ChatMessage(role = "tool", content = resultStr, toolCallId = toolCall.id))

                        // Small delay to let page update
                        if (action in listOf("navigate", "click", "fill_form")) {
                            Thread.sleep(1500)
                        }
                    }
                } else {
                    // No tool call — agent is providing a text response
                    val text = response.text ?: "..."
                    onAgentMessage?.invoke(text)
                    messages.add(ChatMessage(role = "assistant", content = text))

                    // If no tool call, assume task is done or agent is summarizing
                    if (stepCount > 0) {
                        status = TaskStatus.COMPLETED
                        onStatusChange?.invoke(TaskStatus.COMPLETED, "Tarea completada")
                        return BrowserTaskResult(text, true)
                    }
                }

                // Trim messages if getting too long
                if (messages.size > 30) {
                    val system = messages.first()
                    val recent = messages.takeLast(20)
                    messages.clear()
                    messages.add(system)
                    messages.addAll(recent)
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Step error: ${e.message}", e)
                val errorMsg = "Error en paso ${stepCount + 1}: ${e.message?.take(100)}"
                onAgentMessage?.invoke(errorMsg)
                activityLog.logAction(task.sessionId, "", "error", "", e.message?.take(200) ?: "", task.goal, modelName)

                // Don't crash — let the agent retry or user can cancel
                if (stepCount >= MAX_STEPS - 1) {
                    status = TaskStatus.FAILED
                    return BrowserTaskResult(errorMsg, false)
                }
            }
        }

        // Max steps reached
        if (stepCount >= MAX_STEPS) {
            status = TaskStatus.MAX_STEPS
            val msg = "Limite de $MAX_STEPS pasos alcanzado. ${task.completedSteps.size} pasos completados."
            onStatusChange?.invoke(TaskStatus.MAX_STEPS, msg)
            onAgentMessage?.invoke(msg)
            return BrowserTaskResult(msg, false)
        }

        return BrowserTaskResult("Tarea finalizada", status == TaskStatus.COMPLETED)
    }

    /**
     * Read the current page state from BrowserSkill.
     */
    private fun readPageState(): JSONObject {
        val pageData = browserSkill.execute(JSONObject().put("action", "read_page"))
        val elementsData = browserSkill.execute(
            JSONObject().put("action", "get_elements").put("selector", "")
        )

        // Merge
        pageData.put("elements", elementsData.optJSONArray("elements") ?: JSONArray())
        return pageData
    }

    private fun parseElements(pageState: JSONObject): List<PageElement> {
        val arr = pageState.optJSONArray("elements") ?: return emptyList()
        return PageElement.fromJsonArray(arr)
    }
}

// ── Data classes ──

data class BrowserTask(
    val sessionId: String,
    val goal: String,
    val completedSteps: MutableList<BrowserStep> = mutableListOf(),
    var status: TaskStatus = TaskStatus.IDLE
)

data class BrowserStep(
    val stepNumber: Int,
    val action: String,
    val target: String,
    val result: String,
    val timestamp: Long
)

data class BrowserTaskResult(
    val message: String,
    val success: Boolean,
    val isPaused: Boolean = false
)

enum class TaskStatus {
    IDLE,
    RUNNING,
    PAUSED_NEED_HELP,
    PAUSED_LOOP,
    COMPLETED,
    FAILED,
    CANCELLED,
    MAX_STEPS
}
