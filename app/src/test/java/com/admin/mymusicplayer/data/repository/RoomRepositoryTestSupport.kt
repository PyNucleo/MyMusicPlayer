package com.admin.mymusicplayer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.admin.mymusicplayer.data.database.MusicDatabase
import com.admin.mymusicplayer.domain.SourceType
import com.admin.mymusicplayer.domain.Track
import org.junit.After
import org.junit.Before

abstract class RoomRepositoryTestSupport {
    protected lateinit var database: MusicDatabase
    protected lateinit var library: RoomLibraryRepository
    protected lateinit var session: RoomSessionRepository
    private var clock = 1_000L

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = RoomLibraryRepository(database) { clock++ }
        session = RoomSessionRepository(database, now = { clock++ })
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    protected fun track(id: String, title: String = id): Track = Track(
        sourceType = SourceType.YOUTUBE,
        sourceMediaId = id,
        title = title,
        uploader = "Test uploader",
        durationMs = 120_000,
    )
}
