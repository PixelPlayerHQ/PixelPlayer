package com.theveloper.pixelplay.presentation.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.theveloper.pixelplay.data.service.MusicNotificationProvider
import com.theveloper.pixelplay.data.service.SleepTimerReceiver
import com.theveloper.pixelplay.data.EotStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages sleep timer state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Duration-based sleep timer with AlarmManager
 * - End-of-track (EOT) timer functionality
 * - Counted play functionality
 * - Timer display state
 */
@Singleton
class SleepTimerStateHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Timer State
    private val _sleepTimerEndTimeMillis = MutableStateFlow<Long?>(null)
    val sleepTimerEndTimeMillis: StateFlow<Long?> = _sleepTimerEndTimeMillis.asStateFlow()

    private val _isEndOfTrackTimerActive = MutableStateFlow(false)
    val isEndOfTrackTimerActive: StateFlow<Boolean> = _isEndOfTrackTimerActive.asStateFlow()

    private val _activeTimerValueDisplay = MutableStateFlow<String?>(null)
    val activeTimerValueDisplay: StateFlow<String?> = _activeTimerValueDisplay.asStateFlow()

    private val _playCount = MutableStateFlow(1f)
    val playCount: StateFlow<Float> = _playCount.asStateFlow()

    // Internal jobs
    private var sleepTimerJob: Job? = null
    private var eotSongMonitorJob: Job? = null

    // Dependencies that will be injected via initialize
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private var scope: CoroutineScope? = null
    private var toastEmitter: (suspend (String) -> Unit)? = null
    private var mediaControllerProvider: (() -> MediaController?)? = null
    private var currentSongIdProvider: (() -> StateFlow<String?>)? = null
    private var songTitleResolver: ((String?) -> String)? = null

    /**
     * Initialize with dependencies from ViewModel.
     * Must be called before using timer functions.
     */
    fun initialize(
        scope: CoroutineScope,
        toastEmitter: suspend (String) -> Unit,
        mediaControllerProvider: () -> MediaController?,
        currentSongIdProvider: () -> StateFlow<String?>,
        songTitleResolver: (String?) -> String
    ) {
        this.scope = scope
        this.toastEmitter = { msg -> scope.launch { toastEmitter(msg) } }
        this.mediaControllerProvider = mediaControllerProvider
        this.currentSongIdProvider = currentSongIdProvider
        this.songTitleResolver = songTitleResolver
    }

    /**
     * Set a duration-based sleep timer.
     * Uses AlarmManager for reliability + coroutine for UI state clearing.
     */
    fun setSleepTimer(durationMinutes: Int) {
        val scope = this.scope ?: return

        // Cancel any existing EOT timer first
        if (_isEndOfTrackTimerActive.value) {
            eotSongMonitorJob?.cancel()
            cancelSleepTimer(suppressDefaultToast = true)
        }

        sleepTimerJob?.cancel()
        val durationMillis = TimeUnit.MINUTES.toMillis(durationMinutes.toLong())
        val endTime = System.currentTimeMillis() + durationMillis
        _sleepTimerEndTimeMillis.value = endTime
        _activeTimerValueDisplay.value = "$durationMinutes minutes"

        // Schedule alarm for reliable triggering
        val intent = Intent(context, SleepTimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    endTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to schedule exact alarm for sleep timer")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                endTime,
                pendingIntent
            )
        }

        scope.launch { toastEmitter?.invoke("Timer set for $durationMinutes minutes.") }
    }

    /**
     * Start counted play mode - play N more tracks.
     */
    fun playCounted(count: Int) {
        _playCount.value = count.toFloat()
        val args = Bundle().apply { putInt("count", count) }
        mediaControllerProvider?.invoke()?.sendCustomCommand(
            SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_COUNTED_PLAY, Bundle.EMPTY),
            args
        )
    }

    /**
     * Cancel counted play mode.
     */
    fun cancelCountedPlay() {
        val args = Bundle()
        _playCount.value = 1f
        mediaControllerProvider?.invoke()?.sendCustomCommand(
            SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_COUNTED_PLAY, Bundle.EMPTY),
            args
        )
    }

    /**
     * Set or cancel end-of-track timer.
     */
    fun setEndOfTrackTimer(enable: Boolean, currentSongId: String?) {
        val scope = this.scope ?: return

        if (enable) {
            if (currentSongId == null) {
                scope.launch { toastEmitter?.invoke("Cannot enable End of Track: No active song.") }
                return
            }

            _activeTimerValueDisplay.value = "End of Track"
            _isEndOfTrackTimerActive.value = true
            EotStateHolder.setEotTargetSong(currentSongId)

            sleepTimerJob?.cancel()
            _sleepTimerEndTimeMillis.value = null

            // Monitor for song changes
            eotSongMonitorJob?.cancel()
            eotSongMonitorJob = scope.launch {
                currentSongIdProvider?.invoke()
                    ?.filterNotNull() // skip initial null emission from stateIn initialValue
                    ?.collect { newSongId ->
                    if (_isEndOfTrackTimerActive.value &&
                        EotStateHolder.eotTargetSongId.value != null &&
                        newSongId != EotStateHolder.eotTargetSongId.value) {

                        val oldSongTitle = songTitleResolver?.invoke(EotStateHolder.eotTargetSongId.value) ?: "Previous track"
                        val newSongTitle = songTitleResolver?.invoke(newSongId) ?: "Current track"

                        toastEmitter?.invoke("End of Track timer deactivated: song changed from $oldSongTitle to $newSongTitle.")
                        cancelSleepTimer(suppressDefaultToast = true)

                        eotSongMonitorJob?.cancel()
                        eotSongMonitorJob = null
                    }
                }
            }

            scope.launch { toastEmitter?.invoke("Playback will stop at end of track.") }
        } else {
            eotSongMonitorJob?.cancel()
            if (_isEndOfTrackTimerActive.value && EotStateHolder.eotTargetSongId.value != null) {
                cancelSleepTimer()
            }
        }
    }

    /**
     * Cancel any active sleep timer or EOT timer.
     */
    fun cancelSleepTimer(overrideToastMessage: String? = null, suppressDefaultToast: Boolean = false) {
        val scope = this.scope ?: return
        val wasAnythingActive = _activeTimerValueDisplay.value != null

        // Cancel Alarm
        val intent = Intent(context, SleepTimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        // Cancel duration timer
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndTimeMillis.value = null

        // Cancel EOT timer
        eotSongMonitorJob?.cancel()
        eotSongMonitorJob = null
        _isEndOfTrackTimerActive.value = false
        EotStateHolder.setEotTargetSong(null)

        // Clear display
        _activeTimerValueDisplay.value = null

        // Handle toast
        when {
            overrideToastMessage != null -> scope.launch { toastEmitter?.invoke(overrideToastMessage) }
            !suppressDefaultToast && wasAnythingActive -> scope.launch { toastEmitter?.invoke("Timer cancelled.") }
        }
    }

    /**
     * Update play count for counted play display.
     */
    fun updatePlayCount(count: Float) {
        _playCount.value = count
    }

    /**
     * Cleanup when ViewModel is cleared.
     */
    fun onCleared() {
        sleepTimerJob?.cancel()
        eotSongMonitorJob?.cancel()
        scope = null
        toastEmitter = null
        mediaControllerProvider = null
        currentSongIdProvider = null
        songTitleResolver = null
    }
}
