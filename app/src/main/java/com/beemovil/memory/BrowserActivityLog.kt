package com.beemovil.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * BrowserActivityLog — Persistent log of all browser agent actions.
 *
 * Tracks every action the agent takes in the browser, grouped by session.
 * The agent can query past sessions to remember what it did before on a site.
 *
 * Table: browser_sessions
 * - id: auto-increment
 * - session_id: groups actions into one task session
 * - timestamp: epoch ms
 * - url: page URL when action was taken
 * - action: what the agent did (navigate, click, type, etc.)
 * - target: CSS selector or URL target
 * - result: outcome (success, error, paused)
 * - task_goal: the user's original request
 * - model_used: which LLM model was driving
 */
class BrowserActivityLog(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "browser_activity.db"
        private const val DB_VERSION = 1
        private const val TABLE = "browser_sessions"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                url TEXT DEFAULT '',
                action TEXT NOT NULL,
                target TEXT DEFAULT '',
                result TEXT DEFAULT '',
                task_goal TEXT DEFAULT '',
                model_used TEXT DEFAULT ''
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_session ON $TABLE(session_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_url ON $TABLE(url)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON $TABLE(timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /** Log a single browser agent action */
    fun logAction(
        sessionId: String,
        url: String,
        action: String,
        target: String = "",
        result: String = "",
        taskGoal: String = "",
        modelUsed: String = ""
    ) {
        writableDatabase.insert(TABLE, null, ContentValues().apply {
            put("session_id", sessionId)
            put("timestamp", System.currentTimeMillis())
            put("url", url)
            put("action", action)
            put("target", target)
            put("result", result.take(500)) // cap result size
            put("task_goal", taskGoal)
            put("model_used", modelUsed)
        })
    }

    /** Get all actions for a session */
    fun getSession(sessionId: String): List<BrowserAction> {
        val actions = mutableListOf<BrowserAction>()
        val cursor = readableDatabase.query(
            TABLE, null, "session_id = ?", arrayOf(sessionId),
            null, null, "timestamp ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                actions.add(BrowserAction(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    sessionId = it.getString(it.getColumnIndexOrThrow("session_id")),
                    timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                    url = it.getString(it.getColumnIndexOrThrow("url")),
                    action = it.getString(it.getColumnIndexOrThrow("action")),
                    target = it.getString(it.getColumnIndexOrThrow("target")),
                    result = it.getString(it.getColumnIndexOrThrow("result")),
                    taskGoal = it.getString(it.getColumnIndexOrThrow("task_goal")),
                    modelUsed = it.getString(it.getColumnIndexOrThrow("model_used"))
                ))
            }
        }
        return actions
    }

    /** Get recent sessions (distinct session_ids with their goals) */
    fun getRecentSessions(limit: Int = 20): List<SessionSummary> {
        val sessions = mutableListOf<SessionSummary>()
        val cursor = readableDatabase.rawQuery("""
            SELECT session_id, task_goal, MIN(timestamp) as start_time, 
                   MAX(timestamp) as end_time, COUNT(*) as action_count,
                   GROUP_CONCAT(DISTINCT url) as urls
            FROM $TABLE
            GROUP BY session_id
            ORDER BY start_time DESC
            LIMIT ?
        """, arrayOf(limit.toString()))
        cursor.use {
            while (it.moveToNext()) {
                sessions.add(SessionSummary(
                    sessionId = it.getString(0),
                    taskGoal = it.getString(1) ?: "",
                    startTime = it.getLong(2),
                    endTime = it.getLong(3),
                    actionCount = it.getInt(4),
                    urls = it.getString(5)?.split(",") ?: emptyList()
                ))
            }
        }
        return sessions
    }

    /** Search past actions by URL pattern (for agent memory) */
    fun searchByUrl(urlPattern: String, limit: Int = 10): List<BrowserAction> {
        val actions = mutableListOf<BrowserAction>()
        val cursor = readableDatabase.query(
            TABLE, null, "url LIKE ?", arrayOf("%$urlPattern%"),
            null, null, "timestamp DESC", limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                actions.add(BrowserAction(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    sessionId = it.getString(it.getColumnIndexOrThrow("session_id")),
                    timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                    url = it.getString(it.getColumnIndexOrThrow("url")),
                    action = it.getString(it.getColumnIndexOrThrow("action")),
                    target = it.getString(it.getColumnIndexOrThrow("target")),
                    result = it.getString(it.getColumnIndexOrThrow("result")),
                    taskGoal = it.getString(it.getColumnIndexOrThrow("task_goal")),
                    modelUsed = it.getString(it.getColumnIndexOrThrow("model_used"))
                ))
            }
        }
        return actions
    }

    /** Generate a context summary for the agent about past activity on a URL */
    fun getMemoryContext(url: String): String {
        val actions = searchByUrl(url, 5)
        if (actions.isEmpty()) return ""
        val sb = StringBuilder("[MEMORIA WEB] Historial en ${url.take(50)}:\n")
        actions.take(5).forEach { a ->
            sb.appendLine("- ${a.action}(${a.target.take(30)}) → ${a.result.take(50)}")
        }
        return sb.toString()
    }

    /** Total logged actions */
    fun totalActions(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /** Clear old sessions (older than 30 days) */
    fun cleanOld(daysToKeep: Int = 30) {
        val cutoff = System.currentTimeMillis() - daysToKeep * 24L * 60 * 60 * 1000
        writableDatabase.delete(TABLE, "timestamp < ?", arrayOf(cutoff.toString()))
    }
}

data class BrowserAction(
    val id: Long,
    val sessionId: String,
    val timestamp: Long,
    val url: String,
    val action: String,
    val target: String,
    val result: String,
    val taskGoal: String,
    val modelUsed: String
)

data class SessionSummary(
    val sessionId: String,
    val taskGoal: String,
    val startTime: Long,
    val endTime: Long,
    val actionCount: Int,
    val urls: List<String>
)
