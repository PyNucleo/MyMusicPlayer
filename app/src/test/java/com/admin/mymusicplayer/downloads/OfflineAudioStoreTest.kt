package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.resolver.AudioResolver
import com.admin.mymusicplayer.resolver.PlayableAudio
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineAudioStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val track = Track(sourceType = SourceType.YOUTUBE, sourceMediaId = "../test", title = "Offline song")

    @Test fun completeDownloadSurvivesRecreationAndBypassesNetworkResolution() = runBlocking {
        val directory = temporary.newFolder()
        val store = OfflineAudioStore(directory)
        val partial = store.partialFile(track.sourceIdentity)
        partial.writeBytes(byteArrayOf(1, 2, 3))
        store.commit(track, partial, "audio/webm")

        val restored = OfflineAudioStore(directory)
        val resolver = OfflineFirstAudioResolver(restored, object : AudioResolver {
            override suspend fun resolve(track: Track): PlayableAudio = error("Network must not be called offline")
        })
        assertThat(restored.readAll().single().track).isEqualTo(track)
        assertThat(File(java.net.URI(resolver.resolve(track).uri)).readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(directory.listFiles()!!.all { it.canonicalFile.parentFile == directory.canonicalFile }).isTrue()
        restored.remove(track.sourceIdentity)
        assertThat(restored.find(track)).isNull()
        assertThat(restored.readAll()).isEmpty()
    }

    @Test fun partialCorruptAndMissingFilesAreNeverPlayable() {
        val directory = temporary.newFolder()
        val store = OfflineAudioStore(directory)
        store.partialFile(track.sourceIdentity).writeText("incomplete")
        assertThat(store.find(track)).isNull()
        store.cleanInterruptedFiles()
        assertThat(directory.listFiles()).isEmpty()
        File(directory, "corrupt.json").writeText("{bad json")
        val partial = store.partialFile(track.sourceIdentity)
        partial.writeText("complete")
        store.commit(track, partial, "audio/mp4")
        directory.listFiles()!!.single { it.extension == "audio" }.writeText("truncated")
        assertThat(store.readAll()).isEmpty()
        assertThat(store.find(track)).isNull()
    }

    @Test fun missingDownloadFallsBackToNormalResolver() = runBlocking {
        val expected = PlayableAudio("https://example.com/transient")
        val resolver = OfflineFirstAudioResolver(OfflineAudioStore(temporary.newFolder()), object : AudioResolver {
            override suspend fun resolve(track: Track) = expected
        })
        assertThat(resolver.resolve(track)).isEqualTo(expected)
    }
}
