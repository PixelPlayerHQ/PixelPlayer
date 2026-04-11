package com.theveloper.pixelplay.data.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
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

    private var overlayRoot: FrameLayout? = null
    private var lyricCard: LinearLayout? = null
    private var currentLineView: TextView? = null
    private var nextLineView: TextView? = null
    private var controlsRow: LinearLayout? = null
    private var lockButton: ImageButton? = null
    private var closeButton: ImageButton? = null

    private var overlayParams: WindowManager.LayoutParams? = null

    private var lyricsJob: Job? = null
    private var progressTickerJob: Job? = null

    private var syncedLines: List<com.theveloper.pixelplay.data.model.SyncedLine> = emptyList()

    private var overlayLocked: Boolean = false
    private var overlayOpacity: Float = 0.95f
    private var overlayPosY: Int = 220

    private val fixedFontSizeSp = 22f

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
        loadPrefsAndBuildOverlayIfNeeded()
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

    private fun loadPrefsAndBuildOverlayIfNeeded() {
        serviceScope.launch {
            overlayOpacity = userPreferencesRepository.desktopLyricsOpacityFlow.first()
            overlayPosY = userPreferencesRepository.desktopLyricsPosYFlow.first()
            overlayLocked = userPreferencesRepository.desktopLyricsLockedFlow.first()
            ensureOverlay()
            applyVisualState()
            updateLyricLine()
        }
    }

    private fun ensureOverlay() {
        if (overlayRoot != null) return

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val currentText = TextView(this).apply {
            text = "♪"
            setTextColor(Color.WHITE)
            textSize = fixedFontSizeSp
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(10f, 0f, 0f, 0xCC000000.toInt())
            gravity = Gravity.CENTER
            setPadding(20, 8, 20, 2)
            maxLines = 1
        }
        val nextText = TextView(this).apply {
            text = ""
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 16f
            setShadowLayer(6f, 0f, 0f, 0x99000000.toInt())
            gravity = Gravity.CENTER
            setPadding(20, 2, 20, 10)
            maxLines = 1
        }

        val lockBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_lock_lock)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                overlayLocked = true
                serviceScope.launch { userPreferencesRepository.setDesktopLyricsLocked(true) }
                applyVisualState()
            }
        }
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                serviceScope.launch {
                    userPreferencesRepository.setDesktopLyricsEnabled(false)
                    userPreferencesRepository.setDesktopLyricsLocked(false)
                }
                stopSelf()
            }
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            val buttonSize = (resources.displayMetrics.density * 34f).toInt()
            addView(lockBtn, LinearLayout.LayoutParams(buttonSize, buttonSize).apply { marginEnd = 18 })
            addView(closeBtn, LinearLayout.LayoutParams(buttonSize, buttonSize))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = resources.displayMetrics.density * 18f
                setColor(Color.TRANSPARENT)
                setStroke((resources.displayMetrics.density * 1f).toInt(), 0x33FFFFFF)
            }
            setPadding(8, 6, 8, 6)
            addView(
                currentText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                nextText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                actionRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                card,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val overlayWidth = (screenWidth * 0.88f).toInt()

        val params = WindowManager.LayoutParams(
            overlayWidth,
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
            x = 0
            y = overlayPosY
        }

        root.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0
            private var touchDownY = 0f
            private var moved = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                val lp = overlayParams ?: return false
                if (overlayLocked) {
                    return false
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = lp.y
                        touchDownY = event.rawY
                        moved = false
                        showControls(true)
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dy = (event.rawY - touchDownY).toInt()
                        if (kotlin.math.abs(dy) > 4) moved = true
                        lp.y = (startY + dy).coerceAtLeast(0)
                        wm.updateViewLayout(root, lp)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        overlayPosY = lp.y
                        serviceScope.launch { userPreferencesRepository.setDesktopLyricsPosition(0, overlayPosY) }
                        if (!moved) showControls(true)
                        return true
                    }
                }
                return false
            }
        })

        wm.addView(root, params)

        overlayRoot = root
        lyricCard = card
        currentLineView = currentText
        nextLineView = nextText
        controlsRow = actionRow
        lockButton = lockBtn
        closeButton = closeBtn
        overlayParams = params
    }

    private fun applyVisualState() {
        val root = overlayRoot ?: return
        root.alpha = overlayOpacity
        val params = overlayParams ?: return
        if (overlayLocked) {
            controlsRow?.visibility = View.GONE
            root.setBackgroundColor(Color.TRANSPARENT)
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager?.updateViewLayout(root, params)
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            windowManager?.updateViewLayout(root, params)
        }
    }

    private fun showControls(show: Boolean) {
        val root = overlayRoot ?: return
        val row = controlsRow ?: return
        val card = lyricCard ?: return
        if (overlayLocked) {
            row.visibility = View.GONE
            root.setBackgroundColor(Color.TRANSPARENT)
            (card.background as? GradientDrawable)?.setColor(Color.TRANSPARENT)
            return
        }
        row.visibility = if (show) View.VISIBLE else View.GONE
        root.setBackgroundColor(Color.TRANSPARENT)
        (card.background as? GradientDrawable)?.setColor(if (show) 0x3A000000 else Color.TRANSPARENT)
    }

    private fun removeOverlay() {
        val wm = windowManager ?: return
        val view = overlayRoot ?: return
        runCatching { wm.removeView(view) }

        overlayRoot = null
        lyricCard = null
        currentLineView = null
        nextLineView = null
        controlsRow = null
        lockButton = null
        closeButton = null
        overlayParams = null
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
