package com.admin.mymusicplayer.domain

enum class SourceType {
    YOUTUBE,
}

enum class Availability {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
}

data class SourceIdentity(
    val sourceType: SourceType,
    val sourceMediaId: String,
) {
    init {
        require(sourceMediaId.isNotBlank()) { "sourceMediaId must not be blank" }
    }
}

data class Track(
    val id: Long = 0,
    val sourceType: SourceType,
    val sourceMediaId: String,
    val title: String,
    val uploader: String? = null,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
    val availability: Availability = Availability.UNKNOWN,
) {
    val sourceIdentity: SourceIdentity
        get() = SourceIdentity(sourceType, sourceMediaId)

    init {
        require(sourceMediaId.isNotBlank()) { "sourceMediaId must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(durationMs == null || durationMs >= 0) { "durationMs must be non-negative" }
    }
}

data class Playlist(
    val id: Long = 0,
    val name: String,
    val revision: Long = 0,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long = createdAtEpochMs,
) {
    init {
        require(name.isNotBlank()) { "playlist name must not be blank" }
        require(revision >= 0) { "revision must be non-negative" }
    }
}

data class PlaylistEntry(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
    val addedAtEpochMs: Long,
) {
    init {
        require(position >= 0) { "position must be non-negative" }
    }
}

data class QueueEntry(
    val id: Long,
    val trackId: Long,
    val position: Int,
) {
    init {
        require(position >= 0) { "position must be non-negative" }
    }
}

data class PlaybackSession(
    val sessionId: Long,
    val originPlaylistId: Long? = null,
    val originPlaylistRevision: Long? = null,
    val shuffleEnabled: Boolean = false,
    val currentQueueIndex: Int = 0,
    val currentPositionMs: Long = 0,
    val currentRepeatMode: RepeatMode = RepeatMode.PLAY_ONCE,
    val repeatOnceConsumed: Boolean = false,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
) {
    init {
        require(currentQueueIndex >= 0) { "currentQueueIndex must be non-negative" }
        require(currentPositionMs >= 0) { "currentPositionMs must be non-negative" }
    }
}

