package com.admin.mymusicplayer.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MusicDatabaseMigrationHostTest {
    @Test
    fun migrationOneToTwoPreservesEntitiesRelationshipsAndCurrentSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DATABASE)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DATABASE)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onConfigure(db: SupportSQLiteDatabase) {
                            db.setForeignKeyConstraintsEnabled(true)
                        }

                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersionOneSchema(db)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )

        try {
            val db = helper.writableDatabase
            seedVersionOneData(db)

            MusicDatabase.MIGRATION_1_2.migrate(db)

            assertThat(db.intValue("SELECT COUNT(*) FROM tracks")).isEqualTo(2)
            assertThat(db.intValue("SELECT COUNT(*) FROM playlists")).isEqualTo(1)
            assertThat(db.intValue("SELECT COUNT(*) FROM playlist_entries")).isEqualTo(2)
            assertThat(db.intValue("SELECT COUNT(*) FROM playback_sessions")).isEqualTo(1)
            assertThat(db.intValue("SELECT COUNT(*) FROM queue_entries")).isEqualTo(2)
            assertThat(
                db.intValue(
                    """
                    SELECT COUNT(*) FROM playlist_entries pe
                    JOIN playlists p ON p.id = pe.playlist_id
                    JOIN tracks t ON t.id = pe.track_id
                    """.trimIndent(),
                ),
            ).isEqualTo(2)
            assertThat(
                db.intValue(
                    """
                    SELECT COUNT(*) FROM queue_entries qe
                    JOIN playback_sessions ps ON ps.session_id = qe.session_id
                    JOIN tracks t ON t.id = qe.track_id
                    """.trimIndent(),
                ),
            ).isEqualTo(2)
            db.query(
                """
                SELECT current_queue_index, current_position_ms, repeat_mode,
                    repeat_once_consumed, consumed_queue_entry_ids
                FROM playback_sessions WHERE session_id = 1
                """.trimIndent(),
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(1)
                assertThat(cursor.getLong(1)).isEqualTo(42_500L)
                assertThat(cursor.getString(2)).isEqualTo("REPEAT_ONCE")
                assertThat(cursor.getInt(3)).isEqualTo(1)
                assertThat(cursor.getString(4)).isEqualTo("102")
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertThat(cursor.moveToFirst()).isFalse()
            }
        } finally {
            helper.close()
            context.deleteDatabase(TEST_DATABASE)
        }
    }

    private fun createVersionOneSchema(db: SupportSQLiteDatabase) {
        VERSION_ONE_SCHEMA.forEach(db::execSQL)
    }

    private fun seedVersionOneData(db: SupportSQLiteDatabase) {
        VERSION_ONE_DATA.forEach(db::execSQL)
    }

    private fun SupportSQLiteDatabase.intValue(query: String): Int = this.query(query).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private companion object {
        const val TEST_DATABASE = "migration-v1-v2-host-test"
        val VERSION_ONE_SCHEMA = listOf(
            """CREATE TABLE tracks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, source_type TEXT NOT NULL, source_media_id TEXT NOT NULL, title TEXT NOT NULL, uploader TEXT, duration_ms INTEGER, thumbnail_url TEXT, availability TEXT NOT NULL, created_at_epoch_ms INTEGER NOT NULL)""",
            """CREATE UNIQUE INDEX index_tracks_source_type_source_media_id ON tracks(source_type, source_media_id)""",
            """CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, created_at_epoch_ms INTEGER NOT NULL, modified_at_epoch_ms INTEGER NOT NULL, revision INTEGER NOT NULL)""",
            """CREATE INDEX index_playlists_name ON playlists(name)""",
            """CREATE TABLE playlist_entries (playlist_id INTEGER NOT NULL, track_id INTEGER NOT NULL, position INTEGER NOT NULL, added_at_epoch_ms INTEGER NOT NULL, PRIMARY KEY(playlist_id, track_id), FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(track_id) REFERENCES tracks(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""",
            """CREATE INDEX index_playlist_entries_track_id ON playlist_entries(track_id)""",
            """CREATE UNIQUE INDEX index_playlist_entries_playlist_id_position ON playlist_entries(playlist_id, position)""",
            """CREATE TABLE playback_sessions (session_id INTEGER NOT NULL, origin_playlist_id INTEGER, origin_playlist_revision INTEGER, shuffle_enabled INTEGER NOT NULL, current_queue_index INTEGER NOT NULL, current_position_ms INTEGER NOT NULL, repeat_mode TEXT NOT NULL, repeat_once_consumed INTEGER NOT NULL, created_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL, PRIMARY KEY(session_id))""",
            """CREATE TABLE queue_entries (session_id INTEGER NOT NULL, position INTEGER NOT NULL, queue_entry_id INTEGER NOT NULL, track_id INTEGER NOT NULL, PRIMARY KEY(session_id, position), FOREIGN KEY(session_id) REFERENCES playback_sessions(session_id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(track_id) REFERENCES tracks(id) ON UPDATE NO ACTION ON DELETE NO ACTION)""",
            """CREATE INDEX index_queue_entries_track_id ON queue_entries(track_id)""",
            """CREATE UNIQUE INDEX index_queue_entries_queue_entry_id ON queue_entries(queue_entry_id)""",
        )
        val VERSION_ONE_DATA = listOf(
            """INSERT INTO tracks VALUES (1, 'YOUTUBE', 'song-1', 'Song 1', 'Artist', 120000, NULL, 'AVAILABLE', 1)""",
            """INSERT INTO tracks VALUES (2, 'YOUTUBE', 'song-2', 'Song 2', 'Artist', 120000, NULL, 'AVAILABLE', 2)""",
            """INSERT INTO playlists VALUES (7, 'Keep me', 3, 4, 5)""",
            """INSERT INTO playlist_entries VALUES (7, 1, 0, 6)""",
            """INSERT INTO playlist_entries VALUES (7, 2, 1, 7)""",
            """INSERT INTO playback_sessions VALUES (1, 7, 5, 0, 1, 42500, 'REPEAT_ONCE', 1, 8, 9)""",
            """INSERT INTO queue_entries VALUES (1, 0, 101, 1)""",
            """INSERT INTO queue_entries VALUES (1, 1, 102, 2)""",
        )
    }
}
