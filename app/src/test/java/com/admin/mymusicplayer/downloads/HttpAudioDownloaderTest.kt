package com.admin.mymusicplayer.downloads

import com.admin.mymusicplayer.resolver.PlayableAudio
import com.admin.mymusicplayer.source.SourceFailure
import com.admin.mymusicplayer.source.SourceFailureKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class HttpAudioDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val audio = PlayableAudio("https://example.com/audio")

    private fun downloader(code: Int = 200, type: String = "audio/mp4", bytes: String = "audio") = HttpAudioDownloader(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(bytes.toResponseBody(type.toMediaType())).build()
        }.build(),
    )

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
