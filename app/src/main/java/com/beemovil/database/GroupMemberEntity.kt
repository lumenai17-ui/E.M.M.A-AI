package com.beemovil.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_member",
    foreignKeys = [
        ForeignKey(
            entity = ChatThreadEntity::class,
            parentColumns = ["threadId"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AgentConfigEntity::class,
            parentColumns = ["agentId"],
            childColumns = ["agentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadId"), Index("agentId")]
)
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val threadId: String,
    val agentId: String,
    val executionOrder: Int // 0 for parallel, 1, 2, 3... for sequence
)
