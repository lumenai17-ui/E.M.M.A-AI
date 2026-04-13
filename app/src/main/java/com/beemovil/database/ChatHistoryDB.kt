package com.beemovil.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

@Database(
    entities = [
        ChatMessageEntity::class, 
        AgentConfigEntity::class, 
        ChatThreadEntity::class, 
        GroupMemberEntity::class
    ], 
    version = 2, 
    exportSchema = false
)
abstract class ChatHistoryDB : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: ChatHistoryDB? = null

        // Migración segura v1 → v2: AgentConfig y ChatThread fueron añadidos
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i("ChatHistoryDB", "Migrando v1 → v2: creando tablas agent_config, chat_thread y group_member")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS agent_config (
                        agentId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        systemPrompt TEXT NOT NULL,
                        fallbackModel TEXT NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_thread (
                        threadId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        type TEXT NOT NULL,
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        lastUpdateMillis INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS group_member (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        threadId TEXT NOT NULL,
                        agentId TEXT NOT NULL,
                        executionOrder INTEGER NOT NULL
                    )
                """)
            }
        }

        fun getDatabase(context: Context): ChatHistoryDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatHistoryDB::class.java,
                    "emma_chat_history_db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
