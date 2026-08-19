package com.admin.mymusicplayer.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.mymusicplayer.data.fake.FakeLibraryStore
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
)

sealed interface SearchEvent {
    data class QueryChanged(val value: String) : SearchEvent
    data object Submit : SearchEvent
    data object Retry : SearchEvent
    data object LoadMore : SearchEvent
    data class AddToPlaylist(val result: SearchResultItem, val playlistId: Long = 1) : SearchEvent
    data object NoticeShown : SearchEvent
}

class SearchViewModel(
    private val provider: SearchProvider = FakeSearchProvider(),
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var activeSearch: Job? = null
    private var requestId = 0L

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> _state.value = _state.value.copy(query = event.value, error = null)
            SearchEvent.Submit -> submit(_state.value.query, append = false)
            SearchEvent.Retry -> submit(_state.value.submittedQuery.ifBlank { _state.value.query }, append = false)
            SearchEvent.LoadMore -> submit(_state.value.submittedQuery, append = true)
            is SearchEvent.AddToPlaylist -> addToPlaylist(event)
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
        val added = FakeLibraryStore.addTrack(event.playlistId, event.result.asTrack())
        _state.value = _state.value.copy(
            notice = if (added) "Added to Road Trip" else "Already in Road Trip",
        )
    }
}

