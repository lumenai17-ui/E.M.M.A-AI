package com.beemovil.tasks

import androidx.room.*
import java.util.UUID

/**
 * EmmaTask — Core task entity for the universal task manager.
 * Supports assignment (user/emma/external), recurrence, reminders, follow-ups,
 * tags, and source tracking.
 */
@Entity(tableName = "emma_tasks")
data class EmmaTask(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // Core
    val title: String,
    val notes: String = "",
    val status: String = "pending",       // "pending" | "in_progress" | "completed" | "cancelled"
    val priority: Int = 0,                // 0=normal, 1=low, 2=high, 3=urgent

    // Assignment
    val assignee: String = "user",        // "user" | "emma" | "juan@email.com" | "Juan García"
    val assigneeType: String = "user",    // "user" | "emma" | "external"

    // Time
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val dueTime: String? = null,          // "17:00" if specific time
    val completedAt: Long? = null,

    // Reminders
    val reminderType: String? = null,     // "alarm" | "calendar" | "notification" | null
    val reminderOffsetMs: Long? = null,   // ms before dueDate
    val reminderSet: Boolean = false,

    // Follow-up
    val followUpEmail: String? = null,
    val followUpMessage: String? = null,
    val followUpTrigger: String? = null,  // "on_due" | "on_complete" | "manual"
    val followUpSent: Boolean = false,

    // Source tracking
    val source: String = "manual",        // "manual" | "chat" | "voice" | "vision" | "email" | "recurring"
    val sourceThreadId: String? = null,
    val googleTaskId: String? = null,

    // Tags
    val tags: String? = null,             // comma-separated: "mahana,urgente,Q3"

    // Recurrence
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null,    // "DAILY" | "WEEKLY" | "MONTHLY"
    val recurrenceInterval: Int = 1,
    val recurrenceDays: String? = null,    // "MON,WED,FRI" for WEEKLY
    val recurrenceEndDate: Long? = null,
    val parentRecurringId: String? = null
)

/**
 * EmmaSubtask — Checklist items within a task.
 */
@Entity(
    tableName = "emma_subtasks",
    foreignKeys = [ForeignKey(
        entity = EmmaTask::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class EmmaSubtask(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val title: String,
    val completed: Boolean = false,
    val sortOrder: Int = 0,
    val completedAt: Long? = null
)

/**
 * TaskAttachment — File references attached to tasks.
 */
@Entity(
    tableName = "emma_task_attachments",
    foreignKeys = [ForeignKey(
        entity = EmmaTask::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class TaskAttachment(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val filePath: String,
    val fileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "manual"          // "manual" | "emma_generated" | "email" | "camera"
)
