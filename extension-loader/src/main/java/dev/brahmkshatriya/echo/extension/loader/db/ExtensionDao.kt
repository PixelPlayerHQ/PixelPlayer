package dev.brahmkshatriya.echo.extension.loader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.brahmkshatriya.echo.extension.loader.db.models.ExtensionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionDao {

    @Query("SELECT * FROM ExtensionEntity")
    fun getExtensionFlow(): Flow<List<ExtensionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(extension: ExtensionEntity)

}
