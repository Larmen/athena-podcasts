package se.anders.athenapodcaster.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.EpisodeWithFeed
import se.anders.athenapodcaster.playback.AUTO_EMPTY_ID
import se.anders.athenapodcaster.playback.AUTO_LIST_LIMIT
import se.anders.athenapodcaster.playback.AUTO_ROOT_ID
import se.anders.athenapodcaster.playback.AutoLibrary
import se.anders.athenapodcaster.playback.CarEpisodes
import se.anders.athenapodcaster.playback.mayBrowseLibrary

/** FR-081, FR-082, FR-088: the Android Auto list. */
class AutoLibraryTest {
    private fun ep(id: Long, at: Long, feed: String = "Show", done: Boolean = true, played: Boolean = false, dur: Int? = 600) =
        EpisodeWithFeed(
            Episode(
                id = id, feedId = 1, guid = "g$id", title = "Episode $id", publishedAt = at, durationSec = dur, enclosureUrl = "u$id",
                downloadState = if (done) DownloadState.Done else DownloadState.None,
                filePath = if (done) "/f/$id" else null, played = played, imageUrl = "ep$id",
            ),
            feed, "feed-art",
        )

    /** Stands in for the database: the real query keeps downloaded ones, newest first, up to the limit. */
    private class Fake(private val all: List<EpisodeWithFeed>) : CarEpisodes {
        override suspend fun downloaded(limit: Int) =
            all.filter { it.episode.downloadState == DownloadState.Done }
                .sortedWith(compareByDescending<EpisodeWithFeed> { it.episode.publishedAt }.thenByDescending { it.episode.id })
                .take(limit)
        override suspend fun find(id: Long) = all.firstOrNull { it.episode.id == id }
    }

    private fun lib(vararg e: EpisodeWithFeed) = AutoLibrary(Fake(e.toList()), "Athena Podcasts", "Nothing downloaded yet")

    private suspend fun AutoLibrary.eps() = children(AUTO_ROOT_ID, 0, 200)!!

    @Test fun `root is a folder and the list is flat`() = runBlocking {
        val l = lib(ep(1, 10))
        assertTrue(l.root().browsable)
        assertNull(l.children("somewhere-else", 0, 50))
        assertEquals(listOf("1"), l.eps().map { it.id })
    }

    @Test fun `episodes of all podcasts are mixed newest first`() = runBlocking {
        val l = lib(ep(1, 100, "A"), ep(2, 300, "B"), ep(3, 200, "A"))
        assertEquals(listOf("2", "3", "1"), l.eps().map { it.id })
    }

    @Test fun `only downloaded episodes are listed and none can be downloaded or streamed`() = runBlocking {
        val l = lib(ep(1, 100), ep(2, 200, done = false))
        val rows = l.eps()
        assertEquals(listOf("1"), rows.map { it.id })
        assertTrue(rows.all { it.playable && !it.browsable })
        assertNull(l.item("2"))
    }

    @Test fun `played episodes that are still on the phone are listed alike`() = runBlocking {
        val l = lib(ep(1, 100, played = true), ep(2, 200))
        assertEquals(listOf("2", "1"), l.eps().map { it.id })
    }

    @Test fun `the list is capped at 50`() = runBlocking {
        val l = lib(*(1L..80L).map { ep(it, it) }.toTypedArray())
        val rows = l.eps()
        assertEquals(AUTO_LIST_LIMIT, rows.size)
        assertEquals("80", rows.first().id)
        assertEquals("31", rows.last().id)
    }

    @Test fun `paging slices the list`() = runBlocking {
        val l = lib(*(1L..7L).map { ep(it, it) }.toTypedArray())
        assertEquals(listOf("7", "6", "5"), l.children(AUTO_ROOT_ID, 0, 3)!!.map { it.id })
        assertEquals(listOf("1"), l.children(AUTO_ROOT_ID, 2, 3)!!.map { it.id })
        assertTrue(l.children(AUTO_ROOT_ID, 3, 3)!!.isEmpty())
        assertTrue(l.children(AUTO_ROOT_ID, 0, 0)!!.isEmpty())
    }

    @Test fun `there are no folders or other entries besides the episodes`() = runBlocking {
        val rows = lib(ep(1, 1), ep(2, 2)).children(AUTO_ROOT_ID, 0, 50)!!
        assertTrue(rows.all { it.playable && !it.browsable })
    }

    @Test fun `nothing downloaded shows one plain line that cannot be played`() = runBlocking {
        val l = lib(ep(1, 1, done = false))
        val rows = l.children(AUTO_ROOT_ID, 0, 50)!!
        assertEquals(1, rows.size)
        assertEquals("Nothing downloaded yet", rows.single().title)
        assertEquals(AUTO_EMPTY_ID, rows.single().id)
        assertFalse(rows.single().playable)
        assertTrue(l.children(AUTO_ROOT_ID, 1, 50)!!.isEmpty())
    }

    @Test fun `rows carry title, show, duration and art candidates`() = runBlocking {
        val r = lib(ep(7, 1, "The Show", dur = 90)).eps().single()
        assertEquals("Episode 7", r.title)
        assertEquals("The Show", r.subtitle)
        assertEquals(90_000L, r.durationMs)
        assertEquals(listOf<String?>("ep7", "feed-art"), r.artUrls)
        assertNull(lib(ep(8, 1, dur = null)).eps().single().durationMs)
    }

    @Test fun `item lookup refuses unknown ids`() = runBlocking {
        val l = lib(ep(1, 1))
        assertEquals("1", l.item("1")!!.id)
        assertNull(l.item("99"))
        assertNull(l.item("not-a-number"))
    }

    @Test fun `only the app, the system and Android Auto may browse`() {
        val me = "se.anders.athenapodcaster"
        assertTrue(mayBrowseLibrary(me, me, false))
        assertTrue(mayBrowseLibrary("x.y", me, true))
        assertTrue(mayBrowseLibrary("com.google.android.projection.gearhead", me, false))
        assertTrue(mayBrowseLibrary("com.google.android.googlequicksearchbox", me, false))
        assertFalse(mayBrowseLibrary("com.evil.app", me, false))
        assertFalse(mayBrowseLibrary("com.google.android.projection.gearheadx", me, false))
    }
}
