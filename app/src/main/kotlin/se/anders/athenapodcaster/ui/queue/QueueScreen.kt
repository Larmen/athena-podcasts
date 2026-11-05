package se.anders.athenapodcaster.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.db.EpisodeWithFeed
import se.anders.athenapodcaster.ui.feeds.muted
import se.anders.athenapodcaster.ui.formatDuration

/** The play queue (FR-102): rows with plain buttons, no gestures. The episode now playing is not in it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(
    rows: List<EpisodeWithFeed>,
    onBack: () -> Unit,
    onPlayNow: (EpisodeWithFeed) -> Unit,
    onRemove: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            TextButton(onClick = { confirmClear = true }, enabled = rows.isNotEmpty()) { Text(stringResource(R.string.queue_clear)) }
        }
        Text(
            stringResource(R.string.queue_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        if (rows.isEmpty()) {
            Text(stringResource(R.string.queue_empty), Modifier.padding(24.dp), color = muted())
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows, key = { _, r -> r.episode.id }) { index, row ->
                    QueueRow(row, first = index == 0, last = index == rows.lastIndex, onPlayNow, onRemove, onMoveUp, onMoveDown)
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.queue_clear_title)) },
            text = { Text(stringResource(R.string.queue_clear_body)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.queue_clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.backup_import_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueRow(
    row: EpisodeWithFeed,
    first: Boolean,
    last: Boolean,
    onPlayNow: (EpisodeWithFeed) -> Unit,
    onRemove: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
) {
    val title = row.episode.title
    val meta = listOfNotNull(row.feedTitle, formatDuration(row.episode.durationSec)).joinToString(" · ")
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(meta, style = MaterialTheme.typography.bodyMedium, color = muted())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val play = stringResource(R.string.queue_play_now) + ", " + title
            val remove = stringResource(R.string.queue_remove) + ", " + title
            val up = stringResource(R.string.queue_move_up) + ", " + title
            val down = stringResource(R.string.queue_move_down) + ", " + title
            TextButton(modifier = Modifier.semantics { contentDescription = play }, onClick = { onPlayNow(row) }) { Text(stringResource(R.string.queue_play_now)) }
            TextButton(modifier = Modifier.semantics { contentDescription = remove }, onClick = { onRemove(row.episode.id) }) { Text(stringResource(R.string.queue_remove)) }
            TextButton(modifier = Modifier.semantics { contentDescription = up }, onClick = { onMoveUp(row.episode.id) }, enabled = !first) { Text(stringResource(R.string.queue_move_up)) }
            TextButton(modifier = Modifier.semantics { contentDescription = down }, onClick = { onMoveDown(row.episode.id) }, enabled = !last) { Text(stringResource(R.string.queue_move_down)) }
        }
    }
}
