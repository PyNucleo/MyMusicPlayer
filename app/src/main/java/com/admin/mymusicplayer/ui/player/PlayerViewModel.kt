package com.admin.mymusicplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.mymusicplayer.domain.RepeatMode
import com.admin.mymusicplayer.playback.PlaybackClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val title: String = "Night Drive",
    val uploader: String = "Demo artist",
    val isPlaying: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.PLAY_ONCE,
    val positionMs: Long = 42_000,
    val durationMs: Long = 210_000,
    val isBuffering: Boolean = false,
    val error: String? = null,
)

sealed interface PlayerEvent {
    data object TogglePlay : PlayerEvent
    data object Previous : PlayerEvent
    data object Next : PlayerEvent
    data object ToggleShuffle : PlayerEvent
    data object CycleRepeat : PlayerEvent
    data object Reshuffle : PlayerEvent
    data class Seek(val positionMs: Long) : PlayerEvent
}

class PlayerViewModel(
    private val playbackClient: PlaybackClient? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        playbackClient?.let { client ->
            viewModelScope.launch {
                client.state.collect { playback ->
                    _state.value = PlayerUiState(
                        title = playback.title,
                        uploader = playback.uploader,
                        isPlaying = playback.isPlaying,
                        shuffleEnabled = playback.shuffleEnabled,
                        repeatMode = playback.repeatMode,
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        isBuffering = playback.isBuffering,
                        error = playback.error,
                    )
                }
            }
        }
    }

    fun onEvent(event: PlayerEvent) {
        playbackClient?.let { client ->
            when (event) {
                PlayerEvent.TogglePlay -> client.togglePlayPause()
                PlayerEvent.Previous -> client.previous()
                PlayerEvent.Next -> client.next()
                PlayerEvent.ToggleShuffle -> client.toggleShuffle()
                PlayerEvent.CycleRepeat -> client.cycleRepeat()
                PlayerEvent.Reshuffle -> client.reshuffle()
                is PlayerEvent.Seek -> client.seekTo(event.positionMs)
            }
            return
        }
        val current = _state.value
        _state.value = when (event) {
            PlayerEvent.TogglePlay -> current.copy(isPlaying = !current.isPlaying)
            PlayerEvent.Previous -> current.copy(positionMs = 0, repeatMode = RepeatMode.PLAY_ONCE)
            PlayerEvent.Next -> current.copy(positionMs = 0, repeatMode = RepeatMode.PLAY_ONCE)
            PlayerEvent.ToggleShuffle -> current.copy(shuffleEnabled = !current.shuffleEnabled)
            PlayerEvent.CycleRepeat -> current.copy(repeatMode = current.repeatMode.next())
            PlayerEvent.Reshuffle -> current
            is PlayerEvent.Seek -> current.copy(positionMs = event.positionMs.coerceIn(0, current.durationMs))
        }
    }
}
