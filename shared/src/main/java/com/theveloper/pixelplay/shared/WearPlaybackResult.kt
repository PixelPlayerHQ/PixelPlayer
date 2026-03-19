package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Result sent back to the watch for playback commands that require explicit confirmation.
 */
@Serializable
data class WearPlaybackResult(
    val requestId: String,
    val action: String,
    val songId: String? = null,
    val success: Boolean,
    val error: String? = null,
)
