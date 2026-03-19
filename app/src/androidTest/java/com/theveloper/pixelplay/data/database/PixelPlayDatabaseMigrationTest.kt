package com.theveloper.pixelplay.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PixelPlayDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseNames = mutableListOf<String>()

    @After
    fun tearDown() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun migration23to24_addsDateAddedAndIndexes_whenSongsColumnIsMissing() {
        withDatabase(version = 23) { db ->
            createSupportingTables(db)
            db.execSQL("INSERT INTO artists (id) VALUES (10)")
            db.execSQL("INSERT INTO albums (id) VALUES (20)")
            db.execSQL(
                """
                    INSERT INTO songs (
                        id,
                        title,
                        artist_name,
                        artist_id,
                        album_artist,
                        album_name,
                        album_id,
                        content_uri_string,
                        album_art_uri_string,
                        duration,
                        genre,
                        file_path,
                        parent_directory_path,
                        is_favorite,
                        lyrics,
                        track_number,
                        year,
                        mime_type,
                        bitrate,
                        sample_rate,
                        telegram_chat_id,
                        telegram_file_id
                    ) VALUES (
                        1,
                        'Song A',
                        'Artist A',
                        10,
                        NULL,
                        'Album A',
                        20,
                        'content://song/1',
                        NULL,
                        180000,
                        'Pop',
                        '/music/song-a.mp3',
                        '/music',
                        0,
                        NULL,
                        1,
                        2025,
                        'audio/mpeg',
                        320000,
                        44100,
                        NULL,
                        NULL
                    )
                """.trimIndent()
            )

            PixelPlayDatabase.MIGRATION_23_24.migrate(db)

            assertTrue("songs table should contain date_added", "date_added" in getTableColumns(db, "songs"))
            assertTrue(
                "songs table should expose every expected index",
                getIndexNames(db, "songs").containsAll(EXPECTED_SONG_INDEXES)
            )

            db.query("SELECT title, date_added FROM songs WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Song A", cursor.getString(0))
                assertEquals(0L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun migration23to24_recreatesSongsTable_whenItIsMissing() {
        withDatabase(version = 23) { db ->
            createSupportingTables(db, includeSongsTable = false)

            PixelPlayDatabase.MIGRATION_23_24.migrate(db)

            val columns = getTableColumns(db, "songs")
            assertTrue(
                "recreated songs table should include the required columns",
                setOf("id", "title", "content_uri_string", "date_added").all(columns::contains)
            )
            assertTrue(
                "recreated songs table should expose every expected index",
                getIndexNames(db, "songs").containsAll(EXPECTED_SONG_INDEXES)
            )
        }
    }

    private fun withDatabase(version: Int, block: (SupportSQLiteDatabase) -> Unit) {
        val name = "migration-test-${UUID.randomUUID()}.db"
        databaseNames += name
        val helper = openHelper(name, version)
        val db = helper.writableDatabase

        try {
            block(db)
        } finally {
            db.close()
            helper.close()
        }
    }

    private fun openHelper(name: String, version: Int): SupportSQLiteOpenHelper {
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )
    }

    private fun createSupportingTables(
        db: SupportSQLiteDatabase,
        includeSongsTable: Boolean = true
    ) {
        db.execSQL("CREATE TABLE IF NOT EXISTS artists (id INTEGER NOT NULL PRIMARY KEY)")
        db.execSQL("CREATE TABLE IF NOT EXISTS albums (id INTEGER NOT NULL PRIMARY KEY)")
        if (includeSongsTable) {
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS songs (
                        id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist_name TEXT NOT NULL,
                        artist_id INTEGER NOT NULL,
                        album_artist TEXT,
                        album_name TEXT NOT NULL,
                        album_id INTEGER NOT NULL,
                        content_uri_string TEXT NOT NULL,
                        album_art_uri_string TEXT,
                        duration INTEGER NOT NULL,
                        genre TEXT,
                        file_path TEXT NOT NULL,
                        parent_directory_path TEXT NOT NULL,
                        is_favorite INTEGER NOT NULL DEFAULT 0,
                        lyrics TEXT DEFAULT null,
                        track_number INTEGER NOT NULL DEFAULT 0,
                        year INTEGER NOT NULL DEFAULT 0,
                        mime_type TEXT,
                        bitrate INTEGER,
                        sample_rate INTEGER,
                        telegram_chat_id INTEGER,
                        telegram_file_id INTEGER,
                        PRIMARY KEY(id)
                    )
                """.trimIndent()
            )
        }
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS favorites (
                    songId INTEGER NOT NULL PRIMARY KEY,
                    isFavorite INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent()
        )
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS song_engagements (
                    song_id TEXT NOT NULL PRIMARY KEY,
                    play_count INTEGER NOT NULL DEFAULT 0,
                    total_play_duration_ms INTEGER NOT NULL DEFAULT 0,
                    last_played_timestamp INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent()
        )
    }

    private fun getTableColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        return columns
    }

    private fun getIndexNames(db: SupportSQLiteDatabase, tableName: String): Set<String> {
        val indexNames = mutableSetOf<String>()
        db.query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                indexNames += cursor.getString(nameIndex)
            }
        }
        return indexNames
    }

    private companion object {
        val EXPECTED_SONG_INDEXES = setOf(
            "index_songs_title",
            "index_songs_album_id",
            "index_songs_artist_id",
            "index_songs_artist_name",
            "index_songs_genre",
            "index_songs_parent_directory_path",
            "index_songs_content_uri_string",
            "index_songs_date_added",
            "index_songs_duration"
        )
    }
}
