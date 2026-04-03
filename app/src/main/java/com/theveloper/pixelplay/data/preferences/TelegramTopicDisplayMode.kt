package com.theveloper.pixelplay.data.preferences

enum class TelegramTopicDisplayMode(val storageKey: String) {
    /** One combined playlist per channel; individual topic playlists are hidden. */
    CHANNELS_ONLY("channels_only"),

    /** For forum channels: show only per-topic playlists; hide the combined channel playlist. */
    TOPICS_ONLY("topics_only"),

    /** For forum channels: show both the combined channel playlist and each topic playlist. */
    CHANNELS_AND_TOPICS("channels_and_topics");

    companion object {
        fun fromStorageKey(key: String?): TelegramTopicDisplayMode =
            entries.firstOrNull { it.storageKey == key } ?: CHANNELS_AND_TOPICS
    }
}
