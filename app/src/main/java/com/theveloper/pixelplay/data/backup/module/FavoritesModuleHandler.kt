package com.theveloper.pixelplay.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.FavoritesEntity
import com.theveloper.pixelplay.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesModuleHandler @Inject constructor(
    private val favoritesDao: FavoritesDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.FAVORITES

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(favoritesDao.getAllFavoritesOnce())
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        favoritesDao.getAllFavoritesOnce().size
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, FavoritesEntity::class.java).type
        val favorites: List<FavoritesEntity> = gson.fromJson(payload, type)
        favoritesDao.replaceAll(favorites)
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)
}
