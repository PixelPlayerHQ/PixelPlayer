package com.theveloper.pixelplay.data.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
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
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
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

@AndroidEntryPoint
class DesktopLyricsOverlayService : Service() {

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaController: MediaController? = null
    private var windowManager: WindowManager? = null
    private var overlayRootView: LinearLayout? = null
    private var currentLineView: TextView? = null
    private var nextLineView: TextView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private var lyricsJob: Job? = null
    private var progressTickerJob: Job? = null

    private var syncedLines: List<com.theveloper.pixelplay.data.model.SyncedLine> = emptyList()

    private var overlayFontSizeSp: Float = 22f
    private var overlayOpacity: Float = 0.95f
    private var overlayPosX: Int = 120
    private var overlayPosY: Int = 220

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureForeground()
        loadOverlayPreferencesAndEnsureView()
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

    private fun loadOverlayPreferencesAndEnsureView() {
        serviceScope.launch {
            overlayFontSizeSp = userPreferencesRepository.desktopLyricsFontSizeFlow.first()
            overlayOpacity = userPreferencesRepository.desktopLyricsOpacityFlow.first()
            overlayPosX = userPreferencesRepository.desktopLyricsPosXFlow.first()
            overlayPosY = userPreferencesRepository.desktopLyricsPosYFlow.first()

            ensureOverlay()
            applyTypographyAndOpacity()
            updateLyricLine()
        }
    }

    private fun ensureOverlay() {
        if (overlayRootView != null) return

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val currentText = TextView(this).apply {
            text = "♪"
            setTextColor(0xFFFFFFFF.toInt())
            setShadowLayer(8f, 0f, 0f, 0xAA000000.toInt())
            gravity = Gravity.CENTER
            setPadding(24, 10, 24, 2)
        }
        val nextText = TextView(this).apply {
            text = ""
            setTextColor(0xCCFFFFFF.toInt())
            setShadowLayer(6f, 0f, 0f, 0x77000000)
            gravity = Gravity.CENTER
            setPadding(24, 2, 24, 10)
        }

        currentLineView = currentText
        nextLineView = nextText

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x22000000)
            addView(
                currentText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                nextText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
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
            gravity = Gravity.TOP or Gravity.START
            x = overlayPosX
            y = overlayPosY
        }
        overlayLayoutParams = layoutParams

        container.setOnTouchListener(object : android.view.View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchDownX = 0f
            private var touchDownY = 0f

            override fun onTouch(v: android.view.View?, event: MotionEvent): Boolean {
                val params = overlayLayoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchDownX = event.rawX
                        touchDownY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = (startX + (event.rawX - touchDownX)).toInt().coerceAtLeast(0)
                        params.y = (startY + (event.rawY - touchDownY)).toInt().coerceAtLeast(0)
                        wm.updateViewLayout(container, params)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        overlayPosX = params.x
                        overlayPosY = params.y
                        serviceScope.launch {
                            userPreferencesRepository.setDesktopLyricsPosition(overlayPosX, overlayPosY)
                        }
                        return true
                    }
                }
                return false
            }
        })

        wm.addView(container, layoutParams)
        overlayRootView = container
    }

    private fun applyTypographyAndOpacity() {
        val current = currentLineView ?: return
        val next = nextLineView ?: return
        val root = overlayRootView ?: return

        current.textSize = overlayFontSizeSp
        next.textSize = (overlayFontSizeSp - 4f).coerceAtLeast(12f)
        root.alpha = overlayOpacity
    }

    private fun removeOverlay() {
        val wm = windowManager ?: return
        val view = overlayRootView ?: return
        runCatching { wm.removeView(view) }
        overlayRootView = null
        currentLineView = null
        nextLineView = null
        overlayLayoutParams = null
        windowManager = null
    }

    @OptIn(UnstableApi::class)
    private fun connectMediaControllerIfNeeded() {
        if (mediaController != null) return
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
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
                musicRepository.getSong(mediaId).first()
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
        val current = currentLineView ?: return
        val next = nextLineView ?: return

        if (syncedLines.isEmpty()) {
            current.text = "♪"
            next.text = ""
            return
        }

        val position = controller.currentPosition.toInt().coerceAtLeast(0)
        val currentIndex = syncedLines.indexOfLast { it.time <= position }
        if (currentIndex < 0) {
            current.text = "♪"
            next.text = ""
            return
        }

        val currentLine = syncedLines[currentIndex]
        val nextLine = syncedLines.getOrNull(currentIndex + 1)
        current.text = currentLine.line.ifBlank { "♪" }
        next.text = nextLine?.line.orEmpty()
    }

    companion object {
        private const val NOTIFICATION_ID = 1207
    }
}
