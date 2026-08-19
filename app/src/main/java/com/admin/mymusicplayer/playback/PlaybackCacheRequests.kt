package com.admin.mymusicplayer.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

internal data class ClearCacheRequest(val completion: CompletableDeferred<Int>)

internal object PlaybackCacheRequests {
    val channel = Channel<ClearCacheRequest>(Channel.RENDEZVOUS)
}
