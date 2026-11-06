package se.anders.athenapodcaster.playback

import android.app.PendingIntent
import java.io.File
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import com.google.common.util.concurrent.SettableFuture
import se.anders.athenapodcaster.data.art.ArtworkStore
import se.anders.athenapodcaster.data.db.EpisodeWithFeed
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.anders.athenapodcaster.AthenaPodcasterApplication
import se.anders.athenapodcaster.MainActivity
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.episodes.EpisodeRepository
import se.anders.athenapodcaster.domain.NowPlaying
import se.anders.athenapodcaster.domain.Progress
import se.anders.athenapodcaster.domain.ProgressTracker

/** Sent by the notification and lock-screen widget: open the app on the player. */
const val ACTION_OPEN_PLAYER = "se.anders.athenapodcaster.OPEN_PLAYER"

const val SEEK_BACK_MS = 10_000L
const val SEEK_FORWARD_MS = 30_000L
private const val SAVE_INTERVAL_MS = 5_000L
private const val LIST_ART_PX = 160
private const val MIRRORED_UPCOMING = 10

/**
 * Plays downloaded episodes (FR-020..024). A foreground media service, so audio continues with the screen off
 * and can be controlled from the notification, lock screen, headset and Bluetooth (FR-021).
 * - Only local files can be played: the player is built with a file-only data source, so streaming is
 *   impossible by construction (constitution §3, FR-024).
 * - Progress is saved here, not in the UI, so it keeps working when the app screen is closed (FR-022).
 */
