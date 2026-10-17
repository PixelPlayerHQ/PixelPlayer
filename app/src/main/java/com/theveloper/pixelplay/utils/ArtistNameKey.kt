package com.theveloper.pixelplay.utils

import java.util.Locale

/**
 * Canonical identity key for an artist name.
 *
 * Two artist strings that produce the same key are treated as the *same artist*
 * everywhere in the app: they resolve to one [com.theveloper.pixelplay.data.model.Artist]
 * row, share one album image, and accumulate one track count.
 *
 * The key intentionally only normalises things that are never meaningful in an
 * artist name:
 *  - leading/trailing whitespace
 *  - runs of internal whitespace ("AC   DC" -> "ac dc")
 *  - letter case ("Beyonce" -> "beyonce")
 *
 * It deliberately does **not** strip noise words such as "feat.", "- Remastered"
 * or "The": those can legitimately be part of a real artist's name, and merging
 * on them would fold genuinely different artists into one row.
 *
 * All code that creates or resolves artist rows MUST go through this function.
 * Previously [com.theveloper.pixelplay.data.worker.SyncWorker] keyed its
 * existing-artist map on the raw name while
 * [com.theveloper.pixelplay.data.media.SongMetadataEditor] keyed on
 * `trim().lowercase()`. The two disagreed, so a resync minted a fresh row for
 * every case variant of a name that already existed, inflating the artist list
 * on every scan.
 *
 * Note: the SQL-side equivalent used by the DAO/migrations is
 * `LOWER(TRIM(name))`, which agrees with this function for every real-world case
 * (case and edge whitespace). It does not collapse *internal* whitespace runs;
 * in-memory callers must therefore use this function rather than rebuilding the
 * key by hand.
 */
fun artistNameKey(name: String): String =
    name.trim()
        .replace(WHITESPACE_RUN, " ")
        .lowercase(Locale.ROOT)

private val WHITESPACE_RUN = Regex("\\s+")