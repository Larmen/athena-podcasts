package se.anders.athenapodcaster.ui.licences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.licences.Licence
import se.anders.athenapodcaster.data.licences.Licences
import se.anders.athenapodcaster.data.licences.reflow
import se.anders.athenapodcaster.ui.feeds.muted

/** Open-source licences (FR-066): every bundled font and library, each with its full licence text one button away. */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.licences_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.licences_intro), style = MaterialTheme.typography.bodyMedium, color = muted())
        Licences.all.forEach { LicenceEntry(it) }
    }
}

@Composable
private fun LicenceEntry(licence: Licence) {
    var open by rememberSaveable(licence.name) { mutableStateOf(false) }
    Spacer(Modifier.height(24.dp))
    Text(licence.name, style = MaterialTheme.typography.titleMedium)
    Text(licence.copyright, style = MaterialTheme.typography.bodyMedium, color = muted())
    Text(licence.licenceName, style = MaterialTheme.typography.bodyMedium, color = muted())
    TextButton(onClick = { open = !open }) {
        Text(stringResource(if (open) R.string.licences_hide else R.string.licences_show))
    }
    if (open) {
        val context = LocalContext.current
        val text by produceState("", licence.assetFile) {
            value = withContext(Dispatchers.IO) {
                try { reflow(context.assets.open("licenses/${licence.assetFile}").bufferedReader().use { it.readText() }) } catch (_: Exception) { "" }
            }
        }
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
    }
}
