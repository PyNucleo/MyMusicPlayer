package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.resolver.PlayableAudio
import com.admin.mymusicplayer.source.SourceFailure
import com.admin.mymusicplayer.source.SourceFailureKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.SocketException
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer

class HttpAudioDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val audio = PlayableAudio("https://example.com/audio")

    private fun downloader(code: Int = 200, type: String = "audio/mp4", bytes: String = "audio") = HttpAudioDownloader(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(bytes.toResponseBody(type.toMediaType())).build()
        }.build(),
    )

    private fun interruptedBody(prefix: String = "hello", total: Long = 10): ResponseBody = object : ResponseBody() {
        private var sent = false
        private val input = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (sent) throw SocketException("Connection lost")
                sent = true
                sink.writeUtf8(prefix)
                return prefix.length.toLong()
            }
            override fun timeout() = Timeout.NONE
            override fun close() = Unit
        }.buffer()
        override fun contentLength() = total
        override fun contentType() = "audio/mp4".toMediaType()
        override fun source() = input
    }

    private fun response(request: Request, code: Int, body: ResponseBody, headers: Map<String, String> = emptyMap()): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body)
            .apply { headers.forEach { (name, value) -> header(name, value) } }.build()

    @Test fun interruptedAudioResumesOnlyRemainingBytesWithValidatorAndNoStoredUrl() = runBlocking {
        val destination = temporary.newFile()
        var calls = 0
        val transfer = HttpAudioDownloader(OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val request = chain.request()
            assertThat(request.header("Accept-Encoding")).isEqualTo("identity")
            if (calls == 1) response(request, 200, interruptedBody(), mapOf("ETag" to "\"one\""))
            else {
                assertThat(request.header("Range")).isEqualTo("bytes=5-")
                assertThat(request.header("If-Range")).isEqualTo("\"one\"")
                response(request, 206, "world".toResponseBody("audio/mp4".toMediaType()),
                    mapOf("ETag" to "\"one\"", "Content-Range" to "bytes 5-9/10"))
            }
        }.build())
        val failure = assertThrows(DownloadTransferException::class.java) {
            runBlocking { transfer.download(audio, destination) { _, _ -> } }
        }
        assertThat(failure.canRetry).isTrue()
        assertThat(destination.readText()).isEqualTo("hello")
        val state = java.io.File("${destination.path}.resume")
        assertThat(state.readText()).doesNotContain("example.com")
        var lastProgress = 0L
        // A fresh transient URL must still validate the representation before appending.
        transfer.download(audio.copy(uri = "https://example.com/fresh-url"), destination) { bytes, _ -> lastProgress = bytes }
        assertThat(destination.readText()).isEqualTo("helloworld")
        assertThat(lastProgress).isEqualTo(10L)
        assertThat(calls).isEqualTo(2)
    }

    @Test fun serverIgnoringRangeReplacesPartialInsteadOfAppending() = runBlocking {
        val destination = temporary.newFile()
        var calls = 0
        val transfer = HttpAudioDownloader(OkHttpClient.Builder().addInterceptor { chain ->
            if (++calls == 1) response(chain.request(), 200, interruptedBody(), mapOf("ETag" to "\"one\""))
            else response(chain.request(), 200, "new audio".toResponseBody("audio/mp4".toMediaType()), mapOf("ETag" to "\"two\""))
        }.build())
        assertThrows(DownloadTransferException::class.java) { runBlocking { transfer.download(audio, destination) { _, _ -> } } }
        transfer.download(audio, destination) { _, _ -> }
        assertThat(destination.readText()).isEqualTo("new audio")
    }

    @Test fun validIfRangeResponseMayOmitAlreadyKnownRepresentationHeaders() = runBlocking {
        val destination = temporary.newFile()
        var calls = 0
        val transfer = HttpAudioDownloader(OkHttpClient.Builder().addInterceptor { chain ->
            if (++calls == 1) response(chain.request(), 200, interruptedBody(), mapOf("ETag" to "\"one\""))
            else response(chain.request(), 206, "world".toResponseBody(), mapOf("Content-Range" to "bytes 5-9/10"))
        }.build())
        assertThrows(DownloadTransferException::class.java) { runBlocking { transfer.download(audio, destination) { _, _ -> } } }
        transfer.download(audio, destination) { _, _ -> }
        assertThat(destination.readText()).isEqualTo("helloworld")
    }

    @Test fun refusesWrongRangeChangedValidatorAndChangedRepresentation() {
        val badHeaders = listOf(
            mapOf("ETag" to "\"one\"", "Content-Range" to "bytes 4-8/10"),
            mapOf("ETag" to "\"two\"", "Content-Range" to "bytes 5-9/10"),
            mapOf("ETag" to "\"one\"", "Content-Range" to "bytes 5-9/11"),
            mapOf("ETag" to "\"one\""),
        )
        badHeaders.forEach { headers ->
            val destination = temporary.newFile()
            var calls = 0
            val transfer = HttpAudioDownloader(OkHttpClient.Builder().addInterceptor { chain ->
                if (++calls == 1) response(chain.request(), 200, interruptedBody(), mapOf("ETag" to "\"one\""))
                else response(chain.request(), 206, "world".toResponseBody("audio/mp4".toMediaType()), headers)
            }.build())
            assertThrows(DownloadTransferException::class.java) { runBlocking { transfer.download(audio, destination) { _, _ -> } } }
            val failure = assertThrows(DownloadTransferException::class.java) { runBlocking { transfer.download(audio, destination) { _, _ -> } } }
            assertThat(failure.canRetry).isFalse()
            assertThat(java.io.File("${destination.path}.resume").exists()).isFalse()
        }
    }

    @Test fun strongLastModifiedAllowsResumeButWeakEtagDoesNot() = runBlocking {
        val modified = "Fri, 16 Oct 2026 00:00:00 GMT"
        listOf(false, true).forEach { weakEtag ->
            val destination = temporary.newFile()
            var calls = 0
            val headers = mutableMapOf("Last-Modified" to modified, "Date" to "Sat, 17 Oct 2026 00:00:00 GMT")
            if (weakEtag) headers["ETag"] = "W/\"one\""
            val transfer = HttpAudioDownloader(OkHttpClient.Builder().addInterceptor { chain ->
                if (++calls == 1) response(chain.request(), 200, interruptedBody(), headers)
                else if (weakEtag) {
                    assertThat(chain.request().header("Range")).isNull()
                    response(chain.request(), 200, "helloworld".toResponseBody("audio/mp4".toMediaType()))
                } else {
                    assertThat(chain.request().header("If-Range")).isEqualTo(modified)
                    response(chain.request(), 206, "world".toResponseBody("audio/mp4".toMediaType()),
                        mapOf("Last-Modified" to modified, "Content-Range" to "bytes 5-9/10"))
                }
            }.build())
            assertThrows(DownloadTransferException::class.java) { runBlocking { transfer.download(audio, destination) { _, _ -> } } }
            transfer.download(audio, destination) { _, _ -> }
            assertThat(destination.readText()).isEqualTo("helloworld")
        }
    }

    @Test fun completedPartialIsAcceptedOnlyAfterMatching416Validator() = runBlocking {
        val destination = temporary.newFile()
        var calls = 0
        val transfer = HttpAudioDownloader(OkHttpClient.Builder().addInterceptor { chain ->
            if (++calls == 1) response(chain.request(), 200, interruptedBody("helloworld"), mapOf("ETag" to "\"one\""))
            else response(chain.request(), 416, "".toResponseBody(), mapOf("ETag" to "\"one\"", "Content-Range" to "bytes */10"))
        }.build())
        assertThrows(DownloadTransferException::class.java) { runBlocking { transfer.download(audio, destination) { _, _ -> } } }
        transfer.download(audio, destination) { _, _ -> }
        assertThat(destination.readText()).isEqualTo("helloworld")
        assertThat(calls).isEqualTo(2)
    }

    @Test fun unsatisfiableRangeMakesOneFreshRequestAndNeverLoops() = runBlocking {
        val destination = temporary.newFile()
        var calls = 0
        val transfer = HttpAudioDownloader(OkHttpClient.Builder().addInterceptor { chain ->
            when (++calls) {
                1 -> response(chain.request(), 200, interruptedBody(), mapOf("ETag" to "\"one\""))
                2 -> response(chain.request(), 416, "".toResponseBody())
                else -> {
                    assertThat(chain.request().header("Range")).isNull()
                    response(chain.request(), 200, "new audio".toResponseBody("audio/mp4".toMediaType()))
                }
            }
        }.build())
        assertThrows(DownloadTransferException::class.java) { runBlocking { transfer.download(audio, destination) { _, _ -> } } }
        transfer.download(audio, destination) { _, _ -> }
        assertThat(destination.readText()).isEqualTo("new audio")
        assertThat(calls).isEqualTo(3)
    }

    @Test fun savesCompleteResponseWithProgress() = runBlocking {
        val destination = temporary.newFile()
        var progress = 0L
        downloader().download(audio, destination) { bytes, _ -> progress = bytes }
        assertThat(destination.readText()).isEqualTo("audio")
        assertThat(progress).isEqualTo(5L)
    }

    @Test fun rejectsRestrictionsHtmlEmptyAndCleartext() {
        val denied = assertThrows(SourceFailure::class.java) {
            runBlocking { downloader(403).download(audio, temporary.newFile()) { _, _ -> } }
        }
        assertThat(denied.kind).isEqualTo(SourceFailureKind.ACCESS_RESTRICTED)
        val challenged = assertThrows(SourceFailure::class.java) {
            runBlocking { downloader(429).download(audio, temporary.newFile()) { _, _ -> } }
        }
        assertThat(challenged.kind).isEqualTo(SourceFailureKind.ANTI_BOT_CHALLENGE)
        assertThrows(IOException::class.java) {
            runBlocking { downloader(type = "text/html").download(audio, temporary.newFile()) { _, _ -> } }
        }
        assertThrows(IOException::class.java) {
            runBlocking { downloader(bytes = "").download(audio, temporary.newFile()) { _, _ -> } }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { downloader().download(PlayableAudio("http://example.com/audio"), temporary.newFile()) { _, _ -> } }
        }
    }
}
