package se.anders.athenapodcaster.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A feed with the counts the list shows as plain text (FR-005). */
data class FeedSummary(
    @Embedded val feed: Feed,
    val unplayedCount: Int,
    val downloadedCount: Int,
)

@Dao
interface FeedDao {
    @Query(
        """
        SELECT f.*,
          (SELECT COUNT(*) FROM episodes e WHERE e.feedId = f.id AND e.played = 0) AS unplayedCount,
          (SELECT COUNT(*) FROM episodes e WHERE e.feedId = f.id AND e.downloadState = 'Done') AS downloadedCount
        FROM feeds f ORDER BY f.title COLLATE NOCASE
        """,
    )
    fun observeSummaries(): Flow<List<FeedSummary>>

    @Query("SELECT * FROM feeds WHERE id = :id")
    fun observe(id: Long): Flow<Feed?>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun get(id: Long): Feed?

    @Query("SELECT * FROM feeds WHERE urlHash = :hash")
    suspend fun getByHash(hash: String): Feed?

    @Query("SELECT * FROM feeds")
    suspend fun getAll(): List<Feed>

    @Insert
    suspend fun insert(feed: Feed): Long

    @Update
    suspend fun update(feed: Feed)

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE feedId = :feedId ORDER BY publishedAt DESC, id DESC")
    fun observeByFeed(feedId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE feedId = :feedId")
    suspend fun getByFeed(feedId: Long): List<Episode>

    @Query("SELECT * FROM episodes WHERE id = :id")
    fun observe(id: Long): Flow<Episode?>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun get(id: Long): Episode?

    @Query("UPDATE episodes SET played = 1, positionMs = 0 WHERE id = :id")
    suspend fun markPlayed(id: Long)

    @Query("UPDATE episodes SET played = 0 WHERE id = :id")
    suspend fun markUnplayed(id: Long)

    @Query("UPDATE episodes SET positionMs = :positionMs WHERE id = :id")
    suspend fun setPosition(id: Long, positionMs: Long)

    @Query("UPDATE episodes SET downloadState = :state, filePath = :path, downloadedBytes = 0, downloadTotalBytes = 0 WHERE id = :id")
    suspend fun setDownloaded(id: Long, state: DownloadState, path: String?)

    @Query("UPDATE episodes SET downloadState = :state WHERE id = :id")
    suspend fun setState(id: Long, state: DownloadState)

    @Query("UPDATE episodes SET downloadedBytes = :bytes, downloadTotalBytes = :total WHERE id = :id")
    suspend fun setProgress(id: Long, bytes: Long, total: Long)

    @Query("SELECT * FROM episodes WHERE played = 1 AND downloadState = 'Done'")
    suspend fun getPlayedDownloaded(): List<Episode>

    @Query("SELECT * FROM episodes WHERE feedId = :feedId AND filePath IS NOT NULL")
    suspend fun getWithFiles(feedId: Long): List<Episode>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<Episode>)

    @Update
    suspend fun updateAll(episodes: List<Episode>)
}
