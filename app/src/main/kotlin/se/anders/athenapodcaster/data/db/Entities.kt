package se.anders.athenapodcaster.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A subscribed podcast (spec §6). For a public feed [url] is the real address. For a private feed the real address
 * (it contains the access token) lives in the encrypted secret store under [secretRef], and [url] holds only the
 * redacted form "https://host/…" (constitution §4). [urlHash] identifies the real address for duplicate checks.
 */
@Entity(tableName = "feeds", indices = [Index("urlHash", unique = true)])
data class Feed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val urlHash: String,
    val secretRef: String? = null,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val isPrivate: Boolean = false,
    val lastRefreshAt: Long = 0,
    val etag: String? = null,
    val lastModified: String? = null,
    /** Machine-readable kind of the last failed refresh (a [se.anders.athenapodcaster.data.feeds.FetchErrorKind] name), or null. */
    val lastError: String? = null,
)

enum class DownloadState { None, Queued, Downloading, Done, Failed }

@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(entity = Feed::class, parentColumns = ["id"], childColumns = ["feedId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("feedId", "guid", unique = true), Index("feedId", "publishedAt")],
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    /** `<guid>`, or the enclosure URL when the feed has none (FR-011). */
    val guid: String,
    val title: String,
    val publishedAt: Long = 0,
    val durationSec: Int? = null,
    val enclosureUrl: String,
    val enclosureType: String? = null,
    val enclosureLength: Long? = null,
    val downloadState: DownloadState = DownloadState.None,
    val filePath: String? = null,
    val positionMs: Long = 0,
    val played: Boolean = false,
    val downloadedBytes: Long = 0,
    val downloadTotalBytes: Long = 0,
    val imageUrl: String? = null,
    /** Show notes as plain text (FR-017). */
    val description: String? = null,
)

/** An episode waiting in the play queue (spec §6, FR-100). Rows disappear with their episode. */
@Entity(
    tableName = "queue_items",
    foreignKeys = [ForeignKey(entity = Episode::class, parentColumns = ["id"], childColumns = ["episodeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("position")],
)
data class QueueItem(
    @PrimaryKey val episodeId: Long,
    val position: Int,
)
