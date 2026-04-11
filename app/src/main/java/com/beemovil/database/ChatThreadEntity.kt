package com.beemovil.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_thread")
data class ChatThreadEntity(
    @PrimaryKey
    val threadId: String,
    val type: String, // "INDIVIDUAL" or "GROUP"
    val title: String,
    val lastUpdateMillis: Long,
    val isPinned: Boolean = false
)
