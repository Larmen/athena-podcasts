package se.anders.athenapodcaster.playback

import se.anders.athenapodcaster.data.db.EpisodeWithFeed

const val AUTO_ROOT_ID = "root"
const val AUTO_EMPTY_ID = "empty"

/** FR-081: the car list never shows more than this many episodes. */
const val AUTO_LIST_LIMIT = 50

/** What the car list needs from the database. */
interface CarEpisodes {
    /** Downloaded episodes of every podcast, newest release first. */
    suspend fun downloaded(limit: Int): List<EpisodeWithFeed>
    suspend fun find(id: Long): EpisodeWithFeed?
}

/** One line of the car list, before it is turned into a Media3 item. */
data class CarRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val durationMs: Long? = null,
    val playable: Boolean = false,
    val browsable: Boolean = false,
    /** Picture candidates, best first: the episode's own, then the show's. */
    val artUrls: List<String?> = emptyList(),
)

/**
 * The Android Auto library (plan §3.10, FR-081, FR-082): one flat list. The root holds every downloaded episode of every
 * podcast, newest first, at most [AUTO_LIST_LIMIT]; an empty phone shows a single plain-text line instead. Nothing here
 * can start a download or a stream, and the "Delete download after played" setting alone decides what stays (FR-013).
 */
class AutoLibrary(
    private val source: CarEpisodes,
    private val rootTitle: String,
    private val emptyText: String,
) {
    fun root() = CarRow(id = AUTO_ROOT_ID, title = rootTitle, browsable = true)

    /** The rows under [parentId] for one page, or null when [parentId] is not a list (only the root is). */
    suspend fun children(parentId: String, page: Int, pageSize: Int): List<CarRow>? {
        if (parentId != AUTO_ROOT_ID) return null
        if (page < 0 || pageSize <= 0) return emptyList()
        val all = source.downloaded(AUTO_LIST_LIMIT)
        if (all.isEmpty()) return if (page == 0) listOf(emptyRow()) else emptyList()
        return all.drop(page * pageSize).take(pageSize).map(::rowOf)
    }

    /** One item by id: the root, the empty line, or a downloaded episode; null for anything else (FR-024). */
    suspend fun item(id: String): CarRow? = when (id) {
        AUTO_ROOT_ID -> root()
        AUTO_EMPTY_ID -> emptyRow()
        else -> id.toLongOrNull()?.let { source.find(it) }?.takeIf { it.episode.filePath != null }?.let(::rowOf)
    }

    private fun emptyRow() = CarRow(id = AUTO_EMPTY_ID, title = emptyText)

    private fun rowOf(e: EpisodeWithFeed) = CarRow(
        id = e.episode.id.toString(),
        title = e.episode.title,
        subtitle = e.feedTitle,
        durationMs = e.episode.durationSec?.let { it * 1000L },
        playable = true,
        artUrls = listOf(e.episode.imageUrl, e.feedImageUrl),
    )
}

/** FR-088: who may list the library. Others can still control playback but cannot see the subscriptions. */
fun mayBrowseLibrary(callerPackage: String, ownPackage: String, systemTrusted: Boolean): Boolean =
    systemTrusted ||
        callerPackage == ownPackage ||
        callerPackage == "com.google.android.projection.gearhead" ||
        callerPackage.startsWith("com.google.android.projection.gearhead.") ||
        callerPackage == "com.google.android.googlequicksearchbox"
