package se.anders.athenapodcaster

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.playback.PlayerController

/** US8 / FR-103..106 on the real service: the queue advances by itself, play-now keeps it. */
@RunWith(AndroidJUnit4::class)
class QueuePlaybackTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = (context as AthenaPodcasterApplication).container
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var controller: PlayerController
    private var feedId = 0L
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        runBlocking {
            feedId = container.database.feedDao().insert(Feed(url = "https://t.invalid/${System.nanoTime()}", urlHash = "h${System.nanoTime()}", title = "Show"))
            container.queue.clear()
        }
        instrumentation.runOnMainSync { controller = PlayerController(context) }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { controller.stopAndClear() }
        runBlocking { container.queue.clear(); container.database.feedDao().delete(feedId) }
        files.forEach { it.delete() }
    }

    private fun episode(n: Int, position: Long = 0): Episode = runBlocking {
        val dao = container.database.episodeDao()
        dao.insertAll(listOf(Episode(feedId = feedId, guid = "g$n", title = "E$n", enclosureUrl = "https://t.invalid/$n.mp3", positionMs = position)))
        val e = dao.getByFeed(feedId).first { it.guid == "g$n" }
        val f = File(SampleAudio.write(context, e)).also { files += it }
        container.episodes.setDownloaded(e.id, f.absolutePath)
        dao.get(e.id)!!
    }

    private fun file(e: Episode) = File(runBlocking { container.episodes.get(e.id)!!.filePath!! })
    private fun state() = controller.state.value
    private fun play(e: Episode) = instrumentation.runOnMainSync { controller.play(runBlocking { container.episodes.get(e.id)!! }, "Show", file(e)) }
    private fun queueIds() = runBlocking { container.queue.observeIds().first() }

    private fun waitFor(what: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (condition()) return; Thread.sleep(100) }
        throw AssertionError("Timed out waiting for $what; state=${state()}, queue=${queueIds()}")
    }

    @Test
    fun theNextQueuedEpisodeStartsByItselfAndTheFinishedOneIsPlayed() {
        val a = episode(1, position = 296_000) // about 4 s before its end
        val b = episode(2)
        runBlocking { container.queue.add(b.id) }
        play(a)
        waitFor("a is playing") { state().episodeId == a.id && state().isPlaying }
        waitFor("b starts by itself") { state().episodeId == b.id && state().isPlaying }
        assertTrue("a is marked played", runBlocking { container.episodes.get(a.id)!!.played })
        waitFor("b leaves the queue") { queueIds().isEmpty() }
        assertFalse(runBlocking { container.episodes.get(b.id)!!.played })
    }

    @Test
    fun aQueuedEpisodeResumesWhereItWasLeft() {
        val a = episode(1, position = 296_000)
        val b = episode(2, position = 100_000)
        runBlocking { container.queue.add(b.id) }
        play(a)
        waitFor("b starts at its saved position") { state().episodeId == b.id && state().isPlaying && state().positionMs in 99_000..110_000 }
    }

    @Test
    fun playNowKeepsTheQueue() {
        val a = episode(1); val b = episode(2); val c = episode(3); val d = episode(4)
        runBlocking { container.queue.add(b.id); container.queue.add(c.id) }
        play(a)
        waitFor("a playing") { state().episodeId == a.id && state().isPlaying }
        play(d) // play now
        waitFor("d playing") { state().episodeId == d.id && state().isPlaying }
        Thread.sleep(800)
        assertEquals(listOf(b.id, c.id), queueIds())
    }

    @Test
    fun playingAQueuedEpisodeNowTakesItOutOfTheQueue() {
        val a = episode(1); val b = episode(2); val c = episode(3)
        runBlocking { container.queue.add(b.id); container.queue.add(c.id) }
        play(b)
        waitFor("b playing") { state().episodeId == b.id && state().isPlaying }
        waitFor("b left the queue") { queueIds() == listOf(c.id) }
        assertTrue(a.id != 0L)
    }

    @Test
    fun aQueuedEpisodeWhoseDownloadIsDeletedIsDroppedFromTheQueueAndThePlayer() {
        val a = episode(1, position = 296_000)
        val b = episode(2)
        val c = episode(3)
        runBlocking { container.queue.add(b.id); container.queue.add(c.id) }
        play(a)
        waitFor("a playing") { state().episodeId == a.id && state().isPlaying }
        val bFile = file(b)
        runBlocking { container.episodes.fileMissing(b.id) } // "Delete download" on b while a plays (FR-105)
        bFile.delete()
        waitFor("c plays after a, b is skipped") { state().episodeId == c.id && state().isPlaying }
        assertEquals(emptyList<Long>(), queueIds())
    }
}