@OptIn(UnstableApi::class) // FileDataSource / DefaultMediaSourceFactory: Media3's documented way to restrict sources
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private var exo: ExoPlayer? = null
    private var queueRows: List<EpisodeWithFeed> = emptyList()
    private var syncJob: Job? = null
    private val container get() = (application as AthenaPodcasterApplication).container
    private lateinit var library: AutoLibrary
    private val carArt = HashMap<String, ByteArray?>()
    private lateinit var scope: CoroutineScope
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(FileDataSource.Factory()))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
        exo = player
        // Headset and Bluetooth next / previous keys skip 30 s forward / 10 s back (there is no next episode to go to).
        // Tapping the notification or the lock-screen Now Playing widget opens the app on the player.
        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        library = AutoLibrary(container.episodes, getString(R.string.app_name), getString(R.string.auto_nothing_downloaded))
        val mediaSession = MediaLibrarySession.Builder(this, SkipPlayer(player), LibraryCallback())
            .setSessionActivity(openPlayer).build()
        // Notification and lock screen show 10 s back and 30 s forward (spec FR-021).
        mediaSession.setMediaButtonPreferences(
            listOf(
                CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
                    .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                    .setDisplayName(getString(R.string.player_back_10))
                    .build(),
                CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                    .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                    .setDisplayName(getString(R.string.player_forward_30))
                    .build(),
            ),
        )
        session = mediaSession
        player.addListener(ProgressSaver(player))
        // FR-103: the player's upcoming items follow the queue (and a new current item).
        scope.launch {
            container.queue.observe().collect { rows ->
                queueRows = rows
                carArt.clear()
                syncUpcoming()
            }
        }
        // FR-081: an open car list follows downloads finishing and files being deleted.
        scope.launch {
            container.episodes.observeDownloadedIds(AUTO_LIST_LIMIT).distinctUntilChanged().collect {
                carArt.clear()
                mediaSession.notifyChildrenChanged(AUTO_ROOT_ID, AUTO_LIST_LIMIT, null)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /**
     * FR-103: makes the player's items after the current one equal the queue (the first [MIRRORED_UPCOMING] of it,
     * without the episode now playing). Cheap and idempotent: it does nothing when they already match.
     */
    private fun syncUpcoming() {
        val player = exo ?: return
        if (player.mediaItemCount == 0) return
        val currentId = player.currentMediaItem?.mediaId
        val wanted = queueRows.filter { it.episode.id.toString() != currentId }.take(MIRRORED_UPCOMING)
        val first = player.currentMediaItemIndex + 1
        val have = (first until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (have == wanted.map { it.episode.id.toString() }) return
        syncJob?.cancel()
        syncJob = scope.launch {
            val style = container.settings.settings.first().artStyle
            val items = wanted.mapNotNull { row ->
                val file = row.episode.filePath?.let(::File)?.takeIf { it.isFile } ?: return@mapNotNull null
                val art = container.artwork.mediaBytes(listOf(row.episode.imageUrl, row.feedImageUrl), style, LIST_ART_PX)
                episodeMediaItem(row.episode.id, row.episode.title, row.feedTitle, file, art)
            }
            // The player may have moved on while the covers were read; the next trigger will sync again.
            if (player.currentMediaItem?.mediaId != currentId || player.currentMediaItemIndex + 1 != first) return@launch
            player.replaceMediaItems(first, player.mediaItemCount, items)
        }
    }

    /** Runs [block] on the service scope and hands its result (or failure) to Media3 as a future. */
    private fun <T> futureOf(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try { result.set(block()) } catch (e: kotlin.coroutines.cancellation.CancellationException) { result.cancel(false); throw e } catch (e: Exception) { result.setException(e) }
        }
        return result
    }

    /** Small cover for a list row (FR-085): local files only, cached per picture until the list changes. */
    private suspend fun listArt(row: CarRow): ByteArray? {
        val key = row.artUrls.joinToString("|")
        if (key.isBlank()) return null
        return carArt.getOrPut(key) { container.artwork.mediaBytes(row.artUrls, container.settings.settings.first().artStyle, LIST_ART_PX) }
    }

    private suspend fun itemOf(row: CarRow): MediaItem = MediaItem.Builder()
        .setMediaId(row.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(row.title)
                .setSubtitle(row.subtitle)
                .setArtist(row.subtitle)
                .setIsBrowsable(row.browsable)
                .setIsPlayable(row.playable)
                .apply {
                    row.durationMs?.let(::setDurationMs)
                    listArt(row)?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                    if (row.browsable) setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    else if (row.playable) setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                }
                .build(),
        )
        .build()

    /** FR-080..088: the browse tree for Android Auto, and playback requests that name an episode by id only. */
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!mayBrowseLibrary(browser.packageName, packageName, browser.isTrusted)) {
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            }
            // Ask the car for a plain list (FR-090: one level, big rows).
            val extras = Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            }
            val rootParams = MediaLibraryService.LibraryParams.Builder().setExtras(extras).build()
            return futureOf { LibraryResult.ofItem(itemOf(library.root()), rootParams) }
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!mayBrowseLibrary(browser.packageName, packageName, browser.isTrusted)) {
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            }
            return futureOf {
                val rows = library.children(parentId, page, pageSize)
                    ?: return@futureOf LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                LibraryResult.ofItemList(ImmutableList.copyOf(rows.map { itemOf(it) }), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!mayBrowseLibrary(browser.packageName, packageName, browser.isTrusted)) {
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            }
            return futureOf {
                val row = library.item(mediaId) ?: return@futureOf LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                LibraryResult.ofItem(itemOf(row), null)
            }
        }

        /**
         * Items that already carry a file (the phone's own player) pass through. Items that name an episode only by id
         * (Android Auto) are resolved to the downloaded file and the saved position; a missing file is refused and
         * never turns into a network fetch (FR-024, FR-083).
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (mediaItems.all { it.localConfiguration != null }) {
                return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }
            return futureOf {
                val id = mediaItems.singleOrNull()?.mediaId?.toLongOrNull()
                    ?: throw UnsupportedOperationException("Not an episode")
                val found = container.episodes.find(id) ?: throw UnsupportedOperationException("Unknown episode")
                val file = container.episodes.playableFile(found.episode)
                    ?: throw UnsupportedOperationException(getString(R.string.auto_not_downloaded))
                val art = container.artwork.mediaBytes(
                    listOf(found.episode.imageUrl, found.feedImageUrl), container.settings.settings.first().artStyle,
                )
                val item = episodeMediaItem(id, found.episode.title, found.feedTitle, file, art)
                // An item named by id alone comes from the car, which may ask to start "at 0". The saved listening
                // position always wins: starting at 0 would overwrite the progress with the first seconds (FR-022, FR-083).
                val start = found.episode.positionMs
                MediaSession.MediaItemsWithStartPosition(listOf(item), 0, start)
            }
        }
    }

    /** Swiping the app away while paused ends the service; while playing it keeps going. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        ticker?.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    /**
     * A single episode has no next or previous item, so the media keys of a headset, car or Bluetooth speaker (next,
     * previous) would do nothing. This wrapper turns them into the same skips as the on-screen buttons.
     */
    private class SkipPlayer(player: Player) : ForwardingPlayer(player) {
        private val skipCommands = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(*skipCommands).build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
        override fun hasNextMediaItem(): Boolean = true
        override fun hasPreviousMediaItem(): Boolean = true
        override fun seekToNext() = seekForward()
        override fun seekToPrevious() = seekBack()
        override fun seekToNextMediaItem() = seekForward()
        override fun seekToPreviousMediaItem() = seekBack()
    }

    /** Saves the position every few seconds while playing, and on pause, stop and end (FR-022). */
    private inner class ProgressSaver(private val player: Player) : Player.Listener {
        private val episodes: EpisodeRepository get() = (application as AthenaPodcasterApplication).container.episodes
        private var lastSave: Job? = null

        /** Saves run one after the other, so an older position can never overwrite a newer one (FR-022). */
        private fun persist(id: Long, progress: Progress) {
            val previous = lastSave
            lastSave = scope.launch { previous?.join(); episodes.saveProgress(id, progress) }
        }

        private val tracker = ProgressTracker(
            current = {
                val id = player.currentMediaItem?.mediaId?.toLongOrNull()
                id?.let { NowPlaying(it, player.currentPosition, player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0L) }
            },
            persist = { id, progress -> persist(id, progress) },
        )

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ticker?.cancel()
            if (isPlaying) {
                ticker = scope.launch {
                    while (isActive) {
                        delay(SAVE_INTERVAL_MS)
                        tracker.onTick()
                    }
                }
            }
            tracker.onIsPlayingChanged(isPlaying)
        }

        /** Pausing while the player is still buffering leaves "is playing" false, so this is the event that saves it. */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = tracker.onPlayWhenReadyChanged(playWhenReady)

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                tracker.onEnded()
                // FR-103: nothing follows, so the finished episode leaves the Now Playing bar, the player and the lock screen.
                player.clearMediaItems()
            }
        }

        /** FR-103, FR-106: moving to the next queued episode marks the finished one played; any other jump saves its position. */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, @Player.DiscontinuityReason reason: Int,
        ) {
            val oldId = oldPosition.mediaItem?.mediaId?.toLongOrNull() ?: return
            // Unloading (end of the queue, stop) is not a jump: the position was saved when playback stopped.
            if (reason == Player.DISCONTINUITY_REASON_REMOVE && newPosition.mediaItem == null) return
            if (oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId) return
            val progress = if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) Progress(0, played = true)
            else Progress(oldPosition.positionMs.coerceAtLeast(0), played = false)
            persist(oldId, progress)
        }

        /** The episode now playing leaves the queue (FR-103, FR-104); a queued episode resumes at its saved position. */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId?.toLongOrNull()
            if (id != null) {
                scope.launch {
                    container.queue.remove(id)
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val saved = episodes.get(id)?.positionMs ?: 0
                        if (saved > 0 && player.currentMediaItem?.mediaId == mediaItem.mediaId) player.seekTo(saved)
                    }
                }
            }
            syncUpcoming()
        }

        /** A queued file that cannot be read must not stop the whole queue (FR-024): drop it and go on. */
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val id = player.currentMediaItem?.mediaId?.toLongOrNull()
            val gone = player.currentMediaItem?.localConfiguration?.uri?.path?.let { !File(it).isFile } ?: false
            if (id != null && gone) scope.launch { episodes.fileMissing(id); container.queue.remove(id) }
            if (gone && player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare(); player.play() }
        }

    }
}
