package se.anders.athenapodcaster.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.anders.athenapodcaster.AthenaPodcasterApplication
import se.anders.athenapodcaster.data.db.EpisodeWithFeed
import se.anders.athenapodcaster.data.episodes.QueueRepository

/** The play queue on the phone (FR-100..102). */
class QueueViewModel(private val queue: QueueRepository) : ViewModel() {
    val rows: StateFlow<List<EpisodeWithFeed>> = queue.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ids: StateFlow<Set<Long>> = rows.map { list -> list.map { it.episode.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggle(episodeId: Long) = viewModelScope.launch { queue.toggle(episodeId) }
    fun remove(episodeId: Long) = viewModelScope.launch { queue.remove(episodeId) }
    fun moveUp(episodeId: Long) = viewModelScope.launch { queue.moveUp(episodeId) }
    fun moveDown(episodeId: Long) = viewModelScope.launch { queue.moveDown(episodeId) }
    fun clear() = viewModelScope.launch { queue.clear() }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { QueueViewModel((this[APPLICATION_KEY] as AthenaPodcasterApplication).container.queue) }
        }
    }
}
