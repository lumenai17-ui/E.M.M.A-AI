package com.beemovil.google

import android.util.Log
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import java.util.Date

/**
 * GoogleTasksService — Read/Write Google Tasks.
 *
 * All methods are SYNCHRONOUS — call from Dispatchers.IO.
 * Access token provided by GoogleAuthManager.
 */
class GoogleTasksService(private val accessToken: String) {

    companion object {
        private const val TAG = "GoogleTasks"
        const val SCOPE = TasksScopes.TASKS
        const val SCOPE_READONLY = TasksScopes.TASKS_READONLY
    }

    data class TaskItem(
        val id: String,
        val title: String,
        val notes: String,
        val status: String,        // "needsAction" | "completed"
        val dueDate: Long?,
        val completed: Boolean,
        val selfLink: String?
    )

    data class TaskList(
        val id: String,
        val title: String
    )

    private val tasksService: Tasks by lazy {
        val credentials = GoogleCredentials.create(AccessToken(accessToken, Date(System.currentTimeMillis() + 3600_000)))
        Tasks.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        )
            .setApplicationName("BeeMovil")
            .build()
    }

    // ═══════════════════════════════════════
    // TASK LISTS
    // ═══════════════════════════════════════

    fun getTaskLists(): List<TaskList> {
        return try {
            val result = tasksService.tasklists().list()
                .setMaxResults(20)
                .execute()
            result.items?.map { TaskList(it.id, it.title) } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Get task lists error: ${e.message}", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════
    // LIST TASKS
    // ═══════════════════════════════════════

    /**
     * List tasks from a specific task list.
     * Use "@default" for the user's default list.
     */
    fun listTasks(taskListId: String = "@default", showCompleted: Boolean = false, maxResults: Int = 50): List<TaskItem> {
        return try {
            val request = tasksService.tasks().list(taskListId)
                .setMaxResults(maxResults)
                .setShowCompleted(showCompleted)
                .setShowHidden(false)

            val result = request.execute()
            result.items?.map { parseTask(it) } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "List tasks error: ${e.message}", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════
    // CREATE TASK
    // ═══════════════════════════════════════

    /**
     * Create a new task.
     * @return Task ID or null on error
     */
    fun createTask(
        title: String,
        notes: String = "",
        dueMillis: Long? = null,
        taskListId: String = "@default"
    ): String? {
        return try {
            val task = Task().apply {
                this.title = title
                this.notes = notes
                if (dueMillis != null) {
                    this.due = com.google.api.client.util.DateTime(dueMillis).toStringRfc3339()
                }
            }

            val created = tasksService.tasks()
                .insert(taskListId, task)
                .execute()

            Log.i(TAG, "Task created: ${created.id} — $title")
            created.id
        } catch (e: Exception) {
            Log.e(TAG, "Create task error: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════
    // COMPLETE TASK
    // ═══════════════════════════════════════

    fun completeTask(taskId: String, taskListId: String = "@default"): Boolean {
        return try {
            val task = tasksService.tasks().get(taskListId, taskId).execute()
            task.status = "completed"
            tasksService.tasks().update(taskListId, taskId, task).execute()
            Log.i(TAG, "Task completed: $taskId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Complete task error: ${e.message}", e)
            false
        }
    }

    // ═══════════════════════════════════════
    // DELETE TASK
    // ═══════════════════════════════════════

    fun deleteTask(taskId: String, taskListId: String = "@default"): Boolean {
        return try {
            tasksService.tasks().delete(taskListId, taskId).execute()
            Log.i(TAG, "Task deleted: $taskId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Delete task error: ${e.message}", e)
            false
        }
    }

    // ═══════════════════════════════════════
    // FORMAT FOR LLM
    // ═══════════════════════════════════════

    fun formatTasksForLlm(tasks: List<TaskItem>): String {
        if (tasks.isEmpty()) return "No tienes tareas pendientes. 🎉"
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        return buildString {
            appendLine("📋 Tus ${tasks.size} tareas pendientes:")
            tasks.forEachIndexed { i, t ->
                val check = if (t.completed) "✅" else "⬜"
                val due = if (t.dueDate != null) " (vence: ${sdf.format(Date(t.dueDate))})" else ""
                appendLine("$check ${i + 1}. **${t.title}**$due")
                if (t.notes.isNotBlank()) appendLine("   ${t.notes.take(60)}")
            }
        }
    }

    private fun parseTask(task: Task): TaskItem {
        return TaskItem(
            id = task.id ?: "",
            title = task.title ?: "(Sin título)",
            notes = task.notes ?: "",
            status = task.status ?: "needsAction",
            dueDate = try { task.due?.let { com.google.api.client.util.DateTime.parseRfc3339(it).value } } catch (_: Exception) { null },
            completed = task.status == "completed",
            selfLink = task.selfLink
        )
    }
}
