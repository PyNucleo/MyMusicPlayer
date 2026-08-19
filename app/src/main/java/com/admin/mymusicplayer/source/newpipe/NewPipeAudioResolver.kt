package com.admin.mymusicplayer.source.newpipe

import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track
import com.admin.mymusicplayer.diagnostics.DiagnosticLogger
import com.admin.mymusicplayer.resolver.AudioResolver
import com.admin.mymusicplayer.resolver.PlayableAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

class NewPipeAudioResolver(
    @Suppress("UNUSED_PARAMETER") runtime: NewPipeRuntime,
    private val diagnostics: DiagnosticLogger? = null,
) : AudioResolver {
    override suspend fun resolve(track: Track): PlayableAudio = withContext(Dispatchers.IO) {
        require(track.sourceType == SourceType.YOUTUBE) { "Unsupported source: ${track.sourceType}" }
        diagnostics?.log("AUDIO_RESOLUTION_STARTED")
        runCatching {
            val service = ServiceList.YouTube
            val canonicalUrl = service.streamLHFactory.getUrl(track.sourceMediaId)
            val info = StreamInfo.getInfo(service, canonicalUrl)
            val audio = info.audioStreams
                .asSequence()
                .filter { it.isUrl }
                .filter { it.content.isNotBlank() }
                .maxWithOrNull(compareBy({ it.averageBitrate }, { it.bitrate }))
                ?: error("No playable audio-only stream was returned")
            PlayableAudio(
                uri = audio.content,
                mimeType = audio.format?.mimeType,
                expiresAtEpochMs = Regex("(?:[?&])expire=(\\d+)").find(audio.content)
                    ?.groupValues?.get(1)?.toLongOrNull()?.times(1_000),
            )
        }.onSuccess { diagnostics?.log("AUDIO_RESOLUTION_SUCCESS") }
            .getOrElse {
                val mapped = it.asSourceFailure("Audio resolution")
                diagnostics?.log("AUDIO_RESOLUTION_FAILED", mapped.kind.name, mapped)
                throw mapped
            }
    }
}
