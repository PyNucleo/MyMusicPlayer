package com.admin.mymusicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.admin.mymusicplayer.data.repository.SessionRepository
import com.admin.mymusicplayer.domain.RepeatMode
import com.admin.mymusicplayer.domain.ShufflePlanner
import com.admin.mymusicplayer.domain.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlaybackClientState(
    val connected: Boolean = false,
    val title: String = "Nothing playing",
    val uploader: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.PLAY_ONCE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

class PlaybackClient(
    context: Context,
    private val repository: SessionRepository,
) : PlaybackQueueController, AutoCloseable {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture: ListenableFuture<MediaController> = MediaController.Builder(
        applicationContext,
        SessionToken(applicationContext, ComponentName(applicationContext, PlaybackService::class.java)),
    ).buildAsync()
    private val _state = MutableStateFlow(PlaybackClientState())
    val state: StateFlow<PlaybackClientState> = _state.asStateFlow()
    private val currentMediaId = MutableStateFlow<String?>(null)
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateFromPlayer(player)
    }

    init {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }
                    .onSuccess { connected ->
                        controller = connected
                        connected.addListener(listener)
                        updateFromPlayer(connected)
                    }
                    .onFailure { throwable ->
                        _state.update { it.copy(error = throwable.message ?: "Playback service connection failed") }
                    }
            },
            ContextCompat.getMainExecutor(applicationContext),
        )
        scope.launch {
            repository.state.collect { persistent ->
                _state.update { current ->
                    current.copy(
                        title = persistent?.entries?.getOrNull(persistent.currentIndex)?.track?.title
                            ?: "Nothing playing",
                        uploader = persistent?.entries?.getOrNull(persistent.currentIndex)?.track?.uploader.orEmpty(),
                        shuffleEnabled = persistent?.shuffleEnabled ?: false,
                        repeatMode = persistent?.repeatMode ?: RepeatMode.PLAY_ONCE,
                    )
                }
            }
        }
        scope.launch {
            while (isActive) {
                controller?.let(::updateFromPlayer)
                delay(POSITION_REFRESH_MS)
            }
        }
    }

    override suspend fun playNow(track: Track) {
        val connected = awaitController()
        connected.pause()
        val updated = repository.playNow(track)
        val expectedMediaId = updated.entries[updated.currentIndex].queueEntryId.toString()
        withTimeout(PLAYER_SYNC_TIMEOUT_MS) {
            currentMediaId.filter { it == expectedMediaId }.first()
        }
        connected.play()
    }

    override suspend fun playNext(track: Track) {
        repository.playNext(track)
    }

    override suspend fun addToQueue(track: Track) {
        repository.addToQueue(track)
    }

    override suspend fun playPlaylist(
        tracks: List<Track>,
        selectedIndex: Int,
        playlistId: Long,
        playlistRevision: Long,
    ) {
        require(selectedIndex in tracks.indices) { "Selected track is outside the playlist" }
        val connected = awaitController()
        connected.pause()
        val shuffle = repository.restoreOrResetTransient()?.shuffleEnabled == true
        val selected = tracks[selectedIndex]
        val planned = if (shuffle) {
            ShufflePlanner.planFromExplicitSelection(
                eligible = tracks,
                selectedIdentity = selected.sourceIdentity,
                identity = { it.sourceIdentity },
            )
        } else {
            tracks
        }
        val updated = repository.replaceQueue(
            tracks = planned,
            currentIndex = if (shuffle) 0 else selectedIndex,
            originPlaylistId = playlistId,
            originPlaylistRevision = playlistRevision,
            shuffleEnabled = shuffle,
        )
        val expectedMediaId = updated.entries[updated.currentIndex].queueEntryId.toString()
        withTimeout(PLAYER_SYNC_TIMEOUT_MS) {
            currentMediaId.filter { it == expectedMediaId }.first()
        }
        connected.play()
    }

    fun togglePlayPause() {
        scope.launch {
            val connected = awaitController()
            if (connected.isPlaying) connected.pause() else connected.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun next() {
        scope.launch {
            resetRepeatForManualNavigation()
            awaitController().seekToNextMediaItem()
        }
    }

    fun previous() {
        scope.launch {
            resetRepeatForManualNavigation()
            val connected = awaitController()
            if (connected.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
                connected.seekTo(0)
            } else {
                connected.seekToPreviousMediaItem()
            }
        }
    }

    fun toggleShuffle() {
        scope.launch {
            runCatching {
                val persistent = repository.restoreOrResetTransient() ?: return@runCatching
                repository.setShuffleEnabled(!persistent.shuffleEnabled)
            }.onFailure(::publishError)
        }
    }

    fun reshuffle() {
        scope.launch { runCatching { repository.reshuffle() }.onFailure(::publishError) }
    }

    fun cycleRepeat() {
        scope.launch {
            runCatching {
                val persistent = repository.restoreOrResetTransient() ?: return@runCatching
                repository.updatePlaybackStructure(
                    currentIndex = persistent.currentIndex,
                    shuffleEnabled = persistent.shuffleEnabled,
                    repeatMode = persistent.repeatMode.next(),
                    repeatOnceConsumed = false,
                )
            }.onFailure(::publishError)
        }
    }

    suspend fun clearPlaybackCache(): Int {
        awaitController()
        val completion = kotlinx.coroutines.CompletableDeferred<Int>()
        PlaybackCacheRequests.channel.send(ClearCacheRequest(completion))
        return completion.await()
    }

    private suspend fun resetRepeatForManualNavigation() {
        val persistent = repository.restoreOrResetTransient() ?: return
        repository.updatePlaybackStructure(
            currentIndex = persistent.currentIndex,
            shuffleEnabled = persistent.shuffleEnabled,
            repeatMode = RepeatMode.PLAY_ONCE,
            repeatOnceConsumed = false,
        )
    }

    private fun updateFromPlayer(player: Player) {
        currentMediaId.value = player.currentMediaItem?.mediaId
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: 0
        _state.update { current ->
            current.copy(
                connected = true,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = duration,
                error = player.playerError?.message,
            )
        }
    }

    private suspend fun awaitController(): MediaController {
        controller?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            controllerFuture.addListener(
                {
                    runCatching { controllerFuture.get() }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(applicationContext),
            )
        }
    }

    private fun publishError(throwable: Throwable) {
        _state.update { it.copy(error = throwable.message ?: "Playback action failed") }
    }

    override fun close() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
        scope.cancel()
    }

    private companion object {
        const val POSITION_REFRESH_MS = 500L
        const val PLAYER_SYNC_TIMEOUT_MS = 10_000L
        const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
    }
}
