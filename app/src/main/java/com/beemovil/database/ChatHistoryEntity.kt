package com.beemovil.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_history",
    indices = [Index("threadId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val threadId: String, // Referencia al ChatThread
    val senderId: String, // "user" o el AgentId de quien lo mandó
    val timestamp: Long,
    val role: String, // "user", "assistant", "system"
    val content: String,
    // JSON Raw para almacenar paths de archivos adjuntos, tool calls, o estados UI
    val metadataJson: String? = null
)
