package dev.brahmkshatriya.echo.extension.loader.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.extension.loader.db.models.CurrentUser
import dev.brahmkshatriya.echo.extension.loader.db.models.ExtensionEntity
import dev.brahmkshatriya.echo.extension.loader.db.models.UserEntity

@Database(
    entities = [
        UserEntity::class,
        CurrentUser::class,
        ExtensionEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class ExtensionDatabase : RoomDatabase() {
    private val userDao by lazy { userDao() }
    val currentUsersFlow by lazy { userDao.observeCurrentUser() }
    private val extensionDao by lazy { extensionDao() }
    val extensionEnabledFlow by lazy { extensionDao.getExtensionFlow() }

    abstract fun userDao(): UserDao
    abstract fun extensionDao(): ExtensionDao

    suspend fun getUser(current: CurrentUser): User? {
        val id = current.userId ?: return null
        return userDao.getUser(current.type, current.extId, id)?.user?.getOrNull()
    }

    companion object {
        private const val DATABASE_NAME = "extension-db"
        fun create(context: Context) = Room.databaseBuilder(
            context, ExtensionDatabase::class.java, DATABASE_NAME
        ).fallbackToDestructiveMigration(true).build()
    }
}
