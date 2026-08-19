package com.admin.mymusicplayer.data.repository

import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.SourceType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomLibraryRepositoryTest : RoomRepositoryTestSupport() {
    @Test
    fun sourceIdentityPreventsExactDuplicatesButAllowsDifferentUploads() = runTest {
        val playlistId = library.createPlaylist("Saved")

        assertThat(library.addTrack(playlistId, track("upload-a", "Original title"))).isTrue()
        assertThat(library.addTrack(playlistId, track("upload-a", "Updated title"))).isFalse()
        assertThat(library.addTrack(playlistId, track("upload-b", "Original title"))).isTrue()

        val saved = library.playlists.first { it.singleOrNull()?.tracks?.size == 2 }.single()
        assertThat(saved.tracks.map { it.sourceMediaId }).containsExactly("upload-a", "upload-b").inOrder()
        assertThat(saved.tracks.first().title).isEqualTo("Updated title")
        assertThat(library.libraryCounts()).isEqualTo(Triple(2, 1, 2))
    }

    @Test
    fun movePreservesOrderSkipsTargetDuplicatesAndUndoRestoresBothPlaylists() = runTest {
        val sourceId = library.createPlaylist("Source")
        val targetId = library.createPlaylist("Target")
        listOf("a", "b", "c").forEach { library.addTrack(sourceId, track(it)) }
        library.addTrack(targetId, track("b"))
        val selected = setOf(identity("a"), identity("b"))

        val mutation = library.moveSelected(sourceId, targetId, selected)

        var playlists = library.playlists.first { rows ->
            rows.first { it.playlist.id == sourceId }.tracks.size == 1
        }
        assertThat(playlists.tracks(sourceId)).containsExactly("c").inOrder()
        assertThat(playlists.tracks(targetId)).containsExactly("b", "a").inOrder()
        assertThat(mutation.affectedCount).isEqualTo(2)

        library.restoreUndo(mutation.undoToken)

        playlists = library.playlists.first { rows ->
            rows.first { it.playlist.id == sourceId }.tracks.size == 3
        }
        assertThat(playlists.tracks(sourceId)).containsExactly("a", "b", "c").inOrder()
        assertThat(playlists.tracks(targetId)).containsExactly("b").inOrder()
    }

    @Test
    fun copyAndRemoveAreTransactionalAndUndoable() = runTest {
        val sourceId = library.createPlaylist("Source")
        val targetId = library.createPlaylist("Target")
        listOf("a", "b", "c").forEach { library.addTrack(sourceId, track(it)) }

        val copy = library.copySelected(sourceId, targetId, setOf(identity("c"), identity("a")))
        var playlists = library.playlists.first { rows ->
            rows.first { it.playlist.id == targetId }.tracks.size == 2
        }
        assertThat(playlists.tracks(targetId)).containsExactly("a", "c").inOrder()

        val remove = library.removeSelected(sourceId, setOf(identity("b")))
        playlists = library.playlists.first { rows ->
            rows.first { it.playlist.id == sourceId }.tracks.size == 2
        }
        assertThat(playlists.tracks(sourceId)).containsExactly("a", "c").inOrder()

        library.restoreUndo(remove.undoToken)
        library.restoreUndo(copy.undoToken)
        playlists = library.playlists.first { rows ->
            rows.first { it.playlist.id == sourceId }.tracks.size == 3 &&
                rows.first { it.playlist.id == targetId }.tracks.isEmpty()
        }
        assertThat(playlists.tracks(sourceId)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun manualReorderIsPersistentAndUndoable() = runTest {
        val playlistId = library.createPlaylist("Ordered")
        listOf("a", "b", "c").forEach { library.addTrack(playlistId, track(it)) }

        val mutation = library.reorderTrack(playlistId, identity("c"), 0)

        var playlist = library.playlists.first { it.singleOrNull()?.tracks?.firstOrNull()?.sourceMediaId == "c" }
        assertThat(playlist.tracks(playlistId)).containsExactly("c", "a", "b").inOrder()

        library.restoreUndo(mutation.undoToken)
        playlist = library.playlists.first { it.singleOrNull()?.tracks?.firstOrNull()?.sourceMediaId == "a" }
        assertThat(playlist.tracks(playlistId)).containsExactly("a", "b", "c").inOrder()
    }

    private fun identity(id: String) = SourceIdentity(SourceType.YOUTUBE, id)

    private fun List<LibraryPlaylist>.tracks(playlistId: Long): List<String> =
        first { it.playlist.id == playlistId }.tracks.map { it.sourceMediaId }
}
