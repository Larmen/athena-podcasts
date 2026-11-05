package se.anders.athenapodcaster.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import se.anders.athenapodcaster.data.db.AppDatabase
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.episodes.EpisodeRepository
import se.anders.athenapodcaster.data.settings.SettingsRepository
import se.anders.athenapodcaster.domain.audioExtension
import se.anders.athenapodcaster.domain.selectForAutoDownload

/** Starts, cancels and removes episode downloads, and applies the automatic-download policy (FR-012, 013, 031, 033). */
class Downloads(
    context: Context,
    private val db: AppDatabase,
    private val episodes: EpisodeRepository,
    private val settings: SettingsRepository,
) {
    private val context = context.applicationContext
    private val work get() = WorkManager.getInstance(context)

    /** `…/episodes/<feedId>/<episodeId>.<ext>` in app-specific storage: no permission, removed on uninstall (FR-034). */
    fun fileFor(episode: Episode): File {
        val dir = File(context.getExternalFilesDir("episodes"), episode.feedId.toString())
        return File(dir, "${episode.id}.${audioExtension(episode.enclosureUrl, episode.enclosureType)}")
    }

    /**
     * Queues a download. A manual one (the user tapped Download) ignores the charging rule, runs as soon as
     * possible and, if [allowMobile], may use mobile data (FR-033). Automatic ones follow the settings.
     */
    suspend fun download(episode: Episode, manual: Boolean, allowMobile: Boolean = false) {
        val s = settings.settings.first()
        val network = if (allowMobile || !s.wifiOnly) NetworkType.CONNECTED else NetworkType.UNMETERED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(network)
            .setRequiresCharging(s.chargingOnly && !manual)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE_ID to episode.id))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply { if (manual) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()
        episodes.setState(episode.id, DownloadState.Queued)
        work.enqueueUniqueWork(workName(episode.id), if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    /** Stops a queued or running download and forgets its partial file. */
    suspend fun cancel(episode: Episode) {
        work.cancelUniqueWork(workName(episode.id)).result.get()
        DownloadWorker.partFile(fileFor(episode)).delete()
        episodes.setState(episode.id, DownloadState.None)
        episodes.setProgress(episode.id, 0, 0)
    }

    /** Removes the downloaded file but keeps the episode in the list (FR-012). */
    suspend fun deleteFile(episode: Episode) {
        episode.filePath?.let { File(it).delete() }
        fileFor(episode).let { it.delete(); DownloadWorker.partFile(it).delete() }
        episodes.fileMissing(episode.id)
    }

    /**
     * The "Mark played" button (FR-014): marks the episode played and removes its downloaded file, or cancels a
     * download that is still queued or running. The episode itself stays in the list.
     */
    suspend fun markPlayedAndDelete(episodeId: Long) {
        val episode = episodes.get(episodeId) ?: return
        episodes.setPlayed(episodeId, true)
        when (episode.downloadState) {
            DownloadState.Done -> deleteFile(episode)
            DownloadState.Queued, DownloadState.Downloading -> cancel(episode)
            DownloadState.None, DownloadState.Failed -> Unit
        }
    }

    /**
     * "Mark all as played" on a podcast page (FR-014): every unplayed episode of the feed is marked played and
     * loses its download (or has it cancelled). Episodes that are already played are left as they are.
     * Returns the ids that were changed, so the caller can stop playback of one of them.
     */
    suspend fun markAllPlayedAndDelete(feedId: Long): List<Long> {
        val unplayed = episodes.getByFeed(feedId).filter { !it.played }
        unplayed.forEach { markPlayedAndDelete(it.id) }
        return unplayed.map { it.id }
    }

    /** FR-003: unsubscribing also removes the feed's files. */
    suspend fun deleteFeedFiles(feedId: Long) {
        db.episodeDao().getByFeed(feedId).forEach { work.cancelUniqueWork(workName(it.id)) }
        File(context.getExternalFilesDir("episodes"), feedId.toString()).deleteRecursively()
    }

    /** FR-031: queue the newest [Settings.autoDownloadCount] unplayed episodes of a feed that are not on the phone yet. */
    suspend fun autoDownload(feedId: Long) {
        val count = settings.settings.first().autoDownloadCount
        if (count == 0) return
        selectForAutoDownload(db.episodeDao().getByFeed(feedId), count).forEach { download(it, manual = false) }
    }

    /** FR-013: when "delete after played" is on, played episodes lose their file at the next refresh or app start. */
    suspend fun cleanupPlayed(keepEpisodeId: Long?) {
        if (!settings.settings.first().deleteAfterPlayed) return
        db.episodeDao().getPlayedDownloaded().filter { it.id != keepEpisodeId }.forEach { deleteFile(it) }
    }

    private fun workName(episodeId: Long) = "dl-$episodeId"
}
