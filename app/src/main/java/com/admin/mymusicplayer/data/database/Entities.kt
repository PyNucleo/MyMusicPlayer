package com.admin.mymusicplayer.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["source_type", "source_media_id"], unique = true)],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_media_id") val sourceMediaId: String,
    val title: String,
    val uploader: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    val availability: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)

@Entity(tableName = "playlists", indices = [Index(value = ["name"])])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "modified_at_epoch_ms") val modifiedAtEpochMs: Long,
    val revision: Long,
)

@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlist_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["playlist_id", "position"], unique = true),
    ],
)
data class PlaylistEntryEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "track_id") val trackId: Long,
    val position: Int,
    @ColumnInfo(name = "added_at_epoch_ms") val addedAtEpochMs: Long,
)

@Entity(tableName = "playback_sessions")
data class PlaybackSessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "origin_playlist_id") val originPlaylistId: Long?,
    @ColumnInfo(name = "origin_playlist_revision") val originPlaylistRevision: Long?,
    @ColumnInfo(name = "shuffle_enabled") val shuffleEnabled: Boolean,
    @ColumnInfo(name = "current_queue_index") val currentQueueIndex: Int,
    @ColumnInfo(name = "consumed_queue_entry_ids", defaultValue = "''") val consumedQueueEntryIds: String,
    @ColumnInfo(name = "current_position_ms") val currentPositionMs: Long,
    @ColumnInfo(name = "repeat_mode") val repeatMode: String,
    @ColumnInfo(name = "repeat_once_consumed") val repeatOnceConsumed: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "queue_entries",
    primaryKeys = ["session_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaybackSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["queue_entry_id"], unique = true),
    ],
)
data class QueueEntryEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val position: Int,
    @ColumnInfo(name = "queue_entry_id") val queueEntryId: Long,
    @ColumnInfo(name = "track_id") val trackId: Long,
)
