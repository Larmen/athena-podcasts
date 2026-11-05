package se.anders.athenapodcaster.data.feeds

import androidx.room.withTransaction
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import se.anders.athenapodcaster.data.db.AppDatabase
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.data.db.FeedSummary
import se.anders.athenapodcaster.data.secrets.SecretStore
import se.anders.athenapodcaster.domain.mergeEpisodes

sealed interface AddResult {
    data class Added(val feedId: Long) : AddResult
    data object AlreadySubscribed : AddResult
    /** A private feed must use https, because its address contains the secret token (constitution §4). */
    data object PrivateNeedsHttps : AddResult
    data class Failed(val kind: FetchErrorKind, val httpCode: Int? = null) : AddResult
}

/** Subscriptions and their episodes (FR-001, 003, 011). Feed URLs are secrets: nothing here logs them. */
class FeedRepository(
    private val db: AppDatabase,
    private val source: FeedSource,
    private val secrets: SecretStore,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Pause before the single retry of a refresh that failed on the connection itself. */
    private val retryDelayMs: Long = 1_500,
) {
    /** One refresh at a time per feed, so a slow failing one can never overwrite the result of a later success. */
    private val locks = ConcurrentHashMap<Long, Mutex>()

    private val feeds = db.feedDao()
    private val episodes = db.episodeDao()

    fun observeFeeds(): Flow<List<FeedSummary>> = feeds.observeSummaries()
    fun observeFeed(id: Long): Flow<Feed?> = feeds.observe(id)
    fun observeEpisodes(feedId: Long): Flow<List<Episode>> = episodes.observeByFeed(feedId)

    /** Fetches and validates first; nothing is saved unless the feed parses (FR-001). */
    suspend fun add(rawUrl: String, isPrivate: Boolean): AddResult {
        val url = normalizeFeedUrl(rawUrl) ?: return AddResult.Failed(FetchErrorKind.InvalidUrl)
        if (isPrivate && !url.startsWith("https://")) return AddResult.PrivateNeedsHttps
        if (feeds.getByHash(urlHash(url)) != null) return AddResult.AlreadySubscribed
        return when (val result = source.fetch(url, null, null)) {
            is FetchResult.Failed -> AddResult.Failed(result.kind, result.httpCode)
            FetchResult.NotModified -> AddResult.Failed(FetchErrorKind.NotAFeed) // cannot happen without validators
            is FetchResult.Fetched -> {
                val storedUrl = result.movedTo ?: url
                if (result.movedTo != null && feeds.getByHash(urlHash(storedUrl)) != null) return AddResult.AlreadySubscribed
                // Private feeds keep their real address only in the encrypted store (constitution §4).
                val secretRef = if (isPrivate) secrets.put(storedUrl) else null
                val id = db.withTransaction {
                    val feedId = feeds.insert(
                        Feed(
                            url = if (isPrivate) redactUrl(storedUrl) else storedUrl,
                            urlHash = urlHash(storedUrl),
                            secretRef = secretRef,
                            title = result.feed.title,
                            description = result.feed.description,
                            imageUrl = result.feed.imageUrl,
                            isPrivate = isPrivate,
                            lastRefreshAt = clock(),
                            etag = result.etag,
                            lastModified = result.lastModified,
                        ),
                    )
                    applyEpisodes(feedId, result.feed)
                    feedId
                }
                AddResult.Added(id)
            }
        }
    }

    /** Refreshes one feed. Records the outcome on the feed; a failure never loses episodes or user state. */
    /**
     * Refreshes one feed. A failure of the connection itself is tried once more after a moment. If it still fails and
     * this is a [background] refresh (the scheduled one, which may run while Android blocks background network
     * access), nothing is recorded on the feed: it is the system's doing, not the feed's, and the next refresh simply
     * tries again. A refresh the user asked for, or that runs when the app opens, records the reason.
     */
    suspend fun refresh(feedId: Long, background: Boolean = false): FetchResult? =
        locks.getOrPut(feedId) { Mutex() }.withLock { refreshLocked(feedId, background) }

    private suspend fun refreshLocked(feedId: Long, background: Boolean): FetchResult? {
        val feed = feeds.get(feedId) ?: return null
        val address = addressOf(feed) ?: return FetchResult.Failed(FetchErrorKind.InvalidUrl).also { feeds.update(feed.copy(lastError = FetchErrorKind.InvalidUrl.name)) }
        // A feed with no episodes is fetched in full every time: a "not modified" answer would keep bad data
        // (for example from an older parser) alive for good.
        val hasEpisodes = episodes.getByFeed(feedId).isNotEmpty()
        val etag = feed.etag.takeIf { hasEpisodes }
        val modified = feed.lastModified.takeIf { hasEpisodes }
        var result = source.fetch(address, etag, modified)
        if (result is FetchResult.Failed && result.kind.isConnectivity()) {
            delay(retryDelayMs)
            result = source.fetch(address, etag, modified)
        }
        when (result) {
            is FetchResult.Fetched -> db.withTransaction {
                // Re-read inside the transaction: the feed may have been removed while we were fetching.
                val current = feeds.get(feedId) ?: return@withTransaction
                val moved = result.movedTo?.takeIf { feeds.getByHash(urlHash(it)) == null }
                if (moved != null && current.secretRef != null) secrets.replace(current.secretRef, moved)
                feeds.update(
                    current.copy(
                        url = when {
                            moved == null -> current.url
                            current.secretRef != null -> redactUrl(moved)
                            else -> moved
                        },
                        urlHash = moved?.let(::urlHash) ?: current.urlHash,
                        title = result.feed.title,
                        description = result.feed.description,
                        imageUrl = result.feed.imageUrl,
                        etag = result.etag,
                        lastModified = result.lastModified,
                        lastRefreshAt = clock(),
                        lastError = null,
                    ),
                )
                applyEpisodes(feedId, result.feed)
            }
            FetchResult.NotModified -> feeds.get(feedId)?.let { feeds.update(it.copy(lastRefreshAt = clock(), lastError = null)) }
            is FetchResult.Failed -> if (!(background && result.kind.isConnectivity())) {
                feeds.get(feedId)?.let { feeds.update(it.copy(lastError = result.kind.name)) }
            }
        }
        return result
    }

    suspend fun refreshAll(background: Boolean = false) {
        feeds.getAll().forEach { refresh(it.id, background) }
    }

    /** Removes the feed and (by cascade) its episodes. Downloaded files are handled by the downloads task (T4.6). */
    suspend fun remove(feedId: Long) {
        feeds.get(feedId)?.secretRef?.let(secrets::delete)
        feeds.delete(feedId)
    }

    suspend fun allFeeds(): List<Feed> = feeds.getAll()
    suspend fun feed(id: Long): Feed? = feeds.get(id)

    /** The real address to fetch or export, which for a private feed comes out of the secret store. Null if it was lost. */
    fun addressOf(feed: Feed): String? = if (feed.secretRef != null) secrets.get(feed.secretRef) else feed.url

    private suspend fun applyEpisodes(feedId: Long, parsed: ParsedFeed) {
        val merge = mergeEpisodes(feedId, episodes.getByFeed(feedId), parsed.episodes)
        if (merge.inserts.isNotEmpty()) episodes.insertAll(merge.inserts)
        if (merge.updates.isNotEmpty()) episodes.updateAll(merge.updates)
    }
}

/**
 * Turns what the user typed or pasted into a feed URL: trims, accepts `feed://`, `itpc://` and `pcast://`
 * (as http/https) and a bare host (as https). Returns null if the result is not an http(s) URL.
 */
fun normalizeFeedUrl(raw: String): String? {
    var text = raw.trim()
    if (text.isEmpty()) return null
    for (scheme in listOf("feed:", "itpc:", "pcast:")) {
        if (text.startsWith(scheme, ignoreCase = true)) {
            text = text.substring(scheme.length)
            if (text.startsWith("//")) text = "https:$text" else if (!text.contains("://")) text = "https://$text"
        }
    }
    if (!text.contains("://")) text = "https://$text"
    val url = text.toHttpUrlOrNull() ?: return null
    return url.toString()
}
