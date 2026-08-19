package com.admin.mymusicplayer.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.mymusicplayer.data.repository.FakeLibraryRepository
import com.admin.mymusicplayer.data.repository.LibraryRepository
import com.admin.mymusicplayer.data.repository.SessionRepository
import com.admin.mymusicplayer.playback.PlaybackQueueController
import com.admin.mymusicplayer.search.FakeSearchProvider
import com.admin.mymusicplayer.search.SearchPageToken
import com.admin.mymusicplayer.search.SearchProvider
import com.admin.mymusicplayer.search.SearchResultItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val nextPage: SearchPageToken? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val playlistTargets: List<Pair<Long, String>> = emptyList(),
)

sealed interface SearchEvent {
    data class QueryChanged(val value: String) : SearchEvent
    data object Submit : SearchEvent
    data object Retry : SearchEvent
    data object LoadMore : SearchEvent
    data class AddToPlaylist(val result: SearchResultItem, val playlistId: Long? = null) : SearchEvent
    data class PlayNow(val result: SearchResultItem) : SearchEvent
    data class PlayNext(val result: SearchResultItem) : SearchEvent
    data class AddToQueue(val result: SearchResultItem) : SearchEvent
    data object NoticeShown : SearchEvent
}

class SearchViewModel(
    private val provider: SearchProvider = FakeSearchProvider(),
    private val libraryRepository: LibraryRepository = FakeLibraryRepository(),
    private val sessionRepository: SessionRepository? = null,
    private val playbackController: PlaybackQueueController? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var activeSearch: Job? = null
    private var requestId = 0L

    init {
        viewModelScope.launch {
            libraryRepository.playlists.collect { playlists ->
                _state.value = _state.value.copy(
                    playlistTargets = playlists.map { it.playlist.id to it.playlist.name },
                )
            }
        }
    }

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> _state.value = _state.value.copy(query = event.value, error = null)
            SearchEvent.Submit -> submit(_state.value.query, append = false)
            SearchEvent.Retry -> submit(_state.value.submittedQuery.ifBlank { _state.value.query }, append = false)
            SearchEvent.LoadMore -> submit(_state.value.submittedQuery, append = true)
            is SearchEvent.AddToPlaylist -> addToPlaylist(event)
            is SearchEvent.PlayNow -> queueAction(
                success = "Playing now",
                persistentAction = { it.playNow(event.result.asTrack()) },
                playbackAction = { it.playNow(event.result.asTrack()) },
            )
            is SearchEvent.PlayNext -> queueAction(
                success = "Added to play next",
                persistentAction = { it.playNext(event.result.asTrack()) },
                playbackAction = { it.playNext(event.result.asTrack()) },
            )
            is SearchEvent.AddToQueue -> queueAction(
                success = "Added to queue",
                persistentAction = { it.addToQueue(event.result.asTrack()) },
                playbackAction = { it.addToQueue(event.result.asTrack()) },
            )
            SearchEvent.NoticeShown -> _state.value = _state.value.copy(notice = null)
        }
    }

    private fun submit(rawQuery: String, append: Boolean) {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            _state.value = _state.value.copy(error = "Enter a song, artist, title, or YouTube URL")
            return
        }
        val page = if (append) _state.value.nextPage else null
        if (append && page == null) return
        activeSearch?.cancel()
        val thisRequest = ++requestId
        _state.value = _state.value.copy(
            submittedQuery = query,
            results = if (append) _state.value.results else emptyList(),
            isLoading = !append,
            isLoadingMore = append,
            error = null,
            notice = null,
        )
        activeSearch = viewModelScope.launch {
            runCatching { provider.search(query, page) }
                .onSuccess { response ->
                    if (thisRequest != requestId) return@onSuccess
                    _state.value = _state.value.copy(
                        results = if (append) _state.value.results + response.items else response.items,
                        nextPage = response.nextPage,
                        isLoading = false,
                        isLoadingMore = false,
                    )
                }
                .onFailure { throwable ->
                    if (thisRequest != requestId) return@onFailure
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = throwable.message ?: "Search failed",
                    )
                }
        }
    }

    private fun addToPlaylist(event: SearchEvent.AddToPlaylist) {
        val target = event.playlistId ?: _state.value.playlistTargets.firstOrNull()?.first
        if (target == null) {
            _state.value = _state.value.copy(notice = "Create a playlist first")
            return
        }
        val targetName = _state.value.playlistTargets.firstOrNull { it.first == target }?.second ?: "playlist"
        viewModelScope.launch {
            runCatching { libraryRepository.addTrack(target, event.result.asTrack()) }
                .onSuccess { added ->
                    _state.value = _state.value.copy(
                        notice = if (added) "Added to $targetName" else "Already in $targetName",
                    )
                }
                .onFailure { _state.value = _state.value.copy(notice = it.message ?: "Add failed") }
        }
    }

    private fun queueAction(
        success: String,
        persistentAction: suspend (SessionRepository) -> Unit,
        playbackAction: suspend (PlaybackQueueController) -> Unit,
    ) {
        val playback = playbackController
        val repository = sessionRepository
        if (playback == null && repository == null) {
            _state.value = _state.value.copy(notice = "$success (fake)")
            return
        }
        viewModelScope.launch {
            runCatching {
                if (playback != null) playbackAction(playback)
                else persistentAction(requireNotNull(repository))
            }
                .onSuccess { _state.value = _state.value.copy(notice = success) }
                .onFailure { _state.value = _state.value.copy(notice = it.message ?: "Queue action failed") }
        }
    }
}
