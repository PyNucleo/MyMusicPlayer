package com.admin.mymusicplayer.resolver

import com.admin.mymusicplayer.domain.Track

data class PlayableAudio(
    val uri: String,
    val mimeType: String? = null,
    val expiresAtEpochMs: Long? = null,
)

interface AudioResolver {
    suspend fun resolve(track: Track): PlayableAudio
}

