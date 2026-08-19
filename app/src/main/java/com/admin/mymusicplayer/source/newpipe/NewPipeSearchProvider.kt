package com.admin.mymusicplayer.source.newpipe

import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.diagnostics.DiagnosticLogger
import com.admin.mymusicplayer.search.SearchPageToken
import com.admin.mymusicplayer.search.SearchProvider
import com.admin.mymusicplayer.search.SearchResultItem
import com.admin.mymusicplayer.search.SearchResultPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.LinkedHashMap
import java.util.UUID

class NewPipeSearchProvider(
    @Suppress("UNUSED_PARAMETER") runtime: NewPipeRuntime,
    private val diagnostics: DiagnosticLogger? = null,
) : SearchProvider {
    private val continuations = LinkedHashMap<String, Continuation>()

    override suspend fun search(query: String, page: SearchPageToken?): SearchResultPage =
        withContext(Dispatchers.IO) {
            diagnostics?.log("SEARCH_STARTED", if (page == null) "initial" else "continuation")
            runCatching { searchBlocking(query.trim(), page) }
                .onSuccess { diagnostics?.log("SEARCH_SUCCESS", "${it.items.size} results") }
                .getOrElse {
                    val mapped = it.asSourceFailure("YouTube search")
                    diagnostics?.log("SEARCH_FAILED", mapped.kind.name, mapped)
                    throw mapped
                }
        }

    private fun searchBlocking(query: String, pageToken: SearchPageToken?): SearchResultPage {
        require(query.isNotBlank()) { "Enter a search term" }
        val service = ServiceList.YouTube
        if (pageToken == null && isStreamUrl(service, query)) {
            val info = StreamInfo.getInfo(service, query)
            return SearchResultPage(listOf(info.toSearchResult(service)), null)
        }
        val extractor = service.getSearchExtractor(query)
        val itemsPage = if (pageToken == null) {
            extractor.fetchPage()
            extractor.initialPage
        } else {
            val continuation = synchronized(continuations) { continuations.remove(pageToken.value) }
                ?: error("Search page expired. Run the search again.")
            require(continuation.query == query) { "Search page belongs to another query" }
            extractor.getPage(continuation.page)
        }
        return itemsPage.toSearchResultPage(service, query)
    }

    private fun ListExtractor.InfoItemsPage<*>.toSearchResultPage(
        service: StreamingService,
        query: String,
    ): SearchResultPage {
        val mapped = items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            runCatching { item.toSearchResult(service) }.getOrNull()
        }
        val token = nextPage?.takeIf(Page::isValid)?.let { next ->
            val value = UUID.randomUUID().toString()
            synchronized(continuations) {
                continuations[value] = Continuation(query, next)
                while (continuations.size > MAX_CONTINUATIONS) {
                    continuations.remove(continuations.keys.first())
                }
            }
            SearchPageToken(value)
        }
        if (mapped.isEmpty() && errors.isNotEmpty()) throw errors.first()
        return SearchResultPage(mapped, token)
    }

    private fun StreamInfoItem.toSearchResult(service: StreamingService) = SearchResultItem(
        sourceType = SourceType.YOUTUBE,
        sourceMediaId = service.streamLHFactory.getId(url),
        title = name,
        uploader = uploaderName,
        thumbnailUrl = thumbnails.maxByOrNull { it.width * it.height }?.url,
        durationMs = duration.takeIf { it >= 0 }?.times(1_000),
    )

    private fun StreamInfo.toSearchResult(service: StreamingService) = SearchResultItem(
        sourceType = SourceType.YOUTUBE,
        sourceMediaId = service.streamLHFactory.getId(url),
        title = name,
        uploader = uploaderName,
        thumbnailUrl = thumbnails.maxByOrNull { it.width * it.height }?.url,
        durationMs = duration.takeIf { it >= 0 }?.times(1_000),
    )

    private fun isStreamUrl(service: StreamingService, query: String): Boolean =
        runCatching { service.streamLHFactory.acceptUrl(query) }.getOrDefault(false)

    private data class Continuation(val query: String, val page: Page)

    private companion object {
        const val MAX_CONTINUATIONS = 64
    }
}
