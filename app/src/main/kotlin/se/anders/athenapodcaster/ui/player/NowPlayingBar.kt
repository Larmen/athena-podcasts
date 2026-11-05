package se.anders.athenapodcaster.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.playback.PlayerUiState
import se.anders.athenapodcaster.ui.components.PlayPauseButton
import se.anders.athenapodcaster.ui.components.SkipButton
import se.anders.athenapodcaster.ui.feeds.muted

/**
 * The strip at the bottom of the podcast and episode screens while an episode is loaded: what is playing and the
 * three transport buttons, always within reach. Tapping the title opens the full player. Plain buttons, no gestures.
 */
@Composable
fun NowPlayingBar(
    state: PlayerUiState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).heightIn(min = 56.dp)
                    .clickable(onClickLabel = stringResource(R.string.a11y_open_player), role = Role.Button, onClick = onOpen)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.show.isNotEmpty()) {
                    Text(state.show, style = MaterialTheme.typography.bodySmall, color = muted(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SkipButton(forward = false, seconds = 10, onClick = onSeekBack)
            PlayPauseButton(playing = state.playWhenReady, onClick = onPlayPause)
            SkipButton(forward = true, seconds = 30, onClick = onSeekForward)
        }
    }
}
