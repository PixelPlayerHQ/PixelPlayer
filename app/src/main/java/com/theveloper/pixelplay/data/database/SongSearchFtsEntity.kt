package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4(tokenizer = "unicode61")
@Entity(tableName = "songs_fts")
data class SongSearchFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist_name")
    val artistName: String
)
