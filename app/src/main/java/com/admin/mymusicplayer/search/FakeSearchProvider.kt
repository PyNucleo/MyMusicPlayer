package com.admin.mymusicplayer.search

import com.admin.mymusicplayer.domain.SourceType
import kotlinx.coroutines.delay

class FakeSearchProvider(
    private val delayMs: Long = 250,
) : SearchProvider {
    override suspend fun search(query: String, page: SearchPageToken?): SearchResultPage {
        delay(delayMs)
        val normalized = query.trim()
        require(normalized.isNotBlank()) { "Enter a search term" }
        if (normalized.equals("fail", ignoreCase = true)) {
            error("Deterministic fake failure. Tap Retry to try again.")
        }
        val pageNumber = page?.value?.toIntOrNull() ?: 0
        val offset = pageNumber * PAGE_SIZE
        val slug = normalized.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .ifBlank { "query" }
        val items = (offset until offset + PAGE_SIZE).map { index ->
            SearchResultItem(
                sourceType = SourceType.YOUTUBE,
                sourceMediaId = "fake-$slug-$index",
                title = "$normalized — Result ${index + 1}",
                uploader = "Demo channel ${(index % 3) + 1}",
                thumbnailUrl = null,
                durationMs = 150_000L + index * 5_000L,
            )
        }
        return SearchResultPage(
            items = items,
            nextPage = if (pageNumber < 1) SearchPageToken((pageNumber + 1).toString()) else null,
        )
    }

    private companion object {
        const val PAGE_SIZE = 6
    }
}

