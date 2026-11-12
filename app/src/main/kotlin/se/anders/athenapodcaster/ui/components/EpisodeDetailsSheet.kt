package se.anders.athenapodcaster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.ui.feeds.muted
import se.anders.athenapodcaster.ui.formatDate
import se.anders.athenapodcaster.ui.formatDuration

/** "12.3 MB" from a byte count, or null when unknown. */
internal fun formatSize(bytes: Long?): String? = when {
    bytes == null || bytes <= 0 -> null
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "%d kB".format(bytes / 1000)
}

/**
 * Details of one episode (FR-017): a bottom sheet over the full width with the title, podcast, date, duration, size
 * and the show notes from the feed. A visible Close button, so no gesture is needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailsSheet(episode: Episode, showTitle: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text(episode.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            if (showTitle.isNotBlank()) Text(showTitle, style = MaterialTheme.typography.titleMedium, color = muted())
            val meta = listOfNotNull(formatDate(episode.publishedAt), formatDuration(episode.durationSec), formatSize(episode.enclosureLength))
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = muted())
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f, fill = false).heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val notes = episode.description
                if (notes.isNullOrBlank()) Text(stringResource(R.string.details_none), color = muted())
                else Text(notes, style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(vertical = 8.dp)) { Text(stringResource(R.string.details_close)) }
        }
    }
}
