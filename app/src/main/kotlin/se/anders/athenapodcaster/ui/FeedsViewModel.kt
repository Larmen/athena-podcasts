package se.anders.athenapodcaster.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import se.anders.athenapodcaster.data.db.DownloadState
import se.anders.athenapodcaster.domain.EpisodeFilter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.anders.athenapodcaster.AthenaPodcasterApplication
import se.anders.athenapodcaster.data.art.ArtworkStore
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.db.Feed
import se.anders.athenapodcaster.data.db.FeedSummary
import se.anders.athenapodcaster.data.episodes.EpisodeRepository
import se.anders.athenapodcaster.data.feeds.AddResult
import se.anders.athenapodcaster.data.feeds.FeedRepository
import se.anders.athenapodcaster.data.feeds.FetchErrorKind
import se.anders.athenapodcaster.data.feeds.Opml
import se.anders.athenapodcaster.data.feeds.OpmlFeed
import se.anders.athenapodcaster.data.download.Downloads
import se.anders.athenapodcaster.data.download.NetworkPolicy
import se.anders.athenapodcaster.data.refresh.Sync
import se.anders.athenapodcaster.data.settings.SettingsRepository
import se.anders.athenapodcaster.playback.PlayerController

/** How many unplayed episodes a feed has, and how many of those have a download (or one under way). */
data class MarkAllInfo(val unplayed: Int, val withDownloads: Int)

sealed interface AddState {
    data object Idle : AddState
    data object Working : AddState
    data class Error(val message: AddError) : AddState
}

enum class AddError { InvalidUrl, Network, Dns, Timeout, Tls, AuthFailed, Http, NotAFeed, TooLarge, AlreadySubscribed, PrivateNeedsHttps }

/** Result of an OPML import, shown as one plain line (FR-004). */
data class OpmlOutcome(val added: Int = 0, val skipped: Int = 0, val failed: Int = 0, val notOpml: Boolean = false, val couldNotRead: Boolean = false, val exported: Boolean = false, val exportFailed: Boolean = false)

