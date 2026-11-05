package se.anders.athenapodcaster.ui.feeds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.ui.AddError
import se.anders.athenapodcaster.ui.AddState

/**
 * Add a feed by URL (FR-001, US1/US4). A private (paid) feed must be https. An http feed needs an explicit
 * confirmation first (NFR-004). Nothing is saved unless the feed parses.
 */
@Composable
fun AddFeedScreen(state: AddState, onAdd: (url: String, isPrivate: Boolean) -> Unit, onBack: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var confirmHttp by rememberSaveable { mutableStateOf(false) }
    val working = state is AddState.Working

    fun submit() {
        val typed = url.trim()
        if (typed.startsWith("http://", ignoreCase = true) && !isPrivate) confirmHttp = true else onAdd(typed, isPrivate)
    }

    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.add_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.add_url_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = isPrivate, role = Role.Checkbox, enabled = !working) { isPrivate = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = isPrivate, onCheckedChange = null)
            Text(stringResource(R.string.add_private), modifier = Modifier.padding(start = 12.dp))
        }
        Text(stringResource(R.string.add_private_hint), style = MaterialTheme.typography.bodyMedium, color = muted())
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = ::submit, enabled = !working && url.isNotBlank()) {
            Text(stringResource(if (working) R.string.add_working else R.string.add_button))
        }
        if (state is AddState.Error) Text(stringResource(state.message.textRes()), style = MaterialTheme.typography.bodyMedium)
    }

    if (confirmHttp) {
        AlertDialog(
            onDismissRequest = { confirmHttp = false },
            title = { Text(stringResource(R.string.add_http_title)) },
            text = { Text(stringResource(R.string.add_http_body)) },
            confirmButton = { TextButton(onClick = { confirmHttp = false; onAdd(url.trim(), isPrivate) }) { Text(stringResource(R.string.add_http_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmHttp = false }) { Text(stringResource(R.string.backup_import_cancel)) } },
        )
    }
}

private fun AddError.textRes(): Int = when (this) {
    AddError.InvalidUrl -> R.string.error_invalid_url
    AddError.Network -> R.string.error_network
    AddError.Dns -> R.string.error_dns
    AddError.Timeout -> R.string.error_timeout
    AddError.Tls -> R.string.error_tls
    AddError.AuthFailed -> R.string.error_auth
    AddError.Http -> R.string.error_http
    AddError.NotAFeed -> R.string.error_not_feed
    AddError.TooLarge -> R.string.error_too_large
    AddError.AlreadySubscribed -> R.string.error_already
    AddError.PrivateNeedsHttps -> R.string.error_private_https
}
