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

@Database(entities = [Feed::class, Episode::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        fun create(context: Context, secrets: SecretStore): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "podcaster.db")
                .addMigrations(migration1To2(secrets), MIGRATION_2_3)
                .build()

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
