package se.anders.athenapodcaster.data.episodes

import java.io.File
import kotlinx.coroutines.flow.Flow
import se.anders.athenapodcaster.data.db.AppDatabase
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.EpisodeWithFeed
import se.anders.athenapodcaster.playback.CarEpisodes
import se.anders.athenapodcaster.domain.Progress

/** Per-episode state: played, position, downloaded file (FR-012, FR-014, FR-022, FR-024). */
class EpisodeRepository(db: AppDatabase) : CarEpisodes {
    private val dao = db.episodeDao()

    fun observe(id: Long): Flow<Episode?> = dao.observe(id)
    suspend fun get(id: Long): Episode? = dao.get(id)

    override suspend fun downloaded(limit: Int): List<EpisodeWithFeed> = dao.downloadedWithFeed(limit)
    override suspend fun find(id: Long): EpisodeWithFeed? = dao.withFeed(id)

    /** Ids of the downloaded episodes the car list shows; emits again when the list may have changed (FR-081). */
    fun observeDownloadedIds(limit: Int): Flow<List<Long>> = dao.observeDownloadedIds(limit)

    suspend fun setPlayed(id: Long, played: Boolean) {
        if (played) dao.markPlayed(id) else dao.markUnplayed(id)
    }

    /** Saves playback progress. Marking played resets the position; a normal save never un-plays an episode. */
    suspend fun saveProgress(id: Long, progress: Progress) {
        if (progress.played) dao.markPlayed(id) else dao.setPosition(id, progress.positionMs)
    }

    suspend fun getByFeed(feedId: Long): List<Episode> = dao.getByFeed(feedId)
    suspend fun setState(id: Long, state: DownloadState) = dao.setState(id, state)
    suspend fun setProgress(id: Long, bytes: Long, total: Long) = dao.setProgress(id, bytes, total)

    suspend fun setDownloaded(id: Long, path: String) = dao.setDownloaded(id, DownloadState.Done, path)

    /** FR-024: the file is gone, so the episode goes back to "not downloaded". */
    suspend fun fileMissing(id: Long) = dao.setDownloaded(id, DownloadState.None, null)

    /** The playable file for an episode, or null (and the state is repaired) when it no longer exists. */
    suspend fun playableFile(episode: Episode): File? {
        if (episode.downloadState != DownloadState.Done) return null
        val file = episode.filePath?.let(::File)
        if (file != null && file.isFile) return file
        fileMissing(episode.id)
        return null
    }
}
