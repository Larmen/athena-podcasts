package se.anders.athenapodcaster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.db.AppDatabase
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed

/** NFR-002 / NFR-007: opening a feed with 10,000 episodes reads from the local database quickly. */
@RunWith(AndroidJUnit4::class)
class LargeFeedDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun tenThousandEpisodesLoadAndCountQuickly() = runBlocking {
        val feedId = db.feedDao().insert(Feed(url = "https://x.test/f", urlHash = "h", title = "Big"))
        db.episodeDao().insertAll((1..10_000).map {
            Episode(feedId = feedId, guid = "g$it", title = "Episode $it", publishedAt = it.toLong(), enclosureUrl = "https://x.test/$it.mp3", played = it % 3 == 0)
        })

        val listStart = System.nanoTime()
        val list = db.episodeDao().observeByFeed(feedId).first()
        val listMs = (System.nanoTime() - listStart) / 1_000_000
        assertEquals(10_000, list.size)
        assertEquals("Episode 10000", list.first().title) // newest first
        assertTrue("episode list took $listMs ms", listMs < 2_000)

        val sumStart = System.nanoTime()
        val summary = db.feedDao().observeSummaries().first().single()
        val sumMs = (System.nanoTime() - sumStart) / 1_000_000
        assertEquals(6_667, summary.unplayedCount) // 3,333 of 10,000 are multiples of 3 (played)
        assertTrue("feed summary took $sumMs ms", sumMs < 1_000)
    }
}
