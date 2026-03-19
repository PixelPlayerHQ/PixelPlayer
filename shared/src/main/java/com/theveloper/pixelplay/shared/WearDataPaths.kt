package com.theveloper.pixelplay.shared

/**
 * Shared constants for Wear Data Layer API paths.
 * Used by both the phone app and the Wear OS app for communication.
 */
object WearDataPaths {
    /** DataItem path for player state (phone -> watch) */
    const val PLAYER_STATE = "/player_state"

    /** Message path for playback commands (watch -> phone) */
    const val PLAYBACK_COMMAND = "/playback_command"

    /** Message path for playback command results (phone -> watch) */
    const val PLAYBACK_RESULT = "/playback_result"

    /** Message path for volume commands (watch -> phone) */
    const val VOLUME_COMMAND = "/volume_command"

    /** Message path for current volume state (phone -> watch) */
    const val VOLUME_STATE = "/volume_state"

    /** Key for the album art Asset within a DataItem */
    const val KEY_ALBUM_ART = "album_art"

    /** Key for the JSON state payload within a DataItem */
    const val KEY_STATE_JSON = "state_json"

    /** Key for timestamp to force DataItem updates */
    const val KEY_TIMESTAMP = "timestamp"

    /** Message path for library browse requests (watch -> phone) */
    const val BROWSE_REQUEST = "/browse_request"

    /** Message path for library browse responses (phone -> watch) */
    const val BROWSE_RESPONSE = "/browse_response"

    /** Message path for transfer requests (watch -> phone) */
    const val TRANSFER_REQUEST = "/transfer_request"

    /** Message path for transfer metadata (phone -> watch, sent before channel stream) */
    const val TRANSFER_METADATA = "/transfer_metadata"

    /** ChannelClient path for audio file streaming (phone -> watch) */
    const val TRANSFER_CHANNEL = "/transfer_audio"

    /** ChannelClient path for artwork file streaming (phone -> watch) */
    const val TRANSFER_ARTWORK_CHANNEL = "/transfer_artwork"

    /** Message path for transfer progress updates (phone -> watch) */
    const val TRANSFER_PROGRESS = "/transfer_progress"

    /** Message path for transfer cancellation (watch -> phone) */
    const val TRANSFER_CANCEL = "/transfer_cancel"

    /** Message path to ask the watch for its current saved song IDs (phone -> watch). */
    const val WATCH_LIBRARY_QUERY = "/watch_library_query"

    /** Message path carrying the watch saved song IDs snapshot (watch -> phone). */
    const val WATCH_LIBRARY_STATE = "/watch_library_state"

    /** Message path for asking the phone to resolve favorite states for watch-local songs. */
    const val FAVORITES_SYNC_REQUEST = "/favorites_sync_request"

    /** Message path carrying favorite state snapshots back to the watch. */
    const val FAVORITES_SYNC_STATE = "/favorites_sync_state"
}
