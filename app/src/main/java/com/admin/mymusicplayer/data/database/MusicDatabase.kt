package com.admin.mymusicplayer.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.admin.mymusicplayer.data.dao.MusicDao

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        PlaybackSessionEntity::class,
        QueueEntryEntity::class,
    ],
    version = MusicDatabase.VERSION,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "music-player.db"
    }
}
