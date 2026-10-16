package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.resolver.PlayableAudio
import com.admin.mymusicplayer.source.SourceFailure
import com.admin.mymusicplayer.source.SourceFailureKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

fun interface AudioDownloader {
    suspend fun download(audio: PlayableAudio, destination: File, onProgress: (Long, Long?) -> Unit)
}

/** Progressive audio only; no credentials, challenge handling, or access-control retries. */
class HttpAudioDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build(),
) : AudioDownloader {
    override suspend fun download(audio: PlayableAudio, destination: File, onProgress: (Long, Long?) -> Unit): Unit = coroutineScope {
        val request = Request.Builder().url(audio.uri).build()
        require(request.url.isHttps) { "Downloads require HTTPS audio" }
        val call = client.newCall(request)
        val cancellation = launch(Dispatchers.IO) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            call.execute().use { response ->
                when (response.code) {
                    401, 403 -> throw SourceFailure(SourceFailureKind.ACCESS_RESTRICTED, "Downloading this audio is restricted")
                    429 -> throw SourceFailure(SourceFailureKind.ANTI_BOT_CHALLENGE, "The source is limiting requests. Download stopped")
                }
                if (!response.isSuccessful) throw IOException("Audio download failed (HTTP ${response.code})")
                val body = response.body
                val type = body.contentType()?.let { "${it.type}/${it.subtype}" }
                if (type != null && !type.startsWith("audio/") && type !in ALLOWED_TYPES) {
                    throw IOException("The source did not return a downloadable audio file")
                }
                val length = body.contentLength().takeIf { it >= 0 }
                if (length != null && length > MAX_DOWNLOAD_BYTES) throw IOException("Audio exceeds the 512 MB download limit")
                var copied = 0L
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > MAX_DOWNLOAD_BYTES) throw IOException("Audio exceeds the 512 MB download limit")
                            output.write(buffer, 0, count)
                            onProgress(copied, length)
                        }
                        output.fd.sync()
                    }
                }
                if (copied == 0L || (length != null && copied != length)) throw IOException("Audio download was incomplete")
            }
        } finally {
            cancellation.cancel()
        }
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L
        val ALLOWED_TYPES = setOf("application/octet-stream", "application/ogg", "video/mp4", "video/webm")
    }
}
