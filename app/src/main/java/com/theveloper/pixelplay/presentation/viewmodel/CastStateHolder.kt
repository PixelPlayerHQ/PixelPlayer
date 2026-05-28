package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.player.CastPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Cast/Chromecast state.
 * Extracted from PlayerViewModel to improve modularity.
 * 
 * Note: Complex session transfer logic remains in PlayerViewModel
 * due to tight coupling with playback state.
 */
@Singleton
class CastStateHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val CAST_STATE_TAG = "CastStateHolder"

    // Cast session manager
    val sessionManager: SessionManager? by lazy {
        try {
            CastContext.getSharedInstance(context).sessionManager
        } catch (e: Exception) {
            Timber.tag(CAST_STATE_TAG).e(e, "Failed to get CastContext sharedInstance")
            null
        }
    }
    
    // Current cast session
    private val _castSession = MutableStateFlow<CastSession?>(null)
    val castSession: StateFlow<CastSession?> = _castSession.asStateFlow()
    
    // Cast player instance
    private var _castPlayer: CastPlayer? = null
    val castPlayer: CastPlayer? get() = _castPlayer
    
    // Is remote playback active
    private val _isRemotePlaybackActive = MutableStateFlow(false)
    val isRemotePlaybackActive: StateFlow<Boolean> = _isRemotePlaybackActive.asStateFlow()
    
    // Is currently connecting to cast
    private val _isCastConnecting = MutableStateFlow(false)
    val isCastConnecting: StateFlow<Boolean> = _isCastConnecting.asStateFlow()
    
    // Remote playback position
    private val _remotePosition = MutableStateFlow(0L)
    val remotePosition: StateFlow<Long> = _remotePosition.asStateFlow()
    
    // Pending cast route ID for connection
    private var _pendingCastRouteId: String? = null
    val pendingCastRouteId: String? get() = _pendingCastRouteId
    
    // Last known remote state
    var lastRemoteMediaStatus: MediaStatus? = null
        private set
    var lastRemoteQueue: List<Song> = emptyList()
        private set
    var lastRemoteSongId: String? = null
        private set
    var lastRemoteStreamPosition: Long = 0L
        private set
    var lastRemoteRepeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF
        private set
    var lastRemoteItemId: Int? = null
        private set
    
    // Pending remote song tracking
    var pendingRemoteSongId: String? = null
        private set
    var pendingRemoteSongMarkedAt: Long = 0L
        private set
    
    // Is currently seeking remotely
    private val _isRemotelySeeking = MutableStateFlow(false)
    val isRemotelySeeking: StateFlow<Boolean> = _isRemotelySeeking.asStateFlow()
    
    // Cast control category
    private val castControlCategory = CastMediaControlIntent.categoryForCast(
        CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
    )
    
    /**
     * Check if a route is a Cast route.
     */
    fun MediaRouter.RouteInfo.isCastRoute(): Boolean {
        return supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK) ||
            supportsControlCategory(castControlCategory)
    }
    
    /**
     * Build a media route selector for Cast routes.
     */
    fun buildCastRouteSelector(): MediaRouteSelector {
        return MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .addControlCategory(castControlCategory)
            .build()
    }

    // State setters
    fun setCastSession(session: CastSession?) {
        _castSession.value = session
    }
    
    fun setCastPlayer(player: CastPlayer?) {
        if (_castPlayer === player) return
        _castPlayer?.release()
        _castPlayer = player
    }
    
    fun setRemotePlaybackActive(active: Boolean) {
        _isRemotePlaybackActive.value = active
    }
    
    fun setCastConnecting(connecting: Boolean) {
        _isCastConnecting.value = connecting
    }
    
    fun setRemotePosition(position: Long) {
        _remotePosition.value = position
    }
    
    fun setPendingCastRouteId(routeId: String?) {
        _pendingCastRouteId = routeId
    }
    
    fun setRemotelySeeking(seeking: Boolean) {
        _isRemotelySeeking.value = seeking
    }
    
    fun updateLastRemoteState(
        mediaStatus: MediaStatus? = null,
        queue: List<Song>? = null,
        songId: String? = null,
        streamPosition: Long? = null,
        repeatMode: Int? = null,
        itemId: Int? = null
    ) {
        mediaStatus?.let { lastRemoteMediaStatus = it }
        queue?.let { lastRemoteQueue = it }
        songId?.let { lastRemoteSongId = it }
        streamPosition?.let { lastRemoteStreamPosition = it }
        repeatMode?.let { lastRemoteRepeatMode = it }
        itemId?.let { lastRemoteItemId = it }
    }
    
    fun setPendingRemoteSong(songId: String?, markedAt: Long = System.currentTimeMillis()) {
        pendingRemoteSongId = songId
        pendingRemoteSongMarkedAt = markedAt
    }
    
    fun clearRemoteState() {
        _castSession.value = null
        _castPlayer?.release()
        _castPlayer = null
        _isRemotePlaybackActive.value = false
        _isCastConnecting.value = false
        _pendingCastRouteId = null
        lastRemoteMediaStatus = null
        lastRemoteQueue = emptyList()
        lastRemoteSongId = null
        lastRemoteStreamPosition = 0L
        lastRemoteItemId = null
        pendingRemoteSongId = null
    }
    
    // MediaRouter State
    private val mediaRouter: MediaRouter = MediaRouter.getInstance(context)
    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRoutes()
            syncSelectedRouteFromRouter(router)
        }
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRoutes()
            syncSelectedRouteFromRouter(router)
        }
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRoutes()
            syncSelectedRouteFromRouter(router)
        }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateRoutes()
            syncSelectedRouteFromRouter(router)
        }
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            updateRoutes()
            syncSelectedRouteFromRouter(router)
        }
        override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
             if (route.id == _selectedRoute.value?.id) {
                _routeVolume.value = route.volume
            }
        }
    }

    private val _castRoutes = MutableStateFlow<List<MediaRouter.RouteInfo>>(emptyList())
    val castRoutes: StateFlow<List<MediaRouter.RouteInfo>> = _castRoutes.asStateFlow()

    private val _selectedRoute = MutableStateFlow<MediaRouter.RouteInfo?>(null)
    val selectedRoute: StateFlow<MediaRouter.RouteInfo?> = _selectedRoute.asStateFlow()

    private val _routeVolume = MutableStateFlow(0)
    val routeVolume: StateFlow<Int> = _routeVolume.asStateFlow()

    private val _isRefreshingRoutes = MutableStateFlow(false)
    val isRefreshingRoutes: StateFlow<Boolean> = _isRefreshingRoutes.asStateFlow()
    
    // Coroutine scope for delays (injected via initialize or use GlobalScope helper if Singleton? 
    // Ideally we should have a scope. Since it is Singleton, we can use a custom scope or suspend functions.)
    // But refreshRoutes was launched in ViewModel.
    // We will make refreshRoutes suspend.

    private var refreshRoutesJob: kotlinx.coroutines.Job? = null

    fun refreshRoutes(scope: kotlinx.coroutines.CoroutineScope) {
        refreshRoutesJob?.cancel()
        refreshRoutesJob = scope.launch {
            _isRefreshingRoutes.value = true
            mediaRouter.removeCallback(mediaRouterCallback)
            val mediaRouteSelector = buildCastRouteSelector()
            mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
            updateRoutes()
            syncSelectedRouteFromRouter(mediaRouter)

            kotlinx.coroutines.delay(1800)

            mediaRouter.removeCallback(mediaRouterCallback)
            mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            updateRoutes()
            syncSelectedRouteFromRouter(mediaRouter)
            _isRefreshingRoutes.value = false
        }
    }

    fun startDiscovery() {
        val mediaRouteSelector = buildCastRouteSelector()
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        updateRoutes()
        syncSelectedRouteFromRouter(mediaRouter)
    }

    private fun updateRoutes() {
        _castRoutes.value = mediaRouter.routes.filter { it.isCastRoute() }.distinctBy { it.id }
    }

    private fun syncSelectedRouteFromRouter(router: MediaRouter) {
        val selected = router.selectedRoute
        _selectedRoute.value = selected
        _routeVolume.value = selected.volume
    }

    fun selectRoute(route: MediaRouter.RouteInfo) {
        mediaRouter.selectRoute(route)
    }

    fun setRouteVolume(volume: Int) {
        _routeVolume.value = volume
        _selectedRoute.value?.requestSetVolume(volume)
    }
    
    fun disconnect() {
        mediaRouter.selectRoute(mediaRouter.defaultRoute)
        syncSelectedRouteFromRouter(mediaRouter)
        updateRoutes()
    }
    
    fun onCleared() {
        refreshRoutesJob?.cancel()
        mediaRouter.removeCallback(mediaRouterCallback)
    }

    init {
        // Initial setup? No, we wait for refresh or explicit usage?
        // ViewModel initialized it.
        // We can attach callback passively? No, battery drain.
    }
}
