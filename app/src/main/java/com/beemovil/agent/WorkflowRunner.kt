package com.beemovil.agent

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.beemovil.files.AttachmentManager
import com.beemovil.llm.LlmFactory
import com.beemovil.llm.LlmProvider
import com.beemovil.memory.BeeMemoryDB
import com.beemovil.skills.BeeSkill
import com.beemovil.workflow.WorkflowHistoryDB
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * WorkflowRunner — Resilient workflow executor with:
 * - Per-step model override
 * - Error recovery (retry / skip / cancel)
 * - History persistence via WorkflowHistoryDB
 * - Auto-save results to BeeMovil/generated/
 * - Survives navigation when owned by ViewModel
 *
 * Runs on a background thread. Reports state changes via callback.
 */
class WorkflowRunner(
    private val agentResolver: (String) -> BeeAgent?,
    private val skills: Map<String, BeeSkill>,
    private val onStateChanged: (WorkflowRun) -> Unit,
    private val llmFactory: ((String, String) -> LlmProvider)? = null,
    private val historyDB: WorkflowHistoryDB? = null
) {
    companion object {
        private const val TAG = "WorkflowRunner"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Latch for pausing execution on error, waiting for user action */
    private var errorLatch: CountDownLatch? = null
    private var errorAction = AtomicReference<StepErrorAction?>(null)

    /** Current active run (for resuming after error) */
    @Volatile
    var currentRun: WorkflowRun? = null
        private set

    /**
     * Execute a workflow. MUST be called from a background thread.
     * modelOverrides: map of step_id -> model_id for per-step model selection
     */
    fun execute(
        workflow: Workflow,
        initialInput: String = "",
        modelOverrides: Map<String, String> = emptyMap()
    ): WorkflowRun {
        val run = WorkflowRun(
            workflow = workflow,
            stepStates = workflow.steps.map { StepState(step = it) }.toMutableList()
        )
        currentRun = run

        Log.i(TAG, "Starting workflow: ${workflow.name} (${workflow.steps.size} steps)")

        // Persist to history
        val historyId = historyDB?.startRun(
            workflowId = workflow.id,
            workflowName = workflow.name,
            workflowIcon = workflow.icon,
            userInput = initialInput,
            modelOverrides = modelOverrides
        ) ?: -1
        run.historyId = historyId

        notifyChange(run)

        var previousOutput = initialInput
        val totalStartTime = System.currentTimeMillis()

        var i = 0
        while (i < workflow.steps.size) {
            run.currentStepIndex = i
            val stepState = run.stepStates[i]
            val step = stepState.step

            // Mark as running
            stepState.status = StepStatus.RUNNING
            stepState.error = ""
            notifyChange(run)

            val startTime = System.currentTimeMillis()

            try {
                Log.i(TAG, "Step ${i + 1}/${workflow.steps.size}: ${step.label} (${step.type})")

                // Resolve the prompt with previous output
                val resolvedPrompt = step.prompt.replace("{input}", previousOutput)

                // Check for model override
                val stepModelId = modelOverrides[step.id] ?: step.modelOverride

                val output = when (step.type) {
                    StepType.AGENT -> executeAgentStep(step, resolvedPrompt, stepModelId)
                    StepType.SKILL -> executeSkillStep(step, resolvedPrompt, previousOutput)
                }

                stepState.output = output
                stepState.status = StepStatus.COMPLETED
                stepState.elapsedMs = System.currentTimeMillis() - startTime

                Log.i(TAG, "Step ${i + 1} completed in ${stepState.elapsedMs}ms: ${output.take(100)}")

                // This step's output becomes next step's input
                previousOutput = output

                // Save step progress
                historyDB?.updateSteps(historyId, stepsToJson(run.stepStates))

            } catch (e: Exception) {
                Log.e(TAG, "Step ${i + 1} failed: ${e.message}", e)
                stepState.error = e.message ?: "Unknown error"
                stepState.status = StepStatus.FAILED
                stepState.elapsedMs = System.currentTimeMillis() - startTime

                // ── ERROR RECOVERY: Pause and wait for user action ──
                run.pendingErrorAction = true
                notifyChange(run)

                // Block this thread until user chooses an action
                errorLatch = CountDownLatch(1)
                errorAction.set(null)
                try {
                    errorLatch!!.await()  // Will block until resolveError() is called
                } catch (ie: InterruptedException) {
                    // Treat interruption as cancel
                    errorAction.set(StepErrorAction.CANCEL)
                }

                run.pendingErrorAction = false
                val action = errorAction.get() ?: StepErrorAction.CANCEL

                when (action) {
                    StepErrorAction.RETRY -> {
                        Log.i(TAG, "Retrying step ${i + 1}")
                        stepState.status = StepStatus.PENDING
                        stepState.error = ""
                        // Don't increment i — will re-execute same step
                        notifyChange(run)
                        continue
                    }
                    StepErrorAction.SKIP -> {
                        Log.i(TAG, "Skipping step ${i + 1}")
                        stepState.status = StepStatus.SKIPPED
                        // Keep previousOutput from last successful step
                        notifyChange(run)
                    }
                    StepErrorAction.CANCEL -> {
                        Log.i(TAG, "Cancelling workflow at step ${i + 1}")
                        for (j in (i + 1) until run.stepStates.size) {
                            run.stepStates[j].status = StepStatus.SKIPPED
                        }
                        run.isCancelled = true
                        run.isFailed = true
                        run.finalOutput = buildPartialOutput(run, previousOutput)

                        val elapsed = System.currentTimeMillis() - totalStartTime
                        historyDB?.updateSteps(historyId, stepsToJson(run.stepStates))
                        historyDB?.completeRun(historyId, "cancelled", run.finalOutput, elapsed)
                        autoSaveResult(run)

                        notifyChange(run)
                        currentRun = null
                        return run
                    }
                }
            }

            notifyChange(run)
            i++
        }

        // All steps completed (or skipped)
        val hasAnyFailed = run.stepStates.any { it.status == StepStatus.FAILED }
        run.isComplete = !hasAnyFailed
        run.isFailed = hasAnyFailed
        run.finalOutput = previousOutput

        val totalElapsed = System.currentTimeMillis() - totalStartTime
        val status = if (run.isComplete) "completed" else "failed"
        historyDB?.updateSteps(historyId, stepsToJson(run.stepStates))
        historyDB?.completeRun(historyId, status, run.finalOutput, totalElapsed)
        autoSaveResult(run)

        notifyChange(run)
        currentRun = null

        Log.i(TAG, "Workflow '${workflow.name}' finished: $status")
        return run
    }

    /**
     * Called from UI thread when user selects an error recovery action.
     */
    fun resolveError(action: StepErrorAction) {
        errorAction.set(action)
        errorLatch?.countDown()
    }

    /**
     * Force-cancel a running workflow.
     */
    fun forceCancel() {
        resolveError(StepErrorAction.CANCEL)
    }

    private fun executeAgentStep(step: WorkflowStep, prompt: String, modelOverride: String? = null): String {
        val agent = if (modelOverride != null && llmFactory != null) {
            // Create a temporary agent with the overridden model
            val config = agentResolver(step.agentId)?.config
                ?: throw Exception("Agente '${step.agentId}' no encontrado")
            // Determine provider from model ID
            val provider = when {
                modelOverride.endsWith(":cloud") -> "ollama"
                modelOverride.startsWith("gemma4-e") -> "local"
                else -> "openrouter"
            }
            val llm = llmFactory.invoke(provider, modelOverride)
            BeeAgent(config = config, llm = llm, skills = skills)
        } else {
            agentResolver(step.agentId)
                ?: throw Exception("Agente '${step.agentId}' no encontrado")
        }

        val response = agent.chat(prompt)
        if (response.isError) {
            throw Exception("Agente respondió con error: ${response.text}")
        }

        // Include file paths in output if generated
        val files = response.toolExecutions.mapNotNull { exec ->
            arrayOf("path", "file_path", "filepath", "output_path").firstNotNullOfOrNull { key ->
                if (exec.result.has(key)) exec.result.getString(key) else null
            }
        }

        return if (files.isNotEmpty()) {
            "${response.text}\n\nArchivos: ${files.joinToString(", ")}"
        } else {
            response.text
        }
    }

    private fun executeSkillStep(step: WorkflowStep, prompt: String, rawInput: String): String {
        val skill = skills[step.skillName]
            ?: throw Exception("Skill '${step.skillName}' no encontrado")

        val params = JSONObject()
        step.fixedParams.forEach { (key, value) ->
            params.put(key, value.replace("{input}", rawInput))
        }
        if (params.length() == 0) {
            params.put("text", prompt)
            params.put("content", prompt)
            params.put("query", prompt)
        }

        val result = skill.execute(params)

        return when {
            result.has("error") -> throw Exception(result.getString("error"))
            result.has("result") -> result.getString("result")
            result.has("path") -> "Archivo: ${result.getString("path")}"
            result.has("text") -> result.getString("text")
            else -> result.toString(2)
        }
    }

    /**
     * Auto-save workflow result to BeeMovil/generated/
     */
    private fun autoSaveResult(run: WorkflowRun) {
        if (run.finalOutput.isNotBlank()) {
            try {
                AttachmentManager.saveGeneratedFile(
                    content = run.finalOutput,
                    name = "workflow_${run.workflow.id}",
                    extension = "txt"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-save: ${e.message}")
            }
        }
    }

    private fun buildPartialOutput(run: WorkflowRun, lastGoodOutput: String): String {
        val sb = StringBuilder()
        sb.appendLine("=== Workflow parcial: ${run.workflow.name} ===")
        run.stepStates.forEach { ss ->
            val statusIcon = when (ss.status) {
                StepStatus.COMPLETED -> "✅"
                StepStatus.FAILED -> "❌"
                StepStatus.SKIPPED -> "⏭️"
                StepStatus.RUNNING -> "🔄"
                StepStatus.PENDING -> "⏳"
            }
            sb.appendLine("$statusIcon ${ss.step.label}")
            if (ss.output.isNotBlank()) sb.appendLine("   ${ss.output.take(200)}")
            if (ss.error.isNotBlank()) sb.appendLine("   Error: ${ss.error}")
        }
        sb.appendLine("\n--- Ultimo output exitoso ---")
        sb.appendLine(lastGoodOutput)
        return sb.toString()
    }

    private fun stepsToJson(steps: List<StepState>): String {
        val arr = JSONArray()
        steps.forEach { ss ->
            arr.put(JSONObject().apply {
                put("id", ss.step.id)
                put("label", ss.step.label)
                put("status", ss.status.name)
                put("output", ss.output.take(500))
                put("error", ss.error)
                put("elapsed_ms", ss.elapsedMs)
            })
        }
        return arr.toString()
    }

    private fun notifyChange(run: WorkflowRun) {
        val snapshot = run.copy(
            stepStates = run.stepStates.map { it.copy() }.toMutableList()
        )
        mainHandler.post { onStateChanged(snapshot) }
    }
}
