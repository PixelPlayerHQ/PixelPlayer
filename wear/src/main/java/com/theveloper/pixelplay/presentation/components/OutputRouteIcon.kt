package com.theveloper.pixelplay.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.ui.graphics.vector.ImageVector
import com.theveloper.pixelplay.shared.WearVolumeState

fun outputRouteIcon(routeType: String): ImageVector = when (routeType) {
    WearVolumeState.ROUTE_TYPE_WATCH -> Icons.Rounded.Watch
    WearVolumeState.ROUTE_TYPE_HEADPHONES -> Icons.Rounded.Headphones
    WearVolumeState.ROUTE_TYPE_SPEAKER -> Icons.Rounded.Speaker
    WearVolumeState.ROUTE_TYPE_BLUETOOTH -> Icons.Rounded.Bluetooth
    WearVolumeState.ROUTE_TYPE_CAST -> Icons.Rounded.Devices
    WearVolumeState.ROUTE_TYPE_OTHER -> Icons.Rounded.Devices
    else -> Icons.Rounded.PhoneAndroid
}
