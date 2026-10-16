package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.domain.SourceIdentity
import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.diagnostics.DiagnosticLogger
import com.admin.mymusicplayer.resolver.AudioResolver
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class DownloadStatus { DOWNLOADING, SAVED, FAILED }

data class AudioDownload(
    val track: Track,
    val status: DownloadStatus,
    val bytes: Long = 0,
    val totalBytes: Long? = null,
    val error: String? = null,
)

/** App-scoped work: navigation does not cancel it; process death requires an explicit retry. */
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
                val saved = withContext(ioDispatcher) {
                    store.cleanInterruptedFiles()
                    store.readAll()
                }
                _downloads.value = saved.map { AudioDownload(it.track, DownloadStatus.SAVED, it.bytes, it.bytes) }
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
                publish(AudioDownload(track, DownloadStatus.DOWNLOADING))
                diagnostics?.log("AUDIO_DOWNLOAD_STARTED")
                try {
                    withContext(ioDispatcher) {
                        val partial = store.partialFile(identity)
                        try {
                            val audio = withTimeout(20_000) { resolver.resolve(track) }
                            var lastUpdate = 0L
                            downloader.download(audio, partial) { bytes, total ->
                                // Throttle UI updates, including streams without Content-Length.
                                if (bytes - lastUpdate >= 256 * 1024 || bytes == total) {
                                    lastUpdate = bytes
                                    _downloads.update { entries -> entries.map {
                                        if (it.track.sourceIdentity == identity && it.status == DownloadStatus.DOWNLOADING) {
                                            it.copy(bytes = bytes, totalBytes = total)
                                        } else it
                                    } }
                                }
                            }
                            currentCoroutineContext().ensureActive()
                            val saved = store.commit(track, partial, audio.mimeType)
                            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
                                publish(AudioDownload(track, DownloadStatus.SAVED, saved.bytes, saved.bytes))
                                diagnostics?.log("AUDIO_DOWNLOAD_SAVED")
                            }
                        } finally {
                            partial.delete()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    diagnostics?.log("AUDIO_DOWNLOAD_INTERRUPTED")
                    // A commit can finish immediately before cancellation. Keep that complete download.
                    val saved = withContext(kotlinx.coroutines.NonCancellable + ioDispatcher) { store.find(track) }
                    if (saved == null) publish(AudioDownload(track, DownloadStatus.FAILED, error = "Download interrupted. Retry when ready"))
                    throw cancelled
                } catch (failure: Exception) {
                    diagnostics?.log("AUDIO_DOWNLOAD_FAILED", (failure as? SourceFailure)?.kind?.name ?: "NETWORK_OR_STORAGE")
                    // Do not expose temporary URLs through exception text.
                    val message = when ((failure as? SourceFailure)?.kind) {
                        SourceFailureKind.ACCESS_RESTRICTED -> "The source restricts access to this audio. Download stopped"
                        SourceFailureKind.ANTI_BOT_CHALLENGE -> "The source requires an access challenge. Download stopped"
                        else -> "Audio could not be downloaded. Check the connection and free storage, then retry"
                    }
                    publish(AudioDownload(track, DownloadStatus.FAILED, error = message))
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

    override fun close() { scope.cancel() }
}
