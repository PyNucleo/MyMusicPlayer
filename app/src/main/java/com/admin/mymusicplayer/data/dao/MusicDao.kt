package com.admin.mymusicplayer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.admin.mymusicplayer.data.database.LibraryRow
import com.admin.mymusicplayer.data.database.PlaybackSessionEntity
import com.admin.mymusicplayer.data.database.PlaylistEntity
import com.admin.mymusicplayer.data.database.PlaylistEntryEntity
import com.admin.mymusicplayer.data.database.PlaylistItemRow
import com.admin.mymusicplayer.data.database.QueueEntryEntity
import com.admin.mymusicplayer.data.database.QueueItemRow
import com.admin.mymusicplayer.data.database.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query(
        """
        SELECT
            p.id AS playlist_id,
            p.name AS playlist_name,
            p.created_at_epoch_ms AS playlist_created_at,
            p.modified_at_epoch_ms AS playlist_modified_at,
            p.revision AS playlist_revision,
            pe.position AS entry_position,
            t.id AS track_id,
            t.source_type AS track_source_type,
            t.source_media_id AS track_source_media_id,
            t.title AS track_title,
            t.uploader AS track_uploader,
            t.duration_ms AS track_duration_ms,
            t.thumbnail_url AS track_thumbnail_url,
            t.availability AS track_availability
        FROM playlists p
        LEFT JOIN playlist_entries pe ON pe.playlist_id = p.id
        LEFT JOIN tracks t ON t.id = pe.track_id
        ORDER BY p.created_at_epoch_ms, p.id, pe.position
        """,
    )
    fun observeLibrary(): Flow<List<LibraryRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Query("SELECT * FROM tracks WHERE source_type = :sourceType AND source_media_id = :sourceMediaId")
    suspend fun findTrack(sourceType: String, sourceMediaId: String): TrackEntity?

    @Query(
        """
        UPDATE tracks SET title = :title, uploader = :uploader, duration_ms = :durationMs,
            thumbnail_url = :thumbnailUrl, availability = :availability
        WHERE id = :id
        """,
    )
    suspend fun updateTrackMetadata(
        id: Long,
        title: String,
        uploader: String?,
        durationMs: Long?,
        thumbnailUrl: String?,
        availability: String,
    )

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrack(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY id")
    suspend fun getAllTracks(): List<TrackEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY created_at_epoch_ms, id")
    suspend fun getAllPlaylists(): List<PlaylistEntity>

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query(
        """
        SELECT pe.*, t.source_type, t.source_media_id, t.title, t.uploader,
            t.duration_ms, t.thumbnail_url, t.availability
        FROM playlist_entries pe
        JOIN tracks t ON t.id = pe.track_id
        WHERE pe.playlist_id = :playlistId
        ORDER BY pe.position
        """,
    )
    suspend fun getPlaylistItems(playlistId: Long): List<PlaylistItemRow>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_entries WHERE playlist_id = :playlistId AND track_id = :trackId)")
    suspend fun containsTrack(playlistId: Long, trackId: Long): Boolean

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun nextPlaylistPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylistEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun deletePlaylistEntries(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_entries")
    suspend fun playlistEntryCount(): Int

    @Upsert
    suspend fun upsertSession(session: PlaybackSessionEntity)

    @Query("SELECT * FROM playback_sessions WHERE session_id = :sessionId")
    suspend fun getSession(sessionId: Long): PlaybackSessionEntity?

    @Query("SELECT * FROM playback_sessions WHERE session_id = :sessionId")
    fun observeSession(sessionId: Long): Flow<PlaybackSessionEntity?>

    @Query("DELETE FROM playback_sessions WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("UPDATE playback_sessions SET current_position_ms = :positionMs, updated_at_epoch_ms = :updatedAt WHERE session_id = :sessionId")
    suspend fun checkpointPosition(sessionId: Long, positionMs: Long, updatedAt: Long)

    @Query(
        """
        SELECT qe.*, t.source_type, t.source_media_id, t.title, t.uploader,
            t.duration_ms, t.thumbnail_url, t.availability
        FROM queue_entries qe
        JOIN tracks t ON t.id = qe.track_id
        WHERE qe.session_id = :sessionId
        ORDER BY qe.position
        """,
    )
    suspend fun getQueueItems(sessionId: Long): List<QueueItemRow>

    @Query(
        """
        SELECT qe.*, t.source_type, t.source_media_id, t.title, t.uploader,
            t.duration_ms, t.thumbnail_url, t.availability
        FROM queue_entries qe
        JOIN tracks t ON t.id = qe.track_id
        WHERE qe.session_id = :sessionId
        ORDER BY qe.position
        """,
    )
    fun observeQueueItems(sessionId: Long): Flow<List<QueueItemRow>>

    @Query("DELETE FROM queue_entries WHERE session_id = :sessionId")
    suspend fun deleteQueueEntries(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQueueEntries(entries: List<QueueEntryEntity>)

    @Query("SELECT COALESCE(MAX(queue_entry_id), 0) + 1 FROM queue_entries")
    suspend fun nextQueueEntryId(): Long

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun trackCount(): Int

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun playlistCount(): Int

    @Query("SELECT COUNT(*) FROM queue_entries")
    suspend fun queueEntryCount(): Int
}
