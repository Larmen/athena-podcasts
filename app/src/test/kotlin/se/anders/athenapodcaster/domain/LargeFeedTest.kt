package se.anders.athenapodcaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.feeds.ParsedEpisode

/** NFR-007: a feed with 10,000 episodes must not make a refresh slow. */
class LargeFeedTest {
    @Test
    fun mergingTenThousandEpisodesIsFastAndKeepsUserState() {
        val n = 10_000
        val existing = (1..n).map {
            Episode(id = it.toLong(), feedId = 1, guid = "g$it", title = "E$it", publishedAt = it.toLong(), enclosureUrl = "https://x.test/$it.mp3", played = it % 2 == 0, positionMs = it.toLong())
        }
        val parsed = (1..n + 50).map { ParsedEpisode("g$it", "E$it", it.toLong(), 60, "https://x.test/$it.mp3", "audio/mpeg", 10) }
        val start = System.nanoTime()
        val merge = mergeEpisodes(1, existing, parsed)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(50, merge.inserts.size)
        assertTrue("merge took $ms ms", ms < 1_500)
        // every updated episode keeps its played state and position
        merge.updates.forEach { u -> val old = existing[u.id.toInt() - 1]; assertEquals(old.played, u.played); assertEquals(old.positionMs, u.positionMs) }
    }
}
