package se.anders.athenapodcaster.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.db.FeedSummary
import se.anders.athenapodcaster.data.feeds.FetchErrorKind
import se.anders.athenapodcaster.ui.components.ArtImage
import se.anders.athenapodcaster.ui.components.hasArt

/** Feed list (FR-005): title and plain-text counts, no badges. Plain buttons, no gestures (constitution §2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedListScreen(
    feeds: List<FeedSummary>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    queueCount: Int,
    onQueue: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            stringResource(R.string.feeds_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = onAdd) { Text(stringResource(R.string.feeds_add)) }
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Text(stringResource(if (refreshing) R.string.refreshing else R.string.refresh))
            }
            TextButton(onClick = onQueue) { Text(stringResource(R.string.queue_button, queueCount)) }
            TextButton(onClick = onSettings) { Text(stringResource(R.string.settings_title)) }
        }
        if (feeds.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.feeds_empty), style = MaterialTheme.typography.bodyLarge, color = muted())
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(feeds, key = { it.feed.id }) { summary -> FeedRow(summary, onClick = { onOpen(summary.feed.id) }) }
            }
        }
    }
}

@Composable
private fun FeedRow(summary: FeedSummary, onClick: () -> Unit) {
    val art = listOf(summary.feed.imageUrl)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(onClickLabel = stringResource(R.string.a11y_open_podcast), role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(art, size = 56.dp)
        if (hasArt(art)) Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
        Text(summary.feed.title, style = MaterialTheme.typography.titleMedium)
        val unplayed = pluralStringResource(R.plurals.feeds_unplayed, summary.unplayedCount, summary.unplayedCount)
        val downloaded = stringResource(R.string.feeds_downloaded, summary.downloadedCount)
        Text("$unplayed · $downloaded", style = MaterialTheme.typography.bodyMedium, color = muted())
        summary.feed.lastError?.let { Text(stringResource(R.string.feed_last_refresh_failed, stringResource(errorText(it))), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
internal fun muted() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)

/** Short reason for a stored refresh failure. Unknown values read as a network problem. */
internal fun errorText(code: String): Int = when (runCatching { FetchErrorKind.valueOf(code) }.getOrNull()) {
    FetchErrorKind.AuthFailed -> R.string.error_auth
    FetchErrorKind.NotAFeed -> R.string.error_not_feed
    FetchErrorKind.TooLarge -> R.string.error_too_large
    FetchErrorKind.Http -> R.string.error_http
    FetchErrorKind.Dns -> R.string.error_dns
    FetchErrorKind.Timeout -> R.string.error_timeout
    FetchErrorKind.Tls -> R.string.error_tls
    FetchErrorKind.InvalidUrl -> R.string.error_invalid_url
    else -> R.string.error_network
}
