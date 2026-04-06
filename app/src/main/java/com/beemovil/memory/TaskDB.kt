package com.beemovil.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * TaskDB — Local SQLite database for managing tasks.
 *
 * Tasks can be created by the user, the agent, or workflows.
 * Each task has a status, priority, optional due date, and is linked to an agent.
 */
data class BeeTask(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val agentId: String = "main",
    val dueDate: Long = 0L,  // 0 = no due date
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L
)

enum class TaskStatus { PENDING, IN_PROGRESS, COMPLETED, CANCELLED }
enum class TaskPriority { LOW, NORMAL, HIGH, URGENT }

class TaskDB(context: Context) : SQLiteOpenHelper(context, "bee_tasks.db", null, 1) {

    companion object {
        private const val TAG = "TaskDB"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                description TEXT DEFAULT '',
                status TEXT DEFAULT 'PENDING',
                priority TEXT DEFAULT 'NORMAL',
                agent_id TEXT DEFAULT 'main',
                due_date INTEGER DEFAULT 0,
                created_at INTEGER DEFAULT 0,
                completed_at INTEGER DEFAULT 0
            )
        """)
        Log.i(TAG, "Tasks database created")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS tasks")
        onCreate(db)
    }

    fun addTask(task: BeeTask): Long {
        val values = ContentValues().apply {
            put("title", task.title)
            put("description", task.description)
            put("status", task.status.name)
            put("priority", task.priority.name)
            put("agent_id", task.agentId)
            put("due_date", task.dueDate)
            put("created_at", task.createdAt)
            put("completed_at", task.completedAt)
        }
        val id = writableDatabase.insert("tasks", null, values)
        Log.i(TAG, "Task added: ${task.title} (id=$id)")
        return id
    }

    fun updateTask(task: BeeTask) {
        val values = ContentValues().apply {
            put("title", task.title)
            put("description", task.description)
            put("status", task.status.name)
            put("priority", task.priority.name)
            put("agent_id", task.agentId)
            put("due_date", task.dueDate)
            put("completed_at", task.completedAt)
        }
        writableDatabase.update("tasks", values, "id = ?", arrayOf(task.id.toString()))
        Log.i(TAG, "Task updated: ${task.title}")
    }

    fun completeTask(id: Long) {
        val values = ContentValues().apply {
            put("status", TaskStatus.COMPLETED.name)
            put("completed_at", System.currentTimeMillis())
        }
        writableDatabase.update("tasks", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteTask(id: Long) {
        writableDatabase.delete("tasks", "id = ?", arrayOf(id.toString()))
    }

    fun getAllTasks(statusFilter: TaskStatus? = null): List<BeeTask> {
        val selection = if (statusFilter != null) "status = ?" else null
        val args = if (statusFilter != null) arrayOf(statusFilter.name) else null
        val cursor = readableDatabase.query(
            "tasks", null, selection, args, null, null, "created_at DESC"
        )
        val results = mutableListOf<BeeTask>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(cursorToTask(it))
            }
        }
        return results
    }

    fun getPendingTasks(limit: Int = 5): List<BeeTask> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM tasks WHERE status IN ('PENDING', 'IN_PROGRESS') ORDER BY " +
            "CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END, " +
            "due_date ASC LIMIT ?",
            arrayOf(limit.toString())
        )
        val results = mutableListOf<BeeTask>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(cursorToTask(it))
            }
        }
        return results
    }

    fun getTaskCount(): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM tasks WHERE status IN ('PENDING', 'IN_PROGRESS')", null
        )
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun getTasksByDate(startMs: Long, endMs: Long): List<BeeTask> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM tasks WHERE due_date BETWEEN ? AND ? ORDER BY due_date ASC",
            arrayOf(startMs.toString(), endMs.toString())
        )
        val results = mutableListOf<BeeTask>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(cursorToTask(it))
            }
        }
        return results
    }

    private fun cursorToTask(cursor: android.database.Cursor): BeeTask {
        return BeeTask(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: "",
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")) ?: "",
            status = try { TaskStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))) } catch (_: Exception) { TaskStatus.PENDING },
            priority = try { TaskPriority.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("priority"))) } catch (_: Exception) { TaskPriority.NORMAL },
            agentId = cursor.getString(cursor.getColumnIndexOrThrow("agent_id")) ?: "main",
            dueDate = cursor.getLong(cursor.getColumnIndexOrThrow("due_date")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            completedAt = cursor.getLong(cursor.getColumnIndexOrThrow("completed_at"))
        )
    }
}
