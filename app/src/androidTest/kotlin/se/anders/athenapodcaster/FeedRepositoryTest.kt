package se.anders.athenapodcaster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.db.AppDatabase
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.feeds.AddResult
import se.anders.athenapodcaster.data.feeds.FeedRepository
import se.anders.athenapodcaster.data.feeds.FeedSource
import se.anders.athenapodcaster.data.feeds.FetchErrorKind
import se.anders.athenapodcaster.data.feeds.FetchResult
import se.anders.athenapodcaster.data.feeds.ParsedEpisode
import se.anders.athenapodcaster.data.feeds.ParsedFeed

/** Real Room (in memory) with a scripted feed source: add, refresh, state preservation, cascade delete. */
@RunWith(AndroidJUnit4::class)
class FeedRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FeedRepository
    private val secrets = InMemorySecretStore()
    private var next: FetchResult = FetchResult.Failed(FetchErrorKind.Network)
    private var lastEtag: String? = null
    private val source = object : FeedSource {
        override suspend fun fetch(url: String, etag: String?, lastModified: String?): FetchResult { lastEtag = etag; return next }
    }

    private fun ep(guid: String, title: String = guid) = ParsedEpisode(guid, title, 1000, 60, "https://x.test/$guid.mp3", "audio/mpeg", 10)
    private fun feed(vararg eps: ParsedEpisode, etag: String? = null, movedTo: String? = null) =
        FetchResult.Fetched(ParsedFeed("Show", "About", null, eps.toList()), etag, null, movedTo)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        repo = FeedRepository(db, source, secrets, { 42L }, retryDelayMs = 0)
    }

    @After fun tearDown() = db.close()

    @Test
    fun addStoresFeedAndEpisodes() = runBlocking {
        next = feed(ep("a"), ep("b"), etag = "\"v1\"")
        val id = (repo.add("https://x.test/f.xml", false) as AddResult.Added).feedId
        val stored = repo.observeFeed(id).first()!!
        assertEquals("Show", stored.title)
        assertEquals("\"v1\"", stored.etag)
        assertEquals(42L, stored.lastRefreshAt)
        assertEquals(2, repo.observeEpisodes(id).first().size)
        assertEquals(AddResult.AlreadySubscribed, repo.add("https://x.test/f.xml", false))
    }

    @Test
    fun failedAddSavesNothing() = runBlocking {
        next = FetchResult.Failed(FetchErrorKind.NotAFeed)
        assertEquals(AddResult.Failed(FetchErrorKind.NotAFeed), repo.add("https://x.test/f.xml", false))
        assertTrue(repo.observeFeeds().first().isEmpty())
    }

    @Test
    fun privateFeedMustBeHttps() = runBlocking {
        next = feed(ep("a"))
        assertEquals(AddResult.PrivateNeedsHttps, repo.add("http://x.test/f.xml?token=1", true))
        assertTrue(repo.observeFeeds().first().isEmpty())
        val id = (repo.add("https://x.test/f.xml?token=1", true) as AddResult.Added).feedId
        assertTrue(repo.observeFeed(id).first()!!.isPrivate)
    }

    @Test
    fun privateFeedAddressLivesOnlyInTheSecretStore() = runBlocking {
        val secretUrl = "https://x.test/private/TOKEN123/f.xml"
        next = feed(ep("a"))
        val id = (repo.add(secretUrl, true) as AddResult.Added).feedId
        val stored = repo.observeFeed(id).first()!!
        assertEquals("https://x.test/…", stored.url)
        assertTrue(stored.secretRef != null)
        assertEquals(secretUrl, repo.addressOf(stored))
        // Nothing about the token is in the table.
        db.openHelper.readableDatabase.query("SELECT url, urlHash, secretRef FROM feeds").use { c ->
            c.moveToFirst()
            val row = (0 until c.columnCount).joinToString("|") { c.getString(it).orEmpty() }
            assertTrue(!row.contains("TOKEN123"))
        }
        assertEquals(AddResult.AlreadySubscribed, repo.add(secretUrl, true)) // duplicates are still caught
        repo.remove(id)
        assertTrue(secrets.values.isEmpty())
    }

    @Test
    fun refreshUsesTheSecretAddressAndMovesIt() = runBlocking {
        var fetched: String? = null
        val repo2 = FeedRepository(db, object : FeedSource {
            override suspend fun fetch(url: String, etag: String?, lastModified: String?): FetchResult { fetched = url; return next }
        }, secrets, { 42L }, retryDelayMs = 0)
        next = feed(ep("a"))
        val id = (repo2.add("https://x.test/private/T1/f.xml", true) as AddResult.Added).feedId
        repo2.refresh(id)
        assertEquals("https://x.test/private/T1/f.xml", fetched)
        next = feed(ep("a"), movedTo = "https://y.test/private/T1/f.xml")
        repo2.refresh(id)
        val moved = repo2.observeFeed(id).first()!!
        assertEquals("https://y.test/…", moved.url)
        assertEquals("https://y.test/private/T1/f.xml", repo2.addressOf(moved))
    }

    @Test
    fun refreshKeepsUserStateAndAddsNewEpisodes() = runBlocking {
        next = feed(ep("a"), etag = "\"v1\"")
        val id = (repo.add("https://x.test/f.xml", false) as AddResult.Added).feedId
        val a = repo.observeEpisodes(id).first().single()
        db.episodeDao().updateAll(listOf(a.copy(played = true, positionMs = 9000, downloadState = DownloadState.Done, filePath = "/f/a.mp3")))

        next = feed(ep("a", title = "A renamed"), ep("b"), etag = "\"v2\"")
        repo.refresh(id)

        assertEquals("\"v1\"", lastEtag) // conditional request used the stored validator
        val list = repo.observeEpisodes(id).first()
        assertEquals(2, list.size)
        val refreshedA = list.first { it.guid == "a" }
        assertEquals("A renamed", refreshedA.title)
        assertTrue(refreshedA.played)
        assertEquals(9000L, refreshedA.positionMs)
        assertEquals(DownloadState.Done, refreshedA.downloadState)
        assertEquals("\"v2\"", repo.observeFeed(id).first()!!.etag)
    }

    @Test
    fun aFeedWithNoEpisodesIsAlwaysFetchedInFull() = runBlocking {
        next = feed(etag = "\"v1\"") // parsed fine but no episodes
        val id = (repo.add("https://x.test/f.xml", false) as AddResult.Added).feedId
        repo.refresh(id)
        assertNull(lastEtag) // no validator sent, so the server cannot answer "not modified"
        next = feed(ep("a"), etag = "\"v2\"")
        repo.refresh(id)
        assertEquals(1, repo.observeEpisodes(id).first().size)
        repo.refresh(id)
        assertEquals("\"v2\"", lastEtag) // with episodes, conditional requests are back
    }

    @Test
    fun failedRefreshRecordsErrorAndKeepsEpisodes() = runBlocking {
        next = feed(ep("a"))
        val id = (repo.add("https://x.test/f.xml", false) as AddResult.Added).feedId
        next = FetchResult.Failed(FetchErrorKind.AuthFailed, 403)
        repo.refresh(id)
        assertEquals("AuthFailed", repo.observeFeed(id).first()!!.lastError)
        assertEquals(1, repo.observeEpisodes(id).first().size)
        next = FetchResult.NotModified
        repo.refresh(id)
        assertNull(repo.observeFeed(id).first()!!.lastError)
    }

    @Test
    fun aBackgroundConnectionFailureLeavesNoMessageButAManualOneDoes() = runBlocking {
        next = feed(ep("a"))
        val id = (repo.add("https://x.test/f.xml", false) as AddResult.Added).feedId
        next = FetchResult.Failed(FetchErrorKind.Network)

        repo.refresh(id, background = true)
        assertNull(repo.observeFeed(id).first()!!.lastError) // the system's doing, not the feed's

        repo.refresh(id, background = false)
        assertEquals("Network", repo.observeFeed(id).first()!!.lastError) // the user asked, so say why

        next = feed(ep("a"))
        repo.refresh(id)
        assertNull(repo.observeFeed(id).first()!!.lastError) // and a success clears it again
    }

    @Test
    fun problemsWithTheFeedItselfAreRecordedEvenInTheBackground() = runBlocking {
        next = feed(ep("a"))
        val id = (repo.add("https://x.test/f.xml", false) as AddResult.Added).feedId
        next = FetchResult.Failed(FetchErrorKind.AuthFailed, 403)
        repo.refresh(id, background = true)
        assertEquals("AuthFailed", repo.observeFeed(id).first()!!.lastError)
    }

    @Test
    fun aConnectionFailureIsRetriedOnceBeforeGivingUp() = runBlocking {
        next = feed(ep("a"))
        val id = (repo.add("https://x.test/f.xml", false) as AddResult.Added).feedId
        var calls = 0
        val flaky = object : FeedSource {
            override suspend fun fetch(url: String, etag: String?, lastModified: String?): FetchResult =
                if (++calls == 1) FetchResult.Failed(FetchErrorKind.Dns) else next
        }
        val repo2 = FeedRepository(db, flaky, secrets, { 42L }, retryDelayMs = 0)
        next = feed(ep("a"), ep("b"))
        repo2.refresh(id)
        assertEquals(2, calls)
        assertNull(repo2.observeFeed(id).first()!!.lastError)
        assertEquals(2, repo2.observeEpisodes(id).first().size)
    }

    @Test
    fun permanentRedirectUpdatesStoredUrl() = runBlocking {
        next = feed(ep("a"))
        val id = (repo.add("https://old.test/f.xml", false) as AddResult.Added).feedId
        next = feed(ep("a"), movedTo = "https://new.test/f.xml")
        repo.refresh(id)
        assertEquals("https://new.test/f.xml", repo.observeFeed(id).first()!!.url)
    }

    @Test
    fun removeDeletesEpisodesToo() = runBlocking {
        next = feed(ep("a"), ep("b"))
        val id = (repo.add("https://x.test/f.xml", false) as AddResult.Added).feedId
        repo.remove(id)
        assertTrue(repo.observeFeeds().first().isEmpty())
        assertTrue(db.episodeDao().getByFeed(id).isEmpty())
    }

    @Test
    fun summaryCountsUnplayedAndDownloaded() = runBlocking {
        next = feed(ep("a"), ep("b"), ep("c"))
        val id = (repo.add("https://x.test/f.xml", false) as AddResult.Added).feedId
        val eps = repo.observeEpisodes(id).first()
        db.episodeDao().updateAll(listOf(eps[0].copy(played = true), eps[1].copy(downloadState = DownloadState.Done)))
        val s = repo.observeFeeds().first().single()
        assertEquals(2, s.unplayedCount)
        assertEquals(1, s.downloadedCount)
    }
}
