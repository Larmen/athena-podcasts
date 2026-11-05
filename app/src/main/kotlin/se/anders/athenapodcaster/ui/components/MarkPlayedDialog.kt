package se.anders.athenapodcaster.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import se.anders.athenapodcaster.R

/**
 * Confirmation for "Mark played" (FR-014). Says plainly that the downloaded file is deleted, because that cannot
 * be undone; the episode can be downloaded again afterwards.
 */
@Composable
fun MarkPlayedDialog(hasDownload: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mark_played_title)) },
        text = { Text(stringResource(if (hasDownload) R.string.mark_played_body_delete else R.string.mark_played_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.mark_played)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_import_cancel)) } },
    )
}
