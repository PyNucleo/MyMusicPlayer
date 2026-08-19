package com.admin.mymusicplayer.data.repository

import com.admin.mymusicplayer.backup.BackupPlaylist
import com.admin.mymusicplayer.backup.BackupTrack
import com.admin.mymusicplayer.backup.BackupTrackReference
import com.admin.mymusicplayer.backup.PortableBackup
import com.admin.mymusicplayer.backup.RoomBackupStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomBackupStoreTest : RoomRepositoryTestSupport() {
    @Test
    fun validatedBackupRoundTripRestoresOrderAndClearsOnlyTransientSession() = runTest {
        val sourceId = library.createPlaylist("Road trip")
        listOf("a", "b", "c").forEach { library.addTrack(sourceId, track(it)) }
        session.replaceQueue(listOf(track("a"), track("b")), currentIndex = 1)
        val store = RoomBackupStore(database) { 50_000L }
        val exported = store.export()
        val validated = store.validate(exported)
        library.deletePlaylist(sourceId)
        library.createPlaylist("Temporary")

        store.restore(validated)

        val restored = library.playlists.first { it.size == 1 && it.single().playlist.name == "Road trip" }.single()
        assertThat(restored.tracks.map { it.sourceMediaId }).containsExactly("a", "b", "c").inOrder()
        assertThat(session.restoreOrResetTransient()).isNull()
        assertThat(validated.summary.playlistCount).isEqualTo(1)
        assertThat(validated.summary.trackCount).isEqualTo(3)
        assertThat(validated.summary.entryCount).isEqualTo(3)
    }

    @Test
    fun validationRejectsMissingReferencesBeforeMutation() = runTest {
        val playlistId = library.createPlaylist("Keep")
        library.addTrack(playlistId, track("safe"))
        val invalid = PortableBackup(
            formatVersion = 1,
            applicationId = "com.admin.mymusicplayer",
            createdAtEpochMs = 1,
            tracks = listOf(
                BackupTrack(
                    sourceType = "YOUTUBE",
                    sourceMediaId = "known",
                    title = "Known",
                    availability = "UNKNOWN",
                ),
            ),
            playlists = listOf(
                BackupPlaylist(
                    name = "Broken",
                    entries = listOf(BackupTrackReference("YOUTUBE", "missing")),
                ),
            ),
        )

        val failure = runCatching { RoomBackupStore(database).validate(invalid) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(library.playlists.first().single().playlist.name).isEqualTo("Keep")
    }
}
