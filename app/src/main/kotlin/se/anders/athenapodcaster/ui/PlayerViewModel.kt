package se.anders.athenapodcaster.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.anders.athenapodcaster.AthenaPodcasterApplication
import se.anders.athenapodcaster.data.db.Episode
import se.anders.athenapodcaster.data.art.ArtworkStore
import se.anders.athenapodcaster.data.download.Downloads
import se.anders.athenapodcaster.data.feeds.FeedRepository
import se.anders.athenapodcaster.data.settings.SettingsRepository
import se.anders.athenapodcaster.data.episodes.EpisodeRepository
import se.anders.athenapodcaster.playback.PlayerController
import se.anders.athenapodcaster.playback.PlayerUiState

class PlayerViewModel(
    private val player: PlayerController,
    private val episodes: EpisodeRepository,
    private val downloads: Downloads,
    private val feeds: FeedRepository,
    private val artwork: ArtworkStore,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = player.state

    /** The episode being played, so the screen can show and flip its played state (FR-014). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val current: StateFlow<Episode?> = player.state
        .map { it.episodeId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else episodes.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Starts the episode if its file exists; otherwise the episode goes back to "not downloaded" (FR-024). */
    fun play(episode: Episode, showTitle: String, feedImageUrl: String?, onStarted: () -> Unit) = viewModelScope.launch {
        val file = episodes.playableFile(episode) ?: return@launch
        val art = artwork.mediaBytes(listOf(episode.imageUrl, feedImageUrl), settings.settings.first().artStyle)
        player.play(episode, showTitle, file, art)
        onStarted()
    }

    /** Picture candidates for the player screen: the episode's own, then the show's (FR-040). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val artUrls: StateFlow<List<String?>> = current
        .flatMapLatest { e ->
            if (e == null) flowOf(emptyList())
            else feeds.observeFeed(e.feedId).map { f -> listOf(e.imageUrl, f?.imageUrl) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun togglePlayPause() = player.togglePlayPause()
    fun seekBack() = player.seekBack()
    fun seekForward() = player.seekForward()
    fun setUnplayed(episodeId: Long) = viewModelScope.launch { episodes.setPlayed(episodeId, false) }

    /** After the user confirmed: move on to the next queued episode (or stop), mark played and delete the download. */
    fun markPlayed(episodeId: Long) = viewModelScope.launch {
        player.releaseEpisode(episodeId)
        downloads.markPlayedAndDelete(episodeId)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as AthenaPodcasterApplication).container
                PlayerViewModel(c.player, c.episodes, c.downloads, c.feeds, c.artwork, c.settings)
            }
        }
    }
}
