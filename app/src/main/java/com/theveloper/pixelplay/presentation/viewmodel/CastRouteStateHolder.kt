package com.theveloper.pixelplay.presentation.viewmodel

import android.os.Looper
import android.os.SystemClock
import androidx.mediarouter.media.MediaRouter
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

@ViewModelScoped
class CastRouteStateHolder @Inject constructor(
    private val castStateHolder: CastStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder
) {
    companion object {
        private const val CAST_LOG_TAG = "PlayerCastTransfer"
    }

    fun selectRoute(
        route: MediaRouter.RouteInfo,
        onCastUnavailable: (String) -> Unit
    ) {
        val selectedRouteId = castStateHolder.selectedRoute.value?.id
        val isCastRoute = castStateHolder.run { route.isCastRoute() } && !route.isDefault
        val sessionManager = castStateHolder.sessionManager
        if (isCastRoute && sessionManager == null) {
            castStateHolder.setPendingCastRouteId(null)
            castStateHolder.setCastConnecting(false)
            onCastUnavailable("Cast is unavailable right now. Restart the app and try again.")
            Timber.tag(CAST_LOG_TAG).e("Cannot select Cast route: SessionManager is null")
            return
        }

        val isSwitchingBetweenRemotes = isCastRoute &&
            (castStateHolder.isRemotePlaybackActive.value || castStateHolder.isCastConnecting.value) &&
            selectedRouteId != null &&
            selectedRouteId != route.id
        val isRetryingFailedSameRoute = isCastRoute &&
            selectedRouteId != null &&
            selectedRouteId == route.id &&
            !castStateHolder.isRemotePlaybackActive.value &&
            !castStateHolder.isCastConnecting.value

        if (isSwitchingBetweenRemotes || isRetryingFailedSameRoute) {
            castStateHolder.setPendingCastRouteId(route.id)
            castStateHolder.setCastConnecting(true)
            val currentSession = sessionManager?.currentCastSession
            if (currentSession != null) {
                sessionManager.endCurrentSession(true)
            } else if (isRetryingFailedSameRoute) {
                castStateHolder.disconnect()
            }
        } else {
            castStateHolder.setPendingCastRouteId(null)
        }

        if (isCastRoute) {
            castTransferStateHolder.primeHttpServerStart()
        }

        castStateHolder.selectRoute(route)
    }

    fun disconnect(resetConnecting: Boolean = true) {
        val start = SystemClock.elapsedRealtime()
        castStateHolder.setPendingCastRouteId(null)
        val wasRemote = castStateHolder.isRemotePlaybackActive.value
        if (wasRemote) {
            Timber.tag(CAST_LOG_TAG).i(
                "Manual disconnect requested; marking castConnecting=true until session ends. mainThread=%s",
                Looper.myLooper() == Looper.getMainLooper()
            )
            castStateHolder.setCastConnecting(true)
        }
        castStateHolder.disconnect()
        castStateHolder.setRemotePlaybackActive(false)
        if (resetConnecting && !wasRemote) {
            castStateHolder.setCastConnecting(false)
        }
        Timber.tag(CAST_LOG_TAG).i(
            "Disconnect call finished in %dms (wasRemote=%s resetConnecting=%s)",
            SystemClock.elapsedRealtime() - start,
            wasRemote,
            resetConnecting
        )
    }

    fun setRouteVolume(volume: Int) {
        castStateHolder.setRouteVolume(volume)
    }

    fun refreshCastRoutes(scope: CoroutineScope) {
        castStateHolder.refreshRoutes(scope)
    }
}
