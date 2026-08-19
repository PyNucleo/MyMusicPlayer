package com.admin.mymusicplayer.data.repository

import com.admin.mymusicplayer.domain.RepeatMode
import com.admin.mymusicplayer.domain.Track
import kotlinx.coroutines.flow.Flow

data class PersistentQueueItem(
    val queueEntryId: Long,
    val track: Track,
)

data class PersistentPlaybackState(
    val entries: List<PersistentQueueItem>,
    val currentIndex: Int,
    val consumedQueueEntryIds: Set<Long>,
    val currentPositionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
    val repeatOnceConsumed: Boolean,
    val originPlaylistId: Long?,
    val originPlaylistRevision: Long?,
)

interface SessionRepository {
    val state: Flow<PersistentPlaybackState?>
    suspend fun restoreOrResetTransient(): PersistentPlaybackState?
    suspend fun replaceQueue(
        tracks: List<Track>,
        currentIndex: Int,
        originPlaylistId: Long? = null,
        originPlaylistRevision: Long? = null,
        shuffleEnabled: Boolean = false,
    ): PersistentPlaybackState
    suspend fun playNow(track: Track): PersistentPlaybackState
    suspend fun playNext(track: Track): PersistentPlaybackState
    suspend fun addToQueue(track: Track): PersistentPlaybackState
    suspend fun reorderUpcoming(fromIndex: Int, toIndex: Int): PersistentPlaybackState
    suspend fun removeUpcoming(index: Int): PersistentPlaybackState
    suspend fun clearUpcoming(): PersistentPlaybackState
    suspend fun setShuffleEnabled(enabled: Boolean): PersistentPlaybackState
    suspend fun reshuffle(): PersistentPlaybackState
    suspend fun startNextShuffleCycle(): PersistentPlaybackState
    suspend fun updatePlaybackStructure(
        currentIndex: Int,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
        repeatOnceConsumed: Boolean,
    )
    suspend fun checkpointPosition(positionMs: Long)
    suspend fun clearTransientSession()
}
