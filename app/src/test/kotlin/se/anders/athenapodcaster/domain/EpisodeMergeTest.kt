package se.anders.athenapodcaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.feeds.ParsedEpisode

class EpisodeMergeTest {
    private fun parsed(guid: String, title: String = guid, published: Long = 100, url: String = "https://x.test/$guid.mp3") =
        ParsedEpisode(guid, title, published, 60, url, "audio/mpeg", 10)

    private fun stored(guid: String, id: Long = 1) = Episode(
        id = id, feedId = 7, guid = guid, title = guid, publishedAt = 100, durationSec = 60,
        enclosureUrl = "https://x.test/$guid.mp3", enclosureType = "audio/mpeg", enclosureLength = 10,
    )

    @Test
    fun newEpisodesAreInserted() {
        val m = mergeEpisodes(7, emptyList(), listOf(parsed("a"), parsed("b")))
        assertEquals(listOf("a", "b"), m.inserts.map { it.guid })
        assertTrue(m.updates.isEmpty())
        assertTrue(m.inserts.all { it.feedId == 7L && !it.played && it.downloadState == DownloadState.None })
    }

    @Test
    fun unchangedEpisodesProduceNothing() {
        val m = mergeEpisodes(7, listOf(stored("a")), listOf(parsed("a")))
        assertTrue(m.inserts.isEmpty())
        assertTrue(m.updates.isEmpty())
    }

    @Test
    fun metadataRefreshKeepsUserState() {
        val old = stored("a").copy(played = true, positionMs = 5000, downloadState = DownloadState.Done, filePath = "/f/a.mp3")
        val m = mergeEpisodes(7, listOf(old), listOf(parsed("a", title = "Renamed")))
        val updated = m.updates.single()
        assertEquals("Renamed", updated.title)
        assertTrue(updated.played)
        assertEquals(5000L, updated.positionMs)
        assertEquals(DownloadState.Done, updated.downloadState)
        assertEquals("/f/a.mp3", updated.filePath)
    }

    @Test
    fun episodeArtIsAddedAndKeptWhenTheFeedStopsListingIt() {
        val withArt = parsed("a").copy(imageUrl = "https://x.test/a.jpg")
        assertEquals("https://x.test/a.jpg", mergeEpisodes(7, listOf(stored("a")), listOf(withArt)).updates.single().imageUrl)
        val old = stored("a").copy(imageUrl = "https://x.test/a.jpg")
        assertTrue(mergeEpisodes(7, listOf(old), listOf(parsed("a"))).updates.isEmpty())
    }

    @Test
    fun missingDateKeepsOldDate() {
        val m = mergeEpisodes(7, listOf(stored("a")), listOf(parsed("a", title = "T", published = 0)))
        assertEquals(100L, m.updates.single().publishedAt)
    }

    @Test
    fun episodesRemovedFromFeedAreNotTouched() {
        val m = mergeEpisodes(7, listOf(stored("gone")), listOf(parsed("new")))
        assertEquals(listOf("new"), m.inserts.map { it.guid })
        assertTrue(m.updates.isEmpty())
    }

    @Test
    fun duplicateGuidInOneFeedCountsOnce() {
        val m = mergeEpisodes(7, emptyList(), listOf(parsed("a", title = "first"), parsed("a", title = "second")))
        assertEquals("first", m.inserts.single().title)
    }

    @Test
    fun aRefreshWithoutNotesKeepsTheStoredDescription() {
        val old = Episode(id = 1, feedId = 1, guid = "g", title = "E", durationSec = 60, enclosureUrl = "https://x.test/a.mp3", enclosureType = "audio/mpeg", enclosureLength = 10, description = "Notes")
        val none = mergeEpisodes(1, listOf(old), listOf(ParsedEpisode("g", "E", 0, 60, "https://x.test/a.mp3", "audio/mpeg", 10)))
        assertTrue(none.updates.isEmpty())
        val fresh = mergeEpisodes(1, listOf(old), listOf(ParsedEpisode("g", "E", 0, 60, "https://x.test/a.mp3", "audio/mpeg", 10, description = "New")))
        assertEquals("New", fresh.updates.single().description)
    }
}
