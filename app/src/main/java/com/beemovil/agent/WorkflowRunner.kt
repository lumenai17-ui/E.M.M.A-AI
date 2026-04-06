package com.beemovil.agent

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.beemovil.llm.LlmFactory
import com.beemovil.memory.BeeMemoryDB
import com.beemovil.skills.BeeSkill
import org.json.JSONObject

/**
 * WorkflowRunner — Executes workflow steps sequentially, piping output between them.
 *
 * Runs on a background thread. Reports state changes via callback for UI updates.
 *
 * Flow:
 * 1. For each step, inject previous output as {input}
 * 2. Execute step (agent chat or skill execution)
 * 3. Capture output, update state, notify UI
 * 4. Feed output to next step
 * 5. On completion, combine all outputs into final result
 */
class WorkflowRunner(
    private val agentResolver: (String) -> BeeAgent?,
    private val skills: Map<String, BeeSkill>,
    private val onStateChanged: (WorkflowRun) -> Unit
) {
    companion object {
        private const val TAG = "WorkflowRunner"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Execute a workflow. MUST be called from a background thread.
     */
    fun execute(workflow: Workflow, initialInput: String = ""): WorkflowRun {
        val run = WorkflowRun(
            workflow = workflow,
            stepStates = workflow.steps.map { StepState(step = it) }.toMutableList()
        )

        Log.i(TAG, "Starting workflow: ${workflow.name} (${workflow.steps.size} steps)")
        notifyChange(run)

        var previousOutput = initialInput

        for (i in workflow.steps.indices) {
            run.currentStepIndex = i
            val stepState = run.stepStates[i]
            val step = stepState.step

            // Mark as running
            stepState.status = StepStatus.RUNNING
            notifyChange(run)

            val startTime = System.currentTimeMillis()

            try {
                Log.i(TAG, "Step ${i + 1}/${workflow.steps.size}: ${step.label} (${step.type})")

                // Resolve the prompt with previous output
                val resolvedPrompt = step.prompt.replace("{input}", previousOutput)

                val output = when (step.type) {
                    StepType.AGENT -> executeAgentStep(step, resolvedPrompt)
                    StepType.SKILL -> executeSkillStep(step, resolvedPrompt, previousOutput)
                }

                stepState.output = output
                stepState.status = StepStatus.COMPLETED
                stepState.elapsedMs = System.currentTimeMillis() - startTime

                Log.i(TAG, "Step ${i + 1} completed in ${stepState.elapsedMs}ms: ${output.take(100)}")

                // This step's output becomes next step's input
                previousOutput = output

            } catch (e: Exception) {
                Log.e(TAG, "Step ${i + 1} failed: ${e.message}", e)
                stepState.error = e.message ?: "Unknown error"
                stepState.status = StepStatus.FAILED
                stepState.elapsedMs = System.currentTimeMillis() - startTime

                // Mark remaining steps as skipped
                for (j in (i + 1) until run.stepStates.size) {
                    run.stepStates[j].status = StepStatus.SKIPPED
                }

                run.isFailed = true
                run.finalOutput = "Error: Workflow fallo en paso ${i + 1} (${step.label}): ${e.message}"
                notifyChange(run)
                return run
            }

            notifyChange(run)
        }

        // All steps completed
        run.isComplete = true
        run.finalOutput = previousOutput
        notifyChange(run)

        Log.i(TAG, "Workflow '${workflow.name}' completed successfully")
        return run
    }

    private fun executeAgentStep(step: WorkflowStep, prompt: String): String {
        val agent = agentResolver(step.agentId)
            ?: throw Exception("Agente '${step.agentId}' no encontrado")

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

        // Build params from fixed params + dynamic input
        val params = JSONObject()
        step.fixedParams.forEach { (key, value) ->
            params.put(key, value.replace("{input}", rawInput))
        }
        // If no specific params, pass the prompt as the primary param
        if (params.length() == 0) {
            params.put("text", prompt)
            params.put("content", prompt)
            params.put("query", prompt)
        }

        val result = skill.execute(params)

        // Extract meaningful output
        return when {
            result.has("error") -> throw Exception(result.getString("error"))
            result.has("result") -> result.getString("result")
            result.has("path") -> "Archivo: ${result.getString("path")}"
            result.has("text") -> result.getString("text")
            else -> result.toString(2)
        }
    }

    private fun notifyChange(run: WorkflowRun) {
        // Copy state to avoid race conditions
        val snapshot = run.copy(
            stepStates = run.stepStates.map { it.copy() }.toMutableList()
        )
        mainHandler.post { onStateChanged(snapshot) }
    }
}
