package com.theveloper.pixelplay.soundcloud

import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.worker.SyncManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps Music/PixelPlayer/SoundCloud in or out of the local library without
 * blocking the SoundCloud screen. The folder rule is applied in the background
 * scan the library already uses.
 */
@Singleton
class SoundCloudLibraryGate @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val syncManager: SyncManager,
) {
    suspend fun apply(includeInLibrary: Boolean) {
        val path = SoundCloudDownloadService.downloadDirectory().absolutePath
        val blocked = preferences.blockedDirectoriesFlow.first()
            .map { it.trimEnd('/', '\\') }
            .toMutableSet()
        val allowed = preferences.allowedDirectoriesFlow.first()
        val currentlyBlocked = blocked.any { it.equals(path, ignoreCase = true) }
        val shouldBlock = !includeInLibrary
        if (currentlyBlocked == shouldBlock) return
        if (shouldBlock) {
            blocked.add(path)
        } else {
            blocked.removeAll { it.equals(path, ignoreCase = true) }
        }
        preferences.updateDirectorySelections(allowed, blocked)
        syncManager.incrementalSync()
    }
}
