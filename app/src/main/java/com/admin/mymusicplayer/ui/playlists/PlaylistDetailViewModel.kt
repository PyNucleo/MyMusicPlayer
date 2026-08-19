package com.admin.mymusicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.mymusicplayer.data.fake.FakeLibraryStore
import com.admin.mymusicplayer.data.fake.FakePlaylistRecord
import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlistId: Long,
    val name: String = "Playlist",
    val tracks: List<Track> = emptyList(),
    val selected: Set<SourceIdentity> = emptySet(),
    val targetPlaylists: List<Pair<Long, String>> = emptyList(),
    val canUndo: Boolean = false,
    val message: String? = null,
)

sealed interface PlaylistDetailEvent {
    data class ToggleSelection(val identity: SourceIdentity) : PlaylistDetailEvent
    data object SelectAll : PlaylistDetailEvent
    data object ClearSelection : PlaylistDetailEvent
    data class MoveTo(val playlistId: Long) : PlaylistDetailEvent
    data class CopyTo(val playlistId: Long) : PlaylistDetailEvent
    data object Remove : PlaylistDetailEvent
    data object Undo : PlaylistDetailEvent
    data object MessageShown : PlaylistDetailEvent
}

class PlaylistDetailViewModel(
    private val playlistId: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistDetailUiState(playlistId))
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()
    private var undoSnapshot: List<FakePlaylistRecord>? = null

    init {
        viewModelScope.launch {
            FakeLibraryStore.records.collect { records ->
                val record = records.firstOrNull { it.playlist.id == playlistId }
                _state.value = _state.value.copy(
                    name = record?.playlist?.name ?: "Missing playlist",
                    tracks = record?.tracks.orEmpty(),
                    selected = _state.value.selected.intersect(
                        record?.tracks.orEmpty().mapTo(mutableSetOf()) { it.sourceIdentity },
                    ),
                    targetPlaylists = records.filterNot { it.playlist.id == playlistId }
                        .map { it.playlist.id to it.playlist.name },
                )
            }
        }
    }

    fun onEvent(event: PlaylistDetailEvent) {
        runCatching {
            when (event) {
                is PlaylistDetailEvent.ToggleSelection -> {
                    val updated = _state.value.selected.toMutableSet().apply {
                        if (!add(event.identity)) remove(event.identity)
                    }
                    _state.value = _state.value.copy(selected = updated)
                }
                PlaylistDetailEvent.SelectAll -> _state.value = _state.value.copy(
                    selected = _state.value.tracks.mapTo(mutableSetOf()) { it.sourceIdentity },
                )
                PlaylistDetailEvent.ClearSelection -> _state.value = _state.value.copy(selected = emptySet())
                is PlaylistDetailEvent.MoveTo -> mutate("Moved ${_state.value.selected.size} tracks") {
                    FakeLibraryStore.moveSelected(playlistId, event.playlistId, _state.value.selected)
                }
                is PlaylistDetailEvent.CopyTo -> mutate("Copied ${_state.value.selected.size} tracks") {
                    FakeLibraryStore.copySelected(playlistId, event.playlistId, _state.value.selected)
                }
                PlaylistDetailEvent.Remove -> mutate("Removed ${_state.value.selected.size} tracks") {
                    FakeLibraryStore.removeSelected(playlistId, _state.value.selected)
                }
                PlaylistDetailEvent.Undo -> {
                    val snapshot = undoSnapshot ?: return
                    FakeLibraryStore.restore(snapshot)
                    undoSnapshot = null
                    _state.value = _state.value.copy(canUndo = false, message = "Undo complete")
                }
                PlaylistDetailEvent.MessageShown -> _state.value = _state.value.copy(message = null)
            }
        }.onFailure { _state.value = _state.value.copy(message = it.message) }
    }

    private inline fun mutate(message: String, action: () -> Unit) {
        undoSnapshot = FakeLibraryStore.snapshot()
        action()
        _state.value = _state.value.copy(selected = emptySet(), canUndo = true, message = message)
    }
}

