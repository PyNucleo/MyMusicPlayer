package com.admin.mymusicplayer.importer

import com.admin.mymusicplayer.domain.Track

data class PlaylistImportPreview(
    val suggestedName: String,
    val tracks: List<Track>,
    val skippedCount: Int,
)

interface PlaylistImporter {
    suspend fun inspect(url: String): PlaylistImportPreview
}
