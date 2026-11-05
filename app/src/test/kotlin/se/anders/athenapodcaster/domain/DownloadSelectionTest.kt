package se.anders.athenapodcaster.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode

class DownloadSelectionTest {
    private fun ep(id: Long, published: Long, played: Boolean = false, state: DownloadState = DownloadState.None) = Episode(
        id = id, feedId = 1, guid = "g$id", title = "E$id", publishedAt = published, enclosureUrl = "https://x.test/$id.mp3", played = played, downloadState = state,
    )

    private val feed = listOf(ep(1, 100), ep(2, 300), ep(3, 200))

    @Test
    fun onlyTheNewestIsChosenByDefault() {
        assertEquals(listOf(2L), selectForAutoDownload(feed, 1).map { it.id })
    }

    @Test
    fun newestNAreChosenNewestFirst() {
        assertEquals(listOf(2L, 3L), selectForAutoDownload(feed, 2).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), selectForAutoDownload(feed, 10).map { it.id })
    }

    @Test
    fun zeroMeansNever() {
        assertEquals(emptyList<Long>(), selectForAutoDownload(feed, 0))
        assertEquals(emptyList<Long>(), selectForAutoDownload(feed, -3))
    }

    @Test
    fun playedNewestMeansNothingNotAnOlderEpisode() {
        val list = listOf(ep(1, 100), ep(2, 300, played = true))
        assertEquals(emptyList<Long>(), selectForAutoDownload(list, 1))
    }

    @Test
    fun alreadyHandledEpisodesAreSkippedButFailedOnesRetry() {
        val list = listOf(
            ep(1, 400, state = DownloadState.Done),
            ep(2, 300, state = DownloadState.Queued),
            ep(3, 200, state = DownloadState.Downloading),
            ep(4, 100, state = DownloadState.Failed),
        )
        assertEquals(listOf(4L), selectForAutoDownload(list, 4).map { it.id })
    }

    @Test
    fun missingDatesSortLastAndTiesBreakByNewestId() {
        val list = listOf(ep(1, 0), ep(2, 50), ep(3, 50))
        assertEquals(listOf(3L, 2L, 1L), selectForAutoDownload(list, 3).map { it.id })
    }

    @Test
    fun extensions() {
        assertEquals("mp3", audioExtension("https://x.test/a.MP3?token=1", null))
        assertEquals("m4a", audioExtension("https://x.test/download?id=5", "audio/mp4"))
        assertEquals("ogg", audioExtension("https://x.test/a", "audio/ogg; codecs=vorbis"))
        assertEquals("mp3", audioExtension("https://x.test/a.php", null))
        assertEquals("mp3", audioExtension("https://x.test/a.exe", "audio/mpeg"))
    }
}
