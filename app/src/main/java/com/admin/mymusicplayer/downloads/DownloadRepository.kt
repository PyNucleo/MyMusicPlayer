package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.diagnostics.DiagnosticLogger
import com.admin.mymusicplayer.resolver.AudioResolver
import com.admin.mymusicplayer.resolver.PlayableAudio
import com.admin.mymusicplayer.source.SourceFailure
import com.admin.mymusicplayer.source.SourceFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class DownloadStatus { DOWNLOADING, RETRYING, PAUSED, SAVED, FAILED }

data class AudioDownload(
    val track: Track,
    val status: DownloadStatus,
    val bytes: Long = 0,
    val totalBytes: Long? = null,
    val error: String? = null,
)

/** Bounded app-scoped retries; interrupted work is restored as paused without storing stream URLs. */
class DownloadRepository(
    private val store: OfflineAudioStore,
    private val resolver: AudioResolver,
    private val downloader: AudioDownloader = HttpAudioDownloader(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val diagnostics: DiagnosticLogger? = null,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ready = CompletableDeferred<Unit>()
    private val jobs = mutableMapOf<SourceIdentity, Job>()
    private val removing = mutableSetOf<SourceIdentity>()
    private val _downloads = MutableStateFlow<List<AudioDownload>>(emptyList())
    val downloads = _downloads.asStateFlow()
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError = _storageError.asStateFlow()

    init {
        scope.launch {
            try {
                val restored = withContext(ioDispatcher) {
                    store.cleanInterruptedFiles()
                    store.readAll().map { AudioDownload(it.track, DownloadStatus.SAVED, it.bytes, it.bytes) } +
                        store.readPending().map {
                            AudioDownload(it, DownloadStatus.PAUSED, store.partialFile(it.sourceIdentity).length(),
                                error = "Progress saved. Tap Resume to continue")
                        }
                }
                _downloads.value = restored
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _storageError.value = "Download storage could not be opened"
            } finally {
                ready.complete(Unit)
            }
        }
    }

    fun download(track: Track) {
        scope.launch {
            ready.await()
            val identity = track.sourceIdentity
            if (identity in removing || jobs.containsKey(identity) || _downloads.value.any {
                    it.track.sourceIdentity == identity && it.status == DownloadStatus.SAVED
                }) return@launch
            val job = launch(start = CoroutineStart.LAZY) {
                val previous = _downloads.value.firstOrNull { it.track.sourceIdentity == identity }
                publish(AudioDownload(track, DownloadStatus.DOWNLOADING, previous?.bytes ?: 0, previous?.totalBytes))
                diagnostics?.log("AUDIO_DOWNLOAD_STARTED")
                try {
                    withContext(ioDispatcher) {
                        store.recordPending(track)
                        val partial = store.partialFile(identity)
                        var audio: PlayableAudio? = null
                        for (attempt in 0..MAX_AUTOMATIC_RETRIES) {
                            try {
                                val playable = audio?.takeUnless {
                                    it.expiresAtEpochMs?.let { expiry -> expiry <= System.currentTimeMillis() + 30_000 } == true
                                } ?: withTimeout(20_000) { resolver.resolve(track) }.also { audio = it }
                                var lastUpdate = -PROGRESS_STEP_BYTES
                                downloader.download(playable, partial) { bytes, total ->
                                    if (bytes == 0L || bytes - lastUpdate >= PROGRESS_STEP_BYTES || bytes == total) {
                                        lastUpdate = bytes
                                        _downloads.update { entries -> entries.map {
                                            if (it.track.sourceIdentity == identity) {
                                                it.copy(status = DownloadStatus.DOWNLOADING, bytes = bytes, totalBytes = total, error = null)
                                            } else it
                                        } }
                                    }
                                }
                                currentCoroutineContext().ensureActive()
                                val saved = store.commit(track, partial, playable.mimeType)
                                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
                                    publish(AudioDownload(track, DownloadStatus.SAVED, saved.bytes, saved.bytes))
                                    diagnostics?.log("AUDIO_DOWNLOAD_SAVED")
                                }
                                return@withContext
                            } catch (failure: Exception) {
                                if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                                if (!failure.canRetry()) throw failure
                                if (attempt == MAX_AUTOMATIC_RETRIES) {
                                    publish(AudioDownload(track, DownloadStatus.PAUSED, partial.length(),
                                        error = "Connection interrupted. Progress saved; tap Resume when online"))
                                    diagnostics?.log("AUDIO_DOWNLOAD_PAUSED")
                                    return@withContext
                                }
                                val waitMs = (2_000L shl attempt).coerceAtMost(30_000)
                                publish(AudioDownload(track, DownloadStatus.RETRYING, partial.length(),
                                    error = "Connection interrupted. Retrying in ${waitMs / 1_000} seconds"))
                                diagnostics?.log("AUDIO_DOWNLOAD_RETRYING")
                                delay(waitMs)
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    diagnostics?.log("AUDIO_DOWNLOAD_INTERRUPTED")
                    // A commit can finish immediately before cancellation. Keep that complete download.
                    val interrupted = withContext(kotlinx.coroutines.NonCancellable + ioDispatcher) {
                        store.find(track) to runCatching { store.partialFile(identity).length() }.getOrDefault(0L)
                    }
                    if (interrupted.first == null) publish(AudioDownload(track, DownloadStatus.PAUSED, interrupted.second,
                        error = "Download interrupted. Progress saved; tap Resume to continue"))
                    throw cancelled
                } catch (failure: Exception) {
                    diagnostics?.log("AUDIO_DOWNLOAD_FAILED", (failure as? SourceFailure)?.kind?.name ?: "NETWORK_OR_STORAGE")
                    // Do not expose temporary URLs through exception text.
                    val message = when ((failure as? SourceFailure)?.kind) {
                        SourceFailureKind.ACCESS_RESTRICTED -> "The source restricts access to this audio. Download stopped"
                        SourceFailureKind.ANTI_BOT_CHALLENGE -> "The source requires an access challenge. Download stopped"
                        else -> "Audio could not be downloaded. Check the connection and free storage, then retry"
                    }
                    val bytes = withContext(ioDispatcher) { runCatching { store.partialFile(identity).length() }.getOrDefault(0) }
                    publish(AudioDownload(track, DownloadStatus.FAILED, bytes, error = message))
                } finally {
                    jobs.remove(identity)
                }
            }
            jobs[identity] = job
            job.start()
        }
    }

    fun remove(identity: SourceIdentity) {
        scope.launch {
            ready.await()
            if (!removing.add(identity)) return@launch
            try {
                jobs[identity]?.cancelAndJoin()
                withContext(ioDispatcher) { store.remove(identity) }
                _downloads.update { entries -> entries.filterNot { it.track.sourceIdentity == identity } }
                diagnostics?.log("AUDIO_DOWNLOAD_REMOVED")
            } catch (_: Exception) {
                _storageError.value = "Could not remove the downloaded audio"
            } finally {
                removing.remove(identity)
            }
        }
    }

    private fun publish(download: AudioDownload) {
        _downloads.update { it.filterNot { entry -> entry.track.sourceIdentity == download.track.sourceIdentity } + download }
    }

    private fun Exception.canRetry(): Boolean =
        (this is DownloadTransferException && canRetry) ||
            (this is SourceFailure && kind == SourceFailureKind.NETWORK) || this is TimeoutCancellationException

    private companion object {
        const val MAX_AUTOMATIC_RETRIES = 5
        const val PROGRESS_STEP_BYTES = 256L * 1024L
    }

    override fun close() { scope.cancel() }
}
