package com.admin.mymusicplayer.data.fake

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class FakeLibraryStoreTest {
    @Before
    fun setUp() = FakeLibraryStore.reset()

    @After
    fun tearDown() = FakeLibraryStore.reset()

    @Test
    fun copyAndMove_areAtomicSnapshotsAndPreserveSelectedSourceOrder() {
        val before = FakeLibraryStore.snapshot()
        val source = before.single { it.playlist.id == 1L }
        val selected = setOf(source.tracks[1].sourceIdentity, source.tracks[3].sourceIdentity)

        FakeLibraryStore.copySelected(1, 2, selected)
        val afterCopy = FakeLibraryStore.records.value.single { it.playlist.id == 2L }
        assertThat(afterCopy.tracks.map { it.title }).containsExactly(
            "Soft Current",
            "Distant Lights",
            "Blue Static",
            "After Midnight",
        ).inOrder()

        FakeLibraryStore.moveSelected(1, 2, selected)
        val afterMove = FakeLibraryStore.records.value
        assertThat(afterMove.single { it.playlist.id == 1L }.tracks.map { it.title }).containsExactly(
            "Night Drive",
            "Quiet Signal",
        ).inOrder()
        assertThat(afterMove.single { it.playlist.id == 2L }.tracks.map { it.title }).containsExactly(
            "Soft Current",
            "Distant Lights",
            "Blue Static",
            "After Midnight",
        ).inOrder()

        FakeLibraryStore.restore(before)
        assertThat(FakeLibraryStore.records.value).isEqualTo(before)
    }

    @Test
    fun exactSourceDuplicate_isRejectedButDifferentUploadsMayCoexist() {
        val source = FakeLibraryStore.records.value.single { it.playlist.id == 1L }.tracks.first()
        assertThat(FakeLibraryStore.addTrack(1, source.copy(title = "Renamed metadata"))).isFalse()
        assertThat(FakeLibraryStore.addTrack(1, source.copy(sourceMediaId = "different-upload"))).isTrue()
    }
}

