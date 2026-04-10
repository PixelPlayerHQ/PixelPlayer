package com.theveloper.pixelplay.data.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.PixelPlayApplication
import com.theveloper.pixelplay.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.theveloper.pixelplay.data.repository.MusicRepository

@AndroidEntryPoint
class DesktopLyricsOverlayService : Service() {

    @Inject
    lateinit var musicRepository: MusicRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaController: MediaController? = null
    private var overlayView: TextView? = null
    private var windowManager: WindowManager? = null
    private var lyricsJob: Job? = null
    private var progressTickerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            loadLyricsForCurrentMedia()
            updateLyricLine()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED)
            ) {
                updateLyricLine()
            }
        }
    }

    private var syncedLines: List<com.theveloper.pixelplay.data.model.SyncedLine> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureForeground()
        ensureOverlay()
        connectMediaControllerIfNeeded()

        return START_STICKY
    }

    override fun onDestroy() {
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
        removeOverlay()
        lyricsJob?.cancel()
        progressTickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureForeground() {
        val notification: Notification = NotificationCompat.Builder(this, PixelPlayApplication.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Desktop lyrics running")
            .setOngoing(true)
            .setContentIntent(createOpenAppPendingIntent())
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    @OptIn(UnstableApi::class)
    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureOverlay() {
        if (overlayView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val textView = TextView(this).apply {
            text = "♪"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setShadowLayer(8f, 0f, 0f, 0xAA000000.toInt())
            setPadding(24, 16, 24, 16)
            gravity = Gravity.CENTER
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 220
        }

        wm.addView(textView, layoutParams)
        overlayView = textView
    }

    private fun removeOverlay() {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        runCatching { wm.removeView(view) }
        overlayView = null
        windowManager = null
    }

    @OptIn(UnstableApi::class)
    private fun connectMediaControllerIfNeeded() {
        if (mediaController != null) return
        val token = SessionToken(this, android.content.ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    loadLyricsForCurrentMedia()
                    updateLyricLine()
                    startProgressTicker()
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = serviceScope.launch {
            while (isActive) {
                updateLyricLine()
                delay(250)
            }
        }
    }

    private fun loadLyricsForCurrentMedia() {
        val controller = mediaController ?: return
        val mediaId = controller.currentMediaItem?.mediaId ?: return
        lyricsJob?.cancel()
        lyricsJob = serviceScope.launch {
            val song = withContext(Dispatchers.IO) {
                musicRepository.getSong(mediaId).let { flow ->
                    flow.first()
                }
            } ?: return@launch

            val lyrics = withContext(Dispatchers.IO) {
                musicRepository.getLyrics(song)
            }
            syncedLines = lyrics?.synced.orEmpty()
            updateLyricLine()
        }
    }

    private fun updateLyricLine() {
        val controller = mediaController ?: return
        val view = overlayView ?: return
        if (syncedLines.isEmpty()) {
            view.text = "♪"
            return
        }

        val position = controller.currentPosition.toInt().coerceAtLeast(0)
        val currentIndex = syncedLines.indexOfLast { it.time <= position }
        if (currentIndex < 0) {
            view.text = "♪"
            return
        }
        val line = syncedLines[currentIndex]
        view.text = line.line.ifBlank { "♪" }
    }

    companion object {
        private const val NOTIFICATION_ID = 1207
    }
}
