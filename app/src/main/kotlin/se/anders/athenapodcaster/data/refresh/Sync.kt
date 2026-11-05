package se.anders.athenapodcaster.data.refresh

import kotlinx.coroutines.flow.first
import se.anders.athenapodcaster.data.art.ArtworkStore
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.data.download.Downloads
import se.anders.athenapodcaster.data.download.Notifier
import se.anders.athenapodcaster.data.feeds.FeedRepository
import se.anders.athenapodcaster.data.feeds.FetchErrorKind
import se.anders.athenapodcaster.data.feeds.FetchResult
import se.anders.athenapodcaster.data.feeds.isConnectivity

/**
 * Refresh plus what follows from it: automatic downloads, the notification for a private feed that refuses us,
 * and cleanup of played downloads. Used by the periodic worker and by the Refresh buttons (FR-030, 031, 036).
 */
class Sync(
    private val feeds: FeedRepository,
    private val downloads: Downloads,
    private val notifier: Notifier,
    private val artwork: ArtworkStore,
    private val playingEpisodeId: () -> Long?,
) {
    /**
     * Refreshes every feed. With [background] set (the scheduled refresh), connection problems leave no message on the
     * feeds. Returns true if some feed could not be reached, so the scheduled job can ask to be retried.
     */
    suspend fun refreshAll(background: Boolean = false): Boolean {
        var unreachable = false
        feeds.allFeeds().forEach { if (refreshOne(it, background)) unreachable = true }
        downloads.cleanupPlayed(playingEpisodeId())
        return unreachable
    }

    suspend fun refresh(feedId: Long) {
        feeds.allFeeds().firstOrNull { it.id == feedId }?.let { refreshOne(it, background = false) }
        downloads.cleanupPlayed(playingEpisodeId())
    }

    /** After a new subscription: the latest episode(s) are fetched right away (FR-031). */
    suspend fun afterAdd(feedId: Long) {
        fetchFeedArt(feedId)
        downloads.autoDownload(feedId)
    }

    /** Returns true if the feed could not be reached because of the connection. */
    private suspend fun refreshOne(feed: Feed, background: Boolean): Boolean {
        val result = feeds.refresh(feed.id, background)
        if (result is FetchResult.Failed && result.kind == FetchErrorKind.AuthFailed && feed.isPrivate && feed.lastError != FetchErrorKind.AuthFailed.name) {
            notifier.feedAccessRefused(feed.id, feed.title) // once per failure streak, not at every refresh
        }
        fetchFeedArt(feed.id)
        downloads.autoDownload(feed.id)
        return result is FetchResult.Failed && result.kind.isConnectivity()
    }

    /** FR-040: the show's picture is fetched with the refresh, so it is there offline afterwards. */
    private suspend fun fetchFeedArt(feedId: Long) = artwork.ensure(feeds.feed(feedId)?.imageUrl)
}
