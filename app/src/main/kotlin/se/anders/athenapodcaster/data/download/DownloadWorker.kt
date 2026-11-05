package se.anders.athenapodcaster.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.WorkInfo
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import se.anders.athenapodcaster.AthenaPodcasterApplication
import se.anders.athenapodcaster.data.db.DownloadState

/**
 * Downloads one episode in the background (FR-032). Survives process death and reboot because WorkManager owns
 * it. A progress notification exists only while it runs. Retries with back-off, then gives up visibly.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val container get() = (applicationContext as AthenaPodcasterApplication).container

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("")

    private fun foregroundInfo(title: String) = ForegroundInfo(
        Notifier.DOWNLOAD_NOTIFICATION_ID + (inputData.getLong(KEY_EPISODE_ID, 0).toInt() % 500),
        container.notifier.downloadInProgress(title),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_EPISODE_ID, -1)
        val episodes = container.episodes
        val episode = episodes.get(id) ?: return Result.failure()
        episodes.playableFile(episode)?.let { return Result.success() } // already there

        val dest = container.downloads.fileFor(episode)
        val expected = episode.enclosureLength ?: 0L
        // FR-034: do not start if the disk cannot hold the file (plus a safety margin).
        val free = (dest.parentFile ?: dest).also { it.mkdirs() }.usableSpace
        if (free < expected + MIN_FREE_BYTES) {
            episodes.setState(id, DownloadState.Failed)
            return Result.failure()
        }

        try { setForeground(foregroundInfo(episode.title)) } catch (_: Exception) { /* not allowed right now: carry on without */ }
        episodes.setState(id, DownloadState.Downloading)
        var lastWrite = 0L
        val result = try {
            container.downloader.download(episode.enclosureUrl, dest) { done, total ->
                val now = System.currentTimeMillis()
                if (now - lastWrite >= PROGRESS_INTERVAL_MS) {
                    lastWrite = now
                    episodes.setProgress(id, done, total.coerceAtLeast(0))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Stopped by WorkManager (constraints lost, etc.): wait in the queue. A user cancel is handled by Downloads.cancel.
            if (stopReason != WorkInfo.STOP_REASON_CANCELLED_BY_APP) withContext(NonCancellable) { episodes.setState(id, DownloadState.Queued) }
            throw e
        }
        return when (result) {
            is DownloadResult.Done -> {
                episodes.setDownloaded(id, dest.absolutePath)
                container.artwork.ensure(episode.imageUrl) // the episode's own picture comes with its audio
                Result.success(workDataOf(KEY_EPISODE_ID to id))
            }
            is DownloadResult.Failed -> if (result.retryable && runAttemptCount < MAX_ATTEMPTS) {
                episodes.setState(id, DownloadState.Queued)
                Result.retry()
            } else {
                episodes.setState(id, DownloadState.Failed)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episodeId"
        const val MAX_ATTEMPTS = 4
        const val MIN_FREE_BYTES = 100L * 1024 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L

        fun partFile(dest: File) = File(dest.path + ".part")
    }
}
