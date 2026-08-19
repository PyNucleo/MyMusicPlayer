package com.admin.mymusicplayer.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RepeatPolicyTest {
    @Test
    fun playOnce_advancesAndResets() {
        assertThat(RepeatPolicy.onCompletion(RepeatMode.PLAY_ONCE, false, true)).isEqualTo(
            RepeatTransition(PlaybackAction.ADVANCE, RepeatMode.PLAY_ONCE, false),
        )
    }

    @Test
    fun repeatOnce_replaysExactlyOnceThenAdvances() {
        val first = RepeatPolicy.onCompletion(RepeatMode.REPEAT_ONCE, false, true)
        val second = RepeatPolicy.onCompletion(first.repeatMode, first.repeatOnceConsumed, true)

        assertThat(first).isEqualTo(
            RepeatTransition(PlaybackAction.REPLAY_CURRENT, RepeatMode.REPEAT_ONCE, true),
        )
        assertThat(second).isEqualTo(
            RepeatTransition(PlaybackAction.ADVANCE, RepeatMode.PLAY_ONCE, false),
        )
    }

    @Test
    fun repeatForever_replaysCurrentWithoutQueueMutationState() {
        assertThat(RepeatPolicy.onCompletion(RepeatMode.REPEAT_FOREVER, false, true)).isEqualTo(
            RepeatTransition(PlaybackAction.REPLAY_CURRENT, RepeatMode.REPEAT_FOREVER, false),
        )
    }

    @Test
    fun manualNext_exitsRepeatForeverAndResetsNewItem() {
        assertThat(RepeatPolicy.onManualNext(true)).isEqualTo(
            RepeatTransition(PlaybackAction.ADVANCE, RepeatMode.PLAY_ONCE, false),
        )
    }

    @Test
    fun completionAtEnd_stopsAndResets() {
        assertThat(RepeatPolicy.onCompletion(RepeatMode.PLAY_ONCE, false, false)).isEqualTo(
            RepeatTransition(PlaybackAction.STOP, RepeatMode.PLAY_ONCE, false),
        )
    }

    @Test
    fun previousAndExplicitSelection_resetPerItemRepeat() {
        assertThat(RepeatPolicy.onManualPrevious(true).repeatMode).isEqualTo(RepeatMode.PLAY_ONCE)
        assertThat(RepeatPolicy.onTrackSelected().repeatMode).isEqualTo(RepeatMode.PLAY_ONCE)
    }

    @Test
    fun modeCycle_isExactThreeStateOrder() {
        assertThat(RepeatMode.PLAY_ONCE.next()).isEqualTo(RepeatMode.REPEAT_ONCE)
        assertThat(RepeatMode.REPEAT_ONCE.next()).isEqualTo(RepeatMode.REPEAT_FOREVER)
        assertThat(RepeatMode.REPEAT_FOREVER.next()).isEqualTo(RepeatMode.PLAY_ONCE)
    }
}

