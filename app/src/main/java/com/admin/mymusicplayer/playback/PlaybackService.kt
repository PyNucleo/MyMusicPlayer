package com.admin.mymusicplayer.playback

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.admin.mymusicplayer.MusicPlayerApplication
import com.admin.mymusicplayer.data.repository.PersistentPlaybackState
import com.admin.mymusicplayer.data.repository.SessionRepository
import com.admin.mymusicplayer.domain.RepeatMode
import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.resolver.AudioResolver
import com.admin.mymusicplayer.resolver.PlayableAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SessionRepository
    private lateinit var audioResolver: AudioResolver
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var cache: SimpleCache
    private val tracksByQueueEntryId = ConcurrentHashMap<Long, Track>()
    private val resolvedAudio = ConcurrentHashMap<Long, PlayableAudio>()
    private var currentPersistentState: PersistentPlaybackState? = null
    private var timelineIds: List<Long> = emptyList()
    private var synchronizingTimeline = false
    private var preResolveJob: Job? = null
    private var playbackRetryCount = 0
    private val diagnostics
        get() = (application as MusicPlayerApplication).container.diagnostics

    override fun onCreate() {
        super.onCreate()
        val container = (application as MusicPlayerApplication).container
        repository = container.sessionRepository
        audioResolver = container.audioResolver
        cache = SimpleCache(
            File(cacheDir, PLAYBACK_CACHE_DIRECTORY),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(this),
        )
        val upstream = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
        val cached = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val resolving = ResolvingDataSource.Factory(cached, ::resolveDataSpec)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, player).build()
        serviceScope.launch {
            repository.state.collectLatest { persistent ->
                currentPersistentState = persistent
                synchronizePlayer(persistent)
            }
        }
        serviceScope.launch {
            while (true) {
                delay(POSITION_CHECKPOINT_MS)
                if (player.isPlaying) checkpointPosition()
            }
        }
        serviceScope.launch {
            for (request in PlaybackCacheRequests.channel) {
                runCatching {
                    val wasPlaying = player.isPlaying
                    player.pause()
                    val keys = cache.keys.toList()
                    keys.forEach(cache::removeResource)
                    if (player.mediaItemCount > 0) player.prepare()
                    if (wasPlaying) player.play()
                    keys.size
                }.onSuccess(request.completion::complete)
                    .onFailure(request.completion::completeExceptionally)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        checkpointPosition()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        checkpointPosition()
        preResolveJob?.cancel()
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        cache.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun synchronizePlayer(persistent: PersistentPlaybackState?) {
        if (persistent == null || persistent.entries.isEmpty()) {
            timelineIds = emptyList()
            tracksByQueueEntryId.clear()
            resolvedAudio.clear()
            player.clearMediaItems()
            return
        }
        val newTimelineIds = persistent.entries.map { it.queueEntryId }
        persistent.entries.forEach { tracksByQueueEntryId[it.queueEntryId] = it.track }
        tracksByQueueEntryId.keys.retainAll(newTimelineIds.toSet())
        resolvedAudio.keys.retainAll(newTimelineIds.toSet())
        synchronizingTimeline = true
        try {
            if (timelineIds != newTimelineIds) {
                val shouldPlay = player.playWhenReady
                player.setMediaItems(
                    persistent.entries.map { item -> item.toMediaItem() },
                    persistent.currentIndex,
                    persistent.currentPositionMs,
                )
                timelineIds = newTimelineIds
                player.prepare()
                player.playWhenReady = shouldPlay
                playbackRetryCount = 0
            } else if (player.currentMediaItemIndex != persistent.currentIndex) {
                player.seekTo(persistent.currentIndex, persistent.currentPositionMs)
            }
            applyRepeatMode(persistent)
        } finally {
            synchronizingTimeline = false
        }
        preResolveNext(persistent)
    }

    private fun PersistentPlaybackState.toNextEntryId(): Long? =
        entries.getOrNull(currentIndex + 1)?.queueEntryId

    private fun preResolveNext(persistent: PersistentPlaybackState) {
        preResolveJob?.cancel()
        val nextId = persistent.toNextEntryId() ?: return
        val track = tracksByQueueEntryId[nextId] ?: return
        preResolveJob = serviceScope.launch(Dispatchers.IO) {
            runCatching { audioResolver.resolve(track) }
                .onSuccess { resolvedAudio[nextId] = it }
        }
    }

    @Throws(IOException::class)
    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val queueEntryId = dataSpec.uri.lastPathSegment?.toLongOrNull()
            ?: throw IOException("Invalid queue media URI")
        val track = tracksByQueueEntryId[queueEntryId]
            ?: throw IOException("Queue entry is no longer available")
        val cachedResolution = resolvedAudio[queueEntryId]?.takeUnless { playable ->
            playable.expiresAtEpochMs?.let { it <= System.currentTimeMillis() + URL_EXPIRY_MARGIN_MS } == true
        }
        val playable = cachedResolution ?: runBlocking(Dispatchers.IO) {
            withTimeout(RESOLVE_TIMEOUT_MS) { audioResolver.resolve(track) }
        }.also { resolvedAudio[queueEntryId] = it }
        return dataSpec.buildUpon()
            .setUri(playable.uri.toUri())
            .setKey(track.cacheKey)
            .build()
    }

    private val Track.cacheKey: String
        get() = "${sourceType.name}:$sourceMediaId"

    private fun com.admin.mymusicplayer.data.repository.PersistentQueueItem.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(queueEntryId.toString())
            .setUri("mymusicplayer://queue/$queueEntryId")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.uploader)
                    .setArtworkUri(track.thumbnailUrl?.toUri())
                    .build(),
            )
            .build()

    private fun applyRepeatMode(persistent: PersistentPlaybackState) {
        player.repeatMode = when (persistent.repeatMode) {
            RepeatMode.PLAY_ONCE -> Player.REPEAT_MODE_OFF
            RepeatMode.REPEAT_ONCE -> if (persistent.repeatOnceConsumed) {
                Player.REPEAT_MODE_OFF
            } else {
                Player.REPEAT_MODE_ONE
            }
            RepeatMode.REPEAT_FOREVER -> Player.REPEAT_MODE_ONE
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (synchronizingTimeline || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
            playbackRetryCount = 0
            serviceScope.launch { persistTransition(reason) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val persistent = currentPersistentState ?: return
                if (persistent.shuffleEnabled && persistent.currentIndex == persistent.entries.lastIndex) {
                    serviceScope.launch { repository.startNextShuffleCycle() }
                } else {
                    checkpointPosition()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) diagnostics.log("PLAYBACK_STARTED")
            if (!isPlaying) checkpointPosition()
        }

        override fun onPlayerError(error: PlaybackException) {
            diagnostics.log("PLAYBACK_ERROR", error.errorCodeName, error)
            if (playbackRetryCount < MAX_AUTOMATIC_RETRIES) {
                playbackRetryCount++
                val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
                if (playbackRetryCount > 1 && currentId != null) resolvedAudio.remove(currentId)
                player.prepare()
            }
        }
    }

    private suspend fun persistTransition(reason: Int) {
        val persistent = currentPersistentState ?: return
        val index = player.currentMediaItemIndex.takeIf { it in persistent.entries.indices } ?: return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            persistent.repeatMode == RepeatMode.REPEAT_ONCE &&
            !persistent.repeatOnceConsumed
        ) {
            repository.updatePlaybackStructure(
                currentIndex = index,
                shuffleEnabled = persistent.shuffleEnabled,
                repeatMode = RepeatMode.REPEAT_ONCE,
                repeatOnceConsumed = true,
            )
            withContext(Dispatchers.Main.immediate) { player.repeatMode = Player.REPEAT_MODE_OFF }
        } else if (index != persistent.currentIndex) {
            repository.updatePlaybackStructure(
                currentIndex = index,
                shuffleEnabled = persistent.shuffleEnabled,
                repeatMode = RepeatMode.PLAY_ONCE,
                repeatOnceConsumed = false,
            )
        }
    }

    private fun checkpointPosition() {
        if (!::repository.isInitialized || !::player.isInitialized || player.currentMediaItem == null) return
        val position = player.currentPosition.coerceAtLeast(0)
        serviceScope.launch(Dispatchers.IO) { runCatching { repository.checkpointPosition(position) } }
    }

    companion object {
        const val PLAYBACK_CACHE_DIRECTORY = "media3-playback-cache"
        const val MAX_CACHE_BYTES = 512L * 1024L * 1024L
        private const val POSITION_CHECKPOINT_MS = 5_000L
        private const val RESOLVE_TIMEOUT_MS = 20_000L
        private const val URL_EXPIRY_MARGIN_MS = 30_000L
        private const val MAX_AUTOMATIC_RETRIES = 2
    }
}
