package com.admin.mymusicplayer.data.repository

import com.admin.mymusicplayer.data.database.PlaylistEntity
import com.admin.mymusicplayer.data.database.TrackEntity
import com.admin.mymusicplayer.domain.Availability
import com.admin.mymusicplayer.domain.Playlist
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track

internal fun TrackEntity.toDomain(): Track = Track(
    id = id,
    sourceType = enumValueOrDefault(sourceType, SourceType.YOUTUBE),
    sourceMediaId = sourceMediaId,
    title = title,
    uploader = uploader,
    durationMs = durationMs,
    thumbnailUrl = thumbnailUrl,
    availability = enumValueOrDefault(availability, Availability.UNKNOWN),
)

internal fun Track.toEntity(createdAtEpochMs: Long): TrackEntity = TrackEntity(
    id = id,
    sourceType = sourceType.name,
    sourceMediaId = sourceMediaId,
    title = title,
    uploader = uploader,
    durationMs = durationMs,
    thumbnailUrl = thumbnailUrl,
    availability = availability.name,
    createdAtEpochMs = createdAtEpochMs,
)

internal fun PlaylistEntity.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    revision = revision,
    createdAtEpochMs = createdAtEpochMs,
    modifiedAtEpochMs = modifiedAtEpochMs,
)

internal fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    createdAtEpochMs = createdAtEpochMs,
    modifiedAtEpochMs = modifiedAtEpochMs,
    revision = revision,
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
