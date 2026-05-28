package com.theveloper.pixelplay.data.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide flag describing whether a music playback session is currently active.
 *
 * Owned by [MusicService]: set to true in `onIsPlayingChanged(true)`, cleared on
 * stop/destroy. Read by background workers (AI, sync) to defer non-urgent work
 * while the user is listening, prioritising thermal stability and battery over
 * background completion latency.
 *
 * Intentionally a singleton object with no Hilt wiring — it is read from
 * `CoroutineWorker.doWork()` where injected fields are awkward to coordinate
 * with a cross-cutting playback signal, and the read needs to be cheap.
 */
object PlaybackActivityTracker {
    private val active = AtomicBoolean(false)

    /** True while a [MusicService] playback session is producing audio. */
    val isPlaybackActive: Boolean
        get() = active.get()

    /** Called by MusicService when playback transitions to/from playing. */
    fun setPlaybackActive(active: Boolean) {
        this.active.set(active)
    }
}
