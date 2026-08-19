package com.admin.mymusicplayer.source.newpipe

import com.google.common.truth.Truth.assertThat
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class NewPipeUrlParsingTest {
    @Test
    fun supportedYoutubeUrlShapesProduceStableVideoIdentityWithoutNetwork() {
        val factory = ServiceList.YouTube.streamLHFactory

        assertThat(factory.getId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(factory.getId("https://youtu.be/dQw4w9WgXcQ?t=12")).isEqualTo("dQw4w9WgXcQ")
        assertThat(factory.getId("https://www.youtube.com/shorts/dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun initializeExtractor() {
            NewPipeRuntime()
        }
    }
}
