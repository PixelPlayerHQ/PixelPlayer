package dev.brahmkshatriya.echo.extension.loader.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.extension.loader.db.models.CurrentUser
import dev.brahmkshatriya.echo.extension.loader.db.models.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM UserEntity WHERE type = :type AND extId = :extId AND id = :userId")
    suspend fun getUser(type: ExtensionType, extId: String, userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM CurrentUser")
    fun observeCurrentUser(): Flow<List<CurrentUser>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCurrentUser(currentUser: CurrentUser)

}
