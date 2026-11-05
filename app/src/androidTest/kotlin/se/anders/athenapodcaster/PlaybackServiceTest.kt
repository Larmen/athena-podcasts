package se.anders.athenapodcaster

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.playback.PlaybackService
import se.anders.athenapodcaster.playback.PlayerController

/** The real service on a real player: play/pause, +30 s / -10 s, saved position, and no streaming (T3.6). */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = (context as AthenaPodcasterApplication).container
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var episode: Episode
    private lateinit var file: File
    private lateinit var controller: PlayerController

    @Before
    fun setUp() {
        runBlocking {
            val feedId = container.database.feedDao().insert(Feed(url = "https://test.invalid/${System.nanoTime()}", urlHash = "h${System.nanoTime()}", title = "Test"))
            val draft = Episode(feedId = feedId, guid = "g", title = "Sample", enclosureUrl = "https://test.invalid/a.mp3")
            val id = container.database.episodeDao().let { it.insertAll(listOf(draft)); it.getByFeed(feedId).single().id }
            episode = draft.copy(id = id)
            file = File(SampleAudio.write(context, episode))
            container.episodes.setDownloaded(id, file.absolutePath)
        }
        instrumentation.runOnMainSync { controller = PlayerController(context) }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { if (controller.state.value.playWhenReady) controller.togglePlayPause() }
        runBlocking { container.database.feedDao().delete(episode.feedId) }
        file.delete()
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun state() = controller.state.value

    private fun waitFor(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (condition()) return; Thread.sleep(100) }
        throw AssertionError("Timed out waiting for $what; state=${state()}")
    }

    @Test
    fun playPauseAndSkips() {
        main { controller.play(episode, "Test", file) }
        waitFor("playing") { state().isPlaying }
        assertEquals(episode.id, state().episodeId)
        assertEquals("Sample", state().title)
        assertEquals("Test", state().show)
        assertTrue("duration should be about 5 min, was ${state().durationMs}", state().durationMs in 290_000..310_000)

        val before = state().positionMs
        main { controller.seekForward() }
        waitFor("forward 30 s") { state().positionMs - before in 29_000..35_000 }
        val afterForward = state().positionMs
        main { controller.seekBack() }
        waitFor("back 10 s") { afterForward - state().positionMs in 8_000..12_000 }

        main { controller.togglePlayPause() }
        waitFor("paused") { !state().playWhenReady }
        // FR-022: position is saved on pause by the service.
        val paused = state().positionMs
        waitFor("saved position", 5_000) { runBlocking { container.episodes.get(episode.id)!!.positionMs } in (paused - 1_500)..(paused + 1_500) }
    }

    @Test
    fun tappingTheNotificationOrLockScreenWidgetOpensTheApp() {
        // The session must carry an activity to launch, otherwise the widget's tap does nothing.
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val media = MediaController.Builder(context, token).buildAsync().get(15, TimeUnit.SECONDS)
        try {
            val launch = media.sessionActivity
            assertNotNull("the session has no activity to open", launch)
            assertEquals(context.packageName, launch!!.creatorPackage)
        } finally {
            main { media.release() }
        }
    }

    @Test
    fun resumesFromSavedPosition() {
        runBlocking { container.database.episodeDao().setPosition(episode.id, 120_000) }
        val saved = runBlocking { container.episodes.get(episode.id)!! }
        main { controller.play(saved, "Test", file) }
        waitFor("playing") { state().isPlaying }
        assertTrue("started at ${state().positionMs}", state().positionMs in 119_000..130_000)
    }

    @Test
    fun streamingIsImpossible() {
        // The service's player only has a file data source, so an http item must fail rather than load.
        var failed: Throwable? = null
        lateinit var media: MediaController
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        media = future.get(15, TimeUnit.SECONDS)
        main {
            media.addListener(object : Player.Listener { override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = error } })
            media.setMediaItem(MediaItem.fromUri(Uri.parse("http://127.0.0.1:9/never.mp3")))
            media.prepare()
        }
        waitFor("error for an http source") { failed != null }
        assertNotNull(failed)
        main { media.release() }
    }
}
