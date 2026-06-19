package com.theveloper.pixelplay.data.backup.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.backup.model.BackupHistoryEntry
import com.theveloper.pixelplay.di.BackupGson
import timber.log.Timber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupHistoryRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @BackupGson private val gson: Gson
) {
    companion object {
        private val BACKUP_HISTORY_KEY = stringPreferencesKey("backup_history_json")
        const val MAX_HISTORY_ENTRIES = 10
    }

    private val listType = TypeToken.getParameterized(List::class.java, BackupHistoryEntry::class.java).type

    val historyFlow: Flow<List<BackupHistoryEntry>> = dataStore.data.map { preferences ->
        val json = preferences[BACKUP_HISTORY_KEY]
        if (json != null) {
            try {
                gson.fromJson<List<BackupHistoryEntry>>(json, listType)
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse backup history")
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun addEntry(entry: BackupHistoryEntry) {
        dataStore.edit { preferences ->
            val current = readHistory(preferences)
            // Remove existing entry with same URI to avoid duplicates
            val updated = current.filter { it.uri != entry.uri }.toMutableList()
            updated.add(0, entry) // Add at beginning (most recent first)
            // Limit history size
            val trimmed = updated.take(MAX_HISTORY_ENTRIES)
            preferences[BACKUP_HISTORY_KEY] = gson.toJson(trimmed)
        }
    }

    suspend fun removeEntry(uri: String) {
        dataStore.edit { preferences ->
            val current = readHistory(preferences)
            val updated = current.filter { it.uri != uri }
            preferences[BACKUP_HISTORY_KEY] = gson.toJson(updated)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(BACKUP_HISTORY_KEY)
        }
    }

    private fun readHistory(preferences: Preferences): List<BackupHistoryEntry> {
        val json = preferences[BACKUP_HISTORY_KEY] ?: return emptyList()
        return try {
            gson.fromJson(json, listType)
        } catch (e: Exception) {
            Timber.w(e, "Failed to read backup history")
            emptyList()
        }
    }
}
