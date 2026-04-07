package com.beemovil.workflow

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.beemovil.agent.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * CustomWorkflowDB — Persistent storage for user-created workflows.
 *
 * Each workflow stores:
 * - Name, description, icon
 * - Steps as JSON (agent/skill, prompt, model override, params)
 * - Schedule configuration (cron-like)
 * - Link to TaskDB for scheduled workflows
 * - Created/updated timestamps
 */
class CustomWorkflowDB(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "CustomWorkflowDB"
        private const val DB_NAME = "custom_workflows.db"
        private const val DB_VERSION = 1
        private const val TABLE = "workflows"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT DEFAULT '',
                icon TEXT DEFAULT 'FLOW',
                steps_json TEXT NOT NULL DEFAULT '[]',
                schedule_json TEXT DEFAULT '',
                task_id INTEGER DEFAULT -1,
                is_enabled INTEGER DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_run_at INTEGER DEFAULT 0,
                run_count INTEGER DEFAULT 0
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    // ═══════════════════════════════════════════════════════
    // CRUD
    // ═══════════════════════════════════════════════════════

    /**
     * Save a new custom workflow. Returns the ID.
     */
    fun saveWorkflow(workflow: Workflow, schedule: ScheduleConfig? = null): String {
        val id = workflow.id.ifBlank { "custom_${System.currentTimeMillis()}" }
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", id)
            put("name", workflow.name)
            put("description", workflow.description)
            put("icon", workflow.icon)
            put("steps_json", stepsToJson(workflow.steps))
            put("schedule_json", schedule?.toJson() ?: "")
            put("is_enabled", 1)
            put("created_at", now)
            put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Log.i(TAG, "Saved workflow '$id': ${workflow.name} (${workflow.steps.size} steps)")
        return id
    }

    /**
     * Update an existing workflow (name, steps, schedule).
     */
    fun updateWorkflow(id: String, workflow: Workflow, schedule: ScheduleConfig? = null) {
        val values = ContentValues().apply {
            put("name", workflow.name)
            put("description", workflow.description)
            put("icon", workflow.icon)
            put("steps_json", stepsToJson(workflow.steps))
            put("schedule_json", schedule?.toJson() ?: "")
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))
        Log.i(TAG, "Updated workflow '$id'")
    }

    /**
     * Mark a workflow as just executed.
     */
    fun markRun(id: String) {
        val values = ContentValues().apply {
            put("last_run_at", System.currentTimeMillis())
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))
        writableDatabase.execSQL("UPDATE $TABLE SET run_count = run_count + 1 WHERE id = ?", arrayOf(id))
    }

    /**
     * Delete a workflow.
     */
    fun deleteWorkflow(id: String) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id))
        Log.i(TAG, "Deleted workflow '$id'")
    }

    /**
     * Get all custom workflows.
     */
    fun getAllWorkflows(): List<CustomWorkflowRecord> {
        val list = mutableListOf<CustomWorkflowRecord>()
        val cursor = readableDatabase.query(TABLE, null, null, null, null, null, "updated_at DESC")
        while (cursor.moveToNext()) {
            list.add(cursorToRecord(cursor))
        }
        cursor.close()
        return list
    }

    /**
     * Get a single workflow by ID.
     */
    fun getWorkflow(id: String): CustomWorkflowRecord? {
        val cursor = readableDatabase.query(TABLE, null, "id = ?", arrayOf(id), null, null, null)
        val record = if (cursor.moveToFirst()) cursorToRecord(cursor) else null
        cursor.close()
        return record
    }

    /**
     * Get all scheduled workflows (for the scheduler service).
     */
    fun getScheduledWorkflows(): List<CustomWorkflowRecord> {
        val cursor = readableDatabase.query(
            TABLE, null,
            "schedule_json != '' AND schedule_json IS NOT NULL AND is_enabled = 1",
            null, null, null, null
        )
        val list = mutableListOf<CustomWorkflowRecord>()
        while (cursor.moveToNext()) {
            list.add(cursorToRecord(cursor))
        }
        cursor.close()
        return list
    }

    /**
     * Get count of custom workflows.
     */
    fun getCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    // ═══════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════

    private fun stepsToJson(steps: List<WorkflowStep>): String {
        val arr = JSONArray()
        steps.forEach { step ->
            arr.put(JSONObject().apply {
                put("id", step.id)
                put("label", step.label)
                put("icon", step.icon)
                put("type", step.type.name)
                put("agentId", step.agentId)
                put("skillName", step.skillName)
                put("prompt", step.prompt)
                put("modelOverride", step.modelOverride ?: "")
                val paramsObj = JSONObject()
                step.fixedParams.forEach { (k, v) -> paramsObj.put(k, v) }
                put("fixedParams", paramsObj)
            })
        }
        return arr.toString()
    }

    private fun jsonToSteps(json: String): List<WorkflowStep> {
        if (json.isBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val paramsObj = obj.optJSONObject("fixedParams")
            val params = mutableMapOf<String, String>()
            paramsObj?.keys()?.forEach { key -> params[key] = paramsObj.getString(key) }

            WorkflowStep(
                id = obj.getString("id"),
                label = obj.getString("label"),
                icon = obj.optString("icon", "STEP"),
                type = try { StepType.valueOf(obj.getString("type")) } catch (_: Exception) { StepType.AGENT },
                agentId = obj.optString("agentId", "main"),
                skillName = obj.optString("skillName", ""),
                prompt = obj.optString("prompt", ""),
                modelOverride = obj.optString("modelOverride", "").ifBlank { null },
                fixedParams = params
            )
        }
    }

    private fun cursorToRecord(cursor: android.database.Cursor): CustomWorkflowRecord {
        val stepsJson = cursor.getString(cursor.getColumnIndexOrThrow("steps_json"))
        val scheduleJson = cursor.getString(cursor.getColumnIndexOrThrow("schedule_json"))

        return CustomWorkflowRecord(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
            icon = cursor.getString(cursor.getColumnIndexOrThrow("icon")),
            steps = jsonToSteps(stepsJson),
            schedule = if (scheduleJson.isNotBlank()) ScheduleConfig.fromJson(scheduleJson) else null,
            taskId = cursor.getLong(cursor.getColumnIndexOrThrow("task_id")),
            isEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("is_enabled")) == 1,
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
            lastRunAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_run_at")),
            runCount = cursor.getInt(cursor.getColumnIndexOrThrow("run_count"))
        )
    }
}

