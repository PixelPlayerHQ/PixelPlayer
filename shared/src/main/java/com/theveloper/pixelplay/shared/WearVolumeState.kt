package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Lightweight DTO for syncing STREAM_MUSIC volume between phone and watch.
 */
@Serializable
data class WearVolumeState(
    val level: Int = 0,
    val max: Int = 0,
    val routeType: String = ROUTE_TYPE_PHONE,
    val routeName: String = "",
) {
    companion object {
        const val ROUTE_TYPE_PHONE = "phone"
        const val ROUTE_TYPE_WATCH = "watch"
        const val ROUTE_TYPE_HEADPHONES = "headphones"
        const val ROUTE_TYPE_SPEAKER = "speaker"
        const val ROUTE_TYPE_BLUETOOTH = "bluetooth"
        const val ROUTE_TYPE_CAST = "cast"
        const val ROUTE_TYPE_OTHER = "other"
    }
}
