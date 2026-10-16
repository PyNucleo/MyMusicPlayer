package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.resolver.AudioResolver
import com.admin.mymusicplayer.resolver.PlayableAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfflineFirstAudioResolver(
    private val store: OfflineAudioStore,
    private val upstream: AudioResolver,
) : AudioResolver {
    override suspend fun resolve(track: Track): PlayableAudio = withContext(Dispatchers.IO) {
        store.find(track) ?: upstream.resolve(track)
    }
}