/**
 * A persisted custom workflow with metadata.
 */
data class CustomWorkflowRecord(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val steps: List<WorkflowStep>,
    val schedule: ScheduleConfig?,
    val taskId: Long,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long,
    val runCount: Int
) {
    /** Convert to Workflow for execution */
    fun toWorkflow(): Workflow = Workflow(
        id = id,
        name = name,
        icon = icon,
        description = description,
        steps = steps,
        isTemplate = false
    )

    fun getRelativeUpdated(): String {
        val diff = System.currentTimeMillis() - updatedAt
        return when {
            diff < 60_000 -> "Recién"
            diff < 3_600_000 -> "Hace ${diff / 60_000}m"
            diff < 86_400_000 -> "Hace ${diff / 3_600_000}h"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(updatedAt))
        }
    }

    val isScheduled get() = schedule != null && isEnabled
}

/**
 * Schedule configuration for periodic workflow execution.
 */
data class ScheduleConfig(
    val frequency: ScheduleFrequency = ScheduleFrequency.DAILY,
    val hour: Int = 7,
    val minute: Int = 0,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5), // 1=Mon..7=Sun
    val requireWifi: Boolean = false,
    val notifyOnComplete: Boolean = true,
    val createTask: Boolean = false,
    // Triggers
    val triggerOnLowBattery: Boolean = false,
    val triggerBatteryThreshold: Int = 20,
    val triggerOnWifiConnect: Boolean = false,
    val triggerOnBoot: Boolean = false
) {
    fun toJson(): String = JSONObject().apply {
        put("frequency", frequency.name)
        put("hour", hour)
        put("minute", minute)
        put("daysOfWeek", JSONArray(daysOfWeek.toList()))
        put("requireWifi", requireWifi)
        put("notifyOnComplete", notifyOnComplete)
        put("createTask", createTask)
        put("triggerOnLowBattery", triggerOnLowBattery)
        put("triggerBatteryThreshold", triggerBatteryThreshold)
        put("triggerOnWifiConnect", triggerOnWifiConnect)
        put("triggerOnBoot", triggerOnBoot)
    }.toString()

    fun getDisplayText(): String {
        val dayNames = mapOf(1 to "L", 2 to "M", 3 to "Mi", 4 to "J", 5 to "V", 6 to "S", 7 to "D")
        val days = daysOfWeek.sorted().joinToString("") { dayNames[it] ?: "?" }
        val time = "%02d:%02d".format(hour, minute)
        val triggers = mutableListOf<String>()
        if (triggerOnLowBattery) triggers.add("🔋<${triggerBatteryThreshold}%")
        if (triggerOnWifiConnect) triggers.add("📶WiFi")
        if (triggerOnBoot) triggers.add("🔄Boot")
        val triggerText = if (triggers.isNotEmpty()) " + ${triggers.joinToString(", ")}" else ""
        return when (frequency) {
            ScheduleFrequency.ONCE -> "Una vez a las $time"
            ScheduleFrequency.DAILY -> "Diario a las $time"
            ScheduleFrequency.WEEKLY -> "$days a las $time"
            ScheduleFrequency.CUSTOM -> "$days a las $time$triggerText"
        }
    }

    companion object {
        fun fromJson(json: String): ScheduleConfig? {
            return try {
                val obj = JSONObject(json)
                val daysArr = obj.optJSONArray("daysOfWeek")
                val days = if (daysArr != null) {
                    (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet()
                } else setOf(1, 2, 3, 4, 5)

                ScheduleConfig(
                    frequency = try { ScheduleFrequency.valueOf(obj.optString("frequency", "DAILY")) } catch (_: Exception) { ScheduleFrequency.DAILY },
                    hour = obj.optInt("hour", 7),
                    minute = obj.optInt("minute", 0),
                    daysOfWeek = days,
                    requireWifi = obj.optBoolean("requireWifi", false),
                    notifyOnComplete = obj.optBoolean("notifyOnComplete", true),
                    createTask = obj.optBoolean("createTask", false),
                    triggerOnLowBattery = obj.optBoolean("triggerOnLowBattery", false),
                    triggerBatteryThreshold = obj.optInt("triggerBatteryThreshold", 20),
                    triggerOnWifiConnect = obj.optBoolean("triggerOnWifiConnect", false),
                    triggerOnBoot = obj.optBoolean("triggerOnBoot", false)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class ScheduleFrequency {
    ONCE,
    DAILY,
    WEEKLY,
    CUSTOM
}
