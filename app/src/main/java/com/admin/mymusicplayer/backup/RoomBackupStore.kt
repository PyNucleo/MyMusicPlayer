package com.admin.mymusicplayer.backup

import androidx.room.withTransaction
import com.admin.mymusicplayer.BuildConfig
import com.admin.mymusicplayer.data.dao.MusicDao
import com.admin.mymusicplayer.data.database.MusicDatabase
import com.admin.mymusicplayer.data.database.PlaylistEntity
import com.admin.mymusicplayer.data.database.PlaylistEntryEntity
import com.admin.mymusicplayer.data.database.TrackEntity
import com.admin.mymusicplayer.domain.Availability
import com.admin.mymusicplayer.domain.SourceType

class RoomBackupStore(
    private val database: MusicDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao: MusicDao = database.musicDao()

    suspend fun export(): PortableBackup = database.withTransaction {
        val tracks = linkedMapOf<SourceKey, BackupTrack>()
        val playlists = dao.getAllPlaylists().map { playlist ->
            val entries = dao.getPlaylistItems(playlist.id).map { row ->
                val key = SourceKey(row.sourceType, row.sourceMediaId)
                if (key !in tracks) {
                    tracks[key] = BackupTrack(
                        sourceType = row.sourceType,
                        sourceMediaId = row.sourceMediaId,
                        title = row.title,
                        uploader = row.uploader,
                        durationMs = row.durationMs,
                        thumbnailUrl = row.thumbnailUrl,
                        availability = row.availability,
                        createdAtEpochMs = dao.getTrack(row.entry.trackId)?.createdAtEpochMs ?: 0,
                    )
                }
                BackupTrackReference(row.sourceType, row.sourceMediaId)
            }
            BackupPlaylist(
                name = playlist.name,
                revision = playlist.revision,
                createdAtEpochMs = playlist.createdAtEpochMs,
                modifiedAtEpochMs = playlist.modifiedAtEpochMs,
                entries = entries,
            )
        }
        PortableBackup(
            formatVersion = FORMAT_VERSION,
            applicationId = BuildConfig.APPLICATION_ID,
            createdAtEpochMs = now(),
            tracks = tracks.values.toList(),
            playlists = playlists,
            settings = emptyMap(),
        )
    }

    fun validate(document: PortableBackup): ValidatedBackup {
        require(document.formatVersion == FORMAT_VERSION) {
            "Unsupported backup format ${document.formatVersion}; expected $FORMAT_VERSION"
        }
        require(document.applicationId == BuildConfig.APPLICATION_ID) { "Backup belongs to another application" }
        require(document.createdAtEpochMs > 0) { "Backup creation time is invalid" }
        val tracks = requireNotNull(document.tracks) { "Backup tracks are missing" }
        val playlists = requireNotNull(document.playlists) { "Backup playlists are missing" }
        require(tracks.size <= MAX_TRACKS) { "Backup contains too many tracks" }
        require(playlists.size <= MAX_PLAYLISTS) { "Backup contains too many playlists" }
        val keys = mutableSetOf<SourceKey>()
        tracks.forEach { track ->
            val key = track.validatedKey()
            require(keys.add(key)) { "Backup contains duplicate track ${key.sourceMediaId}" }
            require(!track.title.isNullOrBlank()) { "Backup contains a track without a title" }
            require(track.durationMs == null || track.durationMs >= 0) { "Backup contains an invalid duration" }
            require(track.availability in Availability.entries.map { it.name }) { "Backup availability is invalid" }
        }
        var entryCount = 0
        playlists.forEach { playlist ->
            require(!playlist.name.isNullOrBlank()) { "Backup contains a playlist without a name" }
            require(playlist.revision >= 0) { "Backup contains an invalid playlist revision" }
            val entries = requireNotNull(playlist.entries) { "Backup playlist entries are missing" }
            entryCount += entries.size
            require(entryCount <= MAX_ENTRIES) { "Backup contains too many playlist entries" }
            val playlistKeys = mutableSetOf<SourceKey>()
            entries.forEach { reference ->
                val key = reference.validatedKey()
                require(key in keys) { "Backup playlist references a missing track" }
                require(playlistKeys.add(key)) { "Backup playlist contains an exact duplicate" }
            }
        }
        return ValidatedBackup(
            document,
            BackupSummary(playlists.size, tracks.size, entryCount, document.createdAtEpochMs),
        )
    }

    suspend fun restore(validated: ValidatedBackup) = database.withTransaction {
        val document = validated.document
        val tracks = requireNotNull(document.tracks)
        val playlists = requireNotNull(document.playlists)
        dao.deleteSession(SESSION_ID)
        dao.deleteAllPlaylists()
        val trackIds = mutableMapOf<SourceKey, Long>()
        tracks.forEach { backupTrack ->
            val key = backupTrack.validatedKey()
            val existing = dao.findTrack(key.sourceType, key.sourceMediaId)
            val trackId = if (existing != null) {
                dao.updateTrackMetadata(
                    existing.id,
                    requireNotNull(backupTrack.title),
                    backupTrack.uploader,
                    backupTrack.durationMs,
                    backupTrack.thumbnailUrl,
                    requireNotNull(backupTrack.availability),
                )
                existing.id
            } else {
                dao.insertTrack(
                    TrackEntity(
                        sourceType = key.sourceType,
                        sourceMediaId = key.sourceMediaId,
                        title = requireNotNull(backupTrack.title),
                        uploader = backupTrack.uploader,
                        durationMs = backupTrack.durationMs,
                        thumbnailUrl = backupTrack.thumbnailUrl,
                        availability = requireNotNull(backupTrack.availability),
                        createdAtEpochMs = backupTrack.createdAtEpochMs.takeIf { it > 0 } ?: now(),
                    ),
                )
            }
            trackIds[key] = trackId
        }
        playlists.forEach { backupPlaylist ->
            val playlistId = dao.insertPlaylist(
                PlaylistEntity(
                    name = requireNotNull(backupPlaylist.name),
                    createdAtEpochMs = backupPlaylist.createdAtEpochMs.takeIf { it > 0 } ?: now(),
                    modifiedAtEpochMs = backupPlaylist.modifiedAtEpochMs.takeIf { it > 0 } ?: now(),
                    revision = backupPlaylist.revision,
                ),
            )
            val entries = requireNotNull(backupPlaylist.entries).mapIndexed { index, reference ->
                PlaylistEntryEntity(
                    playlistId = playlistId,
                    trackId = requireNotNull(trackIds[reference.validatedKey()]),
                    position = index,
                    addedAtEpochMs = backupPlaylist.modifiedAtEpochMs.takeIf { it > 0 } ?: now(),
                )
            }
            if (entries.isNotEmpty()) dao.insertPlaylistEntries(entries)
        }
    }

    private fun BackupTrack.validatedKey() = validateKey(sourceType, sourceMediaId)
    private fun BackupTrackReference.validatedKey() = validateKey(sourceType, sourceMediaId)

    private fun validateKey(sourceType: String?, sourceMediaId: String?): SourceKey {
        require(sourceType in SourceType.entries.map { it.name }) { "Backup source type is unsupported" }
        require(!sourceMediaId.isNullOrBlank()) { "Backup source media ID is missing" }
        return SourceKey(requireNotNull(sourceType), sourceMediaId)
    }

    private data class SourceKey(val sourceType: String, val sourceMediaId: String)

    companion object {
        const val FORMAT_VERSION = 1
        private const val SESSION_ID = 1L
        private const val MAX_TRACKS = 100_000
        private const val MAX_PLAYLISTS = 10_000
        private const val MAX_ENTRIES = 1_000_000
    }
}
