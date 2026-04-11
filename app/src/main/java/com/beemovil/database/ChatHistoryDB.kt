package com.beemovil.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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

        fun getDatabase(context: Context): ChatHistoryDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatHistoryDB::class.java,
                    "emma_chat_history_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
