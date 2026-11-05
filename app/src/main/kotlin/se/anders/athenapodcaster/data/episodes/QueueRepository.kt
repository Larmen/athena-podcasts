package se.anders.athenapodcaster.data.episodes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import se.anders.athenapodcaster.data.db.AppDatabase
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.EpisodeWithFeed
import se.anders.athenapodcaster.domain.QueueOrder

/** The play queue (FR-100..105). Reads only return episodes that can play, so a stale row is never used. */
class QueueRepository(private val db: AppDatabase) {
    private val dao = db.queueDao()

    fun observe(): Flow<List<EpisodeWithFeed>> = dao.observe()
    fun observeIds(): Flow<List<Long>> = dao.observe().map { rows -> rows.map { it.episode.id } }

    /** Appends a downloaded, unplayed episode (FR-101). False if it cannot be queued. */
    suspend fun add(episodeId: Long): Boolean {
        val e = db.episodeDao().get(episodeId) ?: return false
        if (e.played || e.downloadState != DownloadState.Done) return false
        change { QueueOrder.add(it, episodeId) }
        return true
    }

    suspend fun remove(episodeId: Long) = change { QueueOrder.remove(it, episodeId) }
    suspend fun moveUp(episodeId: Long) = change { QueueOrder.moveUp(it, episodeId) }
    suspend fun moveDown(episodeId: Long) = change { QueueOrder.moveDown(it, episodeId) }
    suspend fun clear() = dao.deleteAll()

    /** Add if absent, remove if present (the car's Add to queue folder, FR-107). */
    suspend fun toggle(episodeId: Long) {
        if (episodeId in dao.playableIds()) remove(episodeId) else add(episodeId)
    }

    private suspend fun change(op: (List<Long>) -> List<Long>) = dao.replaceAll(op(dao.playableIds()))
}
