package com.admin.mymusicplayer.ui.player

import androidx.lifecycle.ViewModel
import com.admin.mymusicplayer.domain.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val title: String = "Night Drive",
    val uploader: String = "Demo artist",
    val isPlaying: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.PLAY_ONCE,
    val positionMs: Long = 42_000,
    val durationMs: Long = 210_000,
)

sealed interface PlayerEvent {
    data object TogglePlay : PlayerEvent
    data object Previous : PlayerEvent
    data object Next : PlayerEvent
    data object ToggleShuffle : PlayerEvent
    data object CycleRepeat : PlayerEvent
    data class Seek(val positionMs: Long) : PlayerEvent
}

class PlayerViewModel : ViewModel() {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun onEvent(event: PlayerEvent) {
        val current = _state.value
        _state.value = when (event) {
            PlayerEvent.TogglePlay -> current.copy(isPlaying = !current.isPlaying)
            PlayerEvent.Previous -> current.copy(positionMs = 0, repeatMode = RepeatMode.PLAY_ONCE)
            PlayerEvent.Next -> current.copy(positionMs = 0, repeatMode = RepeatMode.PLAY_ONCE)
            PlayerEvent.ToggleShuffle -> current.copy(shuffleEnabled = !current.shuffleEnabled)
            PlayerEvent.CycleRepeat -> current.copy(repeatMode = current.repeatMode.next())
            is PlayerEvent.Seek -> current.copy(positionMs = event.positionMs.coerceIn(0, current.durationMs))
        }
    }
}

