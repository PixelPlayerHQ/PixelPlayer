package com.theveloper.pixelplay.soundcloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which permalinks cannot be streamed.
 * Unplayable URLs are persisted so the next list can gray them out without
 * resolving streams again. Playable results stay in memory for this process.
 */
@Singleton
class SoundCloudPlayabilityStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val known = ConcurrentHashMap<String, Boolean>()

    init {
        preferences.getStringSet(KEY_UNPLAYABLE, emptySet()).orEmpty().forEach { url ->
            if (url.isNotBlank()) known[url] = false
        }
    }

    fun knows(url: String): Boolean = known.containsKey(url)

    fun isUnplayable(url: String): Boolean = known[url] == false

    fun remember(url: String, playable: Boolean) {
        val key = url.trim()
        if (key.isEmpty()) return
        if (!playable && known[key] == false) return
        known[key] = playable
        if (!playable) persistUnplayable()
    }

    private fun persistUnplayable() {
        val urls = known.entries
            .asSequence()
            .filter { !it.value }
            .map { it.key }
            .toList()
            .takeLast(MAX_PERSISTED)
            .toSet()
        preferences.edit().putStringSet(KEY_UNPLAYABLE, urls).apply()
    }

    private companion object {
        const val PREFS = "soundcloud_playability"
        const val KEY_UNPLAYABLE = "unplayable_urls"
        const val MAX_PERSISTED = 800
    }
}
