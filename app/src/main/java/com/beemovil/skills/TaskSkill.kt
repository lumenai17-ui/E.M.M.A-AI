package com.beemovil.skills

import android.content.Context
import com.beemovil.memory.BeeTask
import com.beemovil.memory.TaskDB
import com.beemovil.memory.TaskPriority
import com.beemovil.memory.TaskStatus
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * TaskSkill — Agent tool for managing BeeMovil's internal task/calendar system.
 *
 * Actions: create, list, complete, delete
 *
 * This is the INTERNAL task system (bee_tasks.db), NOT the Android OS calendar.
 * The agent should prefer this over CalendarSkill for BeeMovil tasks.
 */
class TaskSkill(context: Context) : BeeSkill {
    override val name = "task_manager"
    override val description = """Manage BeeMovil internal tasks and calendar. Use this (NOT 'calendar') to create, list, complete, or delete tasks.
Actions:
- create: Create a new task. Params: title (required), description, priority (LOW/NORMAL/HIGH/URGENT), due_date (format: yyyy-MM-dd or yyyy-MM-dd HH:mm)
- list: List tasks. Params: status (PENDING/COMPLETED/all), limit (default 10)
- complete: Mark task as done. Params: task_id
- delete: Delete a task. Params: task_id"""

    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "action":{"type":"string","enum":["create","list","complete","delete"],"description":"Action to perform"},
            "title":{"type":"string","description":"Task title (for create)"},
            "description":{"type":"string","description":"Task description (for create)"},
            "priority":{"type":"string","enum":["LOW","NORMAL","HIGH","URGENT"],"description":"Priority level"},
            "due_date":{"type":"string","description":"Due date: yyyy-MM-dd or yyyy-MM-dd HH:mm"},
            "task_id":{"type":"integer","description":"Task ID (for complete/delete)"},
            "status":{"type":"string","description":"Filter: PENDING, COMPLETED, or all"},
            "limit":{"type":"integer","description":"Max results for list (default 10)"}
        },"required":["action"]}
    """.trimIndent())

    private val taskDB = TaskDB(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val displayFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es"))

    override fun execute(params: JSONObject): JSONObject {
        val action = params.optString("action", "list")

        return try {
            when (action) {
                "create" -> createTask(params)
                "list" -> listTasks(params)
                "complete" -> completeTask(params)
                "delete" -> deleteTask(params)
                else -> JSONObject().put("error", "Accion desconocida: $action. Usa: create, list, complete, delete")
            }
        } catch (e: Exception) {
            JSONObject().put("error", "Error en task_manager: ${e.message}")
        }
    }

    private fun createTask(params: JSONObject): JSONObject {
        val title = params.optString("title", "")
        if (title.isBlank()) return JSONObject().put("error", "Se requiere 'title' para crear tarea")

        val description = params.optString("description", "")
        val priorityStr = params.optString("priority", "NORMAL").uppercase()
        val priority = try { TaskPriority.valueOf(priorityStr) } catch (_: Exception) { TaskPriority.NORMAL }

        var dueDate = 0L
        val dueDateStr = params.optString("due_date", "")
        if (dueDateStr.isNotBlank()) {
            dueDate = try {
                if (dueDateStr.contains(":")) dateTimeFormat.parse(dueDateStr)?.time ?: 0L
                else dateFormat.parse(dueDateStr)?.time ?: 0L
            } catch (_: Exception) { 0L }
        }

        val task = BeeTask(
            title = title,
            description = description,
            priority = priority,
            dueDate = dueDate
        )
        val id = taskDB.addTask(task)

        return JSONObject().apply {
            put("success", true)
            put("message", "Tarea creada: \"$title\" (ID: $id, Prioridad: $priorityStr)")
            put("task_id", id)
            if (dueDate > 0) put("due_date", displayFormat.format(dueDate))
        }
    }

    private fun listTasks(params: JSONObject): JSONObject {
        val statusStr = params.optString("status", "PENDING")
        val limit = params.optInt("limit", 10)

        val tasks = if (statusStr.equals("all", ignoreCase = true)) {
            taskDB.getAllTasks().take(limit)
        } else {
            val status = try { TaskStatus.valueOf(statusStr.uppercase()) } catch (_: Exception) { TaskStatus.PENDING }
            taskDB.getAllTasks(status).take(limit)
        }

        val taskArray = JSONArray()
        tasks.forEach { task ->
            taskArray.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("description", task.description)
                put("status", task.status.name)
                put("priority", task.priority.name)
                if (task.dueDate > 0) put("due_date", displayFormat.format(task.dueDate))
                put("created", displayFormat.format(task.createdAt))
            })
        }

        return JSONObject().apply {
            put("tasks", taskArray)
            put("count", tasks.size)
            put("filter", statusStr)
        }
    }

    private fun completeTask(params: JSONObject): JSONObject {
        val taskId = params.optLong("task_id", -1)
        if (taskId < 0) return JSONObject().put("error", "Se requiere 'task_id' para completar tarea")
        taskDB.completeTask(taskId)
        return JSONObject().apply {
            put("success", true)
            put("message", "Tarea $taskId marcada como completada")
        }
    }

    private fun deleteTask(params: JSONObject): JSONObject {
        val taskId = params.optLong("task_id", -1)
        if (taskId < 0) return JSONObject().put("error", "Se requiere 'task_id' para eliminar tarea")
        taskDB.deleteTask(taskId)
        return JSONObject().apply {
            put("success", true)
            put("message", "Tarea $taskId eliminada")
        }
    }
}
