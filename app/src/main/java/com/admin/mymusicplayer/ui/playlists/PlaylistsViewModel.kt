package com.admin.mymusicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.mymusicplayer.data.fake.FakeLibraryStore
import com.admin.mymusicplayer.domain.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaylistSummaryUi(
    val playlist: Playlist,
    val trackCount: Int,
)

data class PlaylistsUiState(
    val filter: String = "",
    val playlists: List<PlaylistSummaryUi> = emptyList(),
    val error: String? = null,
)

sealed interface PlaylistsEvent {
    data class FilterChanged(val value: String) : PlaylistsEvent
    data class Create(val name: String) : PlaylistsEvent
    data class Rename(val id: Long, val name: String) : PlaylistsEvent
    data class Delete(val id: Long) : PlaylistsEvent
}

class PlaylistsViewModel : ViewModel() {
    private val _state = MutableStateFlow(PlaylistsUiState())
    val state: StateFlow<PlaylistsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            FakeLibraryStore.records.collect { records ->
                updateList(records.map { PlaylistSummaryUi(it.playlist, it.tracks.size) })
            }
        }
    }

    fun onEvent(event: PlaylistsEvent) {
        runCatching {
            when (event) {
                is PlaylistsEvent.FilterChanged -> {
                    _state.value = _state.value.copy(filter = event.value, error = null)
                    updateList(FakeLibraryStore.records.value.map { PlaylistSummaryUi(it.playlist, it.tracks.size) })
                }
                is PlaylistsEvent.Create -> FakeLibraryStore.create(event.name)
                is PlaylistsEvent.Rename -> FakeLibraryStore.rename(event.id, event.name)
                is PlaylistsEvent.Delete -> FakeLibraryStore.delete(event.id)
            }
        }.onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    private fun updateList(all: List<PlaylistSummaryUi>) {
        val filter = _state.value.filter.trim()
        _state.value = _state.value.copy(
            playlists = if (filter.isBlank()) all else all.filter {
                it.playlist.name.contains(filter, ignoreCase = true)
            },
        )
    }
}

