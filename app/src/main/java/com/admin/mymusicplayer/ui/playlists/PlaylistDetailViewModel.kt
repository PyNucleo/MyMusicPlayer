package com.admin.mymusicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.mymusicplayer.data.repository.FakeLibraryRepository
import com.admin.mymusicplayer.data.repository.LibraryMutation
import com.admin.mymusicplayer.data.repository.LibraryRepository
import com.admin.mymusicplayer.data.repository.LibraryUndoToken
import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.playback.PlaybackQueueController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlistId: Long,
    val name: String = "Playlist",
    val tracks: List<Track> = emptyList(),
    val selected: Set<SourceIdentity> = emptySet(),
    val targetPlaylists: List<Pair<Long, String>> = emptyList(),
    val canUndo: Boolean = false,
    val message: String? = null,
    val filter: String = "",
) {
    val visibleTracks: List<Track>
        get() {
            val clean = filter.trim()
            return if (clean.isBlank()) tracks else tracks.filter { track ->
                track.title.contains(clean, ignoreCase = true) ||
                    track.uploader?.contains(clean, ignoreCase = true) == true
            }
        }
}

sealed interface PlaylistDetailEvent {
    data class ToggleSelection(val identity: SourceIdentity) : PlaylistDetailEvent
    data object SelectAll : PlaylistDetailEvent
    data object ClearSelection : PlaylistDetailEvent
    data class MoveTo(val playlistId: Long) : PlaylistDetailEvent
    data class CopyTo(val playlistId: Long) : PlaylistDetailEvent
    data object Remove : PlaylistDetailEvent
    data object Undo : PlaylistDetailEvent
    data class PlayTrack(val track: Track) : PlaylistDetailEvent
    data class FilterChanged(val value: String) : PlaylistDetailEvent
    data class Reorder(val identity: SourceIdentity, val targetIndex: Int) : PlaylistDetailEvent
    data object MessageShown : PlaylistDetailEvent
}

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val repository: LibraryRepository = FakeLibraryRepository(),
    private val playbackController: PlaybackQueueController? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistDetailUiState(playlistId))
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()
    private var undoToken: LibraryUndoToken? = null

    init {
        viewModelScope.launch {
            repository.playlists.collect { playlists ->
                val record = playlists.firstOrNull { it.playlist.id == playlistId }
                _state.value = _state.value.copy(
                    name = record?.playlist?.name ?: "Missing playlist",
                    tracks = record?.tracks.orEmpty(),
                    selected = _state.value.selected.intersect(
                        record?.tracks.orEmpty().mapTo(mutableSetOf()) { it.sourceIdentity },
                    ),
                    targetPlaylists = playlists.filterNot { it.playlist.id == playlistId }
                        .map { it.playlist.id to it.playlist.name },
                )
            }
        }
    }

    fun onEvent(event: PlaylistDetailEvent) {
        when (event) {
            is PlaylistDetailEvent.ToggleSelection -> {
                val updated = _state.value.selected.toMutableSet().apply {
                    if (!add(event.identity)) remove(event.identity)
                }
                _state.value = _state.value.copy(selected = updated)
            }
            PlaylistDetailEvent.SelectAll -> _state.value = _state.value.copy(
                selected = _state.value.visibleTracks.mapTo(mutableSetOf()) { it.sourceIdentity },
            )
            PlaylistDetailEvent.ClearSelection -> _state.value = _state.value.copy(selected = emptySet())
            is PlaylistDetailEvent.MoveTo -> mutate("Moved") {
                repository.moveSelected(playlistId, event.playlistId, it)
            }
            is PlaylistDetailEvent.CopyTo -> mutate("Copied") {
                repository.copySelected(playlistId, event.playlistId, it)
            }
            PlaylistDetailEvent.Remove -> mutate("Removed") {
                repository.removeSelected(playlistId, it)
            }
            PlaylistDetailEvent.Undo -> undo()
            is PlaylistDetailEvent.PlayTrack -> playTrack(event.track)
            is PlaylistDetailEvent.FilterChanged -> _state.value = _state.value.copy(filter = event.value)
            is PlaylistDetailEvent.Reorder -> reorder(event)
            PlaylistDetailEvent.MessageShown -> _state.value = _state.value.copy(message = null)
        }
    }

    private fun reorder(event: PlaylistDetailEvent.Reorder) {
        viewModelScope.launch {
            runCatching { repository.reorderTrack(playlistId, event.identity, event.targetIndex) }
                .onSuccess { mutation ->
                    undoToken = mutation.undoToken
                    _state.value = _state.value.copy(canUndo = true, message = "Track reordered")
                }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Reorder failed") }
        }
    }

    private fun playTrack(track: Track) {
        val controller = playbackController ?: return
        val current = _state.value
        val index = current.tracks.indexOfFirst { it.sourceIdentity == track.sourceIdentity }
        if (index < 0) return
        viewModelScope.launch {
            val playlist = repository.playlists.first()
                .firstOrNull { it.playlist.id == playlistId }?.playlist ?: return@launch
            runCatching {
                controller.playPlaylist(
                    tracks = current.tracks,
                    selectedIndex = index,
                    playlistId = playlistId,
                    playlistRevision = playlist.revision,
                )
            }.onSuccess {
                _state.value = _state.value.copy(message = "Playing ${track.title}")
            }.onFailure {
                _state.value = _state.value.copy(message = it.message ?: "Playback failed")
            }
        }
    }

    private fun mutate(
        verb: String,
        action: suspend (Set<SourceIdentity>) -> LibraryMutation,
    ) {
        val selected = _state.value.selected
        viewModelScope.launch {
            runCatching { action(selected) }
                .onSuccess { mutation ->
                    undoToken = mutation.undoToken
                    _state.value = _state.value.copy(
                        selected = emptySet(),
                        canUndo = true,
                        message = "$verb ${mutation.affectedCount} tracks",
                    )
                }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Playlist action failed") }
        }
    }

    private fun undo() {
        val token = undoToken ?: return
        viewModelScope.launch {
            runCatching { repository.restoreUndo(token) }
                .onSuccess {
                    undoToken = null
                    _state.value = _state.value.copy(canUndo = false, message = "Undo complete")
                }
                .onFailure { _state.value = _state.value.copy(message = it.message ?: "Undo failed") }
        }
    }
}
