package com.admin.mymusicplayer.source.newpipe

import com.admin.mymusicplayer.domain.Availability
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.diagnostics.DiagnosticLogger
import com.admin.mymusicplayer.importer.PlaylistImportPreview
import com.admin.mymusicplayer.importer.PlaylistImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import kotlin.coroutines.coroutineContext

class NewPipePlaylistImporter(
    @Suppress("UNUSED_PARAMETER") runtime: NewPipeRuntime,
    private val diagnostics: DiagnosticLogger? = null,
) : PlaylistImporter {
    override suspend fun inspect(url: String): PlaylistImportPreview = withContext(Dispatchers.IO) {
        diagnostics?.log("PLAYLIST_IMPORT_STARTED")
        runCatching { inspectBlocking(url.trim()) }
            .onSuccess { diagnostics?.log("PLAYLIST_IMPORT_RESOLVED", "${it.tracks.size} tracks") }
            .getOrElse {
                val mapped = it.asSourceFailure("Playlist import")
                diagnostics?.log("PLAYLIST_IMPORT_FAILED", mapped.kind.name, mapped)
                throw mapped
            }
    }

    private suspend fun inspectBlocking(url: String): PlaylistImportPreview {
        require(url.isNotBlank()) { "Enter a public YouTube playlist URL" }
        val service = ServiceList.YouTube
        require(service.playlistLHFactory.acceptUrl(url)) { "Enter a valid public YouTube playlist URL" }
        val extractor = service.getPlaylistExtractor(url)
        extractor.fetchPage()
        val tracks = linkedMapOf<String, Track>()
        var skipped = 0
        var page = extractor.initialPage
        var pageCount = 0
        while (true) {
            coroutineContext.ensureActive()
            page.errors.forEach { skipped++ }
            page.items.forEach { item ->
                val mapped = runCatching { item.toTrack() }.getOrNull()
                if (mapped == null) {
                    skipped++
                } else if (mapped.sourceMediaId in tracks) {
                    skipped++
                } else {
                    tracks[mapped.sourceMediaId] = mapped
                }
            }
            val next = page.nextPage?.takeIf(Page::isValid) ?: break
            require(++pageCount < MAX_PAGES) { "Playlist pagination exceeded the safety limit" }
            page = extractor.getPage(next)
        }
        return PlaylistImportPreview(
            suggestedName = extractor.name.ifBlank { "Imported YouTube playlist" },
            tracks = tracks.values.toList(),
            skippedCount = skipped,
        )
    }

    private fun StreamInfoItem.toTrack(): Track {
        val service = ServiceList.YouTube
        return Track(
            sourceType = SourceType.YOUTUBE,
            sourceMediaId = service.streamLHFactory.getId(url),
            title = name,
            uploader = uploaderName,
            durationMs = duration.takeIf { it >= 0 }?.times(1_000),
            thumbnailUrl = thumbnails.maxByOrNull { it.width * it.height }?.url,
            availability = if (contentAvailability == ContentAvailability.AVAILABLE) {
                Availability.AVAILABLE
            } else {
                Availability.UNKNOWN
            },
        )
    }

    private companion object {
        const val MAX_PAGES = 10_000
    }
}
