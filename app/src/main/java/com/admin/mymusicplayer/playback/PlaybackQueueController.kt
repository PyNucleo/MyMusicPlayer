package com.admin.mymusicplayer.playback

import com.admin.mymusicplayer.domain.Track

interface PlaybackQueueController {
    suspend fun playNow(track: Track)
    suspend fun playNext(track: Track)
    suspend fun addToQueue(track: Track)
    suspend fun playPlaylist(
        tracks: List<Track>,
        selectedIndex: Int,
        playlistId: Long,
        playlistRevision: Long,
    )
}
