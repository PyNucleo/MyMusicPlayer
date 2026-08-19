package com.admin.mymusicplayer.domain

enum class RepeatMode {
    PLAY_ONCE,
    REPEAT_ONCE,
    REPEAT_FOREVER;

    fun next(): RepeatMode = when (this) {
        PLAY_ONCE -> REPEAT_ONCE
        REPEAT_ONCE -> REPEAT_FOREVER
        REPEAT_FOREVER -> PLAY_ONCE
    }
}

enum class PlaybackAction {
    REPLAY_CURRENT,
    ADVANCE,
    MOVE_PREVIOUS,
    SELECT,
    STOP,
}

data class RepeatTransition(
    val action: PlaybackAction,
    val repeatMode: RepeatMode,
    val repeatOnceConsumed: Boolean,
)

object RepeatPolicy {
    fun onCompletion(
        mode: RepeatMode,
        repeatOnceConsumed: Boolean,
        hasNext: Boolean,
    ): RepeatTransition = when (mode) {
        RepeatMode.PLAY_ONCE -> advanceOrStop(hasNext)
        RepeatMode.REPEAT_ONCE -> if (!repeatOnceConsumed) {
            RepeatTransition(PlaybackAction.REPLAY_CURRENT, RepeatMode.REPEAT_ONCE, true)
        } else {
            advanceOrStop(hasNext)
        }
        RepeatMode.REPEAT_FOREVER -> RepeatTransition(
            PlaybackAction.REPLAY_CURRENT,
            RepeatMode.REPEAT_FOREVER,
            false,
        )
    }

    fun onManualNext(hasNext: Boolean): RepeatTransition = advanceOrStop(hasNext)

    fun onManualPrevious(hasPrevious: Boolean): RepeatTransition = RepeatTransition(
        action = if (hasPrevious) PlaybackAction.MOVE_PREVIOUS else PlaybackAction.REPLAY_CURRENT,
        repeatMode = RepeatMode.PLAY_ONCE,
        repeatOnceConsumed = false,
    )

    fun onTrackSelected(): RepeatTransition = RepeatTransition(
        PlaybackAction.SELECT,
        RepeatMode.PLAY_ONCE,
        false,
    )

    fun onModeChanged(mode: RepeatMode): RepeatTransition = RepeatTransition(
        PlaybackAction.REPLAY_CURRENT,
        mode,
        false,
    )

    private fun advanceOrStop(hasNext: Boolean) = RepeatTransition(
        action = if (hasNext) PlaybackAction.ADVANCE else PlaybackAction.STOP,
        repeatMode = RepeatMode.PLAY_ONCE,
        repeatOnceConsumed = false,
    )
}

