package com.admin.mymusicplayer.search

import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track

@JvmInline
value class SearchPageToken(val value: String)

data class SearchResultItem(
    val sourceType: SourceType,
    val sourceMediaId: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationMs: Long?,
) {
    fun asTrack(): Track = Track(
        sourceType = sourceType,
        sourceMediaId = sourceMediaId,
        title = title,
        uploader = uploader,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
    )
}

data class SearchResultPage(
    val items: List<SearchResultItem>,
    val nextPage: SearchPageToken?,
)

interface SearchProvider {
    suspend fun search(query: String, page: SearchPageToken? = null): SearchResultPage
}

