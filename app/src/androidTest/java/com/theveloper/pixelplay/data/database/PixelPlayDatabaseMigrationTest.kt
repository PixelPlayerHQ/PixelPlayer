package com.theveloper.pixelplay.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelPlayDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PixelPlayDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (version in 25..39) {
            context.deleteDatabase(databaseNameFor(version))
        }
        context.deleteDatabase(DB_NAME_33_TO_34)
        context.deleteDatabase(DB_NAME_23_TO_24_DRIFTED)
        context.deleteDatabase(DB_NAME_35_TO_36)
        context.deleteDatabase(DB_NAME_39_TO_40)
    }

    @Test
    fun migrateEveryExportedSchemaToLatest() {
        for (startVersion in 25..39) {
            helper.createDatabase(databaseNameFor(startVersion), startVersion).close()

            helper.runMigrationsAndValidate(
                databaseNameFor(startVersion),
                PixelPlayDatabaseVersion.LATEST,
                true,
                *ALL_MIGRATIONS
            ).close()
        }
    }

    @Test
    fun migration33To34AddsArtistsJsonColumnToSongs() {
        helper.createDatabase(DB_NAME_33_TO_34, 33).close()

        helper.runMigrationsAndValidate(
            DB_NAME_33_TO_34,
            34,
            true,
            PixelPlayDatabase.MIGRATION_33_34
        ).let { db ->
            val cursor = db.query("PRAGMA table_info(`songs`)")
            try {
                val nameIndex = cursor.getColumnIndex("name")
                val defaultValueIndex = cursor.getColumnIndex("dflt_value")
                var foundArtistsJson = false
                var defaultValue: String? = null

                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "artists_json") {
                        foundArtistsJson = true
                        defaultValue = cursor.getString(defaultValueIndex)
                        break
                    }
                }

                assertTrue(foundArtistsJson)
                assertEquals("NULL", defaultValue)
            } finally {
                cursor.close()
                db.close()
            }
        }
    }

    @Test
    fun migration23To24RepairsSongsWithoutDateAddedBeforeCreatingIndexes() {
        val openHelper = createDriftedVersion23Database(DB_NAME_23_TO_24_DRIFTED)
        val db = openHelper.writableDatabase

        try {
            PixelPlayDatabase.MIGRATION_23_24.migrate(db)

            val columns = db.tableColumns("songs")
            assertTrue("date_added" in columns)

            db.query("SELECT date_added FROM songs WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }

            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_songs_date_added'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("index_songs_date_added", cursor.getString(0))
            }
        } finally {
            db.close()
            openHelper.close()
        }
    }

    @Test
    fun migration35To36AddsSongsFtsTable() {
        helper.createDatabase(DB_NAME_35_TO_36, 35).close()

        helper.runMigrationsAndValidate(
            DB_NAME_35_TO_36,
            36,
            true,
            PixelPlayDatabase.MIGRATION_35_36
        ).let { db ->
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'songs_fts'")
            try {
                assertTrue(cursor.moveToFirst())
                assertEquals("songs_fts", cursor.getString(0))
            } finally {
                cursor.close()
                db.close()
            }
        }
    }

    @Test
    fun migration39To40AddsCompositeSongIndexes() {
        helper.createDatabase(DB_NAME_39_TO_40, 39).close()

        helper.runMigrationsAndValidate(
            DB_NAME_39_TO_40,
            40,
            true,
            PixelPlayDatabase.MIGRATION_39_40
        ).let { db ->
            try {
                val indexes = db.tableIndexes("songs")
                assertTrue("index_songs_parent_directory_path_source_type_album_id" in indexes)
                assertTrue("index_songs_parent_directory_path_source_type_id" in indexes)
            } finally {
                db.close()
            }
        }
    }

    private fun databaseNameFor(startVersion: Int): String = "migration-test-$startVersion"

    private fun createDriftedVersion23Database(
        databaseName: String
    ): SupportSQLiteOpenHelper {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)

        val callback = object : SupportSQLiteOpenHelper.Callback(23) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS songs (
                            id INTEGER NOT NULL PRIMARY KEY,
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
                            telegram_file_id INTEGER
                        )
                    """.trimIndent()
                )

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
                            'Song',
                            'Artist',
                            10,
                            NULL,
                            'Album',
                            20,
                            'content://song/1',
                            NULL,
                            180000,
                            NULL,
                            '/music/song.mp3',
                            '/music',
                            0,
                            NULL,
                            1,
                            2024,
                            'audio/mpeg',
                            320000,
                            44100,
                            NULL,
                            NULL
                        )
                    """.trimIndent()
                )

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

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
    }

    private fun SupportSQLiteDatabase.tableColumns(tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        return columns
    }

    private fun SupportSQLiteDatabase.tableIndexes(tableName: String): Set<String> {
        val indexes = mutableSetOf<String>()
        query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                indexes += cursor.getString(nameIndex)
            }
        }
        return indexes
    }

    private object PixelPlayDatabaseVersion {
        const val LATEST = 40
    }

    companion object {
        private const val DB_NAME_23_TO_24_DRIFTED = "migration-test-23-to-24-drifted"
        private const val DB_NAME_33_TO_34 = "migration-test-33-to-34"
        private const val DB_NAME_35_TO_36 = "migration-test-35-to-36"
        private const val DB_NAME_39_TO_40 = "migration-test-39-to-40"

        private val ALL_MIGRATIONS = arrayOf(
            PixelPlayDatabase.MIGRATION_25_26,
            PixelPlayDatabase.MIGRATION_26_27,
            PixelPlayDatabase.MIGRATION_27_28,
            PixelPlayDatabase.MIGRATION_28_29,
            PixelPlayDatabase.MIGRATION_29_30,
            PixelPlayDatabase.MIGRATION_30_31,
            PixelPlayDatabase.MIGRATION_31_32,
            PixelPlayDatabase.MIGRATION_32_33,
            PixelPlayDatabase.MIGRATION_33_34,
            PixelPlayDatabase.MIGRATION_34_35,
            PixelPlayDatabase.MIGRATION_35_36,
            PixelPlayDatabase.MIGRATION_36_37,
            PixelPlayDatabase.MIGRATION_37_38,
            PixelPlayDatabase.MIGRATION_38_39,
            PixelPlayDatabase.MIGRATION_39_40
        )
    }
}
