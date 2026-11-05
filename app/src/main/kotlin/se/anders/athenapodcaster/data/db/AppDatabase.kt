package se.anders.athenapodcaster.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import se.anders.athenapodcaster.data.feeds.redactUrl
import se.anders.athenapodcaster.data.feeds.urlHash
import se.anders.athenapodcaster.data.secrets.SecretStore

@Database(entities = [Feed::class, Episode::class, QueueItem::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao

    companion object {
        fun create(context: Context, secrets: SecretStore): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "podcaster.db")
                .addMigrations(migration1To2(secrets), MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** v4 (T9.1): the play queue. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `queue_items` (`episodeId` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`episodeId`), FOREIGN KEY(`episodeId`) REFERENCES `episodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_items_position` ON `queue_items` (`position`)")
            }
        }

        /** v3 (T6.1): episodes can have their own cover art. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN imageUrl TEXT")
            }
        }

        /**
         * v2 (T4.2): private feed URLs move into the encrypted secret store and the table keeps only a redacted
         * form plus a hash; episodes gain download progress columns.
         */
        fun migration1To2(secrets: SecretStore) = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN secretRef TEXT")
                db.execSQL("ALTER TABLE feeds ADD COLUMN urlHash TEXT NOT NULL DEFAULT ''")
                db.query("SELECT id, url, isPrivate FROM feeds").use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val url = c.getString(1)
                        val isPrivate = c.getInt(2) != 0
                        if (isPrivate) {
                            val ref = secrets.put(url)
                            db.execSQL("UPDATE feeds SET urlHash = ?, secretRef = ?, url = ? WHERE id = ?", arrayOf<Any?>(urlHash(url), ref, redactUrl(url), id))
                        } else {
                            db.execSQL("UPDATE feeds SET urlHash = ? WHERE id = ?", arrayOf<Any?>(urlHash(url), id))
                        }
                    }
                }
                db.execSQL("DROP INDEX IF EXISTS index_feeds_url")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_feeds_urlHash ON feeds (urlHash)")
                db.execSQL("ALTER TABLE episodes ADD COLUMN downloadedBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE episodes ADD COLUMN downloadTotalBytes INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
