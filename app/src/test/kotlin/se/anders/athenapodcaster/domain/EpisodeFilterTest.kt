package se.anders.athenapodcaster.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode

class EpisodeFilterTest {
    private fun ep(id: Long, played: Boolean = false, state: DownloadState = DownloadState.None) = Episode(
        id = id, feedId = 1, guid = "g$id", title = "E$id", enclosureUrl = "https://x.test/$id.mp3", played = played, downloadState = state,
    )

    private val list = listOf(
        ep(1, played = false, state = DownloadState.Done),
        ep(2, played = true, state = DownloadState.Done),
        ep(3, played = false, state = DownloadState.None),
        ep(4, played = true, state = DownloadState.None),
        ep(5, played = false, state = DownloadState.Downloading),
        ep(6, played = false, state = DownloadState.Failed),
    )

    @Test
    fun theListStartsOnUnplayedOnly() = assertEquals(EpisodeFilter.Unplayed, EpisodeFilter.Default)

    @Test
    fun allKeepsEverything() = assertEquals(list.map { it.id }, EpisodeFilter.All.apply(list).map { it.id })

    @Test
    fun downloadedOnlyKeepsFilesOnThePhoneWhetherPlayedOrNot() =
        assertEquals(listOf(1L, 2L), EpisodeFilter.Downloaded.apply(list).map { it.id }) // not queued, running or failed ones

    @Test
    fun unplayedKeepsEverythingNotPlayed() = assertEquals(listOf(1L, 3L, 5L, 6L), EpisodeFilter.Unplayed.apply(list).map { it.id })

    @Test
    fun buttonStepsAllThenDownloadedThenUnplayedThenBackToAll() {
        assertEquals(EpisodeFilter.Downloaded, EpisodeFilter.All.next())
        assertEquals(EpisodeFilter.Unplayed, EpisodeFilter.Downloaded.next())
        assertEquals(EpisodeFilter.All, EpisodeFilter.Unplayed.next())
    }
}
