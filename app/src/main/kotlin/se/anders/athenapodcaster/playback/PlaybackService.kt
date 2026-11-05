package se.anders.athenapodcaster.playback

import android.app.PendingIntent
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
import se.anders.athenapodcaster.domain.progressFor

/** Sent by the notification and lock-screen widget: open the app on the player. */
const val ACTION_OPEN_PLAYER = "se.anders.athenapodcaster.OPEN_PLAYER"

const val SEEK_BACK_MS = 10_000L
const val SEEK_FORWARD_MS = 30_000L
private const val SAVE_INTERVAL_MS = 5_000L

/**
 * Plays downloaded episodes (FR-020..024). A foreground media service, so audio continues with the screen off
 * and can be controlled from the notification, lock screen, headset and Bluetooth (FR-021).
 * - Only local files can be played: the player is built with a file-only data source, so streaming is
 *   impossible by construction (constitution §3, FR-024).
 * - Progress is saved here, not in the UI, so it keeps working when the app screen is closed (FR-022).
 */
@OptIn(UnstableApi::class) // FileDataSource / DefaultMediaSourceFactory: Media3's documented way to restrict sources
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
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
        // Headset and Bluetooth next / previous keys skip 30 s forward / 10 s back (there is no next episode to go to).
        // Tapping the notification or the lock-screen Now Playing widget opens the app on the player.
        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mediaSession = MediaSession.Builder(this, SkipPlayer(player)).setSessionActivity(openPlayer).build()
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

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

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ticker?.cancel()
            if (isPlaying) {
                ticker = scope.launch {
                    while (isActive) {
                        delay(SAVE_INTERVAL_MS)
                        save(ended = false)
                    }
                }
            } else {
                save(ended = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) save(ended = true)
        }

        private fun save(ended: Boolean) {
            val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
            val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            val progress = progressFor(player.currentPosition, duration, ended)
            scope.launch { episodes.saveProgress(id, progress) }
        }
    }
}
