package com.admin.mymusicplayer.data.repository

import androidx.room.withTransaction
import com.admin.mymusicplayer.data.dao.MusicDao
import com.admin.mymusicplayer.data.database.LibraryRow
import com.admin.mymusicplayer.data.database.MusicDatabase
import com.admin.mymusicplayer.data.database.PlaylistEntity
import com.admin.mymusicplayer.data.database.PlaylistEntryEntity
import com.admin.mymusicplayer.data.database.PlaylistItemRow
import com.admin.mymusicplayer.data.database.TrackEntity
import com.admin.mymusicplayer.domain.Availability
import com.admin.mymusicplayer.domain.Playlist
import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLibraryRepository(
    private val database: MusicDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : LibraryRepository {
    private val dao: MusicDao = database.musicDao()

    override val playlists: Flow<List<LibraryPlaylist>> = dao.observeLibrary().map(::mapLibraryRows)

    override suspend fun createPlaylist(name: String): Long = database.withTransaction {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Playlist name cannot be blank" }
        val time = now()
        dao.insertPlaylist(PlaylistEntity(name = clean, createdAtEpochMs = time, modifiedAtEpochMs = time, revision = 0))
    }

    override suspend fun renamePlaylist(id: Long, name: String) = database.withTransaction {
        val clean = name.trim()
        require(clean.isNotBlank()) { "Playlist name cannot be blank" }
        val playlist = requirePlaylist(id)
        dao.updatePlaylist(playlist.copy(name = clean, revision = playlist.revision + 1, modifiedAtEpochMs = now()))
    }

    override suspend fun deletePlaylist(id: Long) = database.withTransaction {
        dao.deletePlaylist(requirePlaylist(id))
    }

    override suspend fun addTrack(playlistId: Long, track: Track): Boolean = database.withTransaction {
        val playlist = requirePlaylist(playlistId)
        val trackId = ensureTrack(track)
        if (dao.containsTrack(playlistId, trackId)) return@withTransaction false
        dao.insertPlaylistEntries(
            listOf(
                PlaylistEntryEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = dao.nextPlaylistPosition(playlistId),
                    addedAtEpochMs = now(),
                ),
            ),
        )
        touch(playlist)
        true
    }

    override suspend fun importPlaylist(name: String, tracks: List<Track>): LibraryImportResult =
        database.withTransaction {
            val clean = name.trim()
            require(clean.isNotBlank()) { "Playlist name cannot be blank" }
            val distinct = tracks.distinctBy { it.sourceIdentity }
            val timestamp = now()
            val playlistId = dao.insertPlaylist(
                PlaylistEntity(
                    name = clean,
                    createdAtEpochMs = timestamp,
                    modifiedAtEpochMs = timestamp,
                    revision = if (distinct.isEmpty()) 0 else 1,
                ),
            )
            val entries = distinct.mapIndexed { index, track ->
                PlaylistEntryEntity(
                    playlistId = playlistId,
                    trackId = ensureTrack(track),
                    position = index,
                    addedAtEpochMs = timestamp,
                )
            }
            if (entries.isNotEmpty()) dao.insertPlaylistEntries(entries)
            LibraryImportResult(playlistId, distinct.size)
        }

    override suspend fun reorderTrack(
        playlistId: Long,
        identity: SourceIdentity,
        targetIndex: Int,
    ): LibraryMutation = database.withTransaction {
        val playlist = requirePlaylist(playlistId)
        val items = dao.getPlaylistItems(playlistId)
        val fromIndex = items.indexOfFirst { it.identity == identity }
        require(fromIndex >= 0) { "Track is no longer in this playlist" }
        require(targetIndex in items.indices) { "Target position is outside the playlist" }
        val undo = LibraryUndoToken(listOf(snapshot(playlistId)))
        val reordered = items.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(targetIndex, moved)
        replacePlaylistItems(playlist, reordered)
        LibraryMutation(1, undo)
    }

    override suspend fun removeSelected(
        playlistId: Long,
        selected: Set<SourceIdentity>,
    ): LibraryMutation = database.withTransaction {
        require(selected.isNotEmpty()) { "No tracks selected" }
        val snapshot = snapshot(playlistId)
        val remaining = dao.getPlaylistItems(playlistId).filterNot { it.identity in selected }
        val affected = snapshot.tracks.size - remaining.size
        require(affected > 0) { "Selected tracks are no longer in this playlist" }
        replacePlaylistItems(requirePlaylist(playlistId), remaining)
        LibraryMutation(affected, LibraryUndoToken(listOf(snapshot)))
    }

    override suspend fun copySelected(
        sourcePlaylistId: Long,
        targetPlaylistId: Long,
        selected: Set<SourceIdentity>,
    ): LibraryMutation = transfer(sourcePlaylistId, targetPlaylistId, selected, removeFromSource = false)

    override suspend fun moveSelected(
        sourcePlaylistId: Long,
        targetPlaylistId: Long,
        selected: Set<SourceIdentity>,
    ): LibraryMutation = transfer(sourcePlaylistId, targetPlaylistId, selected, removeFromSource = true)

    override suspend fun restoreUndo(token: LibraryUndoToken) = database.withTransaction {
        token.playlists.forEach { restoreSnapshot(it) }
    }

    suspend fun libraryCounts(): Triple<Int, Int, Int> = database.withTransaction {
        Triple(dao.trackCount(), dao.playlistCount(), dao.playlistEntryCount())
    }

    private suspend fun transfer(
        sourcePlaylistId: Long,
        targetPlaylistId: Long,
        selected: Set<SourceIdentity>,
        removeFromSource: Boolean,
    ): LibraryMutation = database.withTransaction {
        require(sourcePlaylistId != targetPlaylistId) { "Choose another playlist" }
        require(selected.isNotEmpty()) { "No tracks selected" }
        val sourcePlaylist = requirePlaylist(sourcePlaylistId)
        val targetPlaylist = requirePlaylist(targetPlaylistId)
        val sourceItems = dao.getPlaylistItems(sourcePlaylistId)
        val targetItems = dao.getPlaylistItems(targetPlaylistId)
        val selectedInSource = sourceItems.filter { it.identity in selected }
        require(selectedInSource.isNotEmpty()) { "Selected tracks are no longer in this playlist" }
        val targetIdentities = targetItems.mapTo(mutableSetOf()) { it.identity }
        val appended = selectedInSource.filter { targetIdentities.add(it.identity) }
        val undo = LibraryUndoToken(listOf(snapshot(sourcePlaylistId), snapshot(targetPlaylistId)))

        if (removeFromSource) {
            replacePlaylistItems(sourcePlaylist, sourceItems.filterNot { it.identity in selected })
        }
        if (appended.isNotEmpty()) {
            replacePlaylistItems(targetPlaylist, targetItems + appended)
        }
        if (!removeFromSource && appended.isEmpty()) {
            error("All selected tracks are already in the target playlist")
        }
        LibraryMutation(selectedInSource.size, undo)
    }

    private suspend fun snapshot(playlistId: Long): PlaylistUndoSnapshot {
        val playlist = requirePlaylist(playlistId).toDomain()
        val tracks = dao.getPlaylistItems(playlistId).map { it.toTrack() }
        return PlaylistUndoSnapshot(playlist, tracks)
    }

    private suspend fun restoreSnapshot(snapshot: PlaylistUndoSnapshot) {
        val current = requirePlaylist(snapshot.playlist.id)
        val trackIds = snapshot.tracks.map { ensureTrack(it) }
        dao.deletePlaylistEntries(snapshot.playlist.id)
        if (trackIds.isNotEmpty()) {
            dao.insertPlaylistEntries(
                trackIds.mapIndexed { index, trackId ->
                    PlaylistEntryEntity(snapshot.playlist.id, trackId, index, snapshot.playlist.modifiedAtEpochMs)
                },
            )
        }
        dao.updatePlaylist(
            current.copy(
                name = snapshot.playlist.name,
                revision = snapshot.playlist.revision,
                modifiedAtEpochMs = snapshot.playlist.modifiedAtEpochMs,
            ),
        )
    }

    private suspend fun replacePlaylistItems(playlist: PlaylistEntity, items: List<PlaylistItemRow>) {
        dao.deletePlaylistEntries(playlist.id)
        if (items.isNotEmpty()) {
            dao.insertPlaylistEntries(
                items.mapIndexed { index, row -> row.entry.copy(position = index, playlistId = playlist.id) },
            )
        }
        touch(playlist)
    }

    private suspend fun touch(playlist: PlaylistEntity) {
        dao.updatePlaylist(playlist.copy(revision = playlist.revision + 1, modifiedAtEpochMs = now()))
    }

    private suspend fun ensureTrack(track: Track): Long {
        val existing = dao.findTrack(track.sourceType.name, track.sourceMediaId)
        if (existing != null) {
            dao.updateTrackMetadata(
                existing.id,
                track.title,
                track.uploader,
                track.durationMs,
                track.thumbnailUrl,
                track.availability.name,
            )
            return existing.id
        }
        val inserted = dao.insertTrack(track.toEntity(now()).copy(id = 0))
        if (inserted > 0) return inserted
        return requireNotNull(dao.findTrack(track.sourceType.name, track.sourceMediaId)).id
    }

    private suspend fun requirePlaylist(id: Long): PlaylistEntity =
        requireNotNull(dao.getPlaylist(id)) { "Playlist no longer exists" }

    private fun mapLibraryRows(rows: List<LibraryRow>): List<LibraryPlaylist> = rows.groupBy { it.playlistId }
        .values
        .map { playlistRows ->
            val first = playlistRows.first()
            LibraryPlaylist(
                playlist = Playlist(
                    id = first.playlistId,
                    name = first.playlistName,
                    revision = first.playlistRevision,
                    createdAtEpochMs = first.playlistCreatedAt,
                    modifiedAtEpochMs = first.playlistModifiedAt,
                ),
                tracks = playlistRows.mapNotNull { row ->
                    val id = row.trackId ?: return@mapNotNull null
                    Track(
                        id = id,
                        sourceType = row.trackSourceType.asSourceType(),
                        sourceMediaId = row.trackSourceMediaId ?: return@mapNotNull null,
                        title = row.trackTitle ?: return@mapNotNull null,
                        uploader = row.trackUploader,
                        durationMs = row.trackDurationMs,
                        thumbnailUrl = row.trackThumbnailUrl,
                        availability = row.trackAvailability.asAvailability(),
                    )
                },
            )
        }
        .sortedWith(compareBy<LibraryPlaylist> { it.playlist.createdAtEpochMs }.thenBy { it.playlist.id })

    private val PlaylistItemRow.identity: SourceIdentity
        get() = SourceIdentity(sourceType.asSourceType(), sourceMediaId)

    private fun PlaylistItemRow.toTrack(): Track = Track(
        id = entry.trackId,
        sourceType = sourceType.asSourceType(),
        sourceMediaId = sourceMediaId,
        title = title,
        uploader = uploader,
        durationMs = durationMs,
        thumbnailUrl = thumbnailUrl,
        availability = availability.asAvailability(),
    )

    private fun String?.asSourceType(): SourceType =
        SourceType.entries.firstOrNull { it.name == this } ?: SourceType.YOUTUBE

    private fun String?.asAvailability(): Availability =
        Availability.entries.firstOrNull { it.name == this } ?: Availability.UNKNOWN
}
