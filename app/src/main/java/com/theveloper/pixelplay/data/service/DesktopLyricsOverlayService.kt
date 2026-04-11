package com.theveloper.pixelplay.data.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
    private var topRow: LinearLayout? = null
    private var controlsRow: LinearLayout? = null
    private var settingsPanel: LinearLayout? = null
    private var homeButton: ImageButton? = null
    private var lockButton: ImageButton? = null
    private var settingsButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var settingsOpacitySeekBar: SeekBar? = null
    private var settingsColorSeekBar: SeekBar? = null
    private var settingsOpacityValue: TextView? = null

    private var overlayParams: WindowManager.LayoutParams? = null

    private var lyricsJob: Job? = null
    private var progressTickerJob: Job? = null
    private var prefsObserverJob: Job? = null
    private var autoHideControlsJob: Job? = null

    private var syncedLines: List<com.theveloper.pixelplay.data.model.SyncedLine> = emptyList()

    private var overlayLocked: Boolean = false
    private var expandedControlsVisible: Boolean = false
    private var overlayOpacity: Float = 0.95f
    private var lyricColor: Int = 0xFF4DB6AC.toInt()
    private var lyricAlpha: Float = 0.95f
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
        observePrefsAndBuildOverlayIfNeeded()
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
        prefsObserverJob?.cancel()
        autoHideControlsJob?.cancel()
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

    private fun observePrefsAndBuildOverlayIfNeeded() {
        prefsObserverJob?.cancel()
        prefsObserverJob = serviceScope.launch {
            combine(
                userPreferencesRepository.desktopLyricsEnabledFlow,
                userPreferencesRepository.desktopLyricsOpacityFlow,
                userPreferencesRepository.desktopLyricsTextColorFlow,
                userPreferencesRepository.desktopLyricsPosYFlow,
                userPreferencesRepository.desktopLyricsLockedFlow
            ) { enabled, opacity, textColor, posY, locked ->
                OverlayPrefState(
                    enabled = enabled,
                    opacity = opacity,
                    textColor = textColor,
                    posY = posY,
                    locked = locked
                )
            }.collectLatest { prefState ->
                if (!prefState.enabled) {
                    stopSelf()
                    return@collectLatest
                }

                overlayOpacity = prefState.opacity
                lyricAlpha = prefState.opacity
                lyricColor = prefState.textColor
                overlayPosY = prefState.posY
                overlayLocked = prefState.locked

                ensureOverlay()

                val root = overlayRoot
                val params = overlayParams
                if (root != null && params != null && params.y != overlayPosY) {
                    params.y = overlayPosY
                    windowManager?.updateViewLayout(root, params)
                }

                applyVisualState()
                applyLyricsColor()
                syncSettingsPanelControls()
                updateLyricLine()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun ensureOverlay() {
        if (overlayRoot != null) return

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val currentText = TextView(this).apply {
            text = "♪"
            setTextColor(lyricColor)
            textSize = fixedFontSizeSp
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(10f, 0f, 0f, 0xCC000000.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(4), dp(20), dp(2))
            maxLines = 1
            alpha = lyricAlpha
        }
        val nextText = TextView(this).apply {
            text = ""
            setTextColor(adjustSecondaryColor(lyricColor))
            textSize = fixedFontSizeSp
            setShadowLayer(6f, 0f, 0f, 0x99000000.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(2), dp(20), dp(4))
            maxLines = 1
            alpha = lyricAlpha
        }

        val panel = buildSettingsPanel()

        val openAppClickListener = View.OnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        }

        val homeBtn = ImageButton(this).apply {
            setImageResource(R.drawable.monochrome_player)
            setBackgroundColor(Color.TRANSPARENT)
            // 强制保持比例并居中，不填充整个 View
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener(openAppClickListener)
        }

        val lockBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_lock_lock)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(0xFFD6D6D6.toInt())
            setOnClickListener {
                overlayLocked = true
                serviceScope.launch { userPreferencesRepository.setDesktopLyricsLocked(true) }
                applyVisualState()
            }
        }
        val settingsBtn = ImageButton(this).apply {
            setImageResource(R.drawable.rounded_settings_24)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(0xFFD6D6D6.toInt())
            setOnClickListener {
                toggleSettingsPanel()
            }
        }
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    userPreferencesRepository.setDesktopLyricsEnabled(false)
                    userPreferencesRepository.setDesktopLyricsLocked(false)
                    withContext(Dispatchers.Main) {
                        stopSelf()
                    }
                }
            }
        }

        val topActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(homeBtn, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(View(this@DesktopLyricsOverlayService), LinearLayout.LayoutParams(0, 0, 1f))
            addView(closeBtn, LinearLayout.LayoutParams(dp(28), dp(28)))
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val buttonSize = dp(36)
            addView(lockBtn, LinearLayout.LayoutParams(buttonSize, buttonSize))
            addView(View(this@DesktopLyricsOverlayService), LinearLayout.LayoutParams(0, 0, 1f))
            addView(settingsBtn, LinearLayout.LayoutParams(buttonSize, buttonSize))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = resources.displayMetrics.density * 16f
                setColor(Color.TRANSPARENT)
                setStroke(0, Color.TRANSPARENT)
            }
            setPadding(dp(12), dp(12), dp(12), dp(10))
            addView(
                topActionsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                currentText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                View(this@DesktopLyricsOverlayService),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(8)
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
            addView(
                panel,
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
                        scheduleControlsAutoHide()
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dy = (event.rawY - touchDownY).toInt()
                        if (kotlin.math.abs(dy) > 4) moved = true
                        lp.y = clampOverlayY(startY + dy)
                        wm.updateViewLayout(root, lp)
                        scheduleControlsAutoHide()
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        overlayPosY = lp.y
                        serviceScope.launch { userPreferencesRepository.setDesktopLyricsPosition(0, overlayPosY) }
                        if (!moved) showControls(true)
                        scheduleControlsAutoHide()
                        return true
                    }
                }
                return false
            }
        })

        wm.addView(root, params)

        root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val lp = overlayParams ?: return
                val clampedY = clampOverlayY(lp.y)
                if (clampedY != lp.y) {
                    lp.y = clampedY
                    windowManager?.updateViewLayout(root, lp)
                    overlayPosY = clampedY
                    serviceScope.launch { userPreferencesRepository.setDesktopLyricsPosition(0, overlayPosY) }
                }
                root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        listOf(homeBtn, closeBtn, lockBtn, settingsBtn).forEach(::attachPressAnimation)

        overlayRoot = root
        lyricCard = card
        currentLineView = currentText
        nextLineView = nextText
        topRow = topActionsRow
        controlsRow = actionRow
        settingsPanel = panel
        homeButton = homeBtn
        lockButton = lockBtn
        settingsButton = settingsBtn
        closeButton = closeBtn
        overlayParams = params
        syncSettingsPanelControls()
    }

    private fun applyVisualState() {
        val root = overlayRoot ?: return
        root.alpha = 1f
        val params = overlayParams ?: return
        if (overlayLocked) {
            expandedControlsVisible = false
            showControls(false)
            autoHideControlsJob?.cancel()
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager?.updateViewLayout(root, params)
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            windowManager?.updateViewLayout(root, params)
            showControls(expandedControlsVisible)
            if (expandedControlsVisible) {
                scheduleControlsAutoHide()
            }
        }
    }

    private fun showControls(show: Boolean) {
        val root = overlayRoot ?: return
        val header = topRow ?: return
        val row = controlsRow ?: return
        val panel = settingsPanel ?: return
        val card = lyricCard ?: return
        if (overlayLocked) {
            header.visibility = View.GONE
            row.visibility = View.GONE
            panel.visibility = View.GONE
            root.setBackgroundColor(Color.TRANSPARENT)
            (card.background as? GradientDrawable)?.setColor(Color.TRANSPARENT)
            (card.background as? GradientDrawable)?.setStroke(0, Color.TRANSPARENT)
            autoHideControlsJob?.cancel()
            return
        }
        expandedControlsVisible = show
        header.visibility = if (show) View.VISIBLE else View.GONE
        row.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) panel.visibility = View.GONE
        settingsButton?.setColorFilter(0xFFD6D6D6.toInt())
        root.setBackgroundColor(Color.TRANSPARENT)
        (card.background as? GradientDrawable)?.setColor(if (show) 0xCC222222.toInt() else Color.TRANSPARENT)
        (card.background as? GradientDrawable)?.setStroke(if (show) dp(1) else 0, if (show) 0x33FFFFFF else Color.TRANSPARENT)
        if (!show) {
            autoHideControlsJob?.cancel()
        }
    }

    private fun scheduleControlsAutoHide() {
        if (overlayLocked || !expandedControlsVisible) return
        autoHideControlsJob?.cancel()
        autoHideControlsJob = serviceScope.launch {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            if (!overlayLocked) {
                showControls(false)
            }
        }
    }

    private fun removeOverlay() {
        val wm = windowManager ?: return
        val view = overlayRoot ?: return
        runCatching { wm.removeView(view) }

        overlayRoot = null
        lyricCard = null
        currentLineView = null
        nextLineView = null
        topRow = null
        controlsRow = null
        settingsPanel = null
        homeButton = null
        lockButton = null
        settingsButton = null
        closeButton = null
        settingsOpacitySeekBar = null
        settingsColorSeekBar = null
        settingsOpacityValue = null
        overlayParams = null
        windowManager = null
    }

    private fun buildSettingsPanel(): LinearLayout {
        val titleText = TextView(this).apply {
            text = "Lyrics Settings"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val subtitleText = TextView(this).apply {
            text = "Customize color and opacity"
            setTextColor(0xFFB0BEC5.toInt())
            textSize = 11f
        }

        val panelHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleText)
            addView(subtitleText)
        }

        val customTab = buildSettingsTab(text = "Custom", selected = true)
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            addView(customTab)
        }

        val colorLabel = TextView(this).apply {
            text = "Color"
            setTextColor(0xFFB0BEC5.toInt())
            textSize = 12f
        }

        val colorSeek = SeekBar(this).apply {
            max = 360
            progress = colorHue(lyricColor)
            progressTintList = ColorStateList.valueOf(0xFF4FC3F7.toInt())
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    lyricColor = Color.HSVToColor(floatArrayOf(progress.toFloat(), 0.62f, 0.95f))
                    applyLyricsColor()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    autoHideControlsJob?.cancel()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val color = lyricColor
                    serviceScope.launch { userPreferencesRepository.setDesktopLyricsTextColor(color) }
                    scheduleControlsAutoHide()
                }
            })
        }
        settingsColorSeekBar = colorSeek

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }

        val colorOptions = listOf(0xFF4DB6AC.toInt(), 0xFF4FC3F7.toInt(), 0xFFFFC107.toInt(), 0xFFE91E63.toInt(), 0xFFFFFFFF.toInt())
        colorOptions.forEach { color ->
            colorRow.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(1), 0x66FFFFFF)
                }
                setOnClickListener {
                    lyricColor = color
                    applyLyricsColor()
                    settingsColorSeekBar?.progress = colorHue(color)
                    serviceScope.launch { userPreferencesRepository.setDesktopLyricsTextColor(color) }
                    scheduleControlsAutoHide()
                }
                attachPressAnimation(this)
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(10)
            })
        }

        val opacityLabel = TextView(this).apply {
            text = "Opacity"
            setTextColor(0xFFB0BEC5.toInt())
            textSize = 12f
        }

        val opacityValue = TextView(this).apply {
            text = "95%"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        settingsOpacityValue = opacityValue

        val opacityHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(4))
            addView(opacityLabel)
            addView(View(this@DesktopLyricsOverlayService), LinearLayout.LayoutParams(0, 0, 1f))
            addView(opacityValue)
        }

        val opacitySeek = SeekBar(this).apply {
            max = 100
            progress = (overlayOpacity * 100f).toInt()
            progressTintList = ColorStateList.valueOf(Color.WHITE)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val newOpacity = (progress.coerceIn(20, 100)) / 100f
                    lyricAlpha = newOpacity
                    applyLyricsColor()
                    settingsOpacityValue?.text = "${(newOpacity * 100f).toInt()}%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    autoHideControlsJob?.cancel()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val target = ((seekBar?.progress ?: 95).coerceIn(20, 100)) / 100f
                    serviceScope.launch { userPreferencesRepository.setDesktopLyricsOpacity(target) }
                    scheduleControlsAutoHide()
                }
            })
        }
        settingsOpacitySeekBar = opacitySeek

        val sectionBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(ColorUtils.setAlphaComponent(Color.WHITE, 22))
            setStroke(dp(1), 0x22FFFFFF)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            translationY = dp(6).toFloat()
            setPadding(dp(4), dp(12), dp(4), dp(2))
            addView(panelHeader)
            addView(tabRow)
            addView(LinearLayout(this@DesktopLyricsOverlayService).apply {
                orientation = LinearLayout.VERTICAL
                background = sectionBackground
                setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(colorLabel)
                addView(colorSeek)
                addView(colorRow)
                addView(opacityHeader)
                addView(opacitySeek)
            })
        }
    }

    private fun toggleSettingsPanel() {
        val panel = settingsPanel ?: return
        val shouldShow = panel.visibility != View.VISIBLE
        if (shouldShow) {
            panel.visibility = View.VISIBLE
            panel.animate().alpha(1f).translationY(0f).setDuration(180L).start()
            showControls(true)
        } else {
            panel.animate().alpha(0f).translationY(dp(6).toFloat()).setDuration(140L).withEndAction {
                panel.visibility = View.GONE
                settingsButton?.setColorFilter(0xFFD6D6D6.toInt())
            }.start()
        }
        settingsButton?.setColorFilter(0xFFD6D6D6.toInt())
        scheduleControlsAutoHide()
    }

    private fun buildSettingsTab(text: String, selected: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setTextColor(if (selected) Color.WHITE else 0xFFB0BEC5.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(50).toFloat()
                if (selected) {
                    setColor(ColorUtils.setAlphaComponent(Color.WHITE, 46))
                    setStroke(0, Color.TRANSPARENT)
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), 0x33FFFFFF)
                }
            }
        }
    }

    private fun attachPressAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    ObjectAnimator.ofFloat(v, View.SCALE_X, v.scaleX, 0.9f).setDuration(100L).start()
                    ObjectAnimator.ofFloat(v, View.SCALE_Y, v.scaleY, 0.9f).setDuration(100L).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ObjectAnimator.ofFloat(v, View.SCALE_X, v.scaleX, 1f).setDuration(120L).start()
                    ObjectAnimator.ofFloat(v, View.SCALE_Y, v.scaleY, 1f).setDuration(120L).start()
                }
            }
            false
        }
    }

    private fun syncSettingsPanelControls() {
        settingsOpacitySeekBar?.progress = (lyricAlpha * 100f).toInt().coerceIn(20, 100)
        settingsOpacityValue?.text = "${(lyricAlpha * 100f).toInt()}%"
        settingsColorSeekBar?.progress = colorHue(lyricColor)
    }

    private fun colorHue(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0].toInt().coerceIn(0, 360)
    }

    private fun applyLyricsColor() {
        currentLineView?.setTextColor(lyricColor)
        nextLineView?.setTextColor(adjustSecondaryColor(lyricColor))
        currentLineView?.alpha = lyricAlpha
        nextLineView?.alpha = lyricAlpha
    }

    private fun clampOverlayY(targetY: Int): Int {
        val root = overlayRoot
        val screenHeight = resources.displayMetrics.heightPixels
        if (root == null || root.height <= 0) {
            return targetY.coerceIn(0, screenHeight)
        }
        val maxY = (screenHeight - root.height).coerceAtLeast(0)
        return targetY.coerceIn(0, maxY)
    }

    private fun adjustSecondaryColor(primary: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(primary, hsv)
        hsv[1] = (hsv[1] * 0.75f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.05f).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

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
        private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3_000L
    }

    private data class OverlayPrefState(
        val enabled: Boolean,
        val opacity: Float,
        val textColor: Int,
        val posY: Int,
        val locked: Boolean
    )
}
