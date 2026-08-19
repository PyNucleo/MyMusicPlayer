package com.admin.mymusicplayer.source.newpipe

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test

class NewPipeLiveIntegrationTest {
    @Test
    fun publicSearchResultResolvesToTemporaryAudioStream() = runTest {
        assumeTrue(System.getProperty("liveSourceTests") == "true")
        val runtime = NewPipeRuntime()
        val result = NewPipeSearchProvider(runtime).search("Rick Astley Never Gonna Give You Up").items.first()

        val audio = NewPipeAudioResolver(runtime).resolve(result.asTrack())

        assertThat(result.sourceMediaId).isNotEmpty()
        assertThat(audio.uri).startsWith("https://")
        assertThat(audio.mimeType).isNotEmpty()
    }
}
