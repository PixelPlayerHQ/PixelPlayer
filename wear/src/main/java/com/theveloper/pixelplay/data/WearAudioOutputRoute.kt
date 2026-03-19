package com.theveloper.pixelplay.data

import androidx.mediarouter.media.MediaRouter
import com.theveloper.pixelplay.shared.WearVolumeState

data class WearAudioOutputRoute(
    val id: String,
    val name: String,
    val routeType: String,
    val connectionState: Int,
    val isSelected: Boolean,
    val isActive: Boolean,
) {
    val isConnecting: Boolean
        get() = connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING

    val isConnected: Boolean
        get() = routeType == WearVolumeState.ROUTE_TYPE_WATCH ||
            connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED

    val isBluetooth: Boolean
        get() = routeType == WearVolumeState.ROUTE_TYPE_BLUETOOTH ||
            routeType == WearVolumeState.ROUTE_TYPE_HEADPHONES
}
