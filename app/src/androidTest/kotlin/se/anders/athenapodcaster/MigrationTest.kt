package se.anders.athenapodcaster

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.anders.athenapodcaster.data.db.AppDatabase
import se.anders.athenapodcaster.data.feeds.urlHash

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate2To3AddsEpisodeArtAndKeepsData() {
        helper.createDatabase("migration-test-3", 2).apply {
            execSQL("INSERT INTO feeds (id, url, urlHash, title, isPrivate, lastRefreshAt) VALUES (1, 'https://a.test/f', 'h1', 'Feed', 0, 5)")
            execSQL(
                "INSERT INTO episodes (id, feedId, guid, title, publishedAt, enclosureUrl, downloadState, positionMs, played, downloadedBytes, downloadTotalBytes) " +
                    "VALUES (10, 1, 'g', 'Ep', 7, 'https://a.test/a.mp3', 'None', 0, 0, 0, 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-3", 3, true, AppDatabase.MIGRATION_2_3)
        db.query("SELECT title, imageUrl FROM episodes WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ep", c.getString(0))
            assertEquals(null, c.getString(1))
        }
    }

    @Test
    fun migrate3To4AddsTheQueueAndKeepsData() {
        helper.createDatabase("migration-test-4", 3).apply {
            execSQL("INSERT INTO feeds (id, url, urlHash, title, isPrivate, lastRefreshAt) VALUES (1, 'https://a.test/f', 'h1', 'Feed', 0, 5)")
            execSQL(
                "INSERT INTO episodes (id, feedId, guid, title, publishedAt, enclosureUrl, downloadState, positionMs, played, downloadedBytes, downloadTotalBytes) " +
                    "VALUES (10, 1, 'g', 'Ep', 7, 'https://a.test/a.mp3', 'None', 0, 0, 0, 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-4", 4, true, AppDatabase.MIGRATION_3_4)
        db.query("SELECT title FROM episodes WHERE id = 10").use { c -> assertTrue(c.moveToFirst()); assertEquals("Ep", c.getString(0)) }
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO queue_items (episodeId, position) VALUES (10, 0)")
        db.execSQL("DELETE FROM episodes WHERE id = 10")
        db.query("SELECT COUNT(*) FROM queue_items").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }

    @Test
    fun migrate1To2MovesPrivateUrlsIntoTheSecretStoreAndKeepsEverythingElse() {
        val secrets = InMemorySecretStore()
        val publicUrl = "https://pub.test/feed.xml"
        val privateUrl = "https://paid.test/private/TOKEN999/feed.xml"
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO feeds (id, url, title, isPrivate, lastRefreshAt) VALUES (1, '$publicUrl', 'Public', 0, 5)")
            execSQL("INSERT INTO feeds (id, url, title, isPrivate, lastRefreshAt) VALUES (2, '$privateUrl', 'Paid', 1, 6)")
            execSQL(
                "INSERT INTO episodes (id, feedId, guid, title, publishedAt, enclosureUrl, downloadState, positionMs, played) " +
                    "VALUES (10, 2, 'g', 'Ep', 7, 'https://paid.test/a.mp3', 'Done', 4000, 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test", 2, true, AppDatabase.migration1To2(secrets))

        db.query("SELECT url, urlHash, secretRef, title FROM feeds ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(publicUrl, c.getString(0))
            assertEquals(urlHash(publicUrl), c.getString(1))
            assertEquals(null, c.getString(2))
            assertTrue(c.moveToNext())
            assertEquals("https://paid.test/…", c.getString(0))
            assertEquals(urlHash(privateUrl), c.getString(1))
            val ref = c.getString(2)
            assertNotNull(ref)
            assertEquals(privateUrl, secrets.get(ref))
            assertEquals("Paid", c.getString(3))
        }
        db.query("SELECT positionMs, played, downloadState, downloadedBytes, downloadTotalBytes FROM episodes WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4000L, c.getLong(0))
            assertEquals(1, c.getInt(1))
            assertEquals("Done", c.getString(2))
            assertEquals(0L, c.getLong(3))
            assertEquals(0L, c.getLong(4))
        }
        // The secret URL is nowhere in the table any more.
        db.query("SELECT url || urlHash || ifnull(secretRef,'') FROM feeds WHERE id = 2").use { c ->
            c.moveToFirst()
            assertTrue(!c.getString(0).contains("TOKEN999"))
        }
    }
}
