package se.anders.athenapodcaster

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.data.download.DownloadWorker
import se.anders.athenapodcaster.data.feeds.urlHash

/** The download worker against a real (local) server, and the select → queue → WorkManager → download chain. */
@RunWith(AndroidJUnit4::class)
class DownloadsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = (context as AthenaPodcasterApplication).container
    private lateinit var server: MockWebServer
    private var feedId = 0L
    private val audio = ByteArray(200_000) { (it % 200).toByte() }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        feedId = runBlocking {
            container.database.feedDao().insert(Feed(url = "https://t.invalid/${System.nanoTime()}", urlHash = urlHash("t${System.nanoTime()}"), title = "T"))
        }
    }

    @After
    fun tearDown() {
        server.close()
        runBlocking {
            container.downloads.deleteFeedFiles(feedId)
            container.database.feedDao().delete(feedId)
            container.settings.replaceAll(se.anders.athenapodcaster.data.settings.Settings())
        }
    }

    private fun newEpisode(guid: String, published: Long, path: String = "/$guid.mp3"): Episode = runBlocking {
        val dao = container.database.episodeDao()
        dao.insertAll(listOf(Episode(feedId = feedId, guid = guid, title = guid, publishedAt = published, enclosureUrl = server.url(path).toString(), enclosureType = "audio/mpeg")))
        dao.getByFeed(feedId).first { it.guid == guid }
    }

    private fun runWorker(id: Long, attempt: Int = 0): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<DownloadWorker>(context)
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE_ID to id))
            .setRunAttemptCount(attempt)
            .build().doWork()
    }

    private fun episode(id: Long) = runBlocking { container.episodes.get(id)!! }

    @Test
    fun workerDownloadsAndRecordsTheFile() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(audio)).build())
        val e = newEpisode("a", 100)
        assertTrue(runWorker(e.id) is ListenableWorker.Result.Success)
        val done = episode(e.id)
        assertEquals(DownloadState.Done, done.downloadState)
        assertTrue(File(done.filePath!!).readBytes().contentEquals(audio))
        assertFalse(File(done.filePath + ".part").exists())
    }

    @Test
    fun notFoundFailsForGood() {
        server.enqueue(MockResponse.Builder().code(404).build())
        val e = newEpisode("a", 100)
        assertTrue(runWorker(e.id) is ListenableWorker.Result.Failure)
        assertEquals(DownloadState.Failed, episode(e.id).downloadState)
    }

    @Test
    fun serverErrorRetriesThenGivesUp() {
        server.enqueue(MockResponse.Builder().code(503).build())
        val e = newEpisode("a", 100)
        assertTrue(runWorker(e.id, attempt = 0) is ListenableWorker.Result.Retry)
        assertEquals(DownloadState.Queued, episode(e.id).downloadState)
        server.enqueue(MockResponse.Builder().code(503).build())
        assertTrue(runWorker(e.id, attempt = DownloadWorker.MAX_ATTEMPTS) is ListenableWorker.Result.Failure)
        assertEquals(DownloadState.Failed, episode(e.id).downloadState)
    }

    @Test
    fun alreadyDownloadedIsLeftAlone() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(audio)).build())
        val e = newEpisode("a", 100)
        runWorker(e.id)
        assertTrue(runWorker(e.id) is ListenableWorker.Result.Success)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun autoDownloadFetchesOnlyTheLatestEpisodeThroughWorkManager() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(audio)).build())
        val oldest = newEpisode("old", 100)
        val newest = newEpisode("new", 300)
        val middle = newEpisode("mid", 200)

        runBlocking { container.downloads.autoDownload(feedId) } // default setting: only the latest episode

        // The real WorkManager runs it as soon as its constraints hold (the emulator is on unmetered Wi-Fi).
        val end = System.currentTimeMillis() + 60_000
        while (episode(newest.id).downloadState != DownloadState.Done && System.currentTimeMillis() < end) Thread.sleep(200)

        assertEquals(DownloadState.Done, episode(newest.id).downloadState)
        assertEquals(DownloadState.None, episode(oldest.id).downloadState)
        assertEquals(DownloadState.None, episode(middle.id).downloadState)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun deleteFileKeepsTheEpisodeButForgetsTheFile() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(audio)).build())
        val e = newEpisode("a", 100)
        runWorker(e.id)
        val path = episode(e.id).filePath!!
        runBlocking { container.downloads.deleteFile(episode(e.id)) }
        assertFalse(File(path).exists())
        assertEquals(DownloadState.None, episode(e.id).downloadState)
        assertEquals(1, runBlocking { container.episodes.getByFeed(feedId) }.size)
    }

    @Test
    fun markPlayedDeletesTheDownloadedFile() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(audio)).build())
        val e = newEpisode("a", 100)
        runWorker(e.id)
        val path = episode(e.id).filePath!!
        assertTrue(File(path).exists())

        runBlocking { container.downloads.markPlayedAndDelete(e.id) }

        val after = episode(e.id)
        assertTrue(after.played)
        assertEquals(DownloadState.None, after.downloadState)
        assertFalse(File(path).exists())
        assertEquals(1, runBlocking { container.episodes.getByFeed(feedId) }.size) // still in the list
    }

    @Test
    fun markAllPlayedAffectsOnlyUnplayedEpisodesAndRemovesTheirFiles() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(audio)).build())
        server.enqueue(MockResponse.Builder().body(Buffer().write(audio)).build())
        val downloaded = newEpisode("downloaded", 300)
        val plain = newEpisode("plain", 200)
        val alreadyPlayed = newEpisode("played", 100)
        runWorker(downloaded.id)
        runWorker(alreadyPlayed.id)
        runBlocking { container.episodes.setPlayed(alreadyPlayed.id, true) } // played, and its file was kept
        val keptFile = episode(alreadyPlayed.id).filePath!!
        val removedFile = episode(downloaded.id).filePath!!

        val changed = runBlocking { container.downloads.markAllPlayedAndDelete(feedId) }

        assertEquals(setOf(downloaded.id, plain.id), changed.toSet())
        assertTrue(episode(downloaded.id).played && episode(plain.id).played)
        assertEquals(DownloadState.None, episode(downloaded.id).downloadState)
        assertFalse(File(removedFile).exists())
        assertTrue(File(keptFile).exists()) // already played: left alone
        assertEquals(DownloadState.Done, episode(alreadyPlayed.id).downloadState)
        assertEquals(3, runBlocking { container.episodes.getByFeed(feedId) }.size)
    }

    @Test
    fun afterMarkAllPlayedNothingIsAutoDownloaded() {
        val latest = newEpisode("latest", 300)
        newEpisode("older", 200)
        runBlocking {
            container.downloads.markAllPlayedAndDelete(feedId)
            container.downloads.autoDownload(feedId) // the "I have heard it all" case: the latest episode stays unfetched
        }
        assertEquals(DownloadState.None, episode(latest.id).downloadState)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun markPlayedOnAnEpisodeWithoutAFileJustMarksIt() {
        val e = newEpisode("a", 100)
        runBlocking { container.downloads.markPlayedAndDelete(e.id) }
        assertTrue(episode(e.id).played)
        assertEquals(DownloadState.None, episode(e.id).downloadState)
    }

    @Test
    fun cleanupRemovesPlayedDownloadsOnlyWhenEnabledAndNotWhilePlaying() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(audio)).build())
        server.enqueue(MockResponse.Builder().body(Buffer().write(audio)).build())
        val a = newEpisode("a", 100)
        val b = newEpisode("b", 200)
        runWorker(a.id); runWorker(b.id)
        runBlocking { container.episodes.setPlayed(a.id, true); container.episodes.setPlayed(b.id, true) }

        runBlocking { container.downloads.cleanupPlayed(null) } // setting is off
        assertEquals(DownloadState.Done, episode(a.id).downloadState)

        runBlocking { container.settings.setDeleteAfterPlayed(true); container.downloads.cleanupPlayed(keepEpisodeId = b.id) }
        assertEquals(DownloadState.None, episode(a.id).downloadState)
        assertEquals(DownloadState.Done, episode(b.id).downloadState) // currently playing: kept
    }
}
