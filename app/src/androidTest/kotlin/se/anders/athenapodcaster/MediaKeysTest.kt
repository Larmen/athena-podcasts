package se.anders.athenapodcaster

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.playback.PlayerController

/**
 * What a wired headset, Bluetooth earbuds or a car stereo send: system media-key events, routed by Android to the
 * active media session. The keys must control the player the same way the on-screen buttons do.
 */
@RunWith(AndroidJUnit4::class)
class MediaKeysTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = (context as AthenaPodcasterApplication).container
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val audio get() = context.getSystemService(AudioManager::class.java)
    private lateinit var episode: Episode
    private lateinit var file: File
    private lateinit var controller: PlayerController

    @Before
    fun setUp() {
        runBlocking {
            val feedId = container.database.feedDao().insert(Feed(url = "https://test.invalid/${System.nanoTime()}", urlHash = "k${System.nanoTime()}", title = "Keys"))
            val draft = Episode(feedId = feedId, guid = "g", title = "Key test", enclosureUrl = "https://test.invalid/a.mp3")
            val id = container.database.episodeDao().let { it.insertAll(listOf(draft)); it.getByFeed(feedId).single().id }
            episode = draft.copy(id = id)
            file = File(SampleAudio.write(context, episode))
            container.episodes.setDownloaded(id, file.absolutePath)
        }
        instrumentation.runOnMainSync { controller = PlayerController(context) }
        instrumentation.runOnMainSync { controller.play(episode, "Keys", file) }
        waitFor("playing") { state().isPlaying }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { if (controller.state.value.playWhenReady) controller.togglePlayPause() }
        runBlocking { container.database.feedDao().delete(episode.feedId) }
        file.delete()
    }

    private fun state() = controller.state.value

    private fun waitFor(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (condition()) return; Thread.sleep(100) }
        throw AssertionError("Timed out waiting for $what; state=${state()}")
    }

    private fun press(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    @Test
    fun pauseAndPlayKeys() {
        press(KeyEvent.KEYCODE_MEDIA_PAUSE)
        waitFor("paused by key") { !state().playWhenReady }
        press(KeyEvent.KEYCODE_MEDIA_PLAY)
        waitFor("playing by key") { state().playWhenReady }
    }

    @Test
    fun playPauseAndHeadsetHookToggle() {
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        waitFor("paused by play/pause") { !state().playWhenReady }
        press(KeyEvent.KEYCODE_HEADSETHOOK) // the single button on a wired headset
        waitFor("playing by headset hook") { state().playWhenReady }
    }

    @Test
    fun nextAndPreviousSkipThirtySecondsForwardAndTenBack() {
        val start = state().positionMs
        press(KeyEvent.KEYCODE_MEDIA_NEXT)
        waitFor("+30 s by next") { state().positionMs - start in 29_000..36_000 }
        val after = state().positionMs
        press(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        waitFor("-10 s by previous") { after - state().positionMs in 7_000..12_000 }
    }

    @Test
    fun fastForwardAndRewindKeysSkipToo() {
        val start = state().positionMs
        press(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        waitFor("+30 s by fast forward") { state().positionMs - start in 29_000..36_000 }
        val after = state().positionMs
        press(KeyEvent.KEYCODE_MEDIA_REWIND)
        waitFor("-10 s by rewind") { after - state().positionMs in 7_000..12_000 }
        assertTrue(state().playWhenReady)
    }
}
