package se.anders.athenapodcaster.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

/** The item the player plays for a downloaded episode: a local file only (FR-024), with title, show and cover. */
fun episodeMediaItem(id: Long, title: String, showTitle: String, file: File, artwork: ByteArray?): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.fromFile(file))
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(showTitle)
                // FR-041: the notification and lock screen show the cover; bytes work across processes.
                .apply { if (artwork != null) setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                .build(),
        )
        .build()
