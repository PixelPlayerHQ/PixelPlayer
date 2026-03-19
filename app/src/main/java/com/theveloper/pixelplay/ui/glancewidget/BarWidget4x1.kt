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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.model.PlayerInfo
import kotlin.text.ifEmpty

class BarWidget4x1 : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val playerInfo = currentState<PlayerInfo>()
            GlanceTheme {
                BarWidget4x1Content(playerInfo = playerInfo, context = context)
            }
        }
    }

    @Composable
    private fun BarWidget4x1Content(
        playerInfo: PlayerInfo,
        context: Context
    ) {
        val title = playerInfo.songTitle.ifEmpty { "PixelPlayer" }
        val artist = playerInfo.artistName.ifEmpty { "Tap to open" }
        val isPlaying = playerInfo.isPlaying
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
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.Start
            ) {
                AlbumArtImage(
                    modifier = GlanceModifier.size(44.dp),
                    bitmapData = albumArtBitmapData,
                    albumArtUri = albumArtUri,
                    size = 44.dp,
                    context = context,
                    cornerRadius = albumArtCornerRadius
                )

                Spacer(GlanceModifier.width(12.dp))

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        ),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = artist,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = colors.artist
                        ),
                        maxLines = 1
                    )
                }

                Spacer(GlanceModifier.width(6.dp))

                // Control Buttons Row
                Row(
                    modifier = GlanceModifier,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Button
                    PreviousButton(
                        modifier = GlanceModifier.size(40.dp),
                        backgroundColor = colors.prevNextBackground,
                        iconColor = colors.prevNextIcon,
                        cornerRadius = controlButtonCornerRadius
                    )

                    Spacer(GlanceModifier.width(6.dp))

                    // Play/Pause Button
                    PlayPauseButton(
                        modifier = GlanceModifier.size(40.dp),
                        isPlaying = isPlaying,
                        backgroundColor = colors.playPauseBackground,
                        iconColor = colors.playPauseIcon,
                        cornerRadius = playButtonCornerRadius
                    )

                    Spacer(GlanceModifier.width(6.dp))

                    // Next Button
                    NextButton(
                        modifier = GlanceModifier.size(40.dp),
                        backgroundColor = colors.prevNextBackground,
                        iconColor = colors.prevNextIcon,
                        cornerRadius = controlButtonCornerRadius
                    )
                }
            }
        }
    }
}
