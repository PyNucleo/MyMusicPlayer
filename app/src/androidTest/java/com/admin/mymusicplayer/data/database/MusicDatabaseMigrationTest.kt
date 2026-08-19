package com.admin.mymusicplayer.data.database

import androidx.room.Room
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
    fun exportedVersionOneSchemaOpensWithRoom() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO playlists(name, created_at_epoch_ms, modified_at_epoch_ms, revision)
                VALUES ('Migration sentinel', 1, 1, 0)
                """.trimIndent(),
            )
            close()
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, MusicDatabase::class.java, TEST_DATABASE).build()
        val cursor = database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM playlists")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-v1-test"
    }
}
