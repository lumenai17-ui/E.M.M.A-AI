package com.beemovil.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agent_config")
data class AgentConfigEntity(
    @PrimaryKey
    val agentId: String,
    val name: String,
    val icon: String,
    val systemPrompt: String,
    val fallbackModel: String = "koog-engine"
)
