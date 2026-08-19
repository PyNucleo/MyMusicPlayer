package com.admin.mymusicplayer.resolver

import com.admin.mymusicplayer.domain.Track
import kotlinx.coroutines.delay

class FakeAudioResolver : AudioResolver {
    override suspend fun resolve(track: Track): PlayableAudio {
        delay(100)
        return PlayableAudio(
            uri = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
            mimeType = "audio/mpeg",
        )
    }
}

