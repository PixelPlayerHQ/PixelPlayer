package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.common.Player
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.PlayerInfo
import kotlin.text.ifEmpty

class ControlWidget4x2 : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val playerInfo = currentState<PlayerInfo>()
            GlanceTheme {
                ControlWidget4x2Content(playerInfo = playerInfo, context = context)
            }
        }
    }

    @Composable
    private fun ControlWidget4x2Content(
        playerInfo: PlayerInfo,
        context: Context
    ) {
        val title = playerInfo.songTitle.ifEmpty { "PixelPlayer" }
        val artist = playerInfo.artistName.ifEmpty { "Tap to open" }
        val isPlaying = playerInfo.isPlaying
        val isShuffleEnabled = playerInfo.isShuffleEnabled
        val repeatMode = playerInfo.repeatMode
        val albumArtBitmapData = playerInfo.albumArtBitmapData
        val albumArtUri = playerInfo.albumArtUri

        val colors = playerInfo.getWidgetColors()

        val widgetCornerRadius = 28.dp
        val albumArtCornerRadius = 16.dp
        val playButtonCornerRadius = if (isPlaying) 16.dp else 20.dp
        val controlButtonCornerRadius = 16.dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.surface)
                .cornerRadius(widgetCornerRadius)
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Album Art + Info
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtImage(
                        modifier = GlanceModifier.size(72.dp),
                        bitmapData = albumArtBitmapData,
                        albumArtUri = albumArtUri,
                        size = 72.dp,
                        context = context,
                        cornerRadius = albumArtCornerRadius
                    )

                    Spacer(GlanceModifier.width(16.dp))

                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface
                            ),
                            maxLines = 2
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = artist,
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = colors.artist
                            ),
                            maxLines = 1
                        )
                    }
                }

                Spacer(GlanceModifier.defaultWeight())

                // Bottom Row: Controls
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val buttonModifier = GlanceModifier
                        .defaultWeight()
                        .height(48.dp)

                    // Shuffle Button
                    ShuffleButton(
                        modifier = buttonModifier,
                        backgroundColor = if (isShuffleEnabled) colors.onSurface else colors.prevNextBackground,
                        iconColor = if (isShuffleEnabled) colors.surface else colors.prevNextIcon,
                        cornerRadius = controlButtonCornerRadius
                    )

                    Spacer(GlanceModifier.width(6.dp))

                    // Previous Button
                    PreviousButton(
                        modifier = buttonModifier,
                        backgroundColor = colors.prevNextBackground,
                        iconColor = colors.prevNextIcon,
                        cornerRadius = controlButtonCornerRadius
                    )

                    Spacer(GlanceModifier.width(6.dp))

                    // Play/Pause Button
                    PlayPauseButton(
                        modifier = buttonModifier,
                        isPlaying = isPlaying,
                        backgroundColor = colors.playPauseBackground,
                        iconColor = colors.playPauseIcon,
                        cornerRadius = playButtonCornerRadius
                    )

                    Spacer(GlanceModifier.width(6.dp))

                    // Next Button
                    NextButton(
                        modifier = buttonModifier,
                        backgroundColor = colors.prevNextBackground,
                        iconColor = colors.prevNextIcon,
                        cornerRadius = controlButtonCornerRadius
                    )

                    Spacer(GlanceModifier.width(6.dp))

                    // Repeat Button
                    val buttonColor = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> colors.prevNextBackground
                        else -> colors.onSurface
                    }
                    val iconRes = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_24
                        else -> R.drawable.rounded_repeat_24
                    }
                    val iconColor = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> colors.prevNextIcon
                        else -> colors.surface
                    }
                    RepeatButton(
                        modifier = buttonModifier,
                        backgroundColor = buttonColor,
                        iconRes = iconRes,
                        iconColor = iconColor,
                        cornerRadius = controlButtonCornerRadius
                    )
                }
            }
        }
    }
}
