package com.admin.mymusicplayer.source.newpipe

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewPipeRuntime(
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    init {
        initializeOnce(OkHttpNewPipeDownloader(httpClient))
    }

    private companion object {
        @Volatile
        private var initialized = false

        fun initializeOnce(downloader: Downloader) {
            if (initialized) return
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(downloader)
                    initialized = true
                }
            }
        }
    }
}

private class OkHttpNewPipeDownloader(
    private val client: OkHttpClient,
) : Downloader() {
    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val contentType = request.headers()["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull()
        val requestBody = request.dataToSend()?.toRequestBody(contentType)
            ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody(contentType) else null
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), requestBody)
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        if (request.headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            builder.header("User-Agent", USER_AGENT)
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("YouTube requested an anti-bot challenge", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
