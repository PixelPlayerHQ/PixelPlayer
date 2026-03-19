package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Command sent from the watch to the phone to control volume.
 * Serialized to JSON and sent via MessageClient.
 */
@Serializable
data class WearVolumeCommand(
    val direction: String,
    /** Optional absolute volume value in percent (0-100). If set, direction is ignored. */
    val value: Int? = null,
) {
    companion object {
        const val UP = "up"
        const val DOWN = "down"
        const val SET = "set"
        const val QUERY = "query"
    }
}
