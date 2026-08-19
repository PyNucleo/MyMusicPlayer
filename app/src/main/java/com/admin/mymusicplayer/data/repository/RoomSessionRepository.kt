package com.admin.mymusicplayer.data.repository

import androidx.room.withTransaction
import com.admin.mymusicplayer.data.dao.MusicDao
import com.admin.mymusicplayer.data.database.MusicDatabase
import com.admin.mymusicplayer.data.database.PlaybackSessionEntity
import com.admin.mymusicplayer.data.database.QueueEntryEntity
import com.admin.mymusicplayer.data.database.QueueItemRow
import com.admin.mymusicplayer.domain.Availability
import com.admin.mymusicplayer.domain.QueueSnapshot
import com.admin.mymusicplayer.domain.QueueTransformations
import com.admin.mymusicplayer.domain.RepeatMode
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.ShufflePlanner
import com.admin.mymusicplayer.domain.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.random.Random

class RoomSessionRepository(
    private val database: MusicDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : SessionRepository {
    private val dao: MusicDao = database.musicDao()

    override val state: Flow<PersistentPlaybackState?> = combine(
        dao.observeSession(SESSION_ID),
        dao.observeQueueItems(SESSION_ID),
    ) { session, queue ->
        session?.let { mapStateOrNull(it, queue) }
    }

    override suspend fun restoreOrResetTransient(): PersistentPlaybackState? = database.withTransaction {
        val session = dao.getSession(SESSION_ID) ?: return@withTransaction null
        val queueRows = dao.getQueueItems(SESSION_ID)
        val mapped = mapStateOrNull(session, queueRows)
        if (mapped == null) {
            dao.deleteSession(SESSION_ID)
            return@withTransaction null
        }
        mapped
    }

    override suspend fun replaceQueue(
        tracks: List<Track>,
        currentIndex: Int,
        originPlaylistId: Long?,
        originPlaylistRevision: Long?,
        shuffleEnabled: Boolean,
    ): PersistentPlaybackState = database.withTransaction {
        require(tracks.isNotEmpty()) { "A playback queue cannot be empty" }
        require(currentIndex in tracks.indices) { "currentIndex is outside the queue" }
        var nextEntryId = dao.nextQueueEntryId()
        val items = tracks.map { track ->
            PersistentQueueItem(nextEntryId++, ensureTrack(track))
        }
        val state = PersistentPlaybackState(
            items,
            currentIndex,
            0,
            shuffleEnabled,
            RepeatMode.PLAY_ONCE,
            false,
            originPlaylistId,
            originPlaylistRevision,
        )
        persistAll(state)
        state
    }

    override suspend fun playNow(track: Track): PersistentPlaybackState = mutateQueue { current, item ->
        if (current == null) {
            QueueSnapshot(listOf(item), 0)
        } else {
            val inserted = QueueTransformations.playNow(current.asSnapshot(), item) { it.queueEntryId }
            if (current.shuffleEnabled) {
                QueueSnapshot(
                    ShufflePlanner.planFromExplicitSelection(
                        eligible = inserted.entries,
                        selectedIdentity = item.queueEntryId,
                        identity = { it.queueEntryId },
                        random = random,
                    ),
                    0,
                )
            } else {
                inserted
            }
        }
    }(track)

    override suspend fun playNext(track: Track): PersistentPlaybackState = mutateQueue { current, item ->
        if (current == null) QueueSnapshot(listOf(item), 0)
        else QueueTransformations.playNext(current.asSnapshot(), item) { it.queueEntryId }
    }(track)

    override suspend fun addToQueue(track: Track): PersistentPlaybackState = mutateQueue { current, item ->
        if (current == null) QueueSnapshot(listOf(item), 0)
        else QueueTransformations.addToQueue(current.asSnapshot(), item) { it.queueEntryId }
    }(track)

    override suspend fun reorderUpcoming(fromIndex: Int, toIndex: Int): PersistentPlaybackState =
        mutateExisting { state -> QueueTransformations.reorderUpcoming(state.asSnapshot(), fromIndex, toIndex) }

    override suspend fun removeUpcoming(index: Int): PersistentPlaybackState =
        mutateExisting { state -> QueueTransformations.removeUpcoming(state.asSnapshot(), index) }

    override suspend fun clearUpcoming(): PersistentPlaybackState =
        mutateExisting { state -> QueueTransformations.clearUpcoming(state.asSnapshot()) }

    override suspend fun setShuffleEnabled(enabled: Boolean): PersistentPlaybackState = database.withTransaction {
        val current = requireState()
        if (current.shuffleEnabled == enabled) return@withTransaction current
        val entries = if (enabled) {
            ShufflePlanner.enableMidCycle(
                currentOrder = current.entries,
                currentIndex = current.currentIndex,
                identity = { it.queueEntryId },
                random = random,
            )
        } else {
            current.entries
        }
        val updated = current.copy(entries = entries, shuffleEnabled = enabled)
        persistAll(updated)
        updated
    }

    override suspend fun reshuffle(): PersistentPlaybackState = database.withTransaction {
        val current = requireState()
        val currentEntry = current.entries[current.currentIndex]
        val entries = ShufflePlanner.reshuffle(
            eligible = current.entries,
            identity = { it.queueEntryId },
            currentIdentity = currentEntry.queueEntryId,
            random = random,
        )
        val updated = current.copy(
            entries = entries,
            currentIndex = 0,
            currentPositionMs = 0,
            shuffleEnabled = true,
            repeatMode = RepeatMode.PLAY_ONCE,
            repeatOnceConsumed = false,
        )
        persistAll(updated)
        updated
    }

    override suspend fun startNextShuffleCycle(): PersistentPlaybackState = database.withTransaction {
        val current = requireState()
        val previousLast = current.entries[current.currentIndex].queueEntryId
        val entries = ShufflePlanner.planCycle(
            eligible = current.entries,
            identity = { it.queueEntryId },
            previousLastIdentity = previousLast,
            random = random,
        )
        val updated = current.copy(
            entries = entries,
            currentIndex = 0,
            currentPositionMs = 0,
            shuffleEnabled = true,
            repeatMode = RepeatMode.PLAY_ONCE,
            repeatOnceConsumed = false,
        )
        persistAll(updated)
        updated
    }

    override suspend fun updatePlaybackStructure(
        currentIndex: Int,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
        repeatOnceConsumed: Boolean,
    ) = database.withTransaction {
        val existing = requireState()
        require(currentIndex in existing.entries.indices) { "currentIndex is outside the queue" }
        val updated = existing.copy(
            currentIndex = currentIndex,
            currentPositionMs = if (currentIndex == existing.currentIndex) existing.currentPositionMs else 0,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            repeatOnceConsumed = repeatOnceConsumed,
        )
        upsertSessionOnly(updated)
    }

    override suspend fun checkpointPosition(positionMs: Long) {
        require(positionMs >= 0) { "positionMs must be non-negative" }
        dao.checkpointPosition(SESSION_ID, positionMs, now())
    }

    override suspend fun clearTransientSession() {
        dao.deleteSession(SESSION_ID)
    }

    suspend fun queueCount(): Int = dao.queueEntryCount()

    private fun mutateQueue(
        transform: (PersistentPlaybackState?, PersistentQueueItem) -> QueueSnapshot<PersistentQueueItem>,
    ): suspend (Track) -> PersistentPlaybackState = { track ->
        database.withTransaction {
            val current = restoreOrResetTransient()
            val item = PersistentQueueItem(dao.nextQueueEntryId(), ensureTrack(track))
            val snapshot = transform(current, item)
            val updated = current?.copy(
                entries = snapshot.entries,
                currentIndex = snapshot.currentIndex,
                currentPositionMs = 0,
                repeatMode = RepeatMode.PLAY_ONCE,
                repeatOnceConsumed = false,
            ) ?: PersistentPlaybackState(
                snapshot.entries,
                snapshot.currentIndex,
                0,
                false,
                RepeatMode.PLAY_ONCE,
                false,
                null,
                null,
            )
            persistAll(updated)
            updated
        }
    }

    private suspend fun mutateExisting(
        transform: (PersistentPlaybackState) -> QueueSnapshot<PersistentQueueItem>,
    ): PersistentPlaybackState = database.withTransaction {
        val current = requireState()
        val snapshot = transform(current)
        val updated = current.copy(entries = snapshot.entries, currentIndex = snapshot.currentIndex)
        persistAll(updated)
        updated
    }

    private suspend fun requireState(): PersistentPlaybackState =
        requireNotNull(restoreOrResetTransient()) { "Playback queue is empty" }

    private fun PersistentPlaybackState.asSnapshot() = QueueSnapshot(entries, currentIndex)

    private suspend fun persistAll(state: PersistentPlaybackState) {
        upsertSessionOnly(state)
        dao.deleteQueueEntries(SESSION_ID)
        dao.insertQueueEntries(
            state.entries.mapIndexed { index, item ->
                QueueEntryEntity(SESSION_ID, index, item.queueEntryId, item.track.id)
            },
        )
    }

    private suspend fun upsertSessionOnly(state: PersistentPlaybackState) {
        val existing = dao.getSession(SESSION_ID)
        val timestamp = now()
        dao.upsertSession(
            PlaybackSessionEntity(
                sessionId = SESSION_ID,
                originPlaylistId = state.originPlaylistId,
                originPlaylistRevision = state.originPlaylistRevision,
                shuffleEnabled = state.shuffleEnabled,
                currentQueueIndex = state.currentIndex,
                currentPositionMs = state.currentPositionMs,
                repeatMode = state.repeatMode.name,
                repeatOnceConsumed = state.repeatOnceConsumed,
                createdAtEpochMs = existing?.createdAtEpochMs ?: timestamp,
                updatedAtEpochMs = timestamp,
            ),
        )
    }

    private suspend fun ensureTrack(track: Track): Track {
        val existing = dao.findTrack(track.sourceType.name, track.sourceMediaId)
        val id = if (existing != null) {
            dao.updateTrackMetadata(
                existing.id,
                track.title,
                track.uploader,
                track.durationMs,
                track.thumbnailUrl,
                track.availability.name,
            )
            existing.id
        } else {
            dao.insertTrack(track.toEntity(now()).copy(id = 0)).takeIf { it > 0 }
                ?: requireNotNull(dao.findTrack(track.sourceType.name, track.sourceMediaId)).id
        }
        return track.copy(id = id)
    }

    private fun mapStateOrNull(
        session: PlaybackSessionEntity,
        queueRows: List<QueueItemRow>,
    ): PersistentPlaybackState? {
        val repeatMode = RepeatMode.entries.firstOrNull { it.name == session.repeatMode }
        val valid = queueRows.isNotEmpty() && session.currentQueueIndex in queueRows.indices &&
            session.currentPositionMs >= 0 && repeatMode != null
        if (!valid) return null
        return PersistentPlaybackState(
            entries = queueRows.map { row ->
                PersistentQueueItem(
                    queueEntryId = row.entry.queueEntryId,
                    track = Track(
                        id = row.entry.trackId,
                        sourceType = SourceType.entries.firstOrNull { it.name == row.sourceType } ?: SourceType.YOUTUBE,
                        sourceMediaId = row.sourceMediaId,
                        title = row.title,
                        uploader = row.uploader,
                        durationMs = row.durationMs,
                        thumbnailUrl = row.thumbnailUrl,
                        availability = Availability.entries.firstOrNull { it.name == row.availability }
                            ?: Availability.UNKNOWN,
                    ),
                )
            },
            currentIndex = session.currentQueueIndex,
            currentPositionMs = session.currentPositionMs,
            shuffleEnabled = session.shuffleEnabled,
            repeatMode = requireNotNull(repeatMode),
            repeatOnceConsumed = session.repeatOnceConsumed,
            originPlaylistId = session.originPlaylistId,
            originPlaylistRevision = session.originPlaylistRevision,
        )
    }

    private companion object {
        const val SESSION_ID = 1L
    }
}
