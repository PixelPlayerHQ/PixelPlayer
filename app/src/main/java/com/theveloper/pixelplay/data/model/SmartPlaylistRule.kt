package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class SmartPlaylistRule(
    val storageKey: String,
    val title: String,
    val subtitle: String
) {
    TOP_PLAYED(
        storageKey = "top_played",
        title = "Top Played",
        subtitle = "Your most played tracks."
    ),
    RECENTLY_PLAYED(
        storageKey = "recently_played",
        title = "Recently Played",
        subtitle = "Songs you listened to most recently."
    ),
    FORGOTTEN_FAVORITES(
        storageKey = "forgotten_favorites",
        title = "Forgotten Favorites",
        subtitle = "Favorite tracks you haven't played in a while."
    ),
    NEW_GEMS(
        storageKey = "new_gems",
        title = "New Gems",
        subtitle = "Recently added tracks with low play counts."
    ),
    RECENTLY_ADDED(
        storageKey = "recently_added",
        title = "Recently Added",
        subtitle = "Tracks most recently added to your library."
    ),
    NEVER_PLAYED(
        storageKey = "never_played",
        title = "Never Played",
        subtitle = "Tracks you haven't played yet."
    ),
    LONGEST_SONGS(
        storageKey = "longest_songs",
        title = "Longest Songs",
        subtitle = "Tracks with the longest duration."
    ),
    SHORTEST_SONGS(
        storageKey = "shortest_songs",
        title = "Shortest Songs",
        subtitle = "Tracks with the shortest duration."
    );

    companion object {
        fun fromStorageKey(key: String?): SmartPlaylistRule? {
            if (key.isNullOrBlank()) return null
            return entries.firstOrNull { it.storageKey == key }
        }
    }
}
