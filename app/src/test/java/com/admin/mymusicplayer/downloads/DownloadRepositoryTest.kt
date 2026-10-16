package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.MainDispatcherRule
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.resolver.AudioResolver
import com.admin.mymusicplayer.resolver.PlayableAudio
import com.admin.mymusicplayer.source.SourceFailure
import com.admin.mymusicplayer.source.SourceFailureKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val temporary = TemporaryFolder()
    private val track = Track(sourceType = SourceType.YOUTUBE, sourceMediaId = "song", title = "Song")
    private val resolver = object : AudioResolver {
        override suspend fun resolve(track: Track) = PlayableAudio("https://example.com/temporary?secret=never-save", "audio/mp4")
    }

    @Test fun duplicateRequestsDownloadOnceAndOnlyCompletedAudioIsPersisted() = runTest {
        val directory = temporary.newFolder()
        val store = OfflineAudioStore(directory)
        var calls = 0
        val repository = DownloadRepository(store, resolver, AudioDownloader { _, file, progress ->
            calls++
            file.writeText("audio bytes")
            progress(file.length(), file.length())
        }, main.dispatcher)
        repository.download(track)
        repository.download(track)
        advanceUntilIdle()
        assertThat(calls).isEqualTo(1)
        assertThat(repository.downloads.value.single().status).isEqualTo(DownloadStatus.SAVED)
        assertThat(directory.listFiles()!!.single { it.extension == "json" }.readText()).doesNotContain("never-save")
        repository.close()
        val restored = DownloadRepository(store, resolver, ioDispatcher = main.dispatcher)
        advanceUntilIdle()
        assertThat(restored.downloads.value.single().status).isEqualTo(DownloadStatus.SAVED)
        restored.remove(track.sourceIdentity)
        advanceUntilIdle()
        assertThat(restored.downloads.value).isEmpty()
        assertThat(store.find(track)).isNull()
        restored.close()
    }

    @Test fun failedTransferCleansPartialAndCanRetry() = runTest {
        val store = OfflineAudioStore(temporary.newFolder())
        var fail = true
        val repository = DownloadRepository(store, resolver, AudioDownloader { _, file, _ ->
            file.writeText("bytes")
            if (fail) error("https://secret.example/temporary")
        }, main.dispatcher)
        repository.download(track)
        advanceUntilIdle()
        assertThat(repository.downloads.value.single().status).isEqualTo(DownloadStatus.FAILED)
        assertThat(repository.downloads.value.single().error).doesNotContain("secret")
        assertThat(store.find(track)).isNull()
        assertThat(store.partialFile(track.sourceIdentity).exists()).isFalse()
        fail = false
        repository.download(track)
        advanceUntilIdle()
        assertThat(repository.downloads.value.single().status).isEqualTo(DownloadStatus.SAVED)
        repository.close()
    }

    @Test fun cancelPreventsLateCompletionFromSavingAudio() = runTest {
        val store = OfflineAudioStore(temporary.newFolder())
        val gate = CompletableDeferred<Unit>()
        val repository = DownloadRepository(store, resolver, AudioDownloader { _, file, _ ->
            file.writeText("partial")
            gate.await()
        }, main.dispatcher)
        repository.download(track)
        advanceUntilIdle()
        repository.remove(track.sourceIdentity)
        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(store.find(track)).isNull()
        assertThat(repository.downloads.value).isEmpty()
        repository.close()
    }

    @Test fun restrictedSourceStopsBeforeTransferAndDoesNotRetryAutomatically() = runTest {
        var resolves = 0
        val restricted = object : AudioResolver {
            override suspend fun resolve(track: Track): PlayableAudio {
                resolves++
                throw SourceFailure(SourceFailureKind.ACCESS_RESTRICTED, "restricted")
            }
        }
        val repository = DownloadRepository(OfflineAudioStore(temporary.newFolder()), restricted,
            AudioDownloader { _, _, _ -> error("Must not transfer restricted content") }, main.dispatcher)
        repository.download(track)
        advanceUntilIdle()
        assertThat(resolves).isEqualTo(1)
        assertThat(repository.downloads.value.single().error).contains("restricts access")
        repository.close()
    }
}
