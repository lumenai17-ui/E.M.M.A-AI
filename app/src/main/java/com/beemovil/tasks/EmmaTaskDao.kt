package com.beemovil.tasks

import androidx.room.*

/**
 * DAO for E.M.M.A. Task Manager — all CRUD + query operations.
 */
@Dao
interface EmmaTaskDao {

    // ═══════════════════════════════════════
    //  TASKS
    // ═══════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: EmmaTask)

    @Update
    suspend fun updateTask(task: EmmaTask)

    @Delete
    suspend fun deleteTask(task: EmmaTask)

    @Query("DELETE FROM emma_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)

    @Query("SELECT * FROM emma_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): EmmaTask?

    /** All non-cancelled tasks ordered by priority desc, then due date. */
    @Query("""
        SELECT * FROM emma_tasks 
        WHERE status != 'cancelled' AND parentRecurringId IS NULL
        ORDER BY 
            CASE WHEN status = 'completed' THEN 1 ELSE 0 END,
            priority DESC, 
            CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END,
            dueDate ASC,
            createdAt DESC
    """)
    suspend fun getAllActiveTasks(): List<EmmaTask>

    /** Filter by assignee type */
    @Query("""
        SELECT * FROM emma_tasks 
        WHERE assigneeType = :type AND status != 'cancelled'
        ORDER BY priority DESC, dueDate ASC
    """)
    suspend fun getTasksByAssigneeType(type: String): List<EmmaTask>

    /** Pending + in_progress only */
    @Query("""
        SELECT * FROM emma_tasks 
        WHERE status IN ('pending', 'in_progress') AND parentRecurringId IS NULL
        ORDER BY priority DESC, dueDate ASC
    """)
    suspend fun getPendingTasks(): List<EmmaTask>

    /** Tasks due today or overdue */
    @Query("""
        SELECT * FROM emma_tasks 
        WHERE status IN ('pending', 'in_progress') 
        AND dueDate IS NOT NULL AND dueDate <= :endOfDayMs
        ORDER BY priority DESC, dueDate ASC
    """)
    suspend fun getTasksDueBy(endOfDayMs: Long): List<EmmaTask>

    /** Search by title or notes */
    @Query("""
        SELECT * FROM emma_tasks 
        WHERE (title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
        AND status != 'cancelled'
        ORDER BY priority DESC, dueDate ASC
    """)
    suspend fun searchTasks(query: String): List<EmmaTask>

    /** Filter by tag */
    @Query("""
        SELECT * FROM emma_tasks 
        WHERE tags LIKE '%' || :tag || '%'
        AND status != 'cancelled'
        ORDER BY priority DESC, dueDate ASC
    """)
    suspend fun getTasksByTag(tag: String): List<EmmaTask>

    /** Completed tasks for history */
    @Query("""
        SELECT * FROM emma_tasks 
        WHERE status = 'completed'
        ORDER BY completedAt DESC
        LIMIT :limit
    """)
    suspend fun getCompletedTasks(limit: Int = 50): List<EmmaTask>

    /** Recurring templates (isRecurring = true) */
    @Query("SELECT * FROM emma_tasks WHERE isRecurring = 1")
    suspend fun getRecurringTemplates(): List<EmmaTask>

    /** Tasks needing follow-up (due, not sent) */
    @Query("""
        SELECT * FROM emma_tasks 
        WHERE followUpEmail IS NOT NULL AND followUpSent = 0
        AND dueDate IS NOT NULL AND dueDate <= :nowMs
        AND status IN ('pending', 'in_progress')
    """)
    suspend fun getTasksNeedingFollowUp(nowMs: Long): List<EmmaTask>

    /** Count by status */
    @Query("SELECT COUNT(*) FROM emma_tasks WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    // ═══════════════════════════════════════
    //  SUBTASKS
    // ═══════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtask(subtask: EmmaSubtask)

    @Update
    suspend fun updateSubtask(subtask: EmmaSubtask)

    @Delete
    suspend fun deleteSubtask(subtask: EmmaSubtask)

    @Query("SELECT * FROM emma_subtasks WHERE taskId = :taskId ORDER BY sortOrder ASC")
    suspend fun getSubtasks(taskId: String): List<EmmaSubtask>

    @Query("UPDATE emma_subtasks SET completed = :completed, completedAt = :completedAt WHERE id = :subtaskId")
    suspend fun toggleSubtask(subtaskId: String, completed: Boolean, completedAt: Long?)

    // ═══════════════════════════════════════
    //  ATTACHMENTS
    // ═══════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: TaskAttachment)

    @Delete
    suspend fun deleteAttachment(attachment: TaskAttachment)

    @Query("SELECT * FROM emma_task_attachments WHERE taskId = :taskId ORDER BY createdAt DESC")
    suspend fun getAttachments(taskId: String): List<TaskAttachment>
}
