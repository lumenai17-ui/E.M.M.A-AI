package com.beemovil.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/**
 * ChatHistoryDB — persists chat messages per agent.
 * Each agent has its own conversation thread, like WhatsApp contacts.
 */
class ChatHistoryDB(context: Context) : SQLiteOpenHelper(context, "bee_chat_history.db", null, 1) {

    data class ChatMessage(
        val id: Long,
        val agentId: String,
        val text: String,
        val isUser: Boolean,
        val agentIcon: String,
        val isError: Boolean,
        val toolsUsed: String, // JSON array
        val timestamp: Long
    )

    data class ConversationPreview(
        val agentId: String,
        val agentName: String,
        val agentIcon: String,
        val lastMessage: String,
        val lastTimestamp: Long,
        val messageCount: Int,
        val unread: Boolean
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                agent_id TEXT NOT NULL,
                text TEXT NOT NULL,
                is_user INTEGER NOT NULL DEFAULT 0,
                agent_icon TEXT DEFAULT 'BEE',
                is_error INTEGER NOT NULL DEFAULT 0,
                tools_used TEXT DEFAULT '[]',
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_agent_id ON chat_messages(agent_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON chat_messages(timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}

    /**
     * Save a message to the history.
     */
    fun saveMessage(agentId: String, text: String, isUser: Boolean, agentIcon: String = "BEE",
                    isError: Boolean = false, toolsUsed: List<String> = emptyList()): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("agent_id", agentId)
            put("text", text)
            put("is_user", if (isUser) 1 else 0)
            put("agent_icon", agentIcon)
            put("is_error", if (isError) 1 else 0)
            put("tools_used", JSONArray(toolsUsed).toString())
            put("timestamp", System.currentTimeMillis())
        }
        return db.insert("chat_messages", null, values)
    }

    /**
     * Get all messages for an agent, ordered by time.
     */
    fun getMessages(agentId: String, limit: Int = 200): List<ChatMessage> {
        val db = readableDatabase
        val messages = mutableListOf<ChatMessage>()
        val cursor = db.query(
            "chat_messages",
            null,
            "agent_id = ?",
            arrayOf(agentId),
            null, null,
            "timestamp ASC",
            "$limit"
        )
        cursor.use {
            while (it.moveToNext()) {
                messages.add(ChatMessage(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    agentId = it.getString(it.getColumnIndexOrThrow("agent_id")),
                    text = it.getString(it.getColumnIndexOrThrow("text")),
                    isUser = it.getInt(it.getColumnIndexOrThrow("is_user")) == 1,
                    agentIcon = it.getString(it.getColumnIndexOrThrow("agent_icon")),
                    isError = it.getInt(it.getColumnIndexOrThrow("is_error")) == 1,
                    toolsUsed = it.getString(it.getColumnIndexOrThrow("tools_used")),
                    timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                ))
            }
        }
        return messages
    }

    /**
     * Get conversation previews for all agents that have messages.
     */
    fun getConversationPreviews(): List<ConversationPreview> {
        val db = readableDatabase
        val previews = mutableListOf<ConversationPreview>()
        val cursor = db.rawQuery("""
            SELECT agent_id, agent_icon, text, timestamp, 
                   (SELECT COUNT(*) FROM chat_messages m2 WHERE m2.agent_id = m.agent_id) as msg_count
            FROM chat_messages m 
            WHERE m.id IN (
                SELECT MAX(id) FROM chat_messages GROUP BY agent_id
            )
            ORDER BY timestamp DESC
        """, null)
        cursor.use {
            while (it.moveToNext()) {
                val agentId = it.getString(0)
                previews.add(ConversationPreview(
                    agentId = agentId,
                    agentName = agentIdToName(agentId),
                    agentIcon = it.getString(1),
                    lastMessage = it.getString(2).take(80),
                    lastTimestamp = it.getLong(3),
                    messageCount = it.getInt(4),
                    unread = false
                ))
            }
        }
        return previews
    }

    /**
     * Clear all messages for an agent.
     */
    fun clearAgent(agentId: String) {
        writableDatabase.delete("chat_messages", "agent_id = ?", arrayOf(agentId))
    }

    /**
     * Clear all chat history.
     */
    fun clearAll() {
        writableDatabase.delete("chat_messages", null, null)
    }

    /**
     * Get total message count.
     */
    fun getTotalMessageCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM chat_messages", null)
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun agentIdToName(id: String): String = when (id) {
        "main" -> "Bee Asistente"
        "ventas" -> "Bee Ventas"
        "agenda" -> "Bee Agenda"
        "creativo" -> "Bee Creativo"
        else -> "Bee $id"
    }
}
