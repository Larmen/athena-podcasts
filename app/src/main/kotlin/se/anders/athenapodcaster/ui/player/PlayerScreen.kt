package se.anders.athenapodcaster.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.playback.PlayerUiState
import se.anders.athenapodcaster.ui.components.ArtImage
import se.anders.athenapodcaster.ui.components.MarkPlayedDialog
import se.anders.athenapodcaster.ui.components.PlayPauseButton
import se.anders.athenapodcaster.ui.components.SkipButton
import se.anders.athenapodcaster.ui.components.hasArt
import se.anders.athenapodcaster.ui.feeds.muted
import se.anders.athenapodcaster.ui.formatClock

/** Player (FR-020): three big buttons, elapsed/remaining time and a plain progress bar. No gestures, no seek drag in v1. */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    episode: Episode?,
    artUrls: List<String?>,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onMarkPlayed: () -> Unit,
) {
    var confirmPlayed by rememberSaveable { mutableStateOf(false) }
    if (confirmPlayed && episode != null) {
        MarkPlayedDialog(
            hasDownload = episode.downloadState == DownloadState.Done,
            onConfirm = { confirmPlayed = false; onMarkPlayed() },
            onDismiss = { confirmPlayed = false },
        )
    }
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Spacer(Modifier.height(16.dp))
        if (state.episodeId == null) {
            Text(stringResource(R.string.player_nothing), color = muted())
            return@Column
        }
        ArtImage(artUrls, size = 280.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (hasArt(artUrls)) Spacer(Modifier.height(24.dp))
        Text(state.show, style = MaterialTheme.typography.bodyMedium, color = muted())
        Text(state.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(24.dp))
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        val progressText = stringResource(R.string.a11y_progress, formatClock(state.positionMs), formatClock(state.durationMs))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressText })
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(state.positionMs), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (state.durationMs > 0) "-" + formatClock((state.durationMs - state.positionMs).coerceAtLeast(0)) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = muted(),
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            SkipButton(forward = false, seconds = 10, onClick = onSeekBack, size = 72.dp, iconSize = 44.dp)
            PlayPauseButton(playing = state.playWhenReady, onClick = onPlayPause, size = 88.dp, iconSize = 56.dp)
            SkipButton(forward = true, seconds = 30, onClick = onSeekForward, size = 72.dp, iconSize = 44.dp)
        }
        Spacer(Modifier.height(16.dp))
        if (episode != null) {
            TextButton(onClick = { if (episode.played) onMarkUnplayed() else confirmPlayed = true }) {
                Text(stringResource(if (episode.played) R.string.mark_unplayed else R.string.mark_played))
            }
        }
    }
}
