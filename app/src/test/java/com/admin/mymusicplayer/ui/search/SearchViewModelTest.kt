package com.admin.mymusicplayer.ui.search

import com.admin.mymusicplayer.MainDispatcherRule
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.search.SearchPageToken
import com.admin.mymusicplayer.search.SearchProvider
import com.admin.mymusicplayer.search.SearchResultItem
import com.admin.mymusicplayer.search.SearchResultPage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rapidReplacement_staleSearchCannotOverwriteNewerResults() = runTest(mainDispatcherRule.dispatcher) {
        val provider = object : SearchProvider {
            override suspend fun search(query: String, page: SearchPageToken?): SearchResultPage {
                withContext(NonCancellable) { delay(if (query == "Radio") 1_000 else 10) }
                return SearchResultPage(
                    listOf(
                        SearchResultItem(
                            SourceType.YOUTUBE,
                            query,
                            query,
                            "tester",
                            null,
                            1_000,
                        ),
                    ),
                    null,
                )
            }
        }
        val viewModel = SearchViewModel(provider)

        viewModel.onEvent(SearchEvent.QueryChanged("Radio"))
        viewModel.onEvent(SearchEvent.Submit)
        advanceTimeBy(100)
        viewModel.onEvent(SearchEvent.QueryChanged("Radiohead"))
        viewModel.onEvent(SearchEvent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.submittedQuery).isEqualTo("Radiohead")
        assertThat(viewModel.state.value.results.single().title).isEqualTo("Radiohead")
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun failureTerminatesLoadingAndRetryCanSucceed() = runTest(mainDispatcherRule.dispatcher) {
        var shouldFail = true
        val provider = object : SearchProvider {
            override suspend fun search(query: String, page: SearchPageToken?): SearchResultPage {
                if (shouldFail) error("network unavailable")
                return SearchResultPage(emptyList(), null)
            }
        }
        val viewModel = SearchViewModel(provider)
        viewModel.onEvent(SearchEvent.QueryChanged("query"))
        viewModel.onEvent(SearchEvent.Submit)
        advanceUntilIdle()

        assertThat(viewModel.state.value.error).isEqualTo("network unavailable")
        assertThat(viewModel.state.value.isLoading).isFalse()

        shouldFail = false
        viewModel.onEvent(SearchEvent.Retry)
        advanceUntilIdle()
        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }
}
