package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Command sent from the watch to the phone to control playback.
 * Serialized to JSON and sent via MessageClient.
 */
@Serializable
data class WearPlaybackCommand(
    val action: String,
    /** Optional song ID for PLAY_ITEM / PLAY_FROM_CONTEXT actions */
    val songId: String? = null,
    /** Optional request ID for commands that expect an explicit result back on watch. */
    val requestId: String? = null,
    /** Optional target state for idempotent toggle actions (favorite/shuffle). */
    val targetEnabled: Boolean? = null,
    /** Context type for PLAY_FROM_CONTEXT: "album", "artist", "playlist", "favorites", "all_songs" */
    val contextType: String? = null,
    /** Context ID for PLAY_FROM_CONTEXT: albumId, artistId, playlistId */
    val contextId: String? = null,
    /** Queue index used by PLAY_QUEUE_INDEX action. */
    val queueIndex: Int? = null,
    /** Minutes used by SET_SLEEP_TIMER_DURATION action. */
    val durationMinutes: Int? = null,
) {
    companion object {
        const val PLAY = "play"
        const val PAUSE = "pause"
        const val TOGGLE_PLAY_PAUSE = "toggle_play_pause"
        const val NEXT = "next"
        const val PREVIOUS = "previous"
        const val TOGGLE_FAVORITE = "toggle_favorite"
        const val TOGGLE_SHUFFLE = "toggle_shuffle"
        const val CYCLE_REPEAT = "cycle_repeat"
        /** Play a specific song by ID */
        const val PLAY_ITEM = "play_item"
        /** Play a song within a context (album/artist/playlist queue) */
        const val PLAY_FROM_CONTEXT = "play_from_context"
        /** Insert a song as next item in the active queue (without interrupting current playback). */
        const val PLAY_NEXT_FROM_CONTEXT = "play_next_from_context"
        /** Append a song to the end of the active queue. */
        const val ADD_TO_QUEUE_FROM_CONTEXT = "add_to_queue_from_context"
        /** Jump to a specific queue index and play. */
        const val PLAY_QUEUE_INDEX = "play_queue_index"
        /** Set sleep timer for a duration in minutes. */
        const val SET_SLEEP_TIMER_DURATION = "set_sleep_timer_duration"
        /** Enable/disable end-of-track sleep timer. */
        const val SET_SLEEP_TIMER_END_OF_TRACK = "set_sleep_timer_end_of_track"
        /** Cancel any active sleep timer. */
        const val CANCEL_SLEEP_TIMER = "cancel_sleep_timer"
    }
}
