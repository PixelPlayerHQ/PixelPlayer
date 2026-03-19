package com.theveloper.pixelplay.data.backup.module

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.database.TransitionDao
import com.theveloper.pixelplay.data.database.TransitionRuleEntity
import com.theveloper.pixelplay.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitionsModuleHandler @Inject constructor(
    private val transitionDao: TransitionDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.TRANSITIONS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(transitionDao.getAllRulesOnce())
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        transitionDao.getAllRulesOnce().size
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val type = TypeToken.getParameterized(List::class.java, TransitionRuleEntity::class.java).type
        val rules: List<TransitionRuleEntity> = gson.fromJson(payload, type)
        transitionDao.replaceAllRules(rules)
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)
}
