package se.anders.athenapodcaster

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.playback.AUTO_LIST_LIMIT
import se.anders.athenapodcaster.playback.AUTO_ROOT_ID
import se.anders.athenapodcaster.playback.PlaybackService

/** Android Auto library (T8.2..T8.6): a browser talks to the real service, as the car does. */
@RunWith(AndroidJUnit4::class)
class AutoBrowseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = (context as AthenaPodcasterApplication).container
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val feedIds = mutableListOf<Long>()
    private val files = mutableListOf<File>()
    private lateinit var browser: MediaBrowser
    private val far = Long.MAX_VALUE / 4 // newer than anything else on the device, so our rows lead the list

    @Before
    fun setUp() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        browser = MediaBrowser.Builder(context, token).buildAsync().get(15, TimeUnit.SECONDS)
        instrumentation.runOnMainSync { browser.stop(); browser.clearMediaItems() } // the service outlives tests
        Thread.sleep(300)
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { browser.stop(); browser.clearMediaItems(); browser.release() }
        runBlocking { feedIds.forEach { container.database.feedDao().delete(it) } }
        files.forEach { it.delete() }
    }

    private fun newFeed(title: String, image: String? = null): Long = runBlocking {
        val n = System.nanoTime()
        container.database.feedDao().insert(Feed(url = "https://t.invalid/$n", urlHash = "h$n", title = title, imageUrl = image)).also { feedIds += it }
    }

    /** An episode; downloaded ones get a real audio file. Returns its id. */
    private fun newEpisode(feedId: Long, title: String, at: Long, downloaded: Boolean, position: Long = 0): Long = runBlocking {
        val dao = container.database.episodeDao()
        dao.insertAll(listOf(Episode(feedId = feedId, guid = "g-$title", title = title, publishedAt = at, enclosureUrl = "https://t.invalid/$title.mp3", durationSec = 300, positionMs = position)))
        val id = dao.getByFeed(feedId).first { it.title == title }.id
        if (downloaded) {
            val f = File(SampleAudio.write(context, dao.get(id)!!)).also { files += it }
            container.episodes.setDownloaded(id, f.absolutePath)
        }
        id
    }

    private fun <T> onMain(block: () -> com.google.common.util.concurrent.ListenableFuture<T>): T {
        var f: com.google.common.util.concurrent.ListenableFuture<T>? = null
        instrumentation.runOnMainSync { f = block() }
        return f!!.get(15, TimeUnit.SECONDS)
    }

    private fun children(): List<MediaItem> {
        val r = onMain { browser.getChildren(AUTO_ROOT_ID, 0, 200, null) }
        assertEquals(0, r.resultCode)
        return r.value!!
    }

    /** What the service's player really holds, read by a fresh controller (the first one shows its own request until the reply). */
    private fun sessionItemCountAndPlaying(): Pair<Int, Boolean> {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val fresh = MediaBrowser.Builder(context, token).buildAsync().get(15, TimeUnit.SECONDS)
        var result = -1 to true
        instrumentation.runOnMainSync { result = fresh.mediaItemCount to fresh.isPlaying; fresh.release() }
        return result
    }

    private fun waitFor(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (condition()) return; Thread.sleep(100) }
        throw AssertionError("Timed out waiting for $what")
    }

    @Test
    fun rootIsAListAndEpisodesOfAllPodcastsAreMixedNewestFirst() {
        val a = newFeed("Show A"); val b = newFeed("Show B")
        val old = newEpisode(a, "Old A", far - 3, downloaded = true)
        val mid = newEpisode(b, "Mid B", far - 2, downloaded = true)
        val notYet = newEpisode(a, "Not downloaded", far - 1, downloaded = false)
        val new = newEpisode(b, "New B", far, downloaded = true)

        val root = onMain { browser.getLibraryRoot(null) }
        assertEquals(0, root.resultCode)
        assertTrue(root.value!!.mediaMetadata.isBrowsable == true)

        val ours = children().take(3)
        assertEquals(listOf(new, mid, old).map { it.toString() }, ours.map { it.mediaId })
        assertFalse(children().any { it.mediaId == notYet.toString() })
        assertEquals("New B", ours[0].mediaMetadata.title)
        assertEquals("Show B", ours[0].mediaMetadata.subtitle)
        assertTrue(ours.all { it.mediaMetadata.isPlayable == true })
        assertTrue(children().size <= AUTO_LIST_LIMIT)
    }

    @Test
    fun anEpisodeStartsFromItsSavedPositionAndSharesTheStateWithThePhone() {
        val f = newFeed("Show")
        val id = newEpisode(f, "Resume me", far, downloaded = true, position = 120_000)
        instrumentation.runOnMainSync {
            browser.setMediaItem(MediaItem.Builder().setMediaId(id.toString()).build())
            browser.prepare(); browser.play()
        }
        waitFor("playing") { var p = false; instrumentation.runOnMainSync { p = browser.isPlaying }; p }
        var pos = 0L; var mediaId: String? = null; var title: CharSequence? = null
        instrumentation.runOnMainSync { pos = browser.currentPosition; mediaId = browser.currentMediaItem?.mediaId; title = browser.mediaMetadata.title }
        assertEquals(id.toString(), mediaId)
        assertEquals("Resume me", title)
        assertTrue("started at $pos", pos in 119_000..130_000)
    }

    @Test
    fun aMissingFileIsRefusedAndNeverStreamed() {
        val f = newFeed("Show")
        val id = newEpisode(f, "Gone", far, downloaded = true)
        files.single { true }.delete() // the file disappears after it was listed
        instrumentation.runOnMainSync {
            browser.setMediaItem(MediaItem.Builder().setMediaId(id.toString()).build())
            browser.prepare(); browser.play()
        }
        Thread.sleep(1500)
        val (count, playing) = sessionItemCountAndPlaying()
        assertEquals(0, count)
        assertFalse(playing)
        // The episode goes back to "not downloaded" (FR-024), so it leaves the car list.
        waitFor("state repaired") { runBlocking { container.episodes.get(id)!!.downloadState == DownloadState.None } }
        assertFalse(children().any { it.mediaId == id.toString() })
    }

    @Test
    fun anUnknownIdIsRefused() {
        instrumentation.runOnMainSync {
            browser.setMediaItem(MediaItem.Builder().setMediaId("999999999").build())
            browser.prepare(); browser.play()
        }
        Thread.sleep(1000)
        val (count, playing) = sessionItemCountAndPlaying()
        assertEquals(0, count)
        assertFalse(playing)
    }

    @Test
    fun theOpenListFollowsDeletionsAndNewDownloads() {
        val f = newFeed("Show")
        val id = newEpisode(f, "Short lived", far, downloaded = true)
        assertTrue(children().any { it.mediaId == id.toString() })
        runBlocking { container.episodes.fileMissing(id) }
        assertFalse(children().any { it.mediaId == id.toString() })
    }

    @Test
    fun aBigFeedBrowsesQuickly() {
        val f = newFeed("Big")
        runBlocking {
            container.database.episodeDao().insertAll((1..10_000).map {
                Episode(feedId = f, guid = "g$it", title = "Episode $it", publishedAt = far - 100_000 + it, enclosureUrl = "https://t.invalid/$it.mp3", downloadState = DownloadState.Done, filePath = "/nowhere/$it")
            })
        }
        children() // warm up the connection
        val start = System.nanoTime()
        val rows = children()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(AUTO_LIST_LIMIT, rows.size)
        assertNotNull(rows.first().mediaId)
        assertTrue("browsing took $ms ms", ms < 500)
    }
}
