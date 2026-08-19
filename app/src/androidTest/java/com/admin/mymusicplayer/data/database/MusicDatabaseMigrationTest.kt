package com.admin.mymusicplayer.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MusicDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationOneToTwoPreservesEntitiesRelationshipsAndSession() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO tracks VALUES (1, 'YOUTUBE', 'song-1', 'Song 1', 'Artist', 120000, NULL, 'AVAILABLE', 1)",
            )
            execSQL("INSERT INTO tracks VALUES (2, 'YOUTUBE', 'song-2', 'Song 2', 'Artist', 120000, NULL, 'AVAILABLE', 2)")
            execSQL("INSERT INTO playlists VALUES (7, 'Keep me', 3, 4, 5)")
            execSQL("INSERT INTO playlist_entries VALUES (7, 1, 0, 6)")
            execSQL("INSERT INTO playlist_entries VALUES (7, 2, 1, 7)")
            execSQL("INSERT INTO playback_sessions VALUES (1, 7, 5, 0, 1, 42500, 'REPEAT_ONCE', 1, 8, 9)")
            execSQL("INSERT INTO queue_entries VALUES (1, 0, 101, 1)")
            execSQL("INSERT INTO queue_entries VALUES (1, 1, 102, 2)")
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            MusicDatabase.MIGRATION_1_2,
        )
        database.query(
            """
            SELECT
                (SELECT COUNT(*) FROM tracks),
                (SELECT COUNT(*) FROM playlists),
                (SELECT COUNT(*) FROM playlist_entries),
                (SELECT COUNT(*) FROM playback_sessions),
                (SELECT COUNT(*) FROM queue_entries)
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(2, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(2, cursor.getInt(4))
        }
        database.query(
            """
            SELECT current_queue_index, current_position_ms, repeat_mode,
                repeat_once_consumed, consumed_queue_entry_ids
            FROM playback_sessions WHERE session_id = 1
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(42_500L, cursor.getLong(1))
            assertEquals("REPEAT_ONCE", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals("102", cursor.getString(4))
        }
        database.query("PRAGMA foreign_key_check").use { cursor ->
            assertTrue(!cursor.moveToFirst())
        }
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-v1-test"
    }
}
