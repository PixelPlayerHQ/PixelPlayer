package com.theveloper.pixelplay.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for locally stored songs on the watch.
 * Tracks songs that have been transferred from the phone for offline playback.
 */
@Database(entities = [LocalSongEntity::class], version = 5, exportSchema = false)
abstract class WearMusicDatabase : RoomDatabase() {
    abstract fun localSongDao(): LocalSongDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_songs ADD COLUMN paletteSeedArgb INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_songs ADD COLUMN artworkPath TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_songs ADD COLUMN themePaletteJson TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_songs ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE local_songs ADD COLUMN favoriteSyncPending INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
