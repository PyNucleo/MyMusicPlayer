package com.admin.mymusicplayer.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.mymusicplayer.data.repository.SessionRepository
import com.admin.mymusicplayer.domain.QueueSnapshot
import com.admin.mymusicplayer.domain.QueueTransformations
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QueueUiState(
    val snapshot: QueueSnapshot<Track> = QueueSnapshot(emptyList(), 0),
)

sealed interface QueueEvent {
    data class MoveUp(val index: Int) : QueueEvent
    data class MoveDown(val index: Int) : QueueEvent
    data class Remove(val index: Int) : QueueEvent
    data object ClearUpcoming : QueueEvent
}

class QueueViewModel(
    private val repository: SessionRepository? = null,
) : ViewModel() {
    private val seeded = listOf("Night Drive", "Blue Static", "Quiet Signal", "After Midnight")
        .mapIndexed { index, title ->
            Track(
                id = index + 1L,
                sourceType = SourceType.YOUTUBE,
                sourceMediaId = "fake-queue-$index",
                title = title,
                uploader = "Demo artist",
            )
        }
    private val _state = MutableStateFlow(QueueUiState(QueueSnapshot(seeded, 0)))
    val state: StateFlow<QueueUiState> = _state.asStateFlow()

    init {
        repository?.let { persistentRepository ->
            _state.value = QueueUiState()
            viewModelScope.launch {
                persistentRepository.state.collect { persistent ->
                    _state.value = QueueUiState(
                        persistent?.let { state ->
                            QueueSnapshot(state.entries.map { it.track }, state.currentIndex)
                        } ?: QueueSnapshot(emptyList(), 0),
                    )
                }
            }
        }
    }

    fun onEvent(event: QueueEvent) {
        val persistentRepository = repository
        if (persistentRepository != null) {
            viewModelScope.launch {
                runCatching {
                    when (event) {
                        is QueueEvent.MoveUp -> persistentRepository.reorderUpcoming(event.index, event.index - 1)
                        is QueueEvent.MoveDown -> persistentRepository.reorderUpcoming(event.index, event.index + 1)
                        is QueueEvent.Remove -> persistentRepository.removeUpcoming(event.index)
                        QueueEvent.ClearUpcoming -> persistentRepository.clearUpcoming()
                    }
                }
            }
            return
        }
        val snapshot = _state.value.snapshot
        _state.value = QueueUiState(
            when (event) {
                is QueueEvent.MoveUp -> if (event.index > snapshot.currentIndex + 1) {
                    QueueTransformations.reorderUpcoming(snapshot, event.index, event.index - 1)
                } else snapshot
                is QueueEvent.MoveDown -> if (event.index in (snapshot.currentIndex + 1) until snapshot.entries.lastIndex) {
                    QueueTransformations.reorderUpcoming(snapshot, event.index, event.index + 1)
                } else snapshot
                is QueueEvent.Remove -> QueueTransformations.removeUpcoming(snapshot, event.index)
                QueueEvent.ClearUpcoming -> QueueTransformations.clearUpcoming(snapshot)
            },
        )
    }
}
