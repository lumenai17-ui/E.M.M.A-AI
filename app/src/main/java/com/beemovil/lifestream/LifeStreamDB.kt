package com.beemovil.lifestream

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * LifeStreamDB — Separate Room database for LifeStream signals.
 *
 * Intentionally separate from ChatHistoryDB:
 * - Different lifecycle (purge won't affect conversations)
 * - Different retention policies (72h vs permanent)
 * - Can be wiped without losing chat history
 */
@Database(
    entities = [LifeStreamEntry::class],
    version = 1,
    exportSchema = false
)
abstract class LifeStreamDB : RoomDatabase() {
    abstract fun lifeStreamDao(): LifeStreamDao

    companion object {
        @Volatile
        private var INSTANCE: LifeStreamDB? = null

        fun getDatabase(context: Context): LifeStreamDB {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LifeStreamDB::class.java,
                    "emma_lifestream_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
