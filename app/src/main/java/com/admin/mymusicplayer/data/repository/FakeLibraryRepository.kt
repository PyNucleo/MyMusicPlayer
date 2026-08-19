package com.admin.mymusicplayer.data.repository

import com.admin.mymusicplayer.data.fake.FakeLibraryStore
import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakeLibraryRepository : LibraryRepository {
    override val playlists: Flow<List<LibraryPlaylist>> = FakeLibraryStore.records.map { records ->
        records.map { LibraryPlaylist(it.playlist, it.tracks) }
    }

    override suspend fun createPlaylist(name: String): Long {
        FakeLibraryStore.create(name)
        return FakeLibraryStore.records.value.maxOf { it.playlist.id }
    }

    override suspend fun renamePlaylist(id: Long, name: String) = FakeLibraryStore.rename(id, name)
    override suspend fun deletePlaylist(id: Long) = FakeLibraryStore.delete(id)
    override suspend fun addTrack(playlistId: Long, track: Track): Boolean =
        FakeLibraryStore.addTrack(playlistId, track)

    override suspend fun importPlaylist(name: String, tracks: List<Track>): LibraryImportResult {
        val playlistId = createPlaylist(name)
        val distinct = tracks.distinctBy { it.sourceIdentity }
        distinct.forEach { FakeLibraryStore.addTrack(playlistId, it) }
        return LibraryImportResult(playlistId, distinct.size)
    }

    override suspend fun reorderTrack(
        playlistId: Long,
        identity: SourceIdentity,
        targetIndex: Int,
    ): LibraryMutation {
        val token = undoToken(playlistId)
        FakeLibraryStore.reorderTrack(playlistId, identity, targetIndex)
        return LibraryMutation(1, token)
    }

    override suspend fun removeSelected(
        playlistId: Long,
        selected: Set<SourceIdentity>,
    ): LibraryMutation {
        val token = undoToken(playlistId)
        FakeLibraryStore.removeSelected(playlistId, selected)
        return LibraryMutation(selected.size, token)
    }

    override suspend fun copySelected(
        sourcePlaylistId: Long,
        targetPlaylistId: Long,
        selected: Set<SourceIdentity>,
    ): LibraryMutation {
        val token = undoToken(sourcePlaylistId, targetPlaylistId)
        FakeLibraryStore.copySelected(sourcePlaylistId, targetPlaylistId, selected)
        return LibraryMutation(selected.size, token)
    }

    override suspend fun moveSelected(
        sourcePlaylistId: Long,
        targetPlaylistId: Long,
        selected: Set<SourceIdentity>,
    ): LibraryMutation {
        val token = undoToken(sourcePlaylistId, targetPlaylistId)
        FakeLibraryStore.moveSelected(sourcePlaylistId, targetPlaylistId, selected)
        return LibraryMutation(selected.size, token)
    }

    override suspend fun restoreUndo(token: LibraryUndoToken) {
        val current = FakeLibraryStore.snapshot().associateBy { it.playlist.id }.toMutableMap()
        token.playlists.forEach { snapshot ->
            current[snapshot.playlist.id] = com.admin.mymusicplayer.data.fake.FakePlaylistRecord(
                snapshot.playlist,
                snapshot.tracks,
            )
        }
        FakeLibraryStore.restore(current.values.sortedBy { it.playlist.createdAtEpochMs })
    }

    private fun undoToken(vararg ids: Long): LibraryUndoToken {
        val selected = ids.toSet()
        return LibraryUndoToken(
            FakeLibraryStore.snapshot().filter { it.playlist.id in selected }
                .map { PlaylistUndoSnapshot(it.playlist, it.tracks) },
        )
    }
}
