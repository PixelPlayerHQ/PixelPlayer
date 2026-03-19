package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.data.model.PlayerInfo

class GridWidget2x2 : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PlayerInfoStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val playerInfo = currentState<PlayerInfo>()
            GlanceTheme {
                GridWidget2x2Content(playerInfo = playerInfo, context = context)
            }
        }
    }

    @Composable
    private fun GridWidget2x2Content(
        playerInfo: PlayerInfo,
        context: Context
    ) {
        val isPlaying = playerInfo.isPlaying
        val albumArtBitmapData = playerInfo.albumArtBitmapData
        val albumArtUri = playerInfo.albumArtUri

        val colors = playerInfo.getWidgetColors()

        val widgetCornerRadius = 28.dp
        val itemCornerRadius = 16.dp

        val size = LocalSize.current
        val minSide = min(size.width, size.height)
        
        val dynamicIconSize = (minSide.value * 0.14f).dp
        val dynamicPlayIconSize = (minSide.value * 0.16f).dp
        val albumArtSize = (minSide.value * 0.40f).dp

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(minSide)
                    .background(colors.surface)
                    .cornerRadius(widgetCornerRadius)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    // Top
                    Row(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                    ) {
                        AlbumArtImage(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight(),
                            bitmapData = albumArtBitmapData,
                            albumArtUri = albumArtUri,
                            size = albumArtSize, // Used for optimization and placeholder size
                            context = context,
                            cornerRadius = itemCornerRadius
                        )

                        Spacer(GlanceModifier.width(6.dp))

                        // Play/Pause Button
                        PlayPauseButton(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight(),
                            isPlaying = isPlaying,
                            backgroundColor = colors.playPauseBackground,
                            iconColor = colors.playPauseIcon,
                            cornerRadius = itemCornerRadius,
                            iconSize = dynamicPlayIconSize
                        )
                    }

                    Spacer(GlanceModifier.height(6.dp))

                    // Bottom
                    Row(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                    ) {
                        // Previous Button
                        PreviousButton(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight(),
                            backgroundColor = colors.prevNextBackground,
                            iconColor = colors.prevNextIcon,
                            cornerRadius = itemCornerRadius,
                            iconSize = dynamicIconSize
                        )

                        Spacer(GlanceModifier.width(6.dp))

                        // Next Button
                        NextButton(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight(),
                            backgroundColor = colors.prevNextBackground,
                            iconColor = colors.prevNextIcon,
                            cornerRadius = itemCornerRadius,
                            iconSize = dynamicIconSize
                        )
                    }
                }
            }
        }
    }
}
