package com.admin.mymusicplayer.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.mymusicplayer.data.repository.FakeLibraryRepository
import com.admin.mymusicplayer.data.repository.LibraryPlaylist
import com.admin.mymusicplayer.data.repository.LibraryRepository
import com.admin.mymusicplayer.domain.Playlist
import com.admin.mymusicplayer.importer.PlaylistImportPreview
import com.admin.mymusicplayer.importer.PlaylistImporter
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
    val importBusy: Boolean = false,
    val pendingImport: PlaylistImportPreview? = null,
    val message: String? = null,
)

sealed interface PlaylistsEvent {
    data class FilterChanged(val value: String) : PlaylistsEvent
    data class Create(val name: String) : PlaylistsEvent
    data class Rename(val id: Long, val name: String) : PlaylistsEvent
    data class Delete(val id: Long) : PlaylistsEvent
}

class PlaylistsViewModel(
    private val repository: LibraryRepository = FakeLibraryRepository(),
    private val playlistImporter: PlaylistImporter? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaylistsUiState())
    val state: StateFlow<PlaylistsUiState> = _state.asStateFlow()
    private var allPlaylists: List<LibraryPlaylist> = emptyList()

    init {
        viewModelScope.launch {
            repository.playlists.collect { playlists ->
                allPlaylists = playlists
                updateList()
            }
        }
    }

    fun inspectImport(url: String) {
        val importer = playlistImporter ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(importBusy = true, error = null, message = null)
            runCatching { importer.inspect(url) }
                .onSuccess { preview ->
                    _state.value = _state.value.copy(importBusy = false, pendingImport = preview)
                }
                .onFailure {
                    _state.value = _state.value.copy(importBusy = false, error = it.message ?: "Import failed")
                }
        }
    }

    fun confirmImport() {
        val preview = _state.value.pendingImport ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(importBusy = true, error = null)
            runCatching { repository.importPlaylist(preview.suggestedName, preview.tracks) }
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        importBusy = false,
                        pendingImport = null,
                        message = "Imported ${result.importedCount} tracks; skipped ${preview.skippedCount}",
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(importBusy = false, error = it.message ?: "Import failed")
                }
        }
    }

    fun cancelImport() {
        _state.value = _state.value.copy(pendingImport = null)
    }

    fun onEvent(event: PlaylistsEvent) {
        if (event is PlaylistsEvent.FilterChanged) {
            _state.value = _state.value.copy(filter = event.value, error = null)
            updateList()
            return
        }
        viewModelScope.launch {
            runCatching {
                when (event) {
                    is PlaylistsEvent.Create -> repository.createPlaylist(event.name)
                    is PlaylistsEvent.Rename -> repository.renamePlaylist(event.id, event.name)
                    is PlaylistsEvent.Delete -> repository.deletePlaylist(event.id)
                    is PlaylistsEvent.FilterChanged -> Unit
                }
            }.onFailure { _state.value = _state.value.copy(error = it.message ?: "Playlist action failed") }
                .onSuccess { _state.value = _state.value.copy(error = null) }
        }
    }

    private fun updateList() {
        val all = allPlaylists.map { PlaylistSummaryUi(it.playlist, it.tracks.size) }
        val filter = _state.value.filter.trim()
        _state.value = _state.value.copy(
            playlists = if (filter.isBlank()) all else all.filter {
                it.playlist.name.contains(filter, ignoreCase = true)
            }
            ,
        )
    }
}
