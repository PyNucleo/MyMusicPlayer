package com.admin.mymusicplayer.data.repository

import com.admin.mymusicplayer.domain.Playlist
import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.Track
import kotlinx.coroutines.flow.Flow

data class LibraryPlaylist(
    val playlist: Playlist,
    val tracks: List<Track>,
)

data class PlaylistUndoSnapshot(
    val playlist: Playlist,
    val tracks: List<Track>,
)

data class LibraryUndoToken(
    val playlists: List<PlaylistUndoSnapshot>,
)

data class LibraryMutation(
    val affectedCount: Int,
    val undoToken: LibraryUndoToken,
)

data class LibraryImportResult(
    val playlistId: Long,
    val importedCount: Int,
)

interface LibraryRepository {
    val playlists: Flow<List<LibraryPlaylist>>

    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(id: Long, name: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun addTrack(playlistId: Long, track: Track): Boolean
    suspend fun importPlaylist(name: String, tracks: List<Track>): LibraryImportResult
    suspend fun reorderTrack(
        playlistId: Long,
        identity: SourceIdentity,
        targetIndex: Int,
    ): LibraryMutation
    suspend fun removeSelected(playlistId: Long, selected: Set<SourceIdentity>): LibraryMutation
    suspend fun copySelected(
        sourcePlaylistId: Long,
        targetPlaylistId: Long,
        selected: Set<SourceIdentity>,
    ): LibraryMutation
    suspend fun moveSelected(
        sourcePlaylistId: Long,
        targetPlaylistId: Long,
        selected: Set<SourceIdentity>,
    ): LibraryMutation
    suspend fun restoreUndo(token: LibraryUndoToken)
}
