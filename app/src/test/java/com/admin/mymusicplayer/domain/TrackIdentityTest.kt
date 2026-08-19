package com.admin.mymusicplayer.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackIdentityTest {
    @Test
    fun sameTitleDifferentSourceIds_areDistinct() {
        val first = Track(sourceType = SourceType.YOUTUBE, sourceMediaId = "upload-a", title = "Song")
        val second = Track(sourceType = SourceType.YOUTUBE, sourceMediaId = "upload-b", title = "Song")

        assertThat(first.sourceIdentity).isNotEqualTo(second.sourceIdentity)
    }

    @Test
    fun metadataChanges_doNotChangeSourceIdentity() {
        val first = Track(sourceType = SourceType.YOUTUBE, sourceMediaId = "abc", title = "Old")
        val second = Track(sourceType = SourceType.YOUTUBE, sourceMediaId = "abc", title = "New")

        assertThat(first.sourceIdentity).isEqualTo(second.sourceIdentity)
    }
}

