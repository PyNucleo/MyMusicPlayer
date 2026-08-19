package com.admin.mymusicplayer.data.database

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class LibraryRow(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "playlist_name") val playlistName: String,
    @ColumnInfo(name = "playlist_created_at") val playlistCreatedAt: Long,
    @ColumnInfo(name = "playlist_modified_at") val playlistModifiedAt: Long,
    @ColumnInfo(name = "playlist_revision") val playlistRevision: Long,
    @ColumnInfo(name = "entry_position") val entryPosition: Int?,
    @ColumnInfo(name = "track_id") val trackId: Long?,
    @ColumnInfo(name = "track_source_type") val trackSourceType: String?,
    @ColumnInfo(name = "track_source_media_id") val trackSourceMediaId: String?,
    @ColumnInfo(name = "track_title") val trackTitle: String?,
    @ColumnInfo(name = "track_uploader") val trackUploader: String?,
    @ColumnInfo(name = "track_duration_ms") val trackDurationMs: Long?,
    @ColumnInfo(name = "track_thumbnail_url") val trackThumbnailUrl: String?,
    @ColumnInfo(name = "track_availability") val trackAvailability: String?,
)

data class PlaylistItemRow(
    @Embedded val entry: PlaylistEntryEntity,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_media_id") val sourceMediaId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "uploader") val uploader: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "availability") val availability: String,
)

data class QueueItemRow(
    @Embedded val entry: QueueEntryEntity,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_media_id") val sourceMediaId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "uploader") val uploader: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "availability") val availability: String,
)
