package com.admin.mymusicplayer.data.repository

import com.admin.mymusicplayer.domain.RepeatMode
import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.SourceType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomSessionRepositoryTest : RoomRepositoryTestSupport() {
    @Test
    fun playbackSnapshotDoesNotChangeWhenOriginPlaylistIsEdited() = runTest {
        val playlistId = library.createPlaylist("Drive")
        listOf("a", "b", "c").forEach { library.addTrack(playlistId, track(it)) }
        val playlist = library.playlists.first { it.singleOrNull()?.tracks?.size == 3 }.single()
        session.replaceQueue(
            tracks = playlist.tracks,
            currentIndex = 1,
            originPlaylistId = playlistId,
            originPlaylistRevision = playlist.playlist.revision,
        )

        library.removeSelected(playlistId, setOf(SourceIdentity(SourceType.YOUTUBE, "b")))

        val restored = requireNotNull(session.restoreOrResetTransient())
        assertThat(restored.entries.map { it.track.sourceMediaId }).containsExactly("a", "b", "c").inOrder()
        assertThat(restored.currentIndex).isEqualTo(1)
        assertThat(restored.originPlaylistRevision).isEqualTo(playlist.playlist.revision)
    }

    @Test
    fun structuralStateAndPositionSurviveRepositoryRecreation() = runTest {
        session.replaceQueue(listOf(track("a"), track("b"), track("c")), currentIndex = 1)
        session.updatePlaybackStructure(
            currentIndex = 2,
            shuffleEnabled = true,
            repeatMode = RepeatMode.REPEAT_ONCE,
            repeatOnceConsumed = true,
        )
        session.checkpointPosition(42_500)

        val persistedSession = requireNotNull(database.musicDao().getSession(1L))
        val persistedQueue = database.musicDao().getQueueItems(1L)
        assertThat(persistedSession.currentQueueIndex).isEqualTo(2)
        assertThat(persistedSession.currentPositionMs).isEqualTo(42_500)
        assertThat(persistedSession.repeatMode).isEqualTo("REPEAT_ONCE")
        assertThat(persistedQueue).hasSize(3)
        val recreated = RoomSessionRepository(database, now = { 9_999L })
        val restored = requireNotNull(recreated.restoreOrResetTransient())

        assertThat(restored.entries.map { it.track.sourceMediaId }).containsExactly("a", "b", "c").inOrder()
        assertThat(restored.currentIndex).isEqualTo(2)
        assertThat(restored.currentPositionMs).isEqualTo(42_500)
        assertThat(restored.shuffleEnabled).isTrue()
        assertThat(restored.consumedQueueEntryIds).containsExactly(
            restored.entries[1].queueEntryId,
            restored.entries[2].queueEntryId,
        )
        assertThat(restored.repeatMode).isEqualTo(RepeatMode.REPEAT_ONCE)
        assertThat(restored.repeatOnceConsumed).isTrue()
    }

    @Test
    fun queueActionsOperateOnStableQueueEntryIdentity() = runTest {
        session.replaceQueue(listOf(track("a"), track("b")), currentIndex = 0)

        session.playNext(track("c"))
        session.addToQueue(track("d"))
        session.reorderUpcoming(fromIndex = 3, toIndex = 2)
        session.removeUpcoming(index = 1)

        val restored = requireNotNull(session.restoreOrResetTransient())
        assertThat(restored.entries.map { it.track.sourceMediaId }).containsExactly("a", "d", "b").inOrder()
        assertThat(restored.entries.map { it.queueEntryId }.toSet()).hasSize(3)
    }

    @Test
    fun corruptTransientSessionIsResetWithoutDeletingLibrary() = runTest {
        val playlistId = library.createPlaylist("Keep me")
        library.addTrack(playlistId, track("saved"))
        session.replaceQueue(listOf(track("saved")), currentIndex = 0)
        val dao = database.musicDao()
        val persisted = requireNotNull(dao.getSession(1L))
        dao.upsertSession(persisted.copy(currentQueueIndex = 999))

        assertThat(session.restoreOrResetTransient()).isNull()
        assertThat(dao.getSession(1L)).isNull()
        assertThat(session.queueCount()).isEqualTo(0)
        assertThat(library.libraryCounts()).isEqualTo(Triple(1, 1, 1))
    }

    @Test
    fun enablingShuffleMidCycleKeepsConsumedPrefixAndUsesExactCoverage() = runTest {
        session.replaceQueue(listOf(track("a"), track("b"), track("c"), track("d")), currentIndex = 0)
        session.updatePlaybackStructure(
            currentIndex = 1,
            shuffleEnabled = false,
            repeatMode = RepeatMode.PLAY_ONCE,
            repeatOnceConsumed = false,
        )

        val shuffled = session.setShuffleEnabled(true)

        assertThat(shuffled.entries.take(2).map { it.track.sourceMediaId }).containsExactly("a", "b").inOrder()
        assertThat(shuffled.entries.map { it.track.sourceMediaId }).containsExactly("a", "b", "c", "d")
        assertThat(shuffled.currentIndex).isEqualTo(1)
    }

    @Test
    fun explicitlySelectingSecondTrackDoesNotConsumeFirstWhenShuffleIsEnabled() = runTest {
        val deterministicSession = RoomSessionRepository(database, random = Random(7))
        deterministicSession.replaceQueue(
            listOf(track("song-1"), track("song-2"), track("song-3")),
            currentIndex = 1,
        )

        val shuffled = deterministicSession.setShuffleEnabled(true)

        assertThat(shuffled.entries[shuffled.currentIndex].track.sourceMediaId).isEqualTo("song-2")
        assertThat(shuffled.entries.drop(shuffled.currentIndex + 1).map { it.track.sourceMediaId })
            .containsExactly("song-1", "song-3")
    }

    @Test
    fun explicitSelectionEligibilitySurvivesRepositoryRecreationBeforeShuffle() = runTest {
        session.replaceQueue(
            listOf(track("song-1"), track("song-2"), track("song-3")),
            currentIndex = 1,
        )
        val recreated = RoomSessionRepository(database, now = { 9_999L }, random = Random(11))

        val shuffled = recreated.setShuffleEnabled(true)

        assertThat(shuffled.entries[shuffled.currentIndex].track.sourceMediaId).isEqualTo("song-2")
        assertThat(shuffled.entries.drop(shuffled.currentIndex + 1).map { it.track.sourceMediaId })
            .containsExactly("song-1", "song-3")
    }

    @Test
    fun shuffledConsumptionAndRemainingOrderSurviveRepositoryRecreation() = runTest {
        val deterministicSession = RoomSessionRepository(database, random = Random(19))
        deterministicSession.replaceQueue(
            listOf(track("song-1"), track("song-2"), track("song-3")),
            currentIndex = 1,
        )
        val shuffled = deterministicSession.setShuffleEnabled(true)
        deterministicSession.updatePlaybackStructure(
            currentIndex = shuffled.currentIndex + 1,
            shuffleEnabled = true,
            repeatMode = RepeatMode.PLAY_ONCE,
            repeatOnceConsumed = false,
        )

        val restored = requireNotNull(
            RoomSessionRepository(database, now = { 10_000L }).restoreOrResetTransient(),
        )

        assertThat(restored.entries.map { it.track.sourceMediaId })
            .containsExactlyElementsIn(shuffled.entries.map { it.track.sourceMediaId }).inOrder()
        assertThat(restored.currentIndex).isEqualTo(1)
        assertThat(restored.consumedQueueEntryIds).containsExactly(
            restored.entries[0].queueEntryId,
            restored.entries[1].queueEntryId,
        )
        assertThat(restored.entries.drop(restored.currentIndex + 1)).hasSize(1)
        assertThat(restored.entries.map { it.track.sourceMediaId }).contains("song-1")
    }

    @Test
    fun enablingShuffleAfterPreviousKeepsConsumedItemBehindCurrent() = runTest {
        session.replaceQueue(listOf(track("song-1"), track("song-2"), track("song-3")), currentIndex = 0)
        session.updatePlaybackStructure(
            currentIndex = 1,
            shuffleEnabled = false,
            repeatMode = RepeatMode.PLAY_ONCE,
            repeatOnceConsumed = false,
        )
        session.updatePlaybackStructure(
            currentIndex = 0,
            shuffleEnabled = false,
            repeatMode = RepeatMode.PLAY_ONCE,
            repeatOnceConsumed = false,
        )

        val shuffled = session.setShuffleEnabled(true)

        assertThat(shuffled.entries.map { it.track.sourceMediaId })
            .containsExactly("song-2", "song-1", "song-3").inOrder()
        assertThat(shuffled.currentIndex).isEqualTo(1)
    }

    @Test
    fun nextShuffleCycleAvoidsImmediateBoundaryRepeat() = runTest {
        session.replaceQueue(listOf(track("a"), track("b"), track("c")), currentIndex = 2, shuffleEnabled = true)
        val previousLast = requireNotNull(session.restoreOrResetTransient()).entries.last().queueEntryId

        val next = session.startNextShuffleCycle()

        assertThat(next.entries.first().queueEntryId).isNotEqualTo(previousLast)
        assertThat(next.entries.map { it.track.sourceMediaId }).containsExactly("a", "b", "c")
        assertThat(next.currentIndex).isEqualTo(0)
    }
}
