package com.admin.mymusicplayer.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        const val VERSION = 2
        const val FILE_NAME = "music-player.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE playback_sessions " +
                        "ADD COLUMN consumed_queue_entry_ids TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    """
                    UPDATE playback_sessions
                    SET consumed_queue_entry_ids = COALESCE(
                        (
                            SELECT CAST(queue_entry_id AS TEXT)
                            FROM queue_entries
                            WHERE queue_entries.session_id = playback_sessions.session_id
                                AND queue_entries.position = playback_sessions.current_queue_index
                        ),
                        ''
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
