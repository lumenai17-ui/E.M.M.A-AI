package com.beemovil.memory

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Edge RAG Memory System for Bee-Movil.
 *
 * Stores conversation messages and extracted "memories" (key facts about the user).
 * Uses lightweight TF-IDF keyword matching for retrieval — no embeddings needed.
 *
 * Architecture:
 *   ┌─────────────────┐
 *   I  conversations   I  Full chat history (messages)
 *   T─────────────────┤
 *   I   memories       I  Key facts extracted from chats (RAG knowledge base)
 *   T─────────────────┤
 *   I     soul         I  Persistent user profile (name, preferences, routines)
 *   L─────────────────┘
 */
class BeeMemoryDB(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "BeeMemory"
        private const val DB_NAME = "beemovil_memory.db"
        private const val DB_VERSION = 2
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Conversations: full chat message log
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                agent_id TEXT DEFAULT 'main',
                timestamp INTEGER NOT NULL,
                metadata TEXT
            )
        """)

        // Memories: extracted key facts (RAG knowledge base)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL,
                keywords TEXT NOT NULL,
                category TEXT DEFAULT 'general',
                importance INTEGER DEFAULT 5,
                source_session TEXT,
                created_at INTEGER NOT NULL,
                last_accessed INTEGER,
                access_count INTEGER DEFAULT 0
            )
        """)

        // Soul: persistent user profile
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS soul (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """)

        // Action log: every skill execution
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS action_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                agent_id TEXT NOT NULL,
                skill_name TEXT NOT NULL,
                params_summary TEXT,
                result_summary TEXT,
                duration_ms INTEGER DEFAULT 0,
                session_id TEXT,
                timestamp INTEGER NOT NULL
            )
        """)

        // Indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_memories_keywords ON memories(keywords)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_session ON conversations(session_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_action_log_time ON action_log(timestamp)")

        Log.i(TAG, "Database created with 4 tables")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS action_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    agent_id TEXT NOT NULL,
                    skill_name TEXT NOT NULL,
                    params_summary TEXT,
                    result_summary TEXT,
                    duration_ms INTEGER DEFAULT 0,
                    session_id TEXT,
                    timestamp INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_action_log_time ON action_log(timestamp)")
            Log.i(TAG, "Migrated to v2: action_log table")
        }
    }

    // ═══════════════════════════════════════════════════════
    // CONVERSATIONS
    // ═══════════════════════════════════════════════════════

    fun saveMessage(sessionId: String, role: String, content: String, agentId: String = "main") {
        writableDatabase.insert("conversations", null, ContentValues().apply {
            put("session_id", sessionId)
            put("role", role)
            put("content", content)
            put("agent_id", agentId)
            put("timestamp", System.currentTimeMillis())
        })
    }

    fun getConversationHistory(sessionId: String, limit: Int = 50): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val cursor = readableDatabase.query(
            "conversations", arrayOf("role", "content", "agent_id", "timestamp"),
            "session_id = ?", arrayOf(sessionId),
            null, null, "timestamp ASC", "$limit"
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(mapOf(
                    "role" to it.getString(0),
                    "content" to it.getString(1),
                    "agent_id" to it.getString(2),
                    "timestamp" to it.getString(3)
                ))
            }
        }
        return results
    }

    fun getRecentSessions(limit: Int = 10): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val cursor = readableDatabase.rawQuery("""
            SELECT session_id, 
                   MIN(timestamp) as started,
                   MAX(timestamp) as ended,
                   COUNT(*) as msg_count,
                   (SELECT content FROM conversations c2 WHERE c2.session_id = c.session_id AND c2.role = 'user' ORDER BY timestamp ASC LIMIT 1) as first_msg
            FROM conversations c
            GROUP BY session_id
            ORDER BY MAX(timestamp) DESC
            LIMIT ?
        """, arrayOf("$limit"))
        cursor.use {
            while (it.moveToNext()) {
                results.add(mapOf(
                    "session_id" to it.getString(0),
                    "started" to it.getString(1),
                    "ended" to it.getString(2),
                    "msg_count" to it.getString(3),
                    "first_msg" to (it.getString(4) ?: "")
                ))
            }
        }
        return results
    }

    // ═══════════════════════════════════════════════════════
    // RAG MEMORY — Edge Retrieval
    // ═══════════════════════════════════════════════════════

    fun addMemory(content: String, category: String = "general", importance: Int = 5, sessionId: String? = null) {
        val keywords = extractKeywords(content)
        writableDatabase.insert("memories", null, ContentValues().apply {
            put("content", content)
            put("keywords", keywords)
            put("category", category)
            put("importance", importance)
            put("source_session", sessionId)
            put("created_at", System.currentTimeMillis())
            put("last_accessed", System.currentTimeMillis())
        })
        Log.d(TAG, "Memory added: ${content.take(50)}... [keywords=$keywords]")
    }

    /**
     * RAG Retrieval: finds relevant memories using keyword matching.
     * Lightweight edge approach — no embeddings needed.
     */
    fun retrieveMemories(query: String, limit: Int = 5): List<MemoryItem> {
        val queryKeywords = extractKeywords(query).split(",").map { it.trim().lowercase() }
        if (queryKeywords.isEmpty()) return emptyList()

        // Build LIKE clauses for each keyword
        val likeClauses = queryKeywords.map { "keywords LIKE '%$it%'" }
        val whereClause = likeClauses.joinToString(" OR ")

        val results = mutableListOf<MemoryItem>()
        try {
            val cursor = readableDatabase.rawQuery("""
                SELECT id, content, keywords, category, importance, created_at, access_count
                FROM memories
                WHERE $whereClause
                ORDER BY importance DESC, access_count DESC
                LIMIT ?
            """, arrayOf("$limit"))

            cursor.use {
                while (it.moveToNext()) {
                    val memKeywords = it.getString(2).split(",").map { k -> k.trim().lowercase() }
                    val matchScore = queryKeywords.count { qk -> memKeywords.any { mk -> mk.contains(qk) || qk.contains(mk) } }

                    results.add(MemoryItem(
                        id = it.getInt(0),
                        content = it.getString(1),
                        keywords = it.getString(2),
                        category = it.getString(3),
                        importance = it.getInt(4),
                        matchScore = matchScore
                    ))

                    // Update access stats
                    writableDatabase.execSQL(
                        "UPDATE memories SET last_accessed = ?, access_count = access_count + 1 WHERE id = ?",
                        arrayOf(System.currentTimeMillis(), it.getInt(0))
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Memory retrieval error: ${e.message}")
        }

        return results.sortedByDescending { it.matchScore }
    }

    fun getAllMemories(): List<MemoryItem> {
        val results = mutableListOf<MemoryItem>()
        val cursor = readableDatabase.rawQuery(
            "SELECT id, content, keywords, category, importance, created_at, access_count FROM memories ORDER BY importance DESC, created_at DESC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(MemoryItem(
                    id = it.getInt(0),
                    content = it.getString(1),
                    keywords = it.getString(2),
                    category = it.getString(3),
                    importance = it.getInt(4),
                    matchScore = 0
                ))
            }
        }
        return results
    }

    fun getMemoryCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM memories", null)
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun deleteMemory(id: Int) {
        writableDatabase.delete("memories", "id = ?", arrayOf("$id"))
    }

    // ═══════════════════════════════════════════════════════
    // SOUL — Persistent user profile
    // ═══════════════════════════════════════════════════════

    fun setSoul(key: String, value: String) {
        writableDatabase.insertWithOnConflict("soul", null, ContentValues().apply {
            put("key", key)
            put("value", value)
            put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSoul(key: String): String? {
        val cursor = readableDatabase.query(
            "soul", arrayOf("value"),
            "key = ?", arrayOf(key),
            null, null, null
        )
        cursor.use {
            return if (it.moveToFirst()) it.getString(0) else null
        }
    }

    fun getAllSoul(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val cursor = readableDatabase.rawQuery("SELECT key, value FROM soul", null)
        cursor.use {
            while (it.moveToNext()) {
                result[it.getString(0)] = it.getString(1)
            }
        }
        return result
    }

    fun getSoulSummary(): String {
        val soul = getAllSoul()
        if (soul.isEmpty()) return ""

        return buildString {
            appendLine("## Perfil del usuario:")
            soul.forEach { (k, v) -> appendLine("- $k: $v") }
        }
    }

    // ═══════════════════════════════════════════════════════
    // KEYWORD EXTRACTION (Edge TF-IDF lite)
    // ═══════════════════════════════════════════════════════

    private val stopWords = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al",
        "en", "con", "por", "para", "es", "son", "está", "están", "fue", "ser",
        "que", "se", "no", "si", "su", "sus", "mi", "me", "te", "lo", "le",
        "y", "o", "a", "e", "u", "pero", "como", "más", "ya", "muy",
        "the", "is", "are", "was", "been", "be", "have", "has", "had",
        "do", "does", "did", "will", "would", "could", "should", "may",
        "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "this", "that", "it", "not"
    )

    fun extractKeywords(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-záéíóúñü0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
            .take(15)
            .joinToString(",")
    }

    // ═══════════════════════════════════════════════════════
    // MEMORY EXTRACTION (LLM-assisted)
    // ═══════════════════════════════════════════════════════

    /**
     * Build a prompt to ask the LLM to extract key facts from a conversation.
     */
    fun buildMemoryExtractionPrompt(messages: List<Map<String, String>>): String {
        val chatLog = messages.joinToString("\n") { "${it["role"]}: ${it["content"]}" }

        return """Analiza esta conversación y extrae los datos importantes del usuario.
Responde SOLO con un JSON array de objetos con esta estructura:
[
  {"fact": "descripción del hecho", "category": "personal|trabajo|preferencia|rutina|contacto", "importance": 1-10}
]

Si no hay datos importantes, responde: []

Conversación:
$chatLog"""
    }

    /**
     * Parse LLM response and store extracted memories.
     */
    fun processExtractedMemories(llmResponse: String, sessionId: String): Int {
        var count = 0
        try {
            // Find JSON array in response
            val jsonStart = llmResponse.indexOf('[')
            val jsonEnd = llmResponse.lastIndexOf(']')
            if (jsonStart < 0 || jsonEnd < 0) return 0

            val jsonStr = llmResponse.substring(jsonStart, jsonEnd + 1)
            val arr = JSONArray(jsonStr)

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fact = obj.optString("fact", "")
                val category = obj.optString("category", "general")
                val importance = obj.optInt("importance", 5)

                if (fact.isNotBlank()) {
                    addMemory(fact, category, importance, sessionId)
                    count++
                }
            }
            Log.i(TAG, "Extracted $count memories from session $sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse extracted memories: ${e.message}")
        }
        return count
    }

    // ═══════════════════════════════════════════════════════
    // CONTEXT BUILDER (for RAG injection)
    // ═══════════════════════════════════════════════════════

    /**
     * Build RAG context to inject into the system prompt.
     * Retrieves relevant memories + soul profile.
     */
    fun buildRagContext(userMessage: String): String {
        val sb = StringBuilder()

        // 1. Soul profile
        val soul = getSoulSummary()
        if (soul.isNotBlank()) {
            sb.appendLine(soul)
        }

        // 2. Relevant memories
        val memories = retrieveMemories(userMessage, limit = 5)
        if (memories.isNotEmpty()) {
            sb.appendLine("\n## Memorias relevantes:")
            memories.forEach { mem ->
                sb.appendLine("- [${mem.category}] ${mem.content}")
            }
        }

        // 3. Stats
        val totalMemories = getMemoryCount()
        if (totalMemories > 0) {
            sb.appendLine("\n(Tienes $totalMemories memorias almacenadas)")
        }

        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════
    // ACTION LOG
    // ═══════════════════════════════════════════════════════

    fun logAction(
        agentId: String, skillName: String, paramsSummary: String,
        resultSummary: String, durationMs: Long, sessionId: String
    ) {
        writableDatabase.insert("action_log", null, ContentValues().apply {
            put("agent_id", agentId)
            put("skill_name", skillName)
            put("params_summary", paramsSummary.take(200))
            put("result_summary", resultSummary.take(500))
            put("duration_ms", durationMs)
            put("session_id", sessionId)
            put("timestamp", System.currentTimeMillis())
        })
    }

    fun getRecentActions(limit: Int = 50): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM action_log ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(mapOf(
                    "id" to it.getInt(0).toString(),
                    "agent_id" to it.getString(1),
                    "skill_name" to it.getString(2),
                    "params" to (it.getString(3) ?: ""),
                    "result" to (it.getString(4) ?: ""),
                    "duration_ms" to it.getLong(5).toString(),
                    "session_id" to (it.getString(6) ?: ""),
                    "timestamp" to it.getLong(7).toString()
                ))
            }
        }
        return results
    }

    fun getActionCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM action_log", null)
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    // ═══════════════════════════════════════════════════════
    // MEMORY MAINTENANCE
    // ═══════════════════════════════════════════════════════

    /**
     * Prune old/low-importance memories to keep the DB lean.
     * Keeps the most recent and highest-importance entries.
     */
    fun pruneOldMemories(maxCount: Int = 500) {
        val count = getMemoryCount()
        if (count <= maxCount) return

        val toDelete = count - maxCount
        writableDatabase.execSQL("""
            DELETE FROM memories WHERE id IN (
                SELECT id FROM memories
                ORDER BY importance ASC, created_at ASC
                LIMIT ?
            )
        """, arrayOf(toDelete.toString()))
        Log.i(TAG, "Pruned $toDelete old/low-importance memories (kept $maxCount)")
    }

    /**
     * Remove duplicate memories with similar content.
     * Uses simple substring matching (efficient on mobile).
     */
    fun deduplicateMemories() {
        try {
            val all = getAllMemories()
            val seenContent = mutableSetOf<String>()
            val toDelete = mutableListOf<Int>()

            for (mem in all.sortedByDescending { it.importance }) {
                val normalized = mem.content.lowercase().trim()
                // Check if we already have something very similar
                val isDupe = seenContent.any { existing ->
                    normalized.length > 10 && (
                        existing.contains(normalized) ||
                        normalized.contains(existing) ||
                        levenshteinSimilarity(existing, normalized) > 0.85
                    )
                }
                if (isDupe) {
                    toDelete.add(mem.id)
                } else {
                    seenContent.add(normalized)
                }
            }

            if (toDelete.isNotEmpty()) {
                toDelete.forEach { deleteMemory(it) }
                Log.i(TAG, "Deduplicated: removed ${toDelete.size} duplicate memories")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deduplication failed: ${e.message}")
        }
    }

    private fun levenshteinSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val shorter = if (a.length < b.length) a else b
        val longer = if (a.length < b.length) b else a
        if (longer.length > 200) return 0.0 // Skip very long strings

        val costs = IntArray(shorter.length + 1) { it }
        for (i in 1..longer.length) {
            var lastValue = i
            for (j in 1..shorter.length) {
                val newValue = if (longer[i - 1] == shorter[j - 1]) {
                    costs[j - 1]
                } else {
                    minOf(costs[j - 1], lastValue, costs[j]) + 1
                }
                costs[j - 1] = lastValue
                lastValue = newValue
            }
            costs[shorter.length] = lastValue
        }
        val distance = costs[shorter.length]
        return 1.0 - (distance.toDouble() / longer.length)
    }
}

data class MemoryItem(
    val id: Int,
    val content: String,
    val keywords: String,
    val category: String,
    val importance: Int,
    val matchScore: Int
)
