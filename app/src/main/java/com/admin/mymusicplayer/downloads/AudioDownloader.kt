package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.resolver.PlayableAudio
import com.admin.mymusicplayer.source.SourceFailure
import com.admin.mymusicplayer.source.SourceFailureKind
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

fun interface AudioDownloader {
    suspend fun download(audio: PlayableAudio, destination: File, onProgress: (Long, Long?) -> Unit)
}

class DownloadTransferException(val canRetry: Boolean, message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** Resume metadata contains validators and lengths only, never a temporary stream URL. */
private data class ResumeCheckpoint(val validator: String, val etag: Boolean, val total: Long?, val mime: String?)

/** Progressive audio with conditional byte ranges; restrictions always stop without a retry. */
class HttpAudioDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build(),
) : AudioDownloader {
    private val gson = Gson()

    override suspend fun download(audio: PlayableAudio, destination: File, onProgress: (Long, Long?) -> Unit) {
        var checkpoint = readCheckpoint(destination)
        val offset = destination.length().takeIf { checkpoint != null } ?: 0L
        val builder = Request.Builder().url(audio.uri).header("Accept-Encoding", "identity")
        require(builder.build().url.isHttps) { "Downloads require HTTPS audio" }
        if (offset > 0) builder.header("Range", "bytes=$offset-").header("If-Range", checkpoint!!.validator)

        coroutineScope {
            val call = client.newCall(builder.build())
            val cancellation = launch(Dispatchers.IO) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                val response = try { call.execute() } catch (failure: IOException) {
                    throw DownloadTransferException(true, "Connection interrupted", failure)
                }
                response.use {
                    when (response.code) {
                        401, 403 -> throw SourceFailure(SourceFailureKind.ACCESS_RESTRICTED, "Downloading this audio is restricted")
                        429 -> throw SourceFailure(SourceFailureKind.ANTI_BOT_CHALLENGE, "The source is limiting requests. Download stopped")
                        404, 410 -> throw SourceFailure(SourceFailureKind.UNAVAILABLE, "This audio is unavailable")
                    }
                    // A previous read may have received all bytes before its final socket read failed.
                    if (response.code == 416 && checkpoint != null && offset > 0 &&
                        checkpoint!!.total == offset && response.header("Content-Range") == "bytes */$offset" &&
                        validatorMatches(response, checkpoint!!, requirePresent = true)
                    ) {
                        onProgress(offset, offset)
                        return@coroutineScope
                    }
                    if (response.code == 416 && offset > 0) {
                        destination.delete()
                        val state = checkpointFile(destination)
                        if (state.exists() && !state.delete()) throw DownloadTransferException(false, "Cannot reset download progress")
                        // One fresh request after an unsatisfiable range; the next attempt has offset zero.
                        return@coroutineScope download(audio, destination, onProgress)
                    }
                    if (response.code !in setOf(200, 206)) {
                        throw DownloadTransferException(response.code in RETRYABLE_CODES, "Audio download failed (HTTP ${response.code})")
                    }
                    if (response.header("Content-Encoding")?.let { !it.equals("identity", true) } == true) {
                        rejectPartial(destination, "Compressed byte ranges cannot be resumed safely")
                    }
                    val body = response.body
                    val mime = body.contentType()?.let { "${it.type}/${it.subtype}" }
                    if (mime != null && (mime in MANIFEST_TYPES || (!mime.startsWith("audio/") && mime !in ALLOWED_TYPES))) {
                        rejectPartial(destination, "The source did not return downloadable audio")
                    }
                    val bodyLength = body.contentLength().takeIf { it >= 0 }
                    val append = response.code == 206
                    val total: Long?
                    val responseEnd: Long?
                    if (append) {
                        val range = response.header("Content-Range")?.let(CONTENT_RANGE::matchEntire)
                            ?.groupValues?.drop(1)?.map { value -> value.toLongOrNull() }
                        val start = range?.getOrNull(0)
                        val end = range?.getOrNull(1)
                        total = range?.getOrNull(2)
                        if (checkpoint == null || offset <= 0 || start != offset || end == null || total == null ||
                            end < offset || end >= total || (bodyLength != null && bodyLength != end - offset + 1) ||
                            (checkpoint!!.total != null && checkpoint!!.total != total) ||
                            (checkpoint!!.mime != null && mime != null && checkpoint!!.mime != mime) ||
                            !validatorMatches(response, checkpoint!!)
                        ) rejectPartial(destination, "The source returned a different or invalid audio range")
                        responseEnd = end + 1
                    } else {
                        // If-Range mismatch or no range support: replace the prefix instead of corrupting it.
                        total = bodyLength
                        responseEnd = total
                    }
                    if (total != null && total > MAX_DOWNLOAD_BYTES) rejectPartial(destination, "Audio exceeds the 512 MB download limit")

                    val output = try { FileOutputStream(destination, append) } catch (failure: IOException) {
                        throw DownloadTransferException(false, "Cannot write download storage", failure)
                    }
                    output.use {
                        checkpoint = if (append) checkpoint!!.copy(total = total, mime = mime ?: checkpoint!!.mime)
                            else response.checkpoint(total, mime)
                        saveCheckpoint(destination, checkpoint)
                        var copied = if (append) offset else 0L
                        onProgress(copied, total)
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = try { input.read(buffer) } catch (failure: IOException) {
                                    throw DownloadTransferException(true, "Connection interrupted", failure)
                                }
                                if (count < 0) break
                                if (copied + count > MAX_DOWNLOAD_BYTES || (responseEnd != null && copied + count > responseEnd)) {
                                    rejectPartial(destination, "The source returned an invalid audio length")
                                }
                                try { output.write(buffer, 0, count) } catch (failure: IOException) {
                                    throw DownloadTransferException(false, "Cannot write download storage", failure)
                                }
                                copied += count
                                onProgress(copied, total)
                            }
                        }
                        try { output.fd.sync() } catch (failure: IOException) {
                            throw DownloadTransferException(false, "Cannot save download storage", failure)
                        }
                        if (copied == 0L || (responseEnd != null && copied != responseEnd) || (total != null && copied != total)) {
                            throw DownloadTransferException(true, "Audio download was interrupted")
                        }
                    }
                }
            } finally {
                cancellation.cancel()
            }
        }
    }

    private fun Response.checkpoint(total: Long?, mime: String?): ResumeCheckpoint? {
        val entityTag = header("ETag")
        entityTag?.takeIf { it.startsWith('"') && it.endsWith('"') && it.length <= 512 }?.let {
            return ResumeCheckpoint(it, true, total, mime)
        }
        if (entityTag != null) return null // A weak ETag must not fall back to an If-Range date.
        val modified = header("Last-Modified") ?: return null
        val date = header("Date") ?: return null
        val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
            isLenient = false
        }
        // Use a conservative age margin when treating Last-Modified as a strong validator.
        val strong = runCatching { parser.parse(date)!!.time - parser.parse(modified)!!.time >= 60_000 }.getOrDefault(false)
        return if (strong) ResumeCheckpoint(modified, false, total, mime) else null
    }

    private fun validatorMatches(response: Response, checkpoint: ResumeCheckpoint, requirePresent: Boolean = false): Boolean {
        val current = response.header(if (checkpoint.etag) "ETag" else "Last-Modified")
        return if (current == null) !requirePresent else current == checkpoint.validator
    }

    private fun readCheckpoint(destination: File): ResumeCheckpoint? = runCatching {
        val state = gson.fromJson(checkpointFile(destination).readText(), ResumeCheckpoint::class.java)
        require(state.validator.isNotBlank() && state.validator.length <= 512 && '\r' !in state.validator && '\n' !in state.validator)
        require(!state.etag || (state.validator.startsWith('"') && state.validator.endsWith('"')))
        require(destination.length() in 0..MAX_DOWNLOAD_BYTES)
        require(state.total == null || state.total in destination.length()..MAX_DOWNLOAD_BYTES)
        state
    }.getOrNull()

    private fun saveCheckpoint(destination: File, checkpoint: ResumeCheckpoint?) {
        val state = checkpointFile(destination)
        try {
            if (checkpoint == null) state.delete()
            else state.outputStream().use { output ->
                output.write(gson.toJson(checkpoint).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
        } catch (failure: IOException) {
            throw DownloadTransferException(false, "Cannot save download progress", failure)
        }
    }

    private fun rejectPartial(destination: File, message: String): Nothing {
        destination.delete()
        checkpointFile(destination).delete()
        throw DownloadTransferException(false, message)
    }

    private fun checkpointFile(destination: File) = File("${destination.path}.resume")

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L
        val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
        val RETRYABLE_CODES = setOf(408, 500, 502, 503, 504)
        val ALLOWED_TYPES = setOf("application/octet-stream", "application/ogg", "video/mp4", "video/webm")
        val MANIFEST_TYPES = setOf("audio/mpegurl", "audio/x-mpegurl", "audio/vnd.apple.mpegurl")
    }
}
