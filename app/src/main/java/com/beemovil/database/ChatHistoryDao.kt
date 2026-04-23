package com.beemovil.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatHistoryDao {
    // --- CHATS ---
    @Insert
    suspend fun insertMessage(msg: ChatMessageEntity)

    @Query("SELECT * FROM chat_history WHERE threadId = :threadId ORDER BY timestamp ASC")
    suspend fun getHistory(threadId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_history WHERE threadId = :threadId AND content LIKE '%' || :query || '%' ORDER BY timestamp ASC")
    suspend fun searchHistory(threadId: String, query: String): List<ChatMessageEntity>

    // C-05 fix: Búsqueda global cross-thread
    @Query("SELECT * FROM chat_history WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 50")
    suspend fun searchAllHistory(query: String): List<ChatMessageEntity>

    @Query("DELETE FROM chat_history WHERE threadId = :threadId")
    suspend fun clearHistory(threadId: String)

    @Query("DELETE FROM chat_history")
    suspend fun clearAll()

    // --- AGENTS & THREADS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentConfigEntity)

    @Query("SELECT * FROM agent_config")
    suspend fun getAllAgents(): List<AgentConfigEntity>

    @Query("SELECT * FROM agent_config")
    fun getAllAgentsSync(): List<AgentConfigEntity>

    @Insert
    suspend fun createThread(thread: ChatThreadEntity)

    @Query("SELECT * FROM chat_thread ORDER BY isPinned DESC, lastUpdateMillis DESC")
    suspend fun getAllThreads(): List<ChatThreadEntity>

    @Insert
    suspend fun addGroupMember(member: GroupMemberEntity)

    @Query("SELECT * FROM group_member WHERE threadId = :threadId ORDER BY executionOrder ASC")
    suspend fun getGroupMembers(threadId: String): List<GroupMemberEntity>

    // UI-14: Limpiar threads al hacer clearAll
    @Query("DELETE FROM chat_thread")
    suspend fun clearAllThreads()
}
