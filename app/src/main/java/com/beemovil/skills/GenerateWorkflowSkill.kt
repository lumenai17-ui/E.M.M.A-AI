package com.beemovil.skills

import android.util.Log
import com.beemovil.agent.*
import com.beemovil.workflow.CustomWorkflowDB
import org.json.JSONArray
import org.json.JSONObject

/**
 * GenerateWorkflowSkill — AI creates or edits custom workflows from natural language.
 *
 * Usage:
 * - "Crea un workflow que busque noticias de crypto y haga un resumen"
 * - "Agrega un paso de email al workflow 'Mi reporte'"
 *
 * The skill generates a structured Workflow JSON that the
 * WorkflowEditorScreen can display for review before saving.
 *
 * This is a META skill — it doesn't execute workflows, it DESIGNS them.
 */
class GenerateWorkflowSkill(
    private val customDB: CustomWorkflowDB? = null
) : BeeSkill {

    override val name = "generate_workflow"
    override val description = "Crea o edita un workflow personalizado basado en una descripción en lenguaje natural"

    override val parametersSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("action", JSONObject().apply {
                put("type", "string")
                put("enum", JSONArray(listOf("create", "edit")))
                put("description", "create = nuevo workflow, edit = modificar existente")
            })
            put("description", JSONObject().apply {
                put("type", "string")
                put("description", "Descripción en lenguaje natural de lo que debe hacer el workflow")
            })
            put("workflow_id", JSONObject().apply {
                put("type", "string")
                put("description", "ID del workflow a editar (solo para action=edit)")
            })
            put("workflow_name", JSONObject().apply {
                put("type", "string")
                put("description", "Nombre del workflow")
            })
            put("steps", JSONObject().apply {
                put("type", "array")
                put("description", "Lista de pasos del workflow")
                put("items", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("label", JSONObject().apply {
                            put("type", "string")
                            put("description", "Nombre descriptivo del paso")
                        })
                        put("type", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("AGENT", "SKILL")))
                            put("description", "AGENT para usar un agente, SKILL para ejecutar un skill directo")
                        })
                        put("agent_id", JSONObject().apply {
                            put("type", "string")
                            put("description", "ID del agente (main, researcher, coder, sales, etc.)")
                        })
                        put("skill_name", JSONObject().apply {
                            put("type", "string")
                            put("description", "Nombre del skill (web_search, generate_pdf, email, etc.)")
                        })
                        put("prompt", JSONObject().apply {
                            put("type", "string")
                            put("description", "Instrucción para el paso. Usa {input} para el output del paso anterior")
                        })
                    })
                })
            })
        })
        put("required", JSONArray(listOf("action", "description", "workflow_name", "steps")))
    }

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "create")
        val userDescription = params.optString("description", "")
        val workflowName = params.optString("workflow_name", "Mi Workflow")
        val stepsArr = params.optJSONArray("steps") ?: JSONArray()
        val workflowId = params.optString("workflow_id", "")

        Log.i("GenerateWorkflowSkill", "Action=$action, Name=$workflowName, Steps=${stepsArr.length()}")

        try {
            val steps = mutableListOf<WorkflowStep>()
            for (i in 0 until stepsArr.length()) {
                val stepObj = stepsArr.getJSONObject(i)
                val stepType = try {
                    StepType.valueOf(stepObj.optString("type", "AGENT"))
                } catch (_: Exception) { StepType.AGENT }

                steps.add(WorkflowStep(
                    id = "s${i + 1}",
                    label = stepObj.optString("label", "Paso ${i + 1}"),
                    icon = "STEP",
                    type = stepType,
                    agentId = stepObj.optString("agent_id", "main"),
                    skillName = stepObj.optString("skill_name", ""),
                    prompt = stepObj.optString("prompt", "{input}")
                ))
            }

            if (steps.isEmpty()) {
                return JSONObject().put("error", "No se generaron pasos. Describe qué quieres que haga el workflow.")
            }

            val workflow = Workflow(
                id = if (action == "edit" && workflowId.isNotBlank()) workflowId else "custom_${System.currentTimeMillis()}",
                name = workflowName,
                icon = "CUSTOM",
                description = userDescription.take(200),
                steps = steps,
                isTemplate = false
            )

            // Auto-save if we have the DB
            if (customDB != null) {
                if (action == "edit" && workflowId.isNotBlank()) {
                    customDB.updateWorkflow(workflowId, workflow)
                } else {
                    customDB.saveWorkflow(workflow)
                }
            }

            // Return confirmation
            val result = JSONObject()
            result.put("result", buildString {
                appendLine("✅ Workflow '${workflow.name}' ${if (action == "edit") "actualizado" else "creado"} con ${steps.size} pasos:")
                appendLine()
                steps.forEachIndexed { index, step ->
                    val typeLabel = if (step.type == StepType.AGENT) "Agent: ${step.agentId}" else "Skill: ${step.skillName}"
                    appendLine("${index + 1}. ${step.label} ($typeLabel)")
                    if (step.prompt.isNotBlank() && step.prompt != "{input}") {
                        appendLine("   Prompt: ${step.prompt.take(80)}")
                    }
                }
                appendLine()
                appendLine("Ve a Workflows → Mis Flujos para verlo, editarlo o ejecutarlo.")
            })
            result.put("workflow_id", workflow.id)
            result.put("workflow_name", workflow.name)
            result.put("step_count", steps.size)
            return result

        } catch (e: Exception) {
            Log.e("GenerateWorkflowSkill", "Error: ${e.message}", e)
            return JSONObject().put("error", "Error generando workflow: ${e.message}")
        }
    }
}
