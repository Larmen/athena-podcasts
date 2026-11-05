package se.anders.athenapodcaster.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import se.anders.athenapodcaster.data.db.Episode
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What the player screen shows. [episodeId] is null until something has been loaded. */
data class PlayerUiState(
    val episodeId: Long? = null,
    val title: String = "",
    val show: String = "",
    val isPlaying: Boolean = false,
    /** What the play/pause button follows: true while playback is wanted, even during a brief buffering after a seek. */
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val ended: Boolean = false,
)

/** The app's handle on [PlaybackService] (plan §3.1). Main-thread only. */
class PlayerController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var connecting: Job? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    /** Connects to the service if not yet connected (starts it, idle, if needed). Safe to call repeatedly. */
    fun connect() {
        if (controller != null || connecting?.isActive == true) return
        connecting = scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            val c = suspendCancellableCoroutine { cont ->
                future.addListener({
                    try { cont.resume(future.get()) } catch (e: Exception) { cont.resumeWithException(e) }
                }, ContextCompat.getMainExecutor(context))
                cont.invokeOnCancellation { future.cancel(false) }
            }
            c.addListener(listener)
            controller = c
            publish(c)
        }
    }

    /** Starts (or resumes) an episode from its downloaded [file], from the saved position (FR-022). */
    fun play(episode: Episode, showTitle: String, file: File, artwork: ByteArray? = null) {
        scope.launch {
            connect()
            connecting?.join()
            val c = controller ?: return@launch
            val id = episode.id.toString()
            if (c.currentMediaItem?.mediaId != id || c.playbackState == Player.STATE_ENDED) {
                val item = episodeMediaItem(episode.id, episode.title, showTitle, file, artwork)
                c.setMediaItem(item, episode.positionMs)
                c.prepare()
            }
            c.play()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.playWhenReady) c.pause() else c.play()
    }

    /** Stops playback and unloads the episode, e.g. before its file is deleted. */
    fun stopAndClear() {
        controller?.run { stop(); clearMediaItems() }
    }

    fun seekBack() { controller?.seekBack() }
    fun seekForward() { controller?.seekForward() }

    private fun publish(player: Player) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        _state.value = PlayerUiState(
            episodeId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            title = player.mediaMetadata.title?.toString().orEmpty(),
            show = player.mediaMetadata.artist?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            ended = player.playbackState == Player.STATE_ENDED,
        )
        ticker?.cancel()
        if (player.isPlaying) {
            // Position moves between events, so refresh it twice a second while playing.
            ticker = scope.launch {
                while (isActive) {
                    delay(500)
                    controller?.let { _state.value = _state.value.copy(positionMs = it.currentPosition.coerceAtLeast(0)) }
                }
            }
        }
    }
}
