package com.theveloper.pixelplay.data.service.tile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.MainActivityIntentContract
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile that resumes the most recently played playlist.
 * Reads the last playlist ID from DataStore and fires ACTION_OPEN_PLAYLIST to MainActivity.
 * Works whether the app is open or not.
 *
 * P0-2: Uses coroutines instead of runBlocking to avoid blocking the binder thread
 * (which could cause ANR when the user opens the Quick Settings panel).
 */
@RequiresApi(Build.VERSION_CODES.N)
class LastPlaylistTileService : TileService() {

    companion object {
        private const val FOLDER_PLAYLIST_PREFIX = "folder_playlist:"
        private const val REQUEST_CODE_LAST_PLAYLIST = 1002
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LastPlaylistTileEntryPoint {
        fun musicRepository(): MusicRepository
        fun playlistPreferencesRepository(): PlaylistPreferencesRepository
        fun userPreferencesRepository(): UserPreferencesRepository
    }

    private val prefsRepo: UserPreferencesRepository by lazy {
        val appContext = applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            LastPlaylistTileEntryPoint::class.java
        )
        entryPoint.userPreferencesRepository()
    }

    private val playlistRepo: PlaylistPreferencesRepository by lazy {
        val appContext = applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            LastPlaylistTileEntryPoint::class.java
        )
        entryPoint.playlistPreferencesRepository()
    }

    private val musicRepo: MusicRepository by lazy {
        val appContext = applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            LastPlaylistTileEntryPoint::class.java
        )
        entryPoint.musicRepository()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        // P0-2: Read DataStore without blocking the binder thread
        serviceScope.launch(Dispatchers.IO) {
            val lastPlaylistId = resolveLaunchablePlaylistId()
            withContext(Dispatchers.Main) {
                qsTile?.apply {
                    state = if (lastPlaylistId != null) Tile.STATE_INACTIVE
                            else Tile.STATE_UNAVAILABLE
                    updateTile()
                }
            }
        }
    }

    override fun onClick() {
        // P0-2: Read DataStore without blocking the binder thread
        serviceScope.launch(Dispatchers.IO) {
            val playlistId = resolveLaunchablePlaylistId()
            withContext(Dispatchers.Main) {
                if (playlistId == null) {
                    Toast.makeText(
                        this@LastPlaylistTileService,
                        R.string.tile_last_playlist_unavailable,
                        Toast.LENGTH_SHORT
                    ).show()
                    qsTile?.apply {
                        state = Tile.STATE_UNAVAILABLE
                        updateTile()
                    }
                    return@withContext
                }

                val intent = Intent(this@LastPlaylistTileService, MainActivity::class.java).apply {
                    action = MainActivityIntentContract.ACTION_OPEN_PLAYLIST
                    putExtra(MainActivityIntentContract.EXTRA_PLAYLIST_ID, playlistId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivityAndCollapseCompat(
                    intent = intent,
                    requestCode = REQUEST_CODE_LAST_PLAYLIST
                )
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun resolveLaunchablePlaylistId(): String? {
        val storedPlaylistId = prefsRepo.lastPlaylistIdFlow.first() ?: return null
        val isValid = if (storedPlaylistId.startsWith(FOLDER_PLAYLIST_PREFIX)) {
            hasExistingFolderPlaylist(storedPlaylistId)
        } else {
            playlistRepo.getPlaylistsOnce().any { playlist -> playlist.id == storedPlaylistId }
        }

        if (isValid) {
            return storedPlaylistId
        }

        prefsRepo.clearLastPlaylist()
        return null
    }

    private suspend fun hasExistingFolderPlaylist(playlistId: String): Boolean {
        val folderPath = Uri.decode(playlistId.removePrefix(FOLDER_PLAYLIST_PREFIX))
        return findFolder(folderPath, musicRepo.getMusicFolders().first()) != null
    }

    private fun findFolder(targetPath: String, folders: List<MusicFolder>): MusicFolder? {
        val queue = ArrayDeque(folders)
        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            if (folder.path == targetPath) {
                return folder
            }
            folder.subFolders.forEach(queue::addLast)
        }
        return null
    }
}
