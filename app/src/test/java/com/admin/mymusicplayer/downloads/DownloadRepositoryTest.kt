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
import kotlinx.coroutines.test.runCurrent
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

    @Test fun failedTransferKeepsPartialAndCanRetry() = runTest {
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
        assertThat(store.partialFile(track.sourceIdentity).readText()).isEqualTo("bytes")
        assertThat(store.readPending()).containsExactly(track)
        fail = false
        repository.download(track)
        advanceUntilIdle()
        assertThat(repository.downloads.value.single().status).isEqualTo(DownloadStatus.SAVED)
        repository.close()
    }

    @Test fun transientFailureRetriesAutomaticallyWithoutDiscardingProgress() = runTest {
        val store = OfflineAudioStore(temporary.newFolder())
        var calls = 0
        val repository = DownloadRepository(store, resolver, AudioDownloader { _, file, progress ->
            if (++calls == 1) {
                file.writeText("hello")
                progress(5, 10)
                throw DownloadTransferException(true, "Disconnected")
            }
            assertThat(file.readText()).isEqualTo("hello")
            file.appendText("world")
            progress(10, 10)
        }, main.dispatcher)
        repository.download(track)
        runCurrent()
        assertThat(repository.downloads.value.single().status).isEqualTo(DownloadStatus.RETRYING)
        assertThat(repository.downloads.value.single().bytes).isEqualTo(5L)
        assertThat(calls).isEqualTo(1)
        advanceUntilIdle()
        assertThat(repository.downloads.value.single().status).isEqualTo(DownloadStatus.SAVED)
        assertThat(calls).isEqualTo(2)
        assertThat(store.readPending()).isEmpty()
        repository.close()
    }

    @Test fun repeatedNetworkFailurePausesAfterFiniteRetriesAndRetainsBytes() = runTest {
        val store = OfflineAudioStore(temporary.newFolder())
        var calls = 0
        val repository = DownloadRepository(store, resolver, AudioDownloader { _, file, _ ->
            calls++
            if (!file.exists()) file.writeText("saved prefix")
            throw DownloadTransferException(true, "No network")
        }, main.dispatcher)
        repository.download(track)
        advanceUntilIdle()
        assertThat(calls).isEqualTo(6)
        assertThat(repository.downloads.value.single().status).isEqualTo(DownloadStatus.PAUSED)
        assertThat(repository.downloads.value.single().bytes).isEqualTo(12L)
        assertThat(store.partialFile(track.sourceIdentity).readText()).isEqualTo("saved prefix")
        assertThat(store.find(track)).isNull()
        repository.close()
    }

    @Test fun pendingDownloadSurvivesRepositoryRecreationAndResumesFromItsPrefix() = runTest {
        val store = OfflineAudioStore(temporary.newFolder())
        val gate = CompletableDeferred<Unit>()
        val original = DownloadRepository(store, resolver, AudioDownloader { _, file, _ ->
            file.writeText("hello")
            gate.await()
        }, main.dispatcher)
        original.download(track)
        runCurrent()
        original.close()
        advanceUntilIdle()
        val restored = DownloadRepository(OfflineAudioStore(store.partialFile(track.sourceIdentity).parentFile!!), resolver,
            AudioDownloader { _, file, progress ->
                assertThat(file.readText()).isEqualTo("hello")
                file.appendText("world")
                progress(10, 10)
            }, main.dispatcher)
        runCurrent()
        assertThat(restored.downloads.value.single().status).isEqualTo(DownloadStatus.PAUSED)
        assertThat(restored.downloads.value.single().bytes).isEqualTo(5L)
        restored.download(track)
        advanceUntilIdle()
        assertThat(restored.downloads.value.single().status).isEqualTo(DownloadStatus.SAVED)
        assertThat(store.readAll().single().bytes).isEqualTo(10L)
        restored.close()
    }

    @Test fun cancellingDuringBackoffRemovesProgressAndPreventsLateRetry() = runTest {
        val store = OfflineAudioStore(temporary.newFolder())
        var calls = 0
        val repository = DownloadRepository(store, resolver, AudioDownloader { _, file, _ ->
            calls++
            file.writeText("partial")
            throw DownloadTransferException(true, "Disconnected")
        }, main.dispatcher)
        repository.download(track)
        runCurrent()
        repository.remove(track.sourceIdentity)
        advanceUntilIdle()
        assertThat(calls).isEqualTo(1)
        assertThat(repository.downloads.value).isEmpty()
        assertThat(store.readPending()).isEmpty()
        assertThat(store.partialFile(track.sourceIdentity).exists()).isFalse()
        repository.close()
    }

    @Test fun storageErrorsAreNotRetriedAsNetworkFailures() = runTest {
        val store = OfflineAudioStore(temporary.newFolder())
        var calls = 0
        val repository = DownloadRepository(store, resolver, AudioDownloader { _, _, _ ->
            calls++
            throw DownloadTransferException(false, "Storage full")
        }, main.dispatcher)
        repository.download(track)
        advanceUntilIdle()
        assertThat(calls).isEqualTo(1)
        assertThat(repository.downloads.value.single().status).isEqualTo(DownloadStatus.FAILED)
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
