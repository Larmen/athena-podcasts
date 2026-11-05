package se.anders.athenapodcaster.ui.episodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.anders.athenapodcaster.R
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.domain.EpisodeFilter
import se.anders.athenapodcaster.ui.MarkAllInfo
import se.anders.athenapodcaster.ui.components.ArtImage
import se.anders.athenapodcaster.ui.components.MarkPlayedDialog
import se.anders.athenapodcaster.ui.components.hasArt
import se.anders.athenapodcaster.ui.feeds.errorText
import se.anders.athenapodcaster.ui.feeds.muted
import se.anders.athenapodcaster.ui.formatDate
import se.anders.athenapodcaster.ui.formatDuration

/** Episodes of one feed, newest first, with a plain-text state per row (FR-010). Downloads and playback come later. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeListScreen(
    feed: Feed?,
    episodes: List<Episode>,
    filter: EpisodeFilter,
    markAllInfo: MarkAllInfo,
    refreshing: Boolean,
    onNextFilter: () -> Unit,
    onRefresh: () -> Unit,
    onUnsubscribe: () -> Unit,
    onBack: () -> Unit,
    onPlay: (Episode) -> Unit,
    onMarkUnplayed: (Episode) -> Unit,
    onMarkPlayed: (Episode) -> Unit,
    onMarkAllPlayed: () -> Unit,
    onDownload: (Episode) -> Unit,
    onCancelDownload: (Episode) -> Unit,
    onDeleteDownload: (Episode) -> Unit,
    askMobile: Episode?,
    onConfirmMobile: () -> Unit,
    onDismissMobile: () -> Unit,
    revealedAddress: String?,
    onToggleAddress: () -> Unit,
) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var confirmPlayedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // FlowRow, not Row: at large font sizes the buttons wrap onto a second line instead of squeezing each other out.
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Text(stringResource(if (refreshing) R.string.refreshing else R.string.refresh))
            }
            TextButton(onClick = { confirmRemove = true }, enabled = feed != null) { Text(stringResource(R.string.episodes_unsubscribe)) }
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(feed?.title.orEmpty(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (feed?.isPrivate == true) {
                // The address holds the secret key: masked unless the user asks (constitution §4).
                Text(revealedAddress ?: feed.url, style = MaterialTheme.typography.bodyMedium, color = muted())
                TextButton(onClick = onToggleAddress) { Text(stringResource(if (revealedAddress != null) R.string.address_hide else R.string.address_show)) }
            }
            feed?.lastError?.let { Text(stringResource(R.string.feed_last_refresh_failed, stringResource(errorText(it))), style = MaterialTheme.typography.bodyMedium) }
        }
        FlowRow(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onNextFilter) {
                Text(
                    stringResource(
                        when (filter) {
                            EpisodeFilter.All -> R.string.episodes_showing_all
                            EpisodeFilter.Downloaded -> R.string.episodes_showing_downloaded
                            EpisodeFilter.Unplayed -> R.string.episodes_showing_unplayed
                        },
                    ),
                )
            }
            TextButton(onClick = { confirmAll = true }, enabled = markAllInfo.unplayed > 0) { Text(stringResource(R.string.mark_all_played)) }
        }
        if (episodes.isEmpty()) {
            Text(stringResource(if (filter == EpisodeFilter.All) R.string.episodes_empty else R.string.episodes_empty_filtered), Modifier.padding(24.dp), color = muted())
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(episodes, key = { it.id }) { EpisodeRow(it, feed?.imageUrl, onPlay, onMarkUnplayed, { e -> confirmPlayedId = e.id }, onDownload, onCancelDownload, onDeleteDownload) }
            }
        }
    }
    if (confirmAll) {
        val unplayed = markAllInfo.unplayed
        val withFiles = markAllInfo.withDownloads
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.mark_all_title)) },
            text = {
                Text(
                    pluralStringResource(R.plurals.mark_all_body, unplayed, unplayed) +
                        if (withFiles > 0) " " + stringResource(R.string.mark_all_body_delete) else "",
                )
            },
            confirmButton = { TextButton(onClick = { confirmAll = false; onMarkAllPlayed() }) { Text(stringResource(R.string.mark_all_played)) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(R.string.backup_import_cancel)) } },
        )
    }
    confirmPlayedId?.let { id ->
        val episode = episodes.firstOrNull { it.id == id }
        if (episode == null) confirmPlayedId = null else MarkPlayedDialog(
            hasDownload = episode.downloadState == DownloadState.Done,
            onConfirm = { confirmPlayedId = null; onMarkPlayed(episode) },
            onDismiss = { confirmPlayedId = null },
        )
    }
    if (askMobile != null) {
        AlertDialog(
            onDismissRequest = onDismissMobile,
            title = { Text(stringResource(R.string.mobile_title)) },
            text = { Text(stringResource(R.string.mobile_body)) },
            confirmButton = { TextButton(onClick = onConfirmMobile) { Text(stringResource(R.string.mobile_confirm)) } },
            dismissButton = { TextButton(onClick = onDismissMobile) { Text(stringResource(R.string.backup_import_cancel)) } },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.unsubscribe_title)) },
            text = { Text(stringResource(R.string.unsubscribe_body)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onUnsubscribe() }) { Text(stringResource(R.string.episodes_unsubscribe)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.backup_import_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeRow(
    episode: Episode,
    feedImageUrl: String?,
    onPlay: (Episode) -> Unit,
    onMarkUnplayed: (Episode) -> Unit,
    onAskMarkPlayed: (Episode) -> Unit,
    onDownload: (Episode) -> Unit,
    onCancel: (Episode) -> Unit,
    onDelete: (Episode) -> Unit,
) {
    val stateText = stringResource(
        when {
            episode.played -> R.string.episode_played
            episode.downloadState == DownloadState.Done -> R.string.episode_downloaded
            episode.downloadState == DownloadState.Downloading -> R.string.episode_downloading
            episode.downloadState == DownloadState.Queued -> R.string.episode_queued
            episode.downloadState == DownloadState.Failed -> R.string.episode_failed
            else -> R.string.episode_not_downloaded
        },
    )
    val percent = if (episode.downloadState == DownloadState.Downloading && episode.downloadTotalBytes > 0) {
        " " + (episode.downloadedBytes * 100 / episode.downloadTotalBytes) + " %"
    } else ""
    val meta = listOfNotNull(formatDate(episode.publishedAt), formatDuration(episode.durationSec), stateText + percent).joinToString(" · ")
    val art = listOf(episode.imageUrl, feedImageUrl)
    // One unambiguous label per button for screen readers: "Play, <episode title>".
    val playLabel = stringResource(R.string.episode_play) + ", " + episode.title
    val deleteLabel = stringResource(R.string.episode_delete_download) + ", " + episode.title
    val cancelLabel = stringResource(R.string.episode_cancel_download) + ", " + episode.title
    val downloadLabel = stringResource(R.string.episode_download) + ", " + episode.title
    val playedLabel = stringResource(if (episode.played) R.string.mark_unplayed else R.string.mark_played) + ", " + episode.title
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row {
            ArtImage(art, size = 56.dp)
            if (hasArt(art)) Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (episode.played) muted() else MaterialTheme.colorScheme.onBackground,
                )
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = muted())
            }
        }
        // Everything is a visible button on the episode itself (constitution §2b), under the picture and text so they never wrap.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (episode.downloadState) {
                    DownloadState.Done -> {
                        TextButton(modifier = Modifier.semantics { contentDescription = playLabel }, onClick = { onPlay(episode) }) { Text(stringResource(R.string.episode_play)) }
                        TextButton(modifier = Modifier.semantics { contentDescription = deleteLabel }, onClick = { onDelete(episode) }) { Text(stringResource(R.string.episode_delete_download)) }
                    }
                    DownloadState.Queued, DownloadState.Downloading ->
                        TextButton(modifier = Modifier.semantics { contentDescription = cancelLabel }, onClick = { onCancel(episode) }) { Text(stringResource(R.string.episode_cancel_download)) }
                    DownloadState.None, DownloadState.Failed ->
                        TextButton(modifier = Modifier.semantics { contentDescription = downloadLabel }, onClick = { onDownload(episode) }) { Text(stringResource(R.string.episode_download)) }
                }
                TextButton(
                modifier = Modifier.semantics { contentDescription = playedLabel },
                onClick = { if (episode.played) onMarkUnplayed(episode) else onAskMarkPlayed(episode) },
            ) {
                    Text(stringResource(if (episode.played) R.string.mark_unplayed else R.string.mark_played))
                }
        }
    }
}