class FeedsViewModel(
    private val repo: FeedRepository,
    private val episodeRepo: EpisodeRepository,
    private val downloads: Downloads,
    private val sync: Sync,
    private val network: NetworkPolicy,
    private val settings: SettingsRepository,
    private val artwork: ArtworkStore,
    private val player: PlayerController,
) : ViewModel() {
    val feeds: StateFlow<List<FeedSummary>> = repo.observeFeeds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedFeedId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedFeed: StateFlow<Feed?> = selectedFeedId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeFeed(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** FR-014: the episode list shows everything, only downloaded episodes, or only unplayed ones. */
    private val _filter = MutableStateFlow(EpisodeFilter.All)
    val filter: StateFlow<EpisodeFilter> = _filter

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allEpisodes: StateFlow<List<Episode>> = selectedFeedId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeEpisodes(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedEpisodes: StateFlow<List<Episode>> = combine(allEpisodes, _filter) { list, f -> f.apply(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What "Mark all as played" will touch, counted over the whole feed and not just what the filter shows,
     * so the confirmation always tells the truth.
     */
    val markAllInfo: StateFlow<MarkAllInfo> = allEpisodes
        .map { list ->
            val unplayed = list.filter { !it.played }
            MarkAllInfo(unplayed.size, unplayed.count { it.downloadState != DownloadState.None && it.downloadState != DownloadState.Failed })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarkAllInfo(0, 0))

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _addState = MutableStateFlow<AddState>(AddState.Idle)
    val addState: StateFlow<AddState> = _addState

    private val _opml = MutableStateFlow<OpmlOutcome?>(null)
    val opmlOutcome: StateFlow<OpmlOutcome?> = _opml
    private val _opmlProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val opmlProgress: StateFlow<Pair<Int, Int>?> = _opmlProgress

    fun select(feedId: Long?) { selectedFeedId.value = feedId; _revealed.value = null }

    fun nextFilter() { _filter.value = _filter.value.next() }

    fun setUnplayed(episode: Episode) = viewModelScope.launch { episodeRepo.setPlayed(episode.id, false) }

    /** After the user confirmed: every unplayed episode of the feed is played and its download removed (FR-014). */
    fun markAllPlayed(feedId: Long) = viewModelScope.launch {
        val changed = episodeRepo.getByFeed(feedId).filter { !it.played }.map { it.id }
        if (player.state.value.episodeId in changed) player.stopAndClear() // its file is about to go
        downloads.markAllPlayedAndDelete(feedId)
    }

    /** After the user confirmed: played, and the downloaded file goes. */
    fun markPlayed(episode: Episode) = viewModelScope.launch { downloads.markPlayedAndDelete(episode.id) }

    /** An episode waiting for the user to confirm a download over mobile data (FR-033). */
    private val _askMobile = MutableStateFlow<Episode?>(null)
    val askMobile: StateFlow<Episode?> = _askMobile

    /** FR-012: the Download button. On a metered network with "Wi-Fi only" on, ask first instead of downloading. */
    fun download(episode: Episode) = viewModelScope.launch {
        if (settings.settings.first().wifiOnly && !network.isUnmetered()) _askMobile.value = episode
        else downloads.download(episode, manual = true)
    }

    fun confirmMobile() = viewModelScope.launch {
        val episode = _askMobile.value ?: return@launch
        _askMobile.value = null
        downloads.download(episode, manual = true, allowMobile = true)
    }

    fun dismissMobile() { _askMobile.value = null }
    fun cancelDownload(episode: Episode) = viewModelScope.launch { downloads.cancel(episode) }
    fun deleteDownload(episode: Episode) = viewModelScope.launch { downloads.deleteFile(episode) }

    /** The real address of a private feed, shown only while the user asks for it (constitution §4). */
    private val _revealed = MutableStateFlow<String?>(null)
    val revealedAddress: StateFlow<String?> = _revealed

    fun toggleAddress(feed: Feed) {
        _revealed.value = if (_revealed.value != null) null else repo.addressOf(feed)
    }

    fun resetAdd() { _addState.value = AddState.Idle }

    fun add(url: String, isPrivate: Boolean, onAdded: (Long) -> Unit) = viewModelScope.launch {
        _addState.value = AddState.Working
        when (val r = repo.add(url, isPrivate)) {
            is AddResult.Added -> { _addState.value = AddState.Idle; onAdded(r.feedId); sync.afterAdd(r.feedId) }
            AddResult.AlreadySubscribed -> _addState.value = AddState.Error(AddError.AlreadySubscribed)
            AddResult.PrivateNeedsHttps -> _addState.value = AddState.Error(AddError.PrivateNeedsHttps)
            is AddResult.Failed -> _addState.value = AddState.Error(r.kind.toAddError())
        }
    }

    fun refreshAll() = viewModelScope.launch {
        if (_refreshing.value) return@launch
        _refreshing.value = true
        try { sync.refreshAll() } finally { _refreshing.value = false }
    }

    fun refresh(feedId: Long) = viewModelScope.launch {
        if (_refreshing.value) return@launch
        _refreshing.value = true
        try { sync.refresh(feedId) } finally { _refreshing.value = false }
    }

    fun remove(feedId: Long) = viewModelScope.launch {
        val urls = listOf(repo.feed(feedId)?.imageUrl) + episodeRepo.getByFeed(feedId).map { it.imageUrl }
        downloads.deleteFeedFiles(feedId)
        repo.remove(feedId)
        artwork.delete(urls)
    }

    fun importOpml(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _opml.value = null
        val entries: List<OpmlFeed> = try {
            withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { Opml.read(it) } }
        } catch (_: Opml.OpmlException) {
            _opml.value = OpmlOutcome(notOpml = true); return@launch
        } catch (_: Exception) {
            _opml.value = OpmlOutcome(couldNotRead = true); return@launch
        }
        var added = 0; var skipped = 0; var failed = 0
        entries.forEachIndexed { i, entry ->
            _opmlProgress.value = (i + 1) to entries.size
            when (val r = repo.add(entry.url, isPrivate = false)) {
                is AddResult.Added -> { added++; sync.afterAdd(r.feedId) }
                AddResult.AlreadySubscribed -> skipped++
                else -> failed++
            }
        }
        _opmlProgress.value = null
        _opml.value = OpmlOutcome(added = added, skipped = skipped, failed = failed)
    }

    /** Private feeds are left out unless the user chose to include them (FR-004, constitution §4). */
    fun exportOpml(resolver: ContentResolver, uri: Uri, includePrivate: Boolean) = viewModelScope.launch {
        try {
            val list = repo.allFeeds().filter { includePrivate || !it.isPrivate }.mapNotNull { f -> repo.addressOf(f)?.let { OpmlFeed(f.title, it) } }
            val text = Opml.write(list)
            withContext(Dispatchers.IO) { resolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
            _opml.value = OpmlOutcome(exported = true)
        } catch (_: Exception) {
            _opml.value = OpmlOutcome(exportFailed = true)
        }
    }

    private fun FetchErrorKind.toAddError() = when (this) {
        FetchErrorKind.InvalidUrl -> AddError.InvalidUrl
        FetchErrorKind.Network -> AddError.Network
        FetchErrorKind.Dns -> AddError.Dns
        FetchErrorKind.Timeout -> AddError.Timeout
        FetchErrorKind.Tls -> AddError.Tls
        FetchErrorKind.AuthFailed -> AddError.AuthFailed
        FetchErrorKind.Http -> AddError.Http
        FetchErrorKind.NotAFeed -> AddError.NotAFeed
        FetchErrorKind.TooLarge -> AddError.TooLarge
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AthenaPodcasterApplication).container
                FeedsViewModel(c.feeds, c.episodes, c.downloads, c.sync, c.networkPolicy, c.settings, c.artwork, c.player)
            }
        }
    }
}
