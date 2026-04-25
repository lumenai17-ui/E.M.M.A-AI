package com.beemovil.lifestream

import androidx.room.*

/**
 * LifeStreamDao — Data Access Object for LifeStream signals.
 *
 * Provides async (suspend) and sync versions of key queries.
 * Sync versions are used by background services (NotificationListener).
 */
@Dao
interface LifeStreamDao {

    // ═══════════════════════════════════════
    // INSERT
    // ═══════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LifeStreamEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSync(entry: LifeStreamEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LifeStreamEntry>)

    // ═══════════════════════════════════════
    // QUERIES (Async)
    // ═══════════════════════════════════════

    @Query("SELECT * FROM lifestream ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<LifeStreamEntry>

    @Query("SELECT * FROM lifestream WHERE category = :cat ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByCategory(cat: String, limit: Int = 20): List<LifeStreamEntry>

    @Query("SELECT * FROM lifestream WHERE source = :src ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getBySource(src: String, limit: Int = 20): List<LifeStreamEntry>

    @Query("SELECT * FROM lifestream WHERE isRead = 0 AND importance >= :minImportance ORDER BY importance DESC, timestamp DESC LIMIT :limit")
    suspend fun getUnread(minImportance: Int = 1, limit: Int = 50): List<LifeStreamEntry>

    @Query("SELECT * FROM lifestream WHERE (content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%') ORDER BY timestamp DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<LifeStreamEntry>

    @Query("SELECT * FROM lifestream WHERE category = 'notification' AND source = :src ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getNotificationsFrom(src: String, limit: Int = 20): List<LifeStreamEntry>

    // ═══════════════════════════════════════
    // QUERIES (Sync — for background services)
    // ═══════════════════════════════════════

    @Query("SELECT * FROM lifestream ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSync(limit: Int = 50): List<LifeStreamEntry>

    @Query("SELECT COUNT(*) FROM lifestream WHERE isRead = 0")
    fun countUnreadSync(): Int

    // ═══════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════

    @Query("UPDATE lifestream SET isRead = 1 WHERE id IN (:ids)")
    suspend fun markAsRead(ids: List<Long>)

    @Query("UPDATE lifestream SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    // ═══════════════════════════════════════
    // MAINTENANCE
    // ═══════════════════════════════════════

    @Query("DELETE FROM lifestream WHERE timestamp < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long): Int

    @Query("DELETE FROM lifestream")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM lifestream")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM lifestream WHERE isRead = 0")
    suspend fun countUnread(): Int

    @Query("SELECT COUNT(*) FROM lifestream WHERE timestamp > :since")
    suspend fun countSince(since: Long): Int

    // ═══════════════════════════════════════
    // STATS (for Dashboard)
    // ═══════════════════════════════════════

    @Query("SELECT source, COUNT(*) as count FROM lifestream WHERE timestamp > :since GROUP BY source ORDER BY count DESC")
    suspend fun getSourceStats(since: Long): List<SourceStat>

    @Query("SELECT category, COUNT(*) as count FROM lifestream WHERE timestamp > :since GROUP BY category ORDER BY count DESC")
    suspend fun getCategoryStats(since: Long): List<CategoryStat>
}

data class SourceStat(
    val source: String,
    val count: Int
)

data class CategoryStat(
    val category: String,
    val count: Int
)
