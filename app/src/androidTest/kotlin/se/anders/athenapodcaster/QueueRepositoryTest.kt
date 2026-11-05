package se.anders.athenapodcaster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.db.AppDatabase
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.data.episodes.QueueRepository

/** FR-100..105 against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class QueueRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var queue: QueueRepository
    private var feed = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        queue = QueueRepository(db)
        feed = db.feedDao().insert(Feed(url = "https://x.test/f", urlHash = "h", title = "Show"))
    }

    @After fun tearDown() = db.close()

    private fun episode(n: Int, downloaded: Boolean = true, played: Boolean = false): Long = runBlocking {
        db.episodeDao().insertAll(listOf(Episode(feedId = feed, guid = "g$n", title = "E$n", enclosureUrl = "u$n",
            downloadState = if (downloaded) DownloadState.Done else DownloadState.None, filePath = if (downloaded) "/f/$n" else null, played = played)))
        db.episodeDao().getByFeed(feed).first { it.guid == "g$n" }.id
    }

    private fun ids() = runBlocking { queue.observeIds().first() }

    @Test fun addsInOrderOnceAndMoves() = runBlocking {
        val a = episode(1); val b = episode(2); val c = episode(3)
        assertTrue(queue.add(a)); assertTrue(queue.add(b)); assertTrue(queue.add(c)); queue.add(a)
        assertEquals(listOf(a, b, c), ids())
        queue.moveUp(c); assertEquals(listOf(a, c, b), ids())
        queue.moveDown(a); assertEquals(listOf(c, a, b), ids())
        queue.remove(a); assertEquals(listOf(c, b), ids())
        queue.clear(); assertTrue(ids().isEmpty())
    }

    @Test fun onlyDownloadedUnplayedEpisodesCanBeQueued() = runBlocking {
        assertFalse(queue.add(episode(1, downloaded = false)))
        assertFalse(queue.add(episode(2, played = true)))
        assertFalse(queue.add(12345))
        assertTrue(ids().isEmpty())
    }

    @Test fun anEpisodeThatStopsBeingPlayableLeavesTheQueue() = runBlocking {
        val a = episode(1); val b = episode(2); val c = episode(3)
        listOf(a, b, c).forEach { queue.add(it) }
        db.episodeDao().markPlayed(a)           // marked played (FR-105)
        db.episodeDao().setDownloaded(b, DownloadState.None, null) // file deleted
        assertEquals(listOf(c), ids())
        val d = episode(4); queue.add(d)
        assertEquals(listOf(c, d), ids())
        assertEquals(listOf(c, d), db.queueDao().playableIds()) // the next change dropped the stale rows
    }

    @Test fun deletingTheEpisodeOrFeedRemovesItsQueueRow() = runBlocking {
        val a = episode(1); queue.add(a)
        db.feedDao().delete(feed)
        assertTrue(ids().isEmpty())
        assertTrue(db.queueDao().playableIds().isEmpty())
    }

    @Test fun toggleAddsThenRemoves() = runBlocking {
        val a = episode(1)
        queue.toggle(a); assertEquals(listOf(a), ids())
        queue.toggle(a); assertTrue(ids().isEmpty())
    }
}
