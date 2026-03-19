package com.theveloper.pixelplay.data

import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.theveloper.pixelplay.data.local.LocalSongDao
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearFavoriteSyncRequest
import com.theveloper.pixelplay.shared.WearFavoriteSyncResponse
import com.theveloper.pixelplay.shared.WearPlaybackCommand
import com.theveloper.pixelplay.shared.WearPlaybackResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearFavoriteSyncRepository @Inject constructor(
    private val localSongDao: LocalSongDao,
    private val messageClient: MessageClient,
    private val nodeClient: NodeClient,
    private val stateRepository: WearStateRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val inFlightFavoriteUpdates = ConcurrentHashMap<String, String>()
    private var pendingReconnectJob: Job? = null

    companion object {
        private const val TAG = "WearFavoriteSync"
        private const val RECONNECT_POLL_INTERVAL_MS = 2500L
        private const val POST_SEND_CONFIRM_DELAY_MS = 1200L
    }

    init {
        scope.launch {
            stateRepository.isPhoneConnected.collect { connected ->
                if (connected) {
                    if (localSongDao.getPendingFavoriteSongs().isNotEmpty()) {
                        flushPendingFavoriteChanges()
                    } else {
                        requestFavoriteSync()
                    }
                }
            }
        }
        scope.launch {
            localSongDao.getAllSongIds()
                .distinctUntilChanged()
                .collect { songIds ->
                    if (songIds.isNotEmpty() && stateRepository.isPhoneConnected.value) {
                        requestFavoriteSync(songIds)
                    }
                }
        }
        scope.launch {
            stateRepository.playbackResults.collect(::handlePlaybackResult)
        }
    }

    fun setFavorite(songId: String, isFavorite: Boolean) {
        scope.launch {
            val localSong = localSongDao.getSongById(songId) ?: return@launch
            localSongDao.updateFavoriteState(
                songId = localSong.songId,
                isFavorite = isFavorite,
                favoriteSyncPending = true,
            )

            val dispatched = dispatchFavoriteUpdate(
                songId = localSong.songId,
                isFavorite = isFavorite,
            )
            if (!dispatched) {
                stateRepository.setPhoneConnected(false)
                ensureReconnectWatcher()
            }
        }
    }

    fun requestFavoriteSync(songIds: Collection<String> = emptyList()) {
        scope.launch {
            val requestedIds = if (songIds.none()) {
                localSongDao.getAllSongIdsOnce()
            } else {
                songIds.filter { it.isNotBlank() }.distinct()
            }
            val pendingSongIds = localSongDao.getPendingFavoriteSongs()
                .map { it.songId }
                .toSet()
            sendFavoriteSyncRequest(requestedIds.filterNot(pendingSongIds::contains))
        }
    }

    suspend fun applyFavoriteSyncResponse(response: WearFavoriteSyncResponse) {
        response.states.forEach { entry ->
            val localSong = localSongDao.getSongById(entry.songId) ?: return@forEach
            if (localSong.favoriteSyncPending && localSong.isFavorite != entry.isFavorite) {
                return@forEach
            }
            localSongDao.updateFavoriteState(
                songId = entry.songId,
                isFavorite = entry.isFavorite,
                favoriteSyncPending = false,
            )
        }
    }

    private suspend fun handlePlaybackResult(result: WearPlaybackResult) {
        if (result.action != WearPlaybackCommand.TOGGLE_FAVORITE) return
        val requestId = result.requestId
        if (requestId.isBlank()) return

        val songId = inFlightFavoriteUpdates.remove(requestId) ?: result.songId ?: return
        if (result.success) {
            localSongDao.updateFavoritePending(songId, favoriteSyncPending = false)
            return
        }

        localSongDao.updateFavoritePending(songId, favoriteSyncPending = false)
        if (stateRepository.isPhoneConnected.value) {
            sendFavoriteSyncRequest(listOf(songId))
        }
    }

    private suspend fun flushPendingFavoriteChanges() {
        val pendingSongs = localSongDao.getPendingFavoriteSongs()
        if (pendingSongs.isEmpty()) {
            pendingReconnectJob?.cancel()
            pendingReconnectJob = null
            return
        }

        val connectedNodeId = getConnectedNodeId()
        if (connectedNodeId == null) {
            stateRepository.setPhoneConnected(false)
            ensureReconnectWatcher()
            return
        }

        stateRepository.setPhoneConnected(true)
        pendingSongs.forEach { song ->
            val dispatched = dispatchFavoriteUpdate(
                songId = song.songId,
                isFavorite = song.isFavorite,
                connectedNodeId = connectedNodeId,
            )
            if (!dispatched) {
                stateRepository.setPhoneConnected(false)
                ensureReconnectWatcher()
                return
            }
        }
    }

    private suspend fun dispatchFavoriteUpdate(
        songId: String,
        isFavorite: Boolean,
        connectedNodeId: String? = null,
    ): Boolean {
        val nodeId = connectedNodeId ?: getConnectedNodeId()
        if (nodeId == null) {
            return false
        }

        val requestId = UUID.randomUUID().toString()
        val payload = json.encodeToString(
            WearPlaybackCommand(
                action = WearPlaybackCommand.TOGGLE_FAVORITE,
                songId = songId,
                requestId = requestId,
                targetEnabled = isFavorite,
            )
        ).toByteArray(Charsets.UTF_8)

        return runCatching {
            inFlightFavoriteUpdates[requestId] = songId
            messageClient.sendMessage(nodeId, WearDataPaths.PLAYBACK_COMMAND, payload).await()
            stateRepository.setPhoneConnected(true)
            confirmStateSoon(songId)
            true
        }.getOrElse { error ->
            inFlightFavoriteUpdates.remove(requestId)
            Timber.tag(TAG).w(error, "Failed to dispatch favorite update for songId=%s", songId)
            false
        }
    }

    private fun confirmStateSoon(songId: String) {
        scope.launch {
            delay(POST_SEND_CONFIRM_DELAY_MS)
            val localSong = localSongDao.getSongById(songId) ?: return@launch
            if (localSong.favoriteSyncPending && stateRepository.isPhoneConnected.value) {
                sendFavoriteSyncRequest(listOf(songId))
            }
        }
    }

    private suspend fun sendFavoriteSyncRequest(songIds: Collection<String>) {
        val requestedIds = songIds.filter { it.isNotBlank() }.distinct()
        if (requestedIds.isEmpty()) return

        val nodeId = getConnectedNodeId()
        if (nodeId == null) {
            stateRepository.setPhoneConnected(false)
            return
        }

        val payload = json.encodeToString(
            WearFavoriteSyncRequest(songIds = requestedIds.sorted())
        ).toByteArray(Charsets.UTF_8)

        runCatching {
            messageClient.sendMessage(nodeId, WearDataPaths.FAVORITES_SYNC_REQUEST, payload).await()
            stateRepository.setPhoneConnected(true)
        }.onFailure { error ->
            stateRepository.setPhoneConnected(false)
            Timber.tag(TAG).w(error, "Failed to request favorite snapshot from phone")
        }
    }

    private fun ensureReconnectWatcher() {
        if (pendingReconnectJob?.isActive == true) return

        pendingReconnectJob = scope.launch {
            while (true) {
                val pendingSongs = localSongDao.getPendingFavoriteSongs()
                if (pendingSongs.isEmpty()) {
                    pendingReconnectJob = null
                    return@launch
                }

                val nodeId = getConnectedNodeId()
                if (nodeId != null) {
                    stateRepository.setPhoneConnected(true)
                    flushPendingFavoriteChanges()
                    pendingReconnectJob = null
                    return@launch
                }

                stateRepository.setPhoneConnected(false)
                delay(RECONNECT_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun getConnectedNodeId(): String? {
        return runCatching {
            nodeClient.connectedNodes.await().firstOrNull()?.id
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to resolve connected phone node")
        }.getOrNull()
    }
}
