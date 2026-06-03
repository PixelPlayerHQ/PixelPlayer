package com.theveloper.pixelplay.data.service

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.service.wear.WearStatePublisher
import com.theveloper.pixelplay.ui.glancewidget.BarWidget4x1
import com.theveloper.pixelplay.ui.glancewidget.ControlWidget4x2
import com.theveloper.pixelplay.ui.glancewidget.GridWidget2x2
import com.theveloper.pixelplay.ui.glancewidget.PixelPlayGlanceWidget
import com.theveloper.pixelplay.ui.glancewidget.PlayerInfoStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs

/**
 * Owns the Glance widget + Wear OS state-publishing pipeline, extracted from
 * [MusicService] during the Pass 5 service decomposition.
 *
 * Responsibilities:
 *  - Debouncing the many `requestFullUpdate` triggers fired by player/cast events.
 *  - Diffing the freshly built [PlayerInfo] against the last published one so we
 *    only re-render widgets / re-publish Wear state when something user-visible
 *    actually changed.
 *  - Rendering every Glance widget variant and publishing to the watch.
 *
 * State *assembly* stays in the service (it is intimately tied to the player,
 * repositories, favorites and theme), supplied here through [buildPlayerInfo] and
 * [resolveCurrentMediaIdForWear]. This manager only orchestrates the *update*.
 */
internal class WidgetUpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val wearStatePublisher: WearStatePublisher,
    private val buildPlayerInfo: suspend () -> PlayerInfo,
    private val resolveCurrentMediaIdForWear: suspend () -> String?,
) {
    private companion object {
        private const val TAG = "MusicService_PixelPlay"
        // Shorter debounce for forced (user-visible) state changes so the watch can
        // foreground PixelPlay quickly; longer one coalesces incidental updates.
        private const val FORCED_DEBOUNCE_MS = 250L
        private const val NORMAL_DEBOUNCE_MS = 300L
        private const val FOLLOW_UP_DELAY_MS = 250L
        private const val QUEUE_PREVIEW_LIMIT = 4
    }

    private var debouncedUpdateJob: Job? = null
    private var followUpUpdateJob: Job? = null
    private var lastWidgetPlayerInfo: PlayerInfo? = null

    fun requestFullUpdate(force: Boolean = false) {
        debouncedUpdateJob?.cancel()
        debouncedUpdateJob = scope.launch {
            val debounceMs = if (force) FORCED_DEBOUNCE_MS else NORMAL_DEBOUNCE_MS
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            processUpdateInternal()
        }
    }

    /** Forces an immediate update plus a delayed follow-up so late-arriving
     *  metadata/artwork (delivered after the first callback) is still published. */
    fun requestWithFollowUp() {
        requestFullUpdate(force = true)
        followUpUpdateJob?.cancel()
        followUpUpdateJob = scope.launch {
            delay(FOLLOW_UP_DELAY_MS)
            requestFullUpdate(force = true)
        }
    }

    fun cancel() {
        followUpUpdateJob?.cancel()
        debouncedUpdateJob?.cancel()
    }

    /**
     * Drops the cached [PlayerInfo] so its embedded artwork ByteArray becomes
     * GC-eligible under memory pressure. The next update rebuilds it from scratch.
     */
    fun clearCachedState() {
        lastWidgetPlayerInfo = null
    }

    private suspend fun processUpdateInternal() {
        val playerInfo = buildPlayerInfo()
        val oldInfo = lastWidgetPlayerInfo

        val shouldUpdateWidgets = oldInfo == null || shouldUpdateWidget(oldInfo, playerInfo)
        val shouldPublishWear = oldInfo == null || shouldPublishWearState(oldInfo, playerInfo)

        if (shouldUpdateWidgets || shouldPublishWear) {
            lastWidgetPlayerInfo = playerInfo
        }

        if (shouldUpdateWidgets) {
            updateGlanceWidgets(playerInfo)
        }

        if (shouldPublishWear) {
            val currentMediaId = resolveCurrentMediaIdForWear()
            // Publish state to Wear OS watch
            wearStatePublisher.publishState(currentMediaId, playerInfo)
        }
    }

    private fun shouldUpdateWidget(old: PlayerInfo, new: PlayerInfo): Boolean {
        if (old.songTitle != new.songTitle) return true
        if (old.artistName != new.artistName) return true
        if (old.isPlaying != new.isPlaying) return true
        if (old.albumArtUri != new.albumArtUri) return true
        // Detect when artwork bytes arrive (null → non-null) or are cleared
        if ((old.albumArtBitmapData == null) != (new.albumArtBitmapData == null)) return true
        if (old.isFavorite != new.isFavorite) return true
        if (old.queue != new.queue) return true
        if (old.themeColors != new.themeColors) return true
        if (old.isShuffleEnabled != new.isShuffleEnabled) return true
        if (old.repeatMode != new.repeatMode) return true
        if (old.totalDurationMs != new.totalDurationMs) return true
        if (old.wearThemePalette != new.wearThemePalette) return true

        val drift = abs(old.currentPositionMs - new.currentPositionMs)
        return drift > 3000L
    }

    private fun shouldPublishWearState(old: PlayerInfo, new: PlayerInfo): Boolean {
        return shouldUpdateWidget(old, new) || old.wearQueueRevision != new.wearQueueRevision
    }

    private suspend fun updateGlanceWidgets(playerInfo: PlayerInfo) = withContext(Dispatchers.IO) {
        try {
            val glanceManager = GlanceAppWidgetManager(context)
            val widgetPlayerInfo = playerInfo.toWidgetTransportState()

            val glanceIds = glanceManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
            glanceIds.forEach { id ->
                updateAppWidgetState(context, PlayerInfoStateDefinition, id) { widgetPlayerInfo }
                PixelPlayGlanceWidget().update(context, id)
            }

            val barGlanceIds = glanceManager.getGlanceIds(BarWidget4x1::class.java)
            barGlanceIds.forEach { id ->
                updateAppWidgetState(context, PlayerInfoStateDefinition, id) { widgetPlayerInfo }
                BarWidget4x1().update(context, id)
            }

            val controlGlanceIds = glanceManager.getGlanceIds(ControlWidget4x2::class.java)
            controlGlanceIds.forEach { id ->
                updateAppWidgetState(context, PlayerInfoStateDefinition, id) { widgetPlayerInfo }
                ControlWidget4x2().update(context, id)
            }

            val gridGlanceIds = glanceManager.getGlanceIds(GridWidget2x2::class.java)
            gridGlanceIds.forEach { id ->
                updateAppWidgetState(context, PlayerInfoStateDefinition, id) { widgetPlayerInfo }
                GridWidget2x2().update(context, id)
            }

            if (glanceIds.isNotEmpty() || barGlanceIds.isNotEmpty() || controlGlanceIds.isNotEmpty() || gridGlanceIds.isNotEmpty()) {
                Timber.tag(TAG)
                    .d("Widgets actualizados: ${playerInfo.songTitle} (Original: ${glanceIds.size}, Bar: ${barGlanceIds.size}, Control: ${controlGlanceIds.size})")
            } else {
                Timber.tag(TAG).w("No se encontraron widgets para actualizar")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error al actualizar el widget")
        }
    }

    private fun PlayerInfo.toWidgetTransportState(): PlayerInfo {
        return copy(
            lyrics = null,
            isLoadingLyrics = false,
            queue = queue.take(QUEUE_PREVIEW_LIMIT),
            wearThemePalette = null,
            wearQueueRevision = "",
        )
    }
}
