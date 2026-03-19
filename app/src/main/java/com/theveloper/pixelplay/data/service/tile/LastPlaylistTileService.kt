package com.theveloper.pixelplay.data.service.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LastPlaylistTileEntryPoint {
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

    override fun onStartListening() {
        // P0-2: Read DataStore without blocking the binder thread
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val lastPlaylistId = prefsRepo.lastPlaylistIdFlow.first()
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
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val playlistId = prefsRepo.lastPlaylistIdFlow.first() ?: return@launch
            withContext(Dispatchers.Main) {
                val intent = Intent(this@LastPlaylistTileService, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_PLAYLIST
                    putExtra(MainActivity.EXTRA_PLAYLIST_ID, playlistId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    this@LastPlaylistTileService, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            }
        }
    }
}
