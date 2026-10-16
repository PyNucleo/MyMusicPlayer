package com.admin.mymusicplayer.downloads

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.test.core.app.ApplicationProvider
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@OptIn(UnstableApi::class)
class OfflinePlaybackDataSourceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun media3ReadsSavedBytesAndSeeksWithoutOpeningNetworkDataSource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val track = Track(sourceType = SourceType.YOUTUBE, sourceMediaId = "offline", title = "Offline")
        val store = OfflineAudioStore(temporary.newFolder())
        val partial = store.partialFile(track.sourceIdentity)
        partial.writeBytes(byteArrayOf(10, 20, 30, 40))
        store.commit(track, partial, "audio/mp4")
        val factory = DefaultDataSource.Factory(context, DataSource.Factory {
            object : DataSource by ByteArrayDataSource(byteArrayOf(99)) {
                override fun open(dataSpec: DataSpec): Long = error("Network/cache must not be opened")
            }
        })
        val source = factory.createDataSource()
        try {
            assertThat(source.open(DataSpec.Builder().setUri(store.find(track)!!.uri.toUri()).setPosition(2).build())).isEqualTo(2L)
            val bytes = ByteArray(2)
            assertThat(source.read(bytes, 0, bytes.size)).isEqualTo(2)
            assertThat(bytes).isEqualTo(byteArrayOf(30, 40))
        } finally {
            source.close()
        }
    }
}
