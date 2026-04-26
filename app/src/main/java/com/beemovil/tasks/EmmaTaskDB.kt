package com.beemovil.tasks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * EmmaTaskDB — Separate Room database for the Task Manager.
 * Kept isolated from ChatHistoryDB to avoid migration conflicts.
 */
@Database(
    entities = [
        EmmaTask::class,
        EmmaSubtask::class,
        TaskAttachment::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EmmaTaskDB : RoomDatabase() {
    abstract fun taskDao(): EmmaTaskDao

    companion object {
        @Volatile
        private var INSTANCE: EmmaTaskDB? = null

        fun getDatabase(context: Context): EmmaTaskDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EmmaTaskDB::class.java,
                    "emma_tasks_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
