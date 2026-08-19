package com.admin.mymusicplayer.backup

data class PortableBackup(
    val formatVersion: Int = 0,
    val applicationId: String? = null,
    val createdAtEpochMs: Long = 0,
    val tracks: List<BackupTrack>? = null,
    val playlists: List<BackupPlaylist>? = null,
    val settings: Map<String, String>? = null,
)

data class BackupTrack(
    val sourceType: String? = null,
    val sourceMediaId: String? = null,
    val title: String? = null,
    val uploader: String? = null,
    val durationMs: Long? = null,
    val thumbnailUrl: String? = null,
    val availability: String? = null,
    val createdAtEpochMs: Long = 0,
)

data class BackupPlaylist(
    val name: String? = null,
    val revision: Long = 0,
    val createdAtEpochMs: Long = 0,
    val modifiedAtEpochMs: Long = 0,
    val entries: List<BackupTrackReference>? = null,
)

data class BackupTrackReference(
    val sourceType: String? = null,
    val sourceMediaId: String? = null,
)

data class BackupSummary(
    val playlistCount: Int,
    val trackCount: Int,
    val entryCount: Int,
    val createdAtEpochMs: Long,
)

class ValidatedBackup internal constructor(
    internal val document: PortableBackup,
    val summary: BackupSummary,
)
