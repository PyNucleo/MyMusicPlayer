package com.admin.mymusicplayer.ui.player

import com.admin.mymusicplayer.domain.RepeatMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerViewModelTest {
    @Test
    fun eventsProduceExplicitStateChangesAndManualNextResetsRepeat() {
        val viewModel = PlayerViewModel()
        viewModel.onEvent(PlayerEvent.TogglePlay)
        viewModel.onEvent(PlayerEvent.ToggleShuffle)
        viewModel.onEvent(PlayerEvent.CycleRepeat)
        viewModel.onEvent(PlayerEvent.CycleRepeat)

        assertThat(viewModel.state.value.isPlaying).isTrue()
        assertThat(viewModel.state.value.shuffleEnabled).isTrue()
        assertThat(viewModel.state.value.repeatMode).isEqualTo(RepeatMode.REPEAT_FOREVER)

        viewModel.onEvent(PlayerEvent.Next)
        assertThat(viewModel.state.value.repeatMode).isEqualTo(RepeatMode.PLAY_ONCE)
        assertThat(viewModel.state.value.positionMs).isEqualTo(0)
    }
}

