package com.beemovil.agent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * CustomAgentDB — SQLite storage for user-created agents.
 * Max 10 custom agents. Default agents are hardcoded in DefaultAgents.
 */
class CustomAgentDB(context: Context) : SQLiteOpenHelper(context, "bee_custom_agents.db", null, 1) {

    companion object {
        private const val TAG = "CustomAgentDB"
        const val MAX_CUSTOM_AGENTS = 10
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS custom_agents (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                icon TEXT NOT NULL DEFAULT 'AI',
                description TEXT DEFAULT '',
                system_prompt TEXT NOT NULL,
                enabled_tools TEXT DEFAULT '*',
                model TEXT DEFAULT '',
                temperature REAL DEFAULT 0.7,
                telegram_token TEXT DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """)
        Log.i(TAG, "Custom agents table created")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}

    fun saveAgent(config: AgentConfig, telegramToken: String = ""): Boolean {
        val count = getAgentCount()
        val isUpdate = getAgent(config.id) != null
        if (!isUpdate && count >= MAX_CUSTOM_AGENTS) {
            Log.w(TAG, "Max agents reached ($MAX_CUSTOM_AGENTS)")
            return false
        }

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", config.id)
            put("name", config.name)
            put("icon", config.icon)
            put("description", config.description)
            put("system_prompt", config.systemPrompt)
            put("enabled_tools", config.enabledTools.joinToString(","))
            put("model", config.model)
            put("temperature", config.temperature)
            put("telegram_token", telegramToken)
            put("created_at", now)
            put("updated_at", now)
        }

        writableDatabase.insertWithOnConflict("custom_agents", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Log.i(TAG, "Saved agent: ${config.name} (${config.id})")
        return true
    }

    fun getAgent(id: String): AgentConfig? {
        val cursor = readableDatabase.query(
            "custom_agents", null, "id = ?", arrayOf(id),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return cursorToAgent(it)
            }
        }
        return null
    }

    fun getAllAgents(): List<AgentConfig> {
        val results = mutableListOf<AgentConfig>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM custom_agents ORDER BY created_at ASC", null
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(cursorToAgent(it))
            }
        }
        return results
    }

    fun getTelegramToken(agentId: String): String {
        val cursor = readableDatabase.query(
            "custom_agents", arrayOf("telegram_token"), "id = ?", arrayOf(agentId),
            null, null, null
        )
        cursor.use {
            return if (it.moveToFirst()) it.getString(0) ?: "" else ""
        }
    }

    fun deleteAgent(id: String) {
        writableDatabase.delete("custom_agents", "id = ?", arrayOf(id))
        Log.i(TAG, "Deleted agent: $id")
    }

    fun getAgentCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM custom_agents", null)
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun cursorToAgent(cursor: android.database.Cursor): AgentConfig {
        val toolsStr = cursor.getString(cursor.getColumnIndexOrThrow("enabled_tools")) ?: "*"
        val tools = if (toolsStr == "*") setOf("*") else toolsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

        return AgentConfig(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: "Agent",
            icon = cursor.getString(cursor.getColumnIndexOrThrow("icon")) ?: "AI",
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")) ?: "",
            systemPrompt = cursor.getString(cursor.getColumnIndexOrThrow("system_prompt")) ?: "",
            enabledTools = tools,
            model = cursor.getString(cursor.getColumnIndexOrThrow("model")) ?: "",
            temperature = cursor.getFloat(cursor.getColumnIndexOrThrow("temperature"))
        )
    }
}
