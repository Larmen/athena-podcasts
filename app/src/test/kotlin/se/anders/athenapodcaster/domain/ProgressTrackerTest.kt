package se.anders.athenapodcaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-022: when progress is saved. The pause-while-buffering case is the one that lost positions on the emulator. */
class ProgressTrackerTest {
    private var now: NowPlaying? = NowPlaying(7, 0, 300_000)
    private val saved = mutableListOf<Pair<Long, Progress>>()
    private val tracker = ProgressTracker({ now }) { id, p -> saved += id to p }

    private fun at(positionMs: Long) { now = now!!.copy(positionMs = positionMs) }

    @Test fun `a tick saves the position`() {
        at(5_000); tracker.onTick()
        assertEquals(listOf(7L to Progress(5_000, false)), saved)
    }

    @Test fun `stopping saves, starting does not`() {
        tracker.onIsPlayingChanged(true); tracker.onPlayWhenReadyChanged(true)
        assertTrue(saved.isEmpty())
        at(12_000); tracker.onIsPlayingChanged(false)
        assertEquals(Progress(12_000, false), saved.single().second)
    }

    /**
     * The bug: seek forward (playback stops to buffer, 30 s is saved), seek back to 20 s while still buffering, then pause.
     * "Is playing" was already false, so only the play-when-ready change can save the 20 s.
     */
    @Test fun `pausing while buffering after a seek saves the new position`() {
        at(30_000); tracker.onIsPlayingChanged(false)      // seek forward: buffering
        at(20_000)                                           // seek back, still buffering: no event
        tracker.onPlayWhenReadyChanged(false)               // the user pauses
        assertEquals(Progress(20_000, false), saved.last().second)
    }

    @Test fun `the end marks the episode played and resets the position`() {
        at(299_000); tracker.onEnded()
        assertEquals(Progress(0, true), saved.single().second)
    }

    @Test fun `the last 15 seconds count as played`() {
        at(290_000); tracker.onPlayWhenReadyChanged(false)
        assertEquals(Progress(0, true), saved.single().second)
    }

    @Test fun `nothing is saved when nothing is loaded`() {
        now = null
        tracker.onTick(); tracker.onIsPlayingChanged(false); tracker.onPlayWhenReadyChanged(false); tracker.onEnded()
        assertTrue(saved.isEmpty())
    }
}
