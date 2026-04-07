package com.beemovil.workflow

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * WorkflowHistoryDB — Persistent storage for workflow execution history.
 *
 * Stores every workflow run with:
 * - Workflow template info (id, name)
 * - User input that started the run
 * - Final output / result
 * - Status (completed, failed, cancelled)
 * - Per-step results as JSON
 * - Timestamps, elapsed time
 *
 * Enables: browse past runs, re-execute, view results, delete old runs.
 */
class WorkflowHistoryDB(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "WorkflowHistoryDB"
        private const val DB_NAME = "workflow_history.db"
        private const val DB_VERSION = 1
        private const val TABLE = "workflow_runs"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                workflow_id TEXT NOT NULL,
                workflow_name TEXT NOT NULL,
                workflow_icon TEXT DEFAULT '',
                user_input TEXT DEFAULT '',
                final_output TEXT DEFAULT '',
                status TEXT DEFAULT 'running',
                steps_json TEXT DEFAULT '[]',
                model_overrides TEXT DEFAULT '{}',
                started_at INTEGER NOT NULL,
                completed_at INTEGER DEFAULT 0,
                elapsed_ms INTEGER DEFAULT 0
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /**
     * Start a new workflow run. Returns the run ID.
     */
    fun startRun(workflowId: String, workflowName: String, workflowIcon: String, userInput: String, modelOverrides: Map<String, String> = emptyMap()): Long {
        val values = ContentValues().apply {
            put("workflow_id", workflowId)
            put("workflow_name", workflowName)
            put("workflow_icon", workflowIcon)
            put("user_input", userInput)
            put("status", "running")
            put("started_at", System.currentTimeMillis())
            put("model_overrides", JSONObject(modelOverrides).toString())
        }
        val id = writableDatabase.insert(TABLE, null, values)
        Log.i(TAG, "Started run #$id for workflow '$workflowName'")
        return id
    }

    /**
     * Update a run with step results (called periodically during execution).
     */
    fun updateSteps(runId: Long, stepsJson: String) {
        val values = ContentValues().apply {
            put("steps_json", stepsJson)
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(runId.toString()))
    }

    /**
     * Complete a run (success or failure).
     */
    fun completeRun(runId: Long, status: String, finalOutput: String, elapsedMs: Long) {
        val values = ContentValues().apply {
            put("status", status)
            put("final_output", finalOutput)
            put("completed_at", System.currentTimeMillis())
            put("elapsed_ms", elapsedMs)
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(runId.toString()))
        Log.i(TAG, "Run #$runId completed: $status (${elapsedMs}ms)")
    }

    /**
     * Get all runs, most recent first.
     */
    fun getAllRuns(): List<WorkflowRunRecord> {
        val list = mutableListOf<WorkflowRunRecord>()
        val cursor = readableDatabase.query(
            TABLE, null, null, null, null, null, "started_at DESC", "100"
        )
        while (cursor.moveToNext()) {
            list.add(cursorToRecord(cursor))
        }
        cursor.close()
        return list
    }

    /**
     * Get a specific run by ID.
     */
    fun getRun(runId: Long): WorkflowRunRecord? {
        val cursor = readableDatabase.query(
            TABLE, null, "id = ?", arrayOf(runId.toString()), null, null, null
        )
        val record = if (cursor.moveToFirst()) cursorToRecord(cursor) else null
        cursor.close()
        return record
    }

    /**
     * Delete a single run.
     */
    fun deleteRun(runId: Long) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(runId.toString()))
        Log.i(TAG, "Deleted run #$runId")
    }

    /**
     * Delete multiple runs.
     */
    fun deleteRuns(runIds: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            runIds.forEach { id ->
                db.delete(TABLE, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Log.i(TAG, "Deleted ${runIds.size} runs")
    }

    /**
     * Delete all completed/failed runs (keep running ones).
     */
    fun clearHistory() {
        writableDatabase.delete(TABLE, "status != ?", arrayOf("running"))
        Log.i(TAG, "Cleared all history")
    }

    /**
     * Get count of runs.
     */
    fun getRunCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }

    private fun cursorToRecord(cursor: android.database.Cursor): WorkflowRunRecord {
        return WorkflowRunRecord(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            workflowId = cursor.getString(cursor.getColumnIndexOrThrow("workflow_id")),
            workflowName = cursor.getString(cursor.getColumnIndexOrThrow("workflow_name")),
            workflowIcon = cursor.getString(cursor.getColumnIndexOrThrow("workflow_icon")),
            userInput = cursor.getString(cursor.getColumnIndexOrThrow("user_input")),
            finalOutput = cursor.getString(cursor.getColumnIndexOrThrow("final_output")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            stepsJson = cursor.getString(cursor.getColumnIndexOrThrow("steps_json")),
            modelOverrides = cursor.getString(cursor.getColumnIndexOrThrow("model_overrides")),
            startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
            completedAt = cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
            elapsedMs = cursor.getLong(cursor.getColumnIndexOrThrow("elapsed_ms"))
        )
    }
}

/**
 * A persisted record of a workflow execution.
 */
data class WorkflowRunRecord(
    val id: Long,
    val workflowId: String,
    val workflowName: String,
    val workflowIcon: String,
    val userInput: String,
    val finalOutput: String,
    val status: String,        // "running", "completed", "failed", "cancelled"
    val stepsJson: String,     // JSON array of step results
    val modelOverrides: String, // JSON object of step_id -> model_id
    val startedAt: Long,
    val completedAt: Long,
    val elapsedMs: Long
) {
    val isComplete get() = status == "completed"
    val isFailed get() = status == "failed"
    val isCancelled get() = status == "cancelled"
    val isRunning get() = status == "running"

    fun getRelativeTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - startedAt
        return when {
            diff < 60_000 -> "Hace unos segundos"
            diff < 3_600_000 -> "Hace ${diff / 60_000} min"
            diff < 86_400_000 -> "Hace ${diff / 3_600_000}h"
            diff < 604_800_000 -> "Hace ${diff / 86_400_000} dias"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(startedAt))
        }
    }

    fun getElapsedFormatted(): String = when {
        elapsedMs < 1000 -> "${elapsedMs}ms"
        elapsedMs < 60_000 -> "${"%.1f".format(elapsedMs / 1000.0)}s"
        else -> "${elapsedMs / 60_000}m ${(elapsedMs % 60_000) / 1000}s"
    }
}
